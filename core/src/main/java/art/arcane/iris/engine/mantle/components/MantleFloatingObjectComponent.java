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

package art.arcane.iris.engine.mantle.components;

import art.arcane.iris.Iris;
import art.arcane.iris.core.loader.IrisData;
import art.arcane.iris.engine.IrisComplex;
import art.arcane.iris.engine.data.cache.Cache;
import art.arcane.iris.engine.mantle.ComponentFlag;
import art.arcane.iris.engine.mantle.EngineMantle;
import art.arcane.iris.engine.mantle.IrisMantleComponent;
import art.arcane.iris.engine.mantle.MantleWriter;
import art.arcane.iris.engine.modifier.IrisFloatingChildBiomeModifier;
import art.arcane.iris.engine.object.CarvingMode;
import art.arcane.iris.engine.object.FloatingIslandSample;
import art.arcane.iris.engine.object.IrisBiome;
import art.arcane.iris.engine.object.IrisFloatingChildBiomes;
import art.arcane.iris.engine.object.IrisObject;
import art.arcane.iris.engine.object.IrisObjectPlacement;
import art.arcane.iris.engine.object.ObjectPlaceMode;
import art.arcane.iris.util.project.context.ChunkContext;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.documentation.ChunkCoordinates;
import art.arcane.volmlib.util.mantle.flag.ReservedFlag;
import art.arcane.volmlib.util.math.RNG;

@ComponentFlag(ReservedFlag.FLOATING_OBJECT)
public class MantleFloatingObjectComponent extends IrisMantleComponent {

    public MantleFloatingObjectComponent(EngineMantle engineMantle) {
        super(engineMantle, ReservedFlag.FLOATING_OBJECT, 2);
    }

    @Override
    public void generateLayer(MantleWriter writer, int x, int z, ChunkContext context) {
        IrisComplex complex = context.getComplex();
        IrisData data = getData();
        int chunkHeight = getEngineMantle().getEngine().getHeight();
        int minX = x << 4;
        int minZ = z << 4;
        long baseSeed = getEngineMantle().getEngine().getSeedManager().getTerrain() ^ IrisFloatingChildBiomeModifier.FLOATING_BASE_SEED_SALT;
        RNG chunkRng = new RNG(Cache.key(x, z) + seed() + 0x0FA710BEL);

        FloatingIslandSample.clearChunkMemo();

        FloatingIslandSample[] samples = new FloatingIslandSample[256];
        for (int xf = 0; xf < 16; xf++) {
            for (int zf = 0; zf < 16; zf++) {
                int wx = minX + xf;
                int wz = minZ + zf;
                IrisBiome parent = complex.getTrueBiomeStream().get(wx, wz);
                if (parent == null || parent.getFloatingChildBiomes() == null || parent.getFloatingChildBiomes().isEmpty()) {
                    continue;
                }
                FloatingIslandSample sample = FloatingIslandSample.sampleMemoized(parent, wx, wz, chunkHeight, baseSeed, data, complex, getEngineMantle().getEngine());
                if (sample != null) {
                    samples[(zf << 4) | xf] = sample;
                }
            }
        }

        java.util.IdentityHashMap<IrisFloatingChildBiomes, KList<Integer>> entryColumns = new java.util.IdentityHashMap<>();
        for (int i = 0; i < 256; i++) {
            FloatingIslandSample s = samples[i];
            if (s == null || s.entry == null) {
                continue;
            }
            entryColumns.computeIfAbsent(s.entry, e -> new KList<>()).add(i);
        }

        for (java.util.Map.Entry<IrisFloatingChildBiomes, KList<Integer>> ec : entryColumns.entrySet()) {
            IrisFloatingChildBiomes entry = ec.getKey();
            KList<Integer> columns = ec.getValue();
            if (columns.isEmpty()) {
                continue;
            }

            IrisBiome parent = complex.getTrueBiomeStream().get(minX + (columns.get(0) & 15), minZ + (columns.get(0) >> 4));
            IrisBiome target = entry.getRealBiome(parent, data);

            KList<IrisObjectPlacement> floating = entry.getFloatingObjects();
            if (floating != null && !floating.isEmpty()) {
                for (IrisObjectPlacement placement : floating) {
                    tryPlaceFloatingChunk(writer, complex, chunkRng, data, placement, samples, columns, minX, minZ, entry);
                }
            }

            if (entry.isInheritObjects() && target != null) {
                for (IrisObjectPlacement placement : target.getSurfaceObjects()) {
                    tryPlaceAnchoredChunk(writer, complex, chunkRng, data, placement, samples, columns, minX, minZ, entry);
                }
            }

            KList<IrisObjectPlacement> extras = entry.getExtraObjects();
            if (extras != null && !extras.isEmpty()) {
                for (IrisObjectPlacement placement : extras) {
                    tryPlaceAnchoredChunk(writer, complex, chunkRng, data, placement, samples, columns, minX, minZ, entry);
                }
            }
        }
    }

