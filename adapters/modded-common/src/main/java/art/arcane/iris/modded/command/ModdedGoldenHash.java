/*
 * Iris is a World Generator for Minecraft Servers
 * Copyright (c) 2026 Arcane Arts (Volmit Software)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package art.arcane.iris.modded.command;

import art.arcane.iris.engine.framework.Engine;
import art.arcane.iris.modded.ModdedBlockBuffer;
import art.arcane.iris.modded.ModdedEngineBootstrap;
import art.arcane.iris.spi.IrisPlatforms;
import art.arcane.iris.spi.PlatformBiome;
import art.arcane.iris.spi.PlatformBlockState;
import art.arcane.iris.util.common.parallel.MultiBurst;
import art.arcane.iris.util.project.hunk.Hunk;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public final class ModdedGoldenHash {
    public enum Mode {
        AUTO,
        CAPTURE,
        VERIFY
    }

    private static final Logger LOGGER = LoggerFactory.getLogger("Iris");
    private static final String FORMAT = "iris-goldenhash v1";
    private static final int BIOME_STEP = 4;
    private static final int MAX_REPORTED_MISMATCHES = 10;
    private static final AtomicBoolean ACTIVE = new AtomicBoolean(false);

    private final CommandSourceStack source;
    private final MinecraftServer server;
    private final ServerLevel level;
    private final Engine engine;
    private final int radius;
    private final int threads;
    private final Mode mode;
    private final File goldenFile;

    private ModdedGoldenHash(CommandSourceStack source, ServerLevel level, Engine engine, int radius, int threads, Mode mode) {
        this.source = source;
        this.server = source.getServer();
        this.level = level;
        this.engine = engine;
        this.radius = Math.max(0, radius);
        this.threads = Math.max(1, threads);
        this.mode = mode;
        File goldenDir = ModdedEngineBootstrap.loader().configDir().resolve("irisworldgen").resolve("golden").toFile();
        goldenDir.mkdirs();
        this.goldenFile = new File(goldenDir, engine.getDimension().getLoadKey()
                + "-s" + level.getSeed()
                + "-c0x0-r" + this.radius + ".hashes");
    }

    public static void start(CommandSourceStack source, ServerLevel level, Engine engine, int radius, int threads, Mode mode) {
        if (!ACTIVE.compareAndSet(false, true)) {
            source.sendFailure(Component.literal("A goldenhash scan is already running."));
            return;
        }
        ModdedGoldenHash scan = new ModdedGoldenHash(source, level, engine, radius, threads, mode);
        int chunks = (scan.radius * 2 + 1) * (scan.radius * 2 + 1);
        scan.ok("GoldenHash started: " + chunks + " chunk(s) around 0,0 in buffers (world untouched), threads=" + scan.threads + " mode=" + mode);
        LOGGER.info("goldenhash start: dim={} seed={} radius={} threads={} mode={} file={}",
                engine.getDimension().getLoadKey(), level.getSeed(), scan.radius, scan.threads, mode, scan.goldenFile.getName());
        Thread thread = new Thread(() -> {
            try {
                scan.run();
            } catch (Throwable e) {
                LOGGER.error("goldenhash failed", e);
                scan.fail("GoldenHash failed: " + e);
            } finally {
                ACTIVE.set(false);
            }
        }, "Iris GoldenHash");
        thread.setDaemon(true);
        thread.start();
    }

    private void run() throws Exception {
        boolean exists = goldenFile.exists();
        if (mode == Mode.VERIFY && !exists) {
            fail("No golden capture at " + goldenFile.getAbsolutePath() + "; run '/iris goldenhash " + radius + " " + threads + " capture' first.");
            return;
        }

        resetMantleFull();
        List<int[]> targets = orderedTargets(0, 0, radius);
        Map<Long, String> lines = scan(targets);
        if (lines.size() != targets.size()) {
            fail("GoldenHash aborted: " + (targets.size() - lines.size()) + " chunk(s) failed to generate. No golden file written.");
            return;
        }

        if (mode == Mode.CAPTURE || (mode == Mode.AUTO && !exists)) {
            capture(lines);
        } else {
            verify(lines);
        }
    }

    private void resetMantleFull() {
        try {
            engine.getMantle().getMantle().saveAll();
            File folder = engine.getMantle().getMantle().getDataFolder();
            File[] files = folder.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isFile()) {
                        file.delete();
                    }
                }
            }
            ok("Mantle reset (" + folder.getAbsolutePath() + ")");
        } catch (Throwable e) {
            LOGGER.error("goldenhash mantle reset failed", e);
            ok("Mantle reset failed (" + e.getClass().getSimpleName() + "); continuing with existing mantle state.");
        }
    }

    private Map<Long, String> scan(List<int[]> targets) throws InterruptedException {
        Map<Long, String> lines = new ConcurrentHashMap<>();
        Semaphore inFlight = new Semaphore(threads);
        CountDownLatch done = new CountDownLatch(targets.size());
        AtomicInteger completed = new AtomicInteger();
        int total = targets.size();
        int stride = total <= 64 ? 1 : 32;
        int height = engine.getMaxHeight() - engine.getMinHeight();
        PlatformBlockState air = IrisPlatforms.get().registries().air();

        for (int[] target : targets) {
            int chunkX = target[0];
            int chunkZ = target[1];
            inFlight.acquire();
            MultiBurst.burst.lazy(() -> {
                try {
                    ModdedBlockBuffer blocks = new ModdedBlockBuffer(height, air);
                    Hunk<PlatformBiome> biomes = Hunk.newArrayHunk(16, height, 16);
                    engine.generate(chunkX << 4, chunkZ << 4, blocks, biomes, false);
                    lines.put(chunkKey(chunkX, chunkZ), hashChunk(chunkX, chunkZ, blocks, biomes, height));
                    int doneCount = completed.incrementAndGet();
                    if (doneCount % stride == 0 || doneCount == total) {
                        ok("[" + doneCount + "/" + total + "] chunk " + chunkX + "," + chunkZ + " hashed");
                    }
                } catch (Throwable e) {
                    LOGGER.error("goldenhash chunk {},{} failed", chunkX, chunkZ, e);
                    fail("Chunk " + chunkX + "," + chunkZ + " FAILED: " + e.getClass().getSimpleName());
                } finally {
                    inFlight.release();
                    done.countDown();
                }
            });
        }

        done.await();
        return lines;
    }

    private String hashChunk(int chunkX, int chunkZ, ModdedBlockBuffer blocks, Hunk<PlatformBiome> biomes, int height) {
        MessageDigest blockDigest = sha256();
        MessageDigest biomeDigest = sha256();
        Map<PlatformBlockState, byte[]> blockCache = new HashMap<>();
        Map<PlatformBiome, byte[]> biomeCache = new HashMap<>();
        byte[] plains = "minecraft:plains\n".getBytes(StandardCharsets.UTF_8);

        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                for (int y = 0; y < height; y++) {
                    PlatformBlockState state = blocks.get(x, y, z);
                    byte[] bytes = blockCache.computeIfAbsent(state, (PlatformBlockState s) -> (s.key() + "\n").getBytes(StandardCharsets.UTF_8));
                    blockDigest.update(bytes);
                }
            }
        }

        for (int x = 0; x < 16; x += BIOME_STEP) {
            for (int z = 0; z < 16; z += BIOME_STEP) {
                for (int y = 0; y < height; y += BIOME_STEP) {
                    PlatformBiome biome = biomes.get(x, y, z);
                    byte[] bytes = biome == null
                            ? plains
                            : biomeCache.computeIfAbsent(biome, (PlatformBiome b) -> (b.key() + "\n").getBytes(StandardCharsets.UTF_8));
                    biomeDigest.update(bytes);
                }
            }
        }

        return chunkX + " " + chunkZ + " "
                + HexFormat.of().formatHex(blockDigest.digest()) + " "
                + HexFormat.of().formatHex(biomeDigest.digest());
    }

    private void capture(Map<Long, String> lines) throws IOException {
        List<String> body = orderedBody(lines);
        String combined = combinedHash(body);
        List<String> out = new ArrayList<>();
        out.add("#" + FORMAT);
        out.add("#world=" + engine.getWorld().name());
        out.add("#dim=" + engine.getDimension().getLoadKey());
        out.add("#seed=" + level.getSeed());
        out.add("#mc=" + ModdedEngineBootstrap.loader().minecraftVersion());
        out.add("#minY=" + engine.getMinHeight() + " maxY=" + engine.getMaxHeight());
        out.add("#center=0,0");
        out.add("#radius=" + radius);
        out.addAll(body);
        out.add("#combined=" + combined);
        Files.write(goldenFile.toPath(), out, StandardCharsets.UTF_8);

        ok("Golden captured: " + body.size() + " chunks combined=" + shortHash(combined));
        ok(goldenFile.getAbsolutePath());
        LOGGER.info("goldenhash captured: {} combined={}", goldenFile.getAbsolutePath(), combined);
    }

    private void verify(Map<Long, String> lines) throws IOException {
        List<String> existing = Files.readAllLines(goldenFile.toPath(), StandardCharsets.UTF_8);
        Map<String, String> meta = new HashMap<>();
        Map<String, String> goldenChunks = new HashMap<>();
        for (String line : existing) {
            if (line.startsWith("#")) {
                int eq = line.indexOf('=');
                if (eq > 0) {
                    meta.put(line.substring(1, eq), line.substring(eq + 1));
                }
            } else if (!line.isBlank()) {
                int second = line.indexOf(' ', line.indexOf(' ') + 1);
                goldenChunks.put(line.substring(0, second), line);
            }
        }

        String expectedSeed = String.valueOf(level.getSeed());
        String expectedDim = engine.getDimension().getLoadKey();
        if (!expectedSeed.equals(meta.get("seed")) || !expectedDim.equals(meta.get("dim"))) {
            fail("Golden file is for dim=" + meta.get("dim") + " seed=" + meta.get("seed")
                    + " but this world is dim=" + expectedDim + " seed=" + expectedSeed + ". Aborting.");
            return;
        }
        String mc = ModdedEngineBootstrap.loader().minecraftVersion();
        if (!mc.equals(meta.get("mc"))) {
            ok("Golden was captured on mc=" + meta.get("mc") + ", running mc=" + mc + ". Diffs may be version-induced.");
        }

        List<String> body = orderedBody(lines);
        List<String> mismatches = new ArrayList<>();
        for (String line : body) {
            int second = line.indexOf(' ', line.indexOf(' ') + 1);
            String key = line.substring(0, second);
            String golden = goldenChunks.get(key);
            if (!line.equals(golden)) {
                mismatches.add(key + (golden == null ? " (missing in golden)" : ""));
            }
        }

        String combined = combinedHash(body);
        if (mismatches.isEmpty()) {
            ok("GOLDEN MATCH: " + body.size() + "/" + goldenChunks.size() + " chunks, combined=" + shortHash(combined));
            LOGGER.info("goldenhash MATCH: {} combined={}", goldenFile.getName(), combined);
            return;
        }

        fail("GOLDEN MISMATCH: " + mismatches.size() + "/" + body.size() + " chunks differ.");
        for (int i = 0; i < Math.min(MAX_REPORTED_MISMATCHES, mismatches.size()); i++) {
            fail("  chunk " + mismatches.get(i));
        }
        if (mismatches.size() > MAX_REPORTED_MISMATCHES) {
            fail("  ... and " + (mismatches.size() - MAX_REPORTED_MISMATCHES) + " more");
        }

        File current = new File(goldenFile.getParentFile(), goldenFile.getName() + ".new");
        List<String> out = new ArrayList<>(body);
        out.add("#combined=" + combined);
        Files.write(current.toPath(), out, StandardCharsets.UTF_8);
        ok("Current hashes written to " + current.getName());
        LOGGER.info("goldenhash MISMATCH: {}/{} -> {}", mismatches.size(), body.size(), current.getAbsolutePath());
        diagnose(mismatches.getFirst());
    }

    private void diagnose(String mismatchKey) {
        try {
            String[] parts = mismatchKey.trim().split(" ");
            int chunkX = Integer.parseInt(parts[0]);
            int chunkZ = Integer.parseInt(parts[1]);
            int height = engine.getMaxHeight() - engine.getMinHeight();
            PlatformBlockState air = IrisPlatforms.get().registries().air();

            ModdedBlockBuffer first = new ModdedBlockBuffer(height, air);
            Hunk<PlatformBiome> firstBiomes = Hunk.newArrayHunk(16, height, 16);
            engine.generate(chunkX << 4, chunkZ << 4, first, firstBiomes, false);
            ModdedBlockBuffer second = new ModdedBlockBuffer(height, air);
            Hunk<PlatformBiome> secondBiomes = Hunk.newArrayHunk(16, height, 16);
            engine.generate(chunkX << 4, chunkZ << 4, second, secondBiomes, false);

            int diffs = 0;
            for (int x = 0; x < 16 && diffs < 50; x++) {
                for (int z = 0; z < 16 && diffs < 50; z++) {
                    for (int y = 0; y < height && diffs < 50; y++) {
                        if (!first.get(x, y, z).key().equals(second.get(x, y, z).key())) {
                            diffs++;
                        }
                    }
                }
            }
            if (diffs == 0) {
                ok("Repeat-gen STABLE for chunk " + chunkX + "," + chunkZ + " (nondeterminism is order/state-dependent, not per-call)");
            } else {
                fail("Repeat-gen UNSTABLE for chunk " + chunkX + "," + chunkZ + " (" + diffs + "+ block diffs between back-to-back generations)");
            }
        } catch (Throwable e) {
            LOGGER.error("goldenhash diagnosis failed", e);
            fail("Diagnosis failed: " + e.getMessage());
        }
    }

    private static List<int[]> orderedTargets(int centerX, int centerZ, int radius) {
        List<int[]> targets = new ArrayList<>();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                targets.add(new int[]{centerX + dx, centerZ + dz});
            }
        }
        targets.sort(Comparator.comparingInt((int[] t) -> {
            int ox = t[0] - centerX;
            int oz = t[1] - centerZ;
            return ox * ox + oz * oz;
        }));
        return targets;
    }

    private List<String> orderedBody(Map<Long, String> lines) {
        Map<Long, String> sorted = new TreeMap<>(lines);
        return new ArrayList<>(sorted.values());
    }

    private String combinedHash(List<String> body) {
        MessageDigest digest = sha256();
        for (String line : body) {
            digest.update((line + "\n").getBytes(StandardCharsets.UTF_8));
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private void ok(String message) {
        server.execute(() -> source.sendSuccess(() -> Component.literal(message), false));
    }

    private void fail(String message) {
        server.execute(() -> source.sendFailure(Component.literal(message)));
    }

    private static String shortHash(String hex) {
        return hex.substring(0, 12);
    }

    private static long chunkKey(int x, int z) {
        return (((long) x) << 32) ^ (z & 0xFFFFFFFFL);
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
