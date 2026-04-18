/*
 * Iris is a World Generator for Minecraft Bukkit Servers
 * Copyright (c) 2022 Arcane Arts (Volmit Software)
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

package art.arcane.iris.engine.modifier;

import art.arcane.iris.core.loader.IrisData;
import art.arcane.iris.core.nms.INMS;
import art.arcane.iris.engine.IrisComplex;
import art.arcane.iris.engine.decorator.IrisFloatingSurfaceDecorator;
import art.arcane.iris.engine.decorator.IrisSeaSurfaceDecorator;
import static art.arcane.iris.engine.mantle.EngineMantle.AIR;
import art.arcane.iris.engine.framework.Engine;
import art.arcane.iris.engine.framework.EngineAssignedModifier;
import art.arcane.iris.engine.framework.EngineDecorator;
import art.arcane.iris.engine.object.FloatingIslandSample;
import art.arcane.iris.engine.object.IrisBiome;
import art.arcane.iris.engine.object.IrisBiomeCustom;
import art.arcane.iris.engine.object.IrisDimension;
import art.arcane.iris.engine.object.IrisFloatingChildBiomes;
import art.arcane.iris.util.common.data.B;
import art.arcane.iris.util.project.context.ChunkContext;
import art.arcane.iris.util.project.hunk.Hunk;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.math.RNG;
import art.arcane.volmlib.util.matter.MatterBiomeInject;
import art.arcane.volmlib.util.matter.slices.BiomeInjectMatter;
import art.arcane.volmlib.util.scheduling.PrecisionStopwatch;
import org.bukkit.block.Biome;
import org.bukkit.block.data.BlockData;

public class IrisFloatingChildBiomeModifier extends EngineAssignedModifier<BlockData> {
    public static final long FLOATING_BASE_SEED_SALT = 0x5EED_F107_00F1B10CL;
    private static final java.util.concurrent.atomic.AtomicLong columnsChecked = new java.util.concurrent.atomic.AtomicLong();
    private static final java.util.concurrent.atomic.AtomicLong samplesAccepted = new java.util.concurrent.atomic.AtomicLong();
    private static final java.util.concurrent.atomic.AtomicLong decorateInvocations = new java.util.concurrent.atomic.AtomicLong();
    private static final java.util.concurrent.atomic.AtomicLong decorateSkippedNotAir = new java.util.concurrent.atomic.AtomicLong();
    private static final java.util.concurrent.atomic.AtomicLong decorateSkippedNoInherit = new java.util.concurrent.atomic.AtomicLong();
    private static final java.util.concurrent.atomic.AtomicLong decoratePhaseColumns = new java.util.concurrent.atomic.AtomicLong();
    private static final java.util.concurrent.atomic.AtomicLong decoratePlaced = new java.util.concurrent.atomic.AtomicLong();
    private static final java.util.concurrent.atomic.AtomicLong decorateNoChange = new java.util.concurrent.atomic.AtomicLong();
    private static final java.util.concurrent.atomic.AtomicLong decorateFloorNull = new java.util.concurrent.atomic.AtomicLong();
    private static final java.util.concurrent.ConcurrentHashMap<String, java.util.concurrent.atomic.AtomicLong> floorMatHisto = new java.util.concurrent.ConcurrentHashMap<>();
    private static final java.util.concurrent.atomic.AtomicLong lastReportMs = new java.util.concurrent.atomic.AtomicLong(0L);
    private final RNG rng;
    private final EngineDecorator surfaceDecorator;
    private final EngineDecorator seaSurfaceDecorator;

    public static void reportFloatingStats() {
        StringBuilder topFloors = new StringBuilder();
        floorMatHisto.entrySet().stream()
                .sorted((a, b) -> Long.compare(b.getValue().get(), a.getValue().get()))
                .limit(5)
                .forEach(e -> topFloors.append(' ').append(e.getKey()).append('=').append(e.getValue().get()));
        art.arcane.iris.Iris.info("[floating-debug] columns=" + columnsChecked.get()
                + " samples=" + samplesAccepted.get()
                + " decInvoke=" + decorateInvocations.get()
                + " decPlaced=" + decoratePlaced.get()
                + " decNoChange=" + decorateNoChange.get()
                + " decFloorNull=" + decorateFloorNull.get()
                + " decSkipNonAir=" + decorateSkippedNotAir.get()
                + " decSkipNoInherit=" + decorateSkippedNoInherit.get()
                + " decPhaseCols=" + decoratePhaseColumns.get()
                + " topFloors:" + (topFloors.length() == 0 ? " <none>" : topFloors.toString()));
    }

    private static void maybeReport() {
        long now = System.currentTimeMillis();
        long last = lastReportMs.get();
        if (now - last >= 10000L && lastReportMs.compareAndSet(last, now)) {
            reportFloatingStats();
        }
    }

    public IrisFloatingChildBiomeModifier(Engine engine) {
        super(engine, "FloatingChildBiomes");
        rng = new RNG(engine.getSeedManager().getTerrain() ^ 0x7EB0A73F1DCE514DL);
        surfaceDecorator = new IrisFloatingSurfaceDecorator(engine);
        seaSurfaceDecorator = new IrisSeaSurfaceDecorator(engine);
    }

    @Override
    public void onModify(int x, int z, Hunk<BlockData> output, boolean multicore, ChunkContext context) {
        PrecisionStopwatch p = PrecisionStopwatch.start();
        int chunkHeight = output.getHeight();
        IrisData data = getData();
        IrisDimension dimension = getDimension();
        IrisComplex complex = getComplex();
        long baseSeed = getEngine().getSeedManager().getTerrain() ^ FLOATING_BASE_SEED_SALT;

        for (int xf = 0; xf < 16; xf++) {
            for (int zf = 0; zf < 16; zf++) {
                int wx = x + xf;
                int wz = z + zf;
                IrisBiome parent = complex.getTrueBiomeStream().get(wx, wz);
                if (parent == null || parent.getFloatingChildBiomes() == null || parent.getFloatingChildBiomes().isEmpty()) {
                    continue;
                }
                columnsChecked.incrementAndGet();

                FloatingIslandSample sample = FloatingIslandSample.sampleMemoized(parent, wx, wz, chunkHeight, baseSeed, data, complex, getEngine());
                if (sample == null) {
                    continue;
                }
                samplesAccepted.incrementAndGet();

                IrisFloatingChildBiomes entry = sample.entry;
                IrisBiome target = entry.getRealBiome(parent, data);
                long colSeed = FloatingIslandSample.columnSeed(baseSeed, wx, wz);
                RNG layerRng = rng.nextParallelRNG((int) (colSeed ^ 0x7A4E));
                int paletteDepth = Math.max(4, sample.solidCount + 4);
                KList<BlockData> blocks = target.generateLayers(dimension, wx, wz, layerRng, paletteDepth, paletteDepth, data, complex);
                if (blocks == null || blocks.isEmpty()) {
                    blocks = parent.generateLayers(dimension, wx, wz, layerRng, paletteDepth, paletteDepth, data, complex);
                }
                BlockData fallbackSolid = B.get("minecraft:stone");

                int depth = 0;
                for (int k = sample.topIdx; k >= 0; k--) {
                    if (!sample.solidMask[k]) {
                        continue;
                    }
                    int y = sample.islandBaseY + k;
                    if (y < 0 || y >= chunkHeight) {
                        continue;
                    }
                    BlockData block = null;
                    if (blocks != null && !blocks.isEmpty()) {
                        block = blocks.hasIndex(depth) ? blocks.get(depth) : blocks.getLast();
                    }
                    if (block == null) {
                        block = fallbackSolid;
                    }
                    if (block != null) {
                        output.set(xf, y, zf, block);
                    }
                    depth++;
                }

                Integer localFluidHeight = entry.getLocalFluidHeight();
                if (localFluidHeight != null && localFluidHeight > 0) {
                    BlockData fluid = B.get(entry.getFluidBlock());
                    if (fluid == null) {
                        fluid = B.get("minecraft:water");
                    }
                    int fluidCap = Math.min(sample.thickness - 1, localFluidHeight);
                    for (int k = 1; k <= fluidCap; k++) {
                        if (sample.solidMask[k]) {
                            continue;
                        }
                        int y = sample.islandBaseY + k;
                        if (y < 0 || y >= chunkHeight) {
                            continue;
                        }
                        boolean hasSolidBelow = false;
                        for (int kb = k - 1; kb >= 0; kb--) {
                            if (sample.solidMask[kb]) {
                                hasSolidBelow = true;
                                break;
                            }
                        }
                        if (hasSolidBelow) {
                            output.set(xf, y, zf, fluid);
                        }
                    }
                }

                if (target != null) {
                    writeIslandSkyBiome(target, wx, wz, sample, chunkHeight);
                }
            }
        }

        getEngine().getMetrics().getDeposit().put(p.getMilliseconds());
    }

    public void decorateColumns(int x, int z, Hunk<BlockData> output, boolean multicore, ChunkContext context) {
        int chunkHeight = output.getHeight();
        IrisData data = getData();
        IrisComplex complex = getComplex();
        long baseSeed = getEngine().getSeedManager().getTerrain() ^ FLOATING_BASE_SEED_SALT;

        for (int xf = 0; xf < 16; xf++) {
            for (int zf = 0; zf < 16; zf++) {
                int wx = x + xf;
                int wz = z + zf;
                IrisBiome parent = complex.getTrueBiomeStream().get(wx, wz);
                if (parent == null || parent.getFloatingChildBiomes() == null || parent.getFloatingChildBiomes().isEmpty()) {
                    continue;
                }
                FloatingIslandSample sample = FloatingIslandSample.sampleMemoized(parent, wx, wz, chunkHeight, baseSeed, data, complex, getEngine());
                if (sample == null) {
                    continue;
                }
                decoratePhaseColumns.incrementAndGet();
                IrisFloatingChildBiomes entry = sample.entry;
                IrisBiome target = entry.getRealBiome(parent, data);

                if (!entry.isInheritDecorators() || target == null) {
                    decorateSkippedNoInherit.incrementAndGet();
                    continue;
                }

                int topY = sample.topY();
                int max = Math.max(1, chunkHeight - topY);
                if (topY + 1 < chunkHeight) {
                    BlockData above = output.get(xf, topY + 1, zf);
                    if (above == null || B.isAir(above)) {
                        decorateInvocations.incrementAndGet();
                        BlockData floor = topY >= 0 && topY < chunkHeight ? output.get(xf, topY, zf) : null;
                        if (floor == null) {
                            decorateFloorNull.incrementAndGet();
                        } else {
                            String matKey = floor.getMaterial().getKey().getKey();
                            floorMatHisto.computeIfAbsent(matKey, k -> new java.util.concurrent.atomic.AtomicLong()).incrementAndGet();
                        }
                        try {
                            surfaceDecorator.decorate(xf, zf, wx, wz, output, target, topY, max);
                        } catch (Throwable e) {
                            art.arcane.iris.Iris.reportError(e);
                        }
                        BlockData afterAbove = output.get(xf, topY + 1, zf);
                        if (afterAbove != null && !B.isAir(afterAbove)) {
                            decoratePlaced.incrementAndGet();
                        } else {
                            decorateNoChange.incrementAndGet();
                        }
                    } else {
                        decorateSkippedNotAir.incrementAndGet();
                    }
                }

                Integer localFluidHeight = entry.getLocalFluidHeight();
                if (localFluidHeight != null && localFluidHeight > 0) {
                    int fluidCap = Math.min(sample.thickness - 1, localFluidHeight);
                    int fluidTopY = -1;
                    for (int k = 1; k <= fluidCap; k++) {
                        if (sample.solidMask[k]) {
                            continue;
                        }
                        int y = sample.islandBaseY + k;
                        if (y < 0 || y >= chunkHeight) {
                            continue;
                        }
                        boolean hasSolidBelow = false;
                        for (int kb = k - 1; kb >= 0; kb--) {
                            if (sample.solidMask[kb]) {
                                hasSolidBelow = true;
                                break;
                            }
                        }
                        if (hasSolidBelow && y > fluidTopY) {
                            fluidTopY = y;
                        }
                    }
                    if (fluidTopY > 0 && fluidTopY + 1 < chunkHeight && B.isAir(output.get(xf, fluidTopY + 1, zf))) {
                        try {
                            seaSurfaceDecorator.decorate(xf, zf,
                                    wx, wx + 1, wx - 1,
                                    wz, wz + 1, wz - 1,
                                    output, target, fluidTopY, chunkHeight);
                        } catch (Throwable e) {
                            art.arcane.iris.Iris.reportError(e);
                        }
                    }
                }
            }
        }
        maybeReport();
    }

    private void writeIslandSkyBiome(IrisBiome target, int wx, int wz, FloatingIslandSample sample, int chunkHeight) {
        try {
            MatterBiomeInject matter;
            if (target.isCustom()) {
                IrisBiomeCustom custom = target.getCustomBiome(rng, wx, 0, wz);
                matter = BiomeInjectMatter.get(INMS.get().getBiomeBaseIdForKey(getDimension().getLoadKey() + ":" + custom.getId()));
            } else {
                Biome v = target.getSkyBiome(rng, wx, 0, wz);
                matter = BiomeInjectMatter.get(v);
            }
            int yFrom = Math.max(0, sample.islandBaseY);
            int yTo = Math.min(chunkHeight - 1, sample.islandBaseY + sample.topIdx);
            for (int y = yFrom; y <= yTo; y += 4) {
                getEngine().getMantle().getMantle().set(wx, y, wz, matter);
            }
        } catch (Throwable e) {
            art.arcane.iris.Iris.reportError(e);
        }
    }
}