    @ChunkCoordinates
    private void tryPlaceFloatingChunk(MantleWriter writer, IrisComplex complex, RNG rng, IrisData data, IrisObjectPlacement placement, FloatingIslandSample[] samples, KList<Integer> columns, int minX, int minZ, IrisFloatingChildBiomes entry) {
        if (placement == null || columns == null || columns.isEmpty()) {
            return;
        }
        int density = placement.getDensity(rng, minX, minZ, data);
        double perAttempt = placement.getChance();
        for (int i = 0; i < density; i++) {
            if (!rng.chance(perAttempt + rng.d(-0.005, 0.005))) {
                continue;
            }
            IrisObject raw = placement.getObject(complex, rng);
            if (raw == null) {
                continue;
            }
            IrisObject obj0 = placement.getScale().get(rng, raw);
            if (obj0 == null) {
                continue;
            }
            if (entry != null && entry.hasObjectShrink()) {
                obj0 = entry.getShrinkScale().get(rng, obj0);
                if (obj0 == null) {
                    continue;
                }
            }
            final IrisObject obj = obj0;

            int key = columns.get(rng.i(0, columns.size() - 1));
            int xx = minX + (key & 15);
            int zz = minZ + (key >> 4);
            IrisObjectPlacement floatingPlacement = placement.toPlacement(obj.getLoadKey());
            int id = rng.i(0, Integer.MAX_VALUE);

            try {
                obj.place(xx, -1, zz, writer, floatingPlacement, rng, (b, bd) -> {
                    String marker = placementMarker(obj, id);
                    if (marker != null) {
                        writer.setData(b.getX(), b.getY(), b.getZ(), marker);
                    }
                }, null, data);
            } catch (Throwable e) {
                Iris.reportError(e);
            }
        }
    }

    @ChunkCoordinates
    private void tryPlaceAnchoredChunk(MantleWriter writer, IrisComplex complex, RNG rng, IrisData data, IrisObjectPlacement placement, FloatingIslandSample[] samples, KList<Integer> columns, int minX, int minZ, IrisFloatingChildBiomes entry) {
        if (placement == null || columns.isEmpty()) {
            return;
        }
        KList<Integer> interior = interiorColumns(samples, columns);
        KList<Integer> pickPool = interior.isEmpty() ? columns : interior;
        int density = placement.getDensity(rng, minX, minZ, data);
        double perAttempt = placement.getChance();
        for (int i = 0; i < density; i++) {
            if (!rng.chance(perAttempt + rng.d(-0.005, 0.005))) {
                continue;
            }
            IrisObject raw = placement.getObject(complex, rng);
            if (raw == null) {
                continue;
            }
            IrisObject obj0 = placement.getScale().get(rng, raw);
            if (obj0 == null) {
                continue;
            }
            if (entry != null && entry.hasObjectShrink()) {
                obj0 = entry.getShrinkScale().get(rng, obj0);
                if (obj0 == null) {
                    continue;
                }
            }
            final IrisObject obj = obj0;

            int key = pickPool.get(rng.i(0, pickPool.size() - 1));
            int xf = key & 15;
            int zf = key >> 4;
            FloatingIslandSample sample = samples[(zf << 4) | xf];
            if (sample == null) {
                continue;
            }
            int wx = minX + xf;
            int wz = minZ + zf;

            int anchorY = sample.topY() + 1 + obj.getCenter().getBlockY();
            int id = rng.i(0, Integer.MAX_VALUE);

            IrisObjectPlacement anchored = placement.toPlacement(obj.getLoadKey());
            anchored.setCarvingSupport(CarvingMode.ANYWHERE);
            anchored.setForcePlace(true);
            anchored.setMode(ObjectPlaceMode.STRUCTURE_PIECE);
            anchored.setBore(false);
            anchored.setMeld(false);

            try {
                obj.place(wx, anchorY, wz, writer, anchored, rng, (b, bd) -> {
                    String marker = placementMarker(obj, id);
                    if (marker != null) {
                        writer.setData(b.getX(), b.getY(), b.getZ(), marker);
                    }
                }, null, data);
            } catch (Throwable e) {
                Iris.reportError(e);
            }
        }
    }

    private static KList<Integer> interiorColumns(FloatingIslandSample[] samples, KList<Integer> columns) {
        KList<Integer> interior = new KList<>();
        for (int key : columns) {
            int xf = key & 15;
            int zf = key >> 4;
            if (xf <= 0 || xf >= 15 || zf <= 0 || zf >= 15) {
                continue;
            }
            if (samples[(zf << 4) | (xf + 1)] == null) continue;
            if (samples[(zf << 4) | (xf - 1)] == null) continue;
            if (samples[((zf + 1) << 4) | xf] == null) continue;
            if (samples[((zf - 1) << 4) | xf] == null) continue;
            interior.add(key);
        }
        return interior;
    }

    private static String placementMarker(IrisObject object, int id) {
        if (object == null) {
            return null;
        }
        String key = object.getLoadKey();
        if (key == null || key.isEmpty() || key.equals("null")) {
            return null;
        }
        return key + "@" + id;
    }

    @Override
    protected int computeRadius() {
        int maxThickness = 0;
        int maxHeightAbove = 0;
        try {
            for (IrisBiome biome : getDimension().getAllBiomes(this::getData)) {
                KList<IrisFloatingChildBiomes> entries = biome.getFloatingChildBiomes();
                if (entries == null || entries.isEmpty()) {
                    continue;
                }
                for (IrisFloatingChildBiomes entry : entries) {
                    maxThickness = Math.max(maxThickness, entry.getMaxThickness());
                    maxHeightAbove = Math.max(maxHeightAbove, entry.getMaxHeightAboveSurface());
                }
            }
        } catch (Throwable ignored) {
        }
        return Math.max(1, (maxThickness + maxHeightAbove) >> 4);
    }
}
