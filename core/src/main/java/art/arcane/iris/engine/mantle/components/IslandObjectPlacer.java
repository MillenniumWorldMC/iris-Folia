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

import art.arcane.iris.core.loader.IrisData;
import art.arcane.iris.engine.framework.Engine;
import art.arcane.iris.engine.mantle.MantleWriter;
import art.arcane.iris.engine.object.FloatingIslandSample;
import art.arcane.iris.engine.object.IObjectPlacer;
import art.arcane.iris.engine.object.TileData;
import art.arcane.iris.util.common.data.B;
import org.bukkit.block.data.BlockData;
import org.jetbrains.annotations.Nullable;

public class IslandObjectPlacer implements IObjectPlacer {
    private static final int OVERHANG_RADIUS = 2;

    private final MantleWriter wrapped;
    private final FloatingIslandSample[] samples;
    private final int minX;
    private final int minZ;
    private final int chunkMaxIslandTopY;
    private final int anchorTopY;
    private int writesAttempted;
    private int writesDroppedBelow;
    private int writesDroppedOverhang;

    public IslandObjectPlacer(MantleWriter wrapped, FloatingIslandSample[] samples, int minX, int minZ, int anchorTopY) {
        this.wrapped = wrapped;
        this.samples = samples;
        this.minX = minX;
        this.minZ = minZ;
        this.anchorTopY = anchorTopY;
        int maxY = -1;
        for (FloatingIslandSample s : samples) {
            if (s != null) {
                int ty = s.topY();
                if (ty > maxY) {
                    maxY = ty;
                }
            }
        }
        this.chunkMaxIslandTopY = maxY;
    }

    public int getWritesAttempted() {
        return writesAttempted;
    }

    public int getWritesDroppedBelow() {
        return writesDroppedBelow;
    }

    public int getWritesDroppedOverhang() {
        return writesDroppedOverhang;
    }

    private boolean shouldSkipAirColumn(int x, int y, int z) {
        writesAttempted++;
        if (sampleAt(x, z) != null) {
            return false;
        }
        if (y <= anchorTopY) {
            writesDroppedBelow++;
            return true;
        }
        if (!hasIslandNeighborWithin(x, z, OVERHANG_RADIUS)) {
            writesDroppedOverhang++;
            return true;
        }
        return false;
    }

    private boolean hasIslandNeighborWithin(int x, int z, int radius) {
        int xf = x - minX;
        int zf = z - minZ;
        boolean touchedChunkEdge = false;
        for (int dx = -radius; dx <= radius; dx++) {
            int nxf = xf + dx;
            for (int dz = -radius; dz <= radius; dz++) {
                int nzf = zf + dz;
                if (nxf < 0 || nxf >= 16 || nzf < 0 || nzf >= 16) {
                    touchedChunkEdge = true;
                    continue;
                }
                if (samples[(nzf << 4) | nxf] != null) {
                    return true;
                }
            }
        }
        return touchedChunkEdge;
    }

    private @Nullable FloatingIslandSample sampleAt(int x, int z) {
        int xf = x - minX;
        int zf = z - minZ;
        if (xf < 0 || xf >= 16 || zf < 0 || zf >= 16) {
            return null;
        }
        return samples[(zf << 4) | xf];
    }

    @Override
    public int getHighest(int x, int z, IrisData data) {
        FloatingIslandSample s = sampleAt(x, z);
        if (s != null) {
            return s.topY();
        }
        return chunkMaxIslandTopY;
    }

    @Override
    public int getHighest(int x, int z, IrisData data, boolean ignoreFluid) {
        FloatingIslandSample s = sampleAt(x, z);
        if (s != null) {
            return s.topY();
        }
        return chunkMaxIslandTopY;
    }

    @Override
    public boolean isUnderwater(int x, int z) {
        return false;
    }

    @Override
    public boolean isSolid(int x, int y, int z) {
        FloatingIslandSample s = sampleAt(x, z);
        if (s != null) {
            int idx = y - s.islandBaseY;
            if (idx >= 0 && idx < s.solidMask.length) {
                return s.solidMask[idx];
            }
            return false;
        }
        return wrapped.isSolid(x, y, z);
    }

    @Override
    public boolean isCarved(int x, int y, int z) {
        return wrapped.isCarved(x, y, z);
    }

    @Override
    public void set(int x, int y, int z, BlockData d) {
        if (shouldSkipAirColumn(x, y, z)) {
            return;
        }
        wrapped.set(x, y, z, d);
    }

    @Override
    public BlockData get(int x, int y, int z) {
        return wrapped.get(x, y, z);
    }

    @Override
    public boolean isPreventingDecay() {
        return wrapped.isPreventingDecay();
    }

    @Override
    public int getFluidHeight() {
        return wrapped.getFluidHeight();
    }

    @Override
    public boolean isDebugSmartBore() {
        return wrapped.isDebugSmartBore();
    }

    @Override
    public void setTile(int xx, int yy, int zz, TileData tile) {
        if (shouldSkipAirColumn(xx, yy, zz)) {
            return;
        }
        wrapped.setTile(xx, yy, zz, tile);
    }

    @Override
    public <T> void setData(int xx, int yy, int zz, T data) {
        if (shouldSkipAirColumn(xx, yy, zz)) {
            return;
        }
        wrapped.setData(xx, yy, zz, data);
    }

    @Override
    public <T> @Nullable T getData(int xx, int yy, int zz, Class<T> t) {
        return wrapped.getData(xx, yy, zz, t);
    }

    @Override
    public Engine getEngine() {
        return wrapped.getEngine();
    }
}
