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

package art.arcane.iris.modded;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.Heightmap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

public final class ModdedWorldCheck {
    private static final int EXIT_PASS = 0;
    private static final int EXIT_FAILURE = 1;
    private static final long SERVER_WAIT_TIMEOUT_MILLIS = 600000L;
    private static final long SERVER_WAIT_INTERVAL_MILLIS = 250L;
    private static final Logger LOGGER = LoggerFactory.getLogger("Iris");
    private static final ProcessExit PROCESS_EXIT = Runtime.getRuntime()::exit;

    private ModdedWorldCheck() {
    }

    public static void schedule() {
        coordinatorThread(() -> waitAndRun(PROCESS_EXIT)).start();
    }

    static Thread coordinatorThread(Runnable coordinator) {
        Thread thread = new Thread(coordinator, "Iris World Check");
        thread.setDaemon(false);
        return thread;
    }

    private static void waitAndRun(ProcessExit processExit) {
        long start = System.currentTimeMillis();
        MinecraftServer server = null;
        boolean ready = false;
        int exitCode = EXIT_FAILURE;
        try {
            while (System.currentTimeMillis() - start < SERVER_WAIT_TIMEOUT_MILLIS) {
                MinecraftServer candidate = ModdedEngineBootstrap.currentServer();
                if (candidate != null) {
                    server = candidate;
                    if (candidate.isReady()) {
                        ready = true;
                        break;
                    }
                }
                Thread.sleep(SERVER_WAIT_INTERVAL_MILLIS);
            }

            if (!ready) {
                LOGGER.error("[worldcheck] server did not become ready within 10 minutes");
                return;
            }

            MinecraftServer serverRef = server;
            AtomicBoolean pass = new AtomicBoolean(false);
            serverRef.submit(() -> pass.set(run(serverRef))).join();
            exitCode = pass.get() ? EXIT_PASS : EXIT_FAILURE;
        } catch (InterruptedException e) {
            LOGGER.error("[worldcheck] coordinator interrupted", e);
            Thread.currentThread().interrupt();
        } catch (Throwable e) {
            LOGGER.error("[worldcheck] check failed", e);
        } finally {
            int resultCode = exitCode;
            LOGGER.info("[worldcheck] shutting down dev server (result={})", resultCode == EXIT_PASS ? "PASS" : "FAIL");
            MinecraftServer serverRef = server;
            stopAndExit(serverRef == null ? null : () -> serverRef.halt(true), resultCode, processExit);
        }
    }

    static void stopAndExit(Runnable serverStop, int requestedExitCode, ProcessExit processExit) {
        int exitCode = requestedExitCode;
        boolean interrupted = Thread.interrupted();
        if (interrupted) {
            exitCode = EXIT_FAILURE;
        }
        try {
            if (serverStop != null) {
                serverStop.run();
            }
        } catch (Throwable e) {
            exitCode = EXIT_FAILURE;
            LOGGER.error("[worldcheck] server shutdown failed", e);
        } finally {
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
        }
        processExit.exit(exitCode);
    }

    private static boolean run(MinecraftServer server) {
        ServerLevel level = targetLevel(server);
        if (level == null) {
            LOGGER.error("[worldcheck] no Iris dimension is loaded");
            return false;
        }

        String levelId = level.dimension().identifier().toString();
        String generatorClass = level.getChunkSource().getGenerator().getClass().getName();
        LOGGER.info("[worldcheck] {} generator: {}", levelId, generatorClass);
        boolean irisGenerator = level.getChunkSource().getGenerator() instanceof IrisModdedChunkGenerator;
        if (!irisGenerator) {
            LOGGER.error("[worldcheck] {} is NOT using IrisModdedChunkGenerator", levelId);
        }

        BlockPos spawn = level.getRespawnData().pos();
        LOGGER.info("[worldcheck] spawn: {} {} {} (minY={} height={})", spawn.getX(), spawn.getY(), spawn.getZ(), level.getMinY(), level.getHeight());

        MessageDigest digest = sha256();
        List<String> samples = new ArrayList<>();
        Set<String> surfaceKeys = new LinkedHashSet<>();
        for (int dx = 0; dx < 4; dx++) {
            for (int dz = 0; dz < 4; dz++) {
                int x = spawn.getX() + (dx - 2) * 16 + 8;
                int z = spawn.getZ() + (dz - 2) * 16 + 8;
                level.getChunk(x >> 4, z >> 4);
                int y = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z);
                BlockState surface = level.getBlockState(new BlockPos(x, y - 1, z));
                String key = BuiltInRegistries.BLOCK.getKey(surface.getBlock()).toString();
                String line = x + " " + (y - 1) + " " + z + " " + key;
                samples.add(line);
                surfaceKeys.add(key);
                digest.update((line + "\n").getBytes(StandardCharsets.UTF_8));
            }
        }

        for (int i = 0; i < Math.min(6, samples.size()); i++) {
            LOGGER.info("[worldcheck] surface sample: {}", samples.get(i));
        }
        LOGGER.info("[worldcheck] surface digest: {} ({} columns, {} distinct surface blocks: {})",
                HexFormat.of().formatHex(digest.digest()).substring(0, 12), samples.size(), surfaceKeys.size(), surfaceKeys);

        ChunkAccess zeroChunk = level.getChunk(0, 0);
        int nonEmptySections = 0;
        for (LevelChunkSection section : zeroChunk.getSections()) {
            if (!section.hasOnlyAir()) {
                nonEmptySections++;
            }
        }
        Set<String> columnKeys = new LinkedHashSet<>();
        int surfaceY = level.getHeight(Heightmap.Types.WORLD_SURFACE, 8, 8);
        for (int y = level.getMinY(); y < surfaceY; y += 16) {
            BlockState state = zeroChunk.getBlockState(new BlockPos(8, y, 8));
            if (!state.isAir()) {
                columnKeys.add(BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString());
            }
        }
        LOGGER.info("[worldcheck] chunk 0,0: {} non-empty sections of {}; column blocks at (8,*,8): {}",
                nonEmptySections, zeroChunk.getSections().length, columnKeys);

        boolean sectionsOk = nonEmptySections >= 4;
        boolean varietyOk = columnKeys.size() >= 2 || surfaceKeys.size() >= 2;
        if (!sectionsOk) {
            LOGGER.error("[worldcheck] chunk 0,0 looks empty/vanilla-flat ({} non-empty sections)", nonEmptySections);
        }
        if (!varietyOk) {
            LOGGER.error("[worldcheck] generated terrain has no block variety (flat-world signature)");
        }

        boolean pass = irisGenerator && sectionsOk && varietyOk;
        LOGGER.info("[worldcheck] {}", pass ? "PASS" : "FAIL");
        return pass;
    }

    private static ServerLevel targetLevel(MinecraftServer server) {
        String target = System.getProperty("iris.worldcheck.dimension");
        if (target != null && !target.isBlank()) {
            for (ServerLevel level : server.getAllLevels()) {
                if (level.dimension().identifier().toString().equals(target.trim())) {
                    return level;
                }
            }
            LOGGER.error("[worldcheck] requested dimension '{}' is not loaded", target);
            return null;
        }

        for (ServerLevel level : server.getAllLevels()) {
            if (level.getChunkSource().getGenerator() instanceof IrisModdedChunkGenerator) {
                return level;
            }
        }
        return null;
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    @FunctionalInterface
    interface ProcessExit {
        void exit(int status);
    }
}
