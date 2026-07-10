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

import art.arcane.iris.core.IrisSettings;
import art.arcane.iris.core.gui.PregeneratorJob;
import art.arcane.iris.engine.IrisComplex;
import art.arcane.iris.engine.framework.Engine;
import art.arcane.iris.engine.framework.EngineWorldManager;
import art.arcane.iris.engine.object.IRare;
import art.arcane.iris.engine.object.IrisBiome;
import art.arcane.iris.engine.object.IrisEntity;
import art.arcane.iris.engine.object.IrisEntitySpawn;
import art.arcane.iris.engine.object.IrisMarker;
import art.arcane.iris.engine.object.IrisPosition;
import art.arcane.iris.engine.object.IrisRange;
import art.arcane.iris.engine.object.IrisRate;
import art.arcane.iris.engine.object.IrisRegion;
import art.arcane.iris.engine.object.IrisSpawnGroup;
import art.arcane.iris.engine.object.IrisSpawner;
import art.arcane.iris.engine.object.IrisSurface;
import art.arcane.iris.spi.IrisLogging;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.mantle.flag.MantleFlag;
import art.arcane.volmlib.util.mantle.runtime.Mantle;
import art.arcane.volmlib.util.mantle.runtime.MantleChunk;
import art.arcane.volmlib.util.math.RNG;
import art.arcane.volmlib.util.matter.Matter;
import art.arcane.volmlib.util.matter.MatterMarker;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.bukkit.Chunk;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public final class ModdedWorldManager implements EngineWorldManager {
    private static final int MAX_INITIAL_QUEUE = 8192;
    private static final int MAX_INITIAL_DRAIN_PER_TICK = 8;
    private static final int MAX_INITIAL_RECOVERY_PER_PASS = 128;
    private static final int MANTLE_WARMUP_QUEUE_CAPACITY = 256;
    private static final long INITIAL_RECOVERY_INTERVAL_MS = 1_000L;
    private static final long COUNT_INTERVAL_MS = 3_000L;
    private static final int ENTITY_SCAN_RADIUS = 64;
    private static final int PLAYER_CHUNK_RADIUS = 4;
    private static final int MIN_TICK_INTERVAL_MS = 1_000;

    private final Engine engine;
    private final InitialSpawnQueue initialSpawnQueue;
    private final Set<Long> mantleWarmups;
    private final ThreadPoolExecutor mantleWarmupExecutor;
    private long lastAmbientAt;
    private long lastCountAt;
    private long lastInitialRecoveryAt;
    private volatile boolean closed;
    private volatile int cachedEntityCount;
    private volatile int cachedConsideredChunks;
    private volatile double cachedSaturation;

    public ModdedWorldManager(Engine engine) {
        this.engine = engine;
        this.initialSpawnQueue = new InitialSpawnQueue(MAX_INITIAL_QUEUE);
        this.mantleWarmups = ConcurrentHashMap.newKeySet();
        BlockingQueue<Runnable> warmupQueue = new ArrayBlockingQueue<>(MANTLE_WARMUP_QUEUE_CAPACITY);
        this.mantleWarmupExecutor = new ThreadPoolExecutor(
                1,
                1,
                30L,
                TimeUnit.SECONDS,
                warmupQueue,
                runnable -> {
                    Thread thread = new Thread(runnable, "Iris Initial Spawn Mantle Warmup");
                    thread.setDaemon(true);
                    thread.setPriority(Thread.MIN_PRIORITY);
                    return thread;
                },
                new ThreadPoolExecutor.AbortPolicy());
        this.mantleWarmupExecutor.allowCoreThreadTimeOut(true);
    }

    public static void enqueueGenerated(Engine engine, int chunkX, int chunkZ) {
        if (engine == null || engine.isClosed()) {
            return;
        }
        EngineWorldManager worldManager = engine.getWorldManager();
        if (worldManager instanceof ModdedWorldManager moddedWorldManager) {
            moddedWorldManager.initialSpawnQueue.offer(pack(chunkX, chunkZ));
        }
    }

    public void serverTick(ServerLevel level) {
        if (closed || engine.isClosed() || engine.getMantle().getMantle().isClosed()) {
            return;
        }
        if (isPregenActive()) {
            return;
        }
        recoverLoadedInitialSpawns(level);
        drainInitialSpawns(level);
        ambientTick(level);
    }

    private void recoverLoadedInitialSpawns(ServerLevel level) {
        if (!markerSystemEnabled() && !ambientSystemEnabled()) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastInitialRecoveryAt < INITIAL_RECOVERY_INTERVAL_MS) {
            return;
        }
        lastInitialRecoveryAt = now;

        Set<Long> candidates = new HashSet<>();
        for (ServerPlayer player : level.players()) {
            int centerX = player.blockPosition().getX() >> 4;
            int centerZ = player.blockPosition().getZ() >> 4;
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    candidates.add(pack(centerX + dx, centerZ + dz));
                }
            }
        }
        candidates.addAll(level.getForceLoadedChunks());

        Mantle<Matter> mantle = engine.getMantle().getMantle();
        int recovered = 0;
        for (long key : candidates) {
            int chunkX = unpackX(key);
            int chunkZ = unpackZ(key);
            if (level.getChunkSource().getChunkNow(chunkX, chunkZ) == null) {
                continue;
            }
            if (mantle.isChunkLoaded(chunkX, chunkZ) && mantle.hasFlag(chunkX, chunkZ, MantleFlag.INITIAL_SPAWNED)) {
                continue;
            }
            if (initialSpawnQueue.offer(key) && ++recovered >= MAX_INITIAL_RECOVERY_PER_PASS) {
                return;
            }
        }
    }

    private void drainInitialSpawns(ServerLevel level) {
        if (initialSpawnQueue.isEmpty()) {
            return;
        }
        if (!markerSystemEnabled() && !ambientSystemEnabled()) {
            initialSpawnQueue.clear();
            return;
        }

        int budget = initialSpawnQueue.batchSize(MAX_INITIAL_DRAIN_PER_TICK);
        while (budget-- > 0) {
            Long key = initialSpawnQueue.poll();
            if (key == null) {
                return;
            }
            int chunkX = unpackX(key);
            int chunkZ = unpackZ(key);
            boolean retry = false;
            try {
                if (level.getChunkSource().getChunkNow(chunkX, chunkZ) == null) {
                    retry = true;
                    continue;
                }
                if (!initialSpawnChunk(level, chunkX, chunkZ)) {
                    retry = true;
                    warmupMantleChunkAsync(key, chunkX, chunkZ);
                }
            } catch (Throwable e) {
                IrisLogging.reportError(e);
                retry = true;
            } finally {
                if (retry) {
                    initialSpawnQueue.retry(key);
                } else {
                    initialSpawnQueue.complete(key);
                }
            }
        }
    }

    private boolean initialSpawnChunk(ServerLevel level, int chunkX, int chunkZ) {
        Mantle<Matter> mantle = engine.getMantle().getMantle();
        if (!mantle.isChunkLoaded(chunkX, chunkZ)) {
            return false;
        }
        if (mantle.hasFlag(chunkX, chunkZ, MantleFlag.INITIAL_SPAWNED)) {
            return true;
        }

        MantleChunk<Matter> chunk = mantle.getChunk(chunkX, chunkZ).use();
        try {
            chunk.raiseFlagUnchecked(MantleFlag.INITIAL_SPAWNED, () -> {
                if (markerSystemEnabled()) {
                    spawnMarkerSpawners(level, chunkX, chunkZ, chunk, true);
                }
                if (ambientSystemEnabled()) {
                    spawnAmbient(level, chunkX, chunkZ, true);
                }
            });
        } finally {
            chunk.release();
        }
        return true;
    }

    private void warmupMantleChunkAsync(long key, int chunkX, int chunkZ) {
        if (closed || !mantleWarmups.add(key)) {
            return;
        }
        try {
            mantleWarmupExecutor.execute(() -> warmupMantleChunk(key, chunkX, chunkZ));
        } catch (RejectedExecutionException e) {
            mantleWarmups.remove(key);
        }
    }

    private void warmupMantleChunk(long key, int chunkX, int chunkZ) {
        try {
            Mantle<Matter> mantle = engine.getMantle().getMantle();
            if (!closed && !engine.isClosed() && !mantle.isClosed() && !mantle.isChunkLoaded(chunkX, chunkZ)) {
                mantle.getChunk(chunkX, chunkZ);
            }
        } catch (Throwable e) {
            if (!closed && !engine.isClosed()) {
                IrisLogging.reportError(e);
            }
        } finally {
            mantleWarmups.remove(key);
        }
    }

    private void ambientTick(ServerLevel level) {
        long now = System.currentTimeMillis();
        long interval = Math.max((long) MIN_TICK_INTERVAL_MS, IrisSettings.get().getWorld().getAsyncTickIntervalMS());
        if (now - lastAmbientAt < interval) {
            return;
        }
        lastAmbientAt = now;

        if (level.players().isEmpty()) {
            return;
        }
        if (!markerSystemEnabled() && !ambientSystemEnabled()) {
            return;
        }

        refreshEntityCount(level, now);
        if (cachedSaturation > IrisSettings.get().getWorld().getTargetSpawnEntitiesPerChunk()) {
            return;
        }

        long[] candidates = loadedChunksNearPlayers(level);
        if (candidates.length == 0) {
            return;
        }

        int spawnBuffer = RNG.r.i(2, 12);
        while (spawnBuffer-- > 0) {
            long key = candidates[RNG.r.nextInt(candidates.length)];
            try {
                ambientSpawnChunk(level, unpackX(key), unpackZ(key));
            } catch (Throwable e) {
                IrisLogging.reportError(e);
            }
        }
    }

    private void ambientSpawnChunk(ServerLevel level, int chunkX, int chunkZ) {
        Mantle<Matter> mantle = engine.getMantle().getMantle();
        if (!mantle.isChunkLoaded(chunkX, chunkZ)) {
            return;
        }

        MantleChunk<Matter> chunk = mantle.getChunk(chunkX, chunkZ).use();
        try {
            if (markerSystemEnabled()) {
                spawnMarkerSpawners(level, chunkX, chunkZ, chunk, false);
            }
            if (ambientSystemEnabled()) {
                spawnAmbient(level, chunkX, chunkZ, false);
            }
        } finally {
            chunk.release();
        }
    }

    private void spawnMarkerSpawners(ServerLevel level, int chunkX, int chunkZ, MantleChunk<Matter> chunk, boolean initial) {
        int minHeight = engine.getWorld().minHeight();
        chunk.iterate(MatterMarker.class, (Integer x, Integer yf, Integer z, MatterMarker marker) -> {
            String tag = marker.getTag();
            if (tag.equals("cave_floor") || tag.equals("cave_ceiling")) {
                return;
            }
            IrisMarker resolved = engine.getData().getMarkerLoader().load(tag);
            if (resolved == null) {
                return;
            }

            int worldX = (chunkX << 4) + (x & 15);
            int worldZ = (chunkZ << 4) + (z & 15);
            int worldY = yf + minHeight;

            if (resolved.isEmptyAbove() && aboveObstructed(level, worldX, worldY, worldZ)) {
                return;
            }

            KList<IrisSpawner> spawners = resolveMarkerSpawners(resolved);
            if (spawners.isEmpty()) {
                return;
            }
            IrisSpawner chosen = spawners.getRandom();
            if (chosen == null) {
                return;
            }
            spawnFromSpawner(level, new IrisPosition(worldX, worldY, worldZ), chosen, initial);
        });
    }

    private KList<IrisSpawner> resolveMarkerSpawners(IrisMarker marker) {
        KList<IrisSpawner> spawners = new KList<>();
        for (String key : marker.getSpawners()) {
            IrisSpawner spawner = engine.getData().getSpawnerLoader().load(key);
            if (spawner == null) {
                IrisLogging.error("Cannot load spawner: " + key + " for marker on " + engine.getName());
                continue;
            }
            spawner.setReferenceMarker(marker);
            spawners.add(spawner);
        }
        return spawners;
    }

    private void spawnFromSpawner(ServerLevel level, IrisPosition position, IrisSpawner spawner, boolean initial) {
        KList<IrisEntitySpawn> spawns = initial ? spawner.getInitialSpawns() : spawner.getSpawns();
        if (spawns.isEmpty()) {
            return;
        }
        for (IrisEntitySpawn entry : spawns) {
            entry.setReferenceSpawner(spawner);
            entry.setReferenceMarker(spawner.getReferenceMarker());
        }
        IrisEntitySpawn chosen = rarityPick(spawns);
        if (chosen == null) {
            return;
        }

        int chunkX = position.getX() >> 4;
        int chunkZ = position.getZ() >> 4;
        if (!canSpawn(spawner, chunkX, chunkZ)) {
            return;
        }
        int spawned = spawnEntryAt(level, chosen, spawner, position);
        if (spawned > 0) {
            spawner.spawn(engine, chunkX, chunkZ);
        }
    }

    private void spawnAmbient(ServerLevel level, int chunkX, int chunkZ, boolean initial) {
        IrisComplex complex = engine.getComplex();
        if (complex == null) {
            return;
        }

        int blockX = chunkX << 4;
        int blockZ = chunkZ << 4;
        IrisBiome biome = engine.getSurfaceBiome(blockX, blockZ);
        IrisRegion region = engine.getRegion(blockX, blockZ);
        int chunkMobs = countChunkMobs(level, chunkX, chunkZ);

        KList<IrisEntitySpawn> pool = new KList<>();
        collectSpawns(pool, engine.getData().getSpawnerLoader().loadAll(engine.getDimension().getEntitySpawners()), biome, chunkX, chunkZ, chunkMobs, initial);
        collectSpawns(pool, engine.getData().getSpawnerLoader().loadAll(region.getEntitySpawners()), null, chunkX, chunkZ, chunkMobs, initial);
        collectSpawns(pool, engine.getData().getSpawnerLoader().loadAll(biome.getEntitySpawners()), null, chunkX, chunkZ, chunkMobs, initial);
        if (pool.isEmpty()) {
            return;
        }

        IrisEntitySpawn chosen = rarityPick(pool);
        if (chosen == null || chosen.getReferenceSpawner() == null) {
            return;
        }
        IrisSpawner spawner = chosen.getReferenceSpawner();
        if (!canSpawn(spawner, chunkX, chunkZ)) {
            return;
        }
        int spawned = spawnEntry(level, chosen, spawner, chunkX, chunkZ);
        if (spawned > 0) {
            spawner.spawn(engine, chunkX, chunkZ);
        }
    }

    private void collectSpawns(KList<IrisEntitySpawn> pool, KList<IrisSpawner> spawners, IrisBiome biomeFilter, int chunkX, int chunkZ, int chunkMobs, boolean initial) {
        for (IrisSpawner spawner : spawners) {
            if (spawner == null) {
                continue;
            }
            if (spawner.getMaxEntitiesPerChunk() <= chunkMobs) {
                continue;
            }
            if (biomeFilter != null && !spawner.isValid(biomeFilter)) {
                continue;
            }
            if (!canSpawn(spawner, chunkX, chunkZ)) {
                continue;
            }
            KList<IrisEntitySpawn> spawns = initial ? spawner.getInitialSpawns() : spawner.getSpawns();
            for (IrisEntitySpawn entry : spawns) {
                entry.setReferenceSpawner(spawner);
                entry.setReferenceMarker(spawner.getReferenceMarker());
                pool.add(entry);
            }
        }
    }

    private int spawnEntry(ServerLevel level, IrisEntitySpawn entry, IrisSpawner spawner, int chunkX, int chunkZ) {
        IrisEntity irisEntity = entry.getRealEntity(engine);
        if (irisEntity == null) {
            return 0;
        }

        int min = entry.getMinSpawns();
        int max = entry.getMaxSpawns();
        int count = min == max ? min : RNG.r.i(Math.min(min, max), Math.max(min, max));
        if (count <= 0) {
            return 0;
        }

        RNG entityRng = new RNG(engine.getSeedManager().getEntity());
        IrisSpawnGroup group = spawner.getGroup();
        int spawned = 0;
        for (int i = 0; i < count; i++) {
            int worldX = (chunkX << 4) + RNG.r.i(15);
            int worldZ = (chunkZ << 4) + RNG.r.i(15);
            int surfaceY = level.getMinY() + engine.getHeight(worldX, worldZ, false);
            int solidY = level.getMinY() + engine.getHeight(worldX, worldZ, true);
            int worldY = switch (group) {
                case NORMAL -> surfaceY + 1;
                case CAVE -> solidY + 1;
                case UNDERWATER, BEACH -> surfaceY > solidY + 1 ? RNG.r.i(solidY + 1, surfaceY) : surfaceY;
            };
            if (worldY <= level.getMinY() || worldY >= level.getMaxY()) {
                continue;
            }
            if (!lightAllowed(spawner, level, worldX, worldY, worldZ)) {
                continue;
            }
            if (!surfaceMatches(irisEntity.getSurface(), level, worldX, worldY, worldZ)) {
                continue;
            }
            if (!clearForSpawn(level, worldX, worldY, worldZ)) {
                continue;
            }
            if (ModdedEntitySpawner.spawn(engine, irisEntity, level, worldX, worldY, worldZ, entityRng) != null) {
                spawned++;
            }
        }
        return spawned;
    }

    private int spawnEntryAt(ServerLevel level, IrisEntitySpawn entry, IrisSpawner spawner, IrisPosition position) {
        IrisEntity irisEntity = entry.getRealEntity(engine);
        if (irisEntity == null) {
            return 0;
        }

        int min = entry.getMinSpawns();
        int max = entry.getMaxSpawns();
        int count = min == max ? min : RNG.r.i(Math.min(min, max), Math.max(min, max));
        if (count <= 0) {
            return 0;
        }

        exhaustMarker(spawner, position);

        RNG entityRng = new RNG(engine.getSeedManager().getEntity());
        int worldX = position.getX();
        int worldY = position.getY() + 1;
        int worldZ = position.getZ();
        int spawned = 0;
        for (int i = 0; i < count; i++) {
            if (!lightAllowed(spawner, level, worldX, worldY, worldZ)) {
                continue;
            }
            if (ModdedEntitySpawner.spawn(engine, irisEntity, level, worldX, worldY, worldZ, entityRng) != null) {
                spawned++;
            }
        }
        return spawned;
    }

    private void exhaustMarker(IrisSpawner spawner, IrisPosition position) {
        IrisMarker marker = spawner.getReferenceMarker();
        if (marker == null || !marker.shouldExhaust()) {
            return;
        }
        engine.getMantle().getMantle().remove(position.getX(), position.getY() - engine.getWorld().minHeight(), position.getZ(), MatterMarker.class);
    }

    private boolean canSpawn(IrisSpawner spawner, int chunkX, int chunkZ) {
        IrisRate rate = spawner.getMaximumRate();
        if (!rate.isInfinite() && !engine.getEngineData().getCooldown(spawner).canSpawn(rate)) {
            return false;
        }
        IrisRate chunkRate = spawner.getMaximumRatePerChunk();
        return chunkRate.isInfinite() || engine.getEngineData().getChunk(chunkX, chunkZ).getCooldown(spawner).canSpawn(chunkRate);
    }

    private boolean lightAllowed(IrisSpawner spawner, ServerLevel level, int worldX, int worldY, int worldZ) {
        IrisRange range = spawner.getAllowedLightLevels();
        if (range.getMin() > 0 || range.getMax() < 15) {
            return range.contains(level.getMaxLocalRawBrightness(new BlockPos(worldX, worldY, worldZ)));
        }
        return true;
    }

    private boolean surfaceMatches(IrisSurface surface, ServerLevel level, int worldX, int worldY, int worldZ) {
        BlockState below = level.getBlockState(new BlockPos(worldX, worldY - 1, worldZ));
        return matchesSurface(surface, below);
    }

    private static boolean matchesSurface(IrisSurface surface, BlockState below) {
        if (ModdedBlockResolution.isSolid(below)) {
            return surface == IrisSurface.LAND || surface == IrisSurface.OVERWORLD
                    || (surface == IrisSurface.ANIMAL && isAnimalGround(below));
        }
        if (below.is(Blocks.LAVA)) {
            return surface == IrisSurface.LAVA;
        }
        if (ModdedBlockResolution.isWater(below) || ModdedBlockResolution.isWaterLogged(below) || isAquaticFoliage(below)) {
            return surface == IrisSurface.WATER || surface == IrisSurface.OVERWORLD;
        }
        return false;
    }

    private static boolean isAnimalGround(BlockState state) {
        return state.is(Blocks.GRASS_BLOCK) || state.is(Blocks.DIRT) || state.is(Blocks.DIRT_PATH)
                || state.is(Blocks.COARSE_DIRT) || state.is(Blocks.ROOTED_DIRT) || state.is(Blocks.PODZOL)
                || state.is(Blocks.MYCELIUM) || state.is(Blocks.SNOW_BLOCK);
    }

    private static boolean isAquaticFoliage(BlockState state) {
        return state.is(Blocks.SEAGRASS) || state.is(Blocks.TALL_SEAGRASS)
                || state.is(Blocks.KELP) || state.is(Blocks.KELP_PLANT);
    }

    private boolean clearForSpawn(ServerLevel level, int worldX, int worldY, int worldZ) {
        BlockState at = level.getBlockState(new BlockPos(worldX, worldY, worldZ));
        BlockState above = level.getBlockState(new BlockPos(worldX, worldY + 1, worldZ));
        return !ModdedBlockResolution.isSolid(at) && !ModdedBlockResolution.isSolid(above);
    }

    private boolean aboveObstructed(ServerLevel level, int worldX, int worldY, int worldZ) {
        return ModdedBlockResolution.isSolid(level.getBlockState(new BlockPos(worldX, worldY + 1, worldZ)))
                || ModdedBlockResolution.isSolid(level.getBlockState(new BlockPos(worldX, worldY + 2, worldZ)));
    }

    private int countChunkMobs(ServerLevel level, int chunkX, int chunkZ) {
        int baseX = chunkX << 4;
        int baseZ = chunkZ << 4;
        AABB box = new AABB(baseX, level.getMinY(), baseZ, baseX + 16, level.getMaxY(), baseZ + 16);
        return level.getEntities((Entity) null, box, (Entity e) -> e instanceof Mob && e.isAlive()).size();
    }

    private void refreshEntityCount(ServerLevel level, long now) {
        if (now - lastCountAt < COUNT_INTERVAL_MS) {
            return;
        }
        lastCountAt = now;

        int mobs = 0;
        int chunks = 0;
        for (ServerPlayer player : level.players()) {
            int px = player.blockPosition().getX();
            int pz = player.blockPosition().getZ();
            AABB box = new AABB(px - ENTITY_SCAN_RADIUS, level.getMinY(), pz - ENTITY_SCAN_RADIUS,
                    px + ENTITY_SCAN_RADIUS, level.getMaxY(), pz + ENTITY_SCAN_RADIUS);
            mobs += level.getEntities((Entity) null, box, (Entity e) -> e instanceof Mob && e.isAlive()).size();
            chunks += (PLAYER_CHUNK_RADIUS * 2 + 1) * (PLAYER_CHUNK_RADIUS * 2 + 1);
        }

        cachedEntityCount = mobs;
        cachedConsideredChunks = chunks;
        cachedSaturation = mobs / (chunks + 1.0) * 1.28;
    }

    private long[] loadedChunksNearPlayers(ServerLevel level) {
        Set<Long> keys = new HashSet<>();
        for (ServerPlayer player : level.players()) {
            int centerX = player.blockPosition().getX() >> 4;
            int centerZ = player.blockPosition().getZ() >> 4;
            for (int dx = -PLAYER_CHUNK_RADIUS; dx <= PLAYER_CHUNK_RADIUS; dx++) {
                for (int dz = -PLAYER_CHUNK_RADIUS; dz <= PLAYER_CHUNK_RADIUS; dz++) {
                    int chunkX = centerX + dx;
                    int chunkZ = centerZ + dz;
                    if (level.getChunkSource().getChunkNow(chunkX, chunkZ) != null) {
                        keys.add(pack(chunkX, chunkZ));
                    }
                }
            }
        }

        long[] result = new long[keys.size()];
        int index = 0;
        for (long key : keys) {
            result[index++] = key;
        }
        return result;
    }

    private boolean isPregenActive() {
        PregeneratorJob job = PregeneratorJob.getInstance();
        return job != null && job.targetsWorldName(engine.getWorld().name());
    }

    private static boolean markerSystemEnabled() {
        return IrisSettings.get().getWorld().isMarkerEntitySpawningSystem();
    }

    private static boolean ambientSystemEnabled() {
        return IrisSettings.get().getWorld().isAmbientEntitySpawningSystem();
    }

    private IrisEntitySpawn rarityPick(KList<IrisEntitySpawn> entries) {
        int totalRarity = 0;
        for (IrisEntitySpawn entry : entries) {
            totalRarity += IRare.get(entry);
        }
        if (totalRarity <= 0) {
            return entries.getRandom();
        }
        KList<IrisEntitySpawn> weighted = new KList<>();
        for (IrisEntitySpawn entry : entries) {
            weighted.addMultiple(entry, totalRarity / IRare.get(entry));
        }
        return weighted.getRandom();
    }

    private static long pack(int x, int z) {
        return (((long) x) & 0xFFFFFFFFL) | ((((long) z) & 0xFFFFFFFFL) << 32);
    }

    private static int unpackX(long key) {
        return (int) (key & 0xFFFFFFFFL);
    }

    private static int unpackZ(long key) {
        return (int) ((key >> 32) & 0xFFFFFFFFL);
    }

    @Override
    public void close() {
        closed = true;
        initialSpawnQueue.close();
        mantleWarmupExecutor.shutdownNow();
        mantleWarmups.clear();
    }

    @Override
    public int getEntityCount() {
        return cachedEntityCount;
    }

    @Override
    public int getChunkCount() {
        return cachedConsideredChunks;
    }

    @Override
    public double getEntitySaturation() {
        return cachedSaturation;
    }

    @Override
    public void onTick() {
    }

    @Override
    public void onSave() {
        engine.getMantle().save();
    }

    @Override
    public void onBlockBreak(BlockBreakEvent e) {
    }

    @Override
    public void onBlockPlace(BlockPlaceEvent e) {
    }

    @Override
    public void onChunkLoad(Chunk e, boolean generated) {
    }

    @Override
    public void onChunkUnload(Chunk e) {
    }

    @Override
    public void teleportAsync(PlayerTeleportEvent e) {
    }
}
