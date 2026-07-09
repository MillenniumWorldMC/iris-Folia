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

import art.arcane.iris.engine.framework.Engine;
import art.arcane.iris.engine.framework.EngineAssignedModifier;
import art.arcane.iris.engine.object.IrisBiome;
import art.arcane.iris.engine.object.IrisProceduralBlocks;
import art.arcane.iris.engine.object.IrisSlopeClip;
import art.arcane.iris.util.project.context.ChunkContext;
import art.arcane.iris.util.common.data.B;
import art.arcane.iris.util.project.hunk.Hunk;
import art.arcane.volmlib.util.math.RNG;
import art.arcane.volmlib.util.scheduling.PrecisionStopwatch;
import art.arcane.iris.spi.PlatformBlockState;

public class IrisPostModifier extends EngineAssignedModifier<PlatformBlockState> {
    private static final class States {
        private static final PlatformBlockState AIR = B.getState("AIR");
        private static final PlatformBlockState WATER = B.getState("WATER");
    }

    private final RNG rng;

    public IrisPostModifier(Engine engine) {
        super(engine, "Post");
        rng = new RNG(getEngine().getSeedManager().getPost());
    }

    @Override
    public void onModify(int x, int z, Hunk<PlatformBlockState> output, boolean multicore, ChunkContext context) {
        PrecisionStopwatch p = PrecisionStopwatch.start();
        Hunk<PlatformBlockState> sync = output.synchronize();
        int width = output.getWidth();
        int depth = output.getDepth();
        for (int i = 0; i < width; i++) {
            for (int j = 0; j < depth; j++) {
                post(i, j, sync, i + x, j + z, context);
            }
        }

        getEngine().getMetrics().getPost().put(p.getMilliseconds());
    }

    private void post(int currentPostX, int currentPostZ, Hunk<PlatformBlockState> currentData, int x, int z, ChunkContext context) {
        int h = getEngine().getMantle().trueHeight(x, z);
        int ha = getEngine().getMantle().trueHeight(x + 1, z);
        int hb = getEngine().getMantle().trueHeight(x, z + 1);
        int hc = getEngine().getMantle().trueHeight(x - 1, z);
        int hd = getEngine().getMantle().trueHeight(x, z - 1);

        // Floating Nibs
        int g = 0;

        if (h < 1) {
            return;
        }

        g += ha < h - 1 ? 1 : 0;
        g += hb < h - 1 ? 1 : 0;
        g += hc < h - 1 ? 1 : 0;
        g += hd < h - 1 ? 1 : 0;

        if (g == 4 && isAir(x, h - 1, z, currentPostX, currentPostZ, currentData)) {
            setPostBlock(x, h, z, States.AIR, currentPostX, currentPostZ, currentData);

            for (int i = h - 1; i > 0; i--) {
                if (!isAir(x, i, z, currentPostX, currentPostZ, currentData)) {
                    h = i;
                    break;
                }
            }
        }

        // Nibs
        g = 0;
        g += ha == h - 1 ? 1 : 0;
        g += hb == h - 1 ? 1 : 0;
        g += hc == h - 1 ? 1 : 0;
        g += hd == h - 1 ? 1 : 0;

        if (g >= 4) {
            PlatformBlockState bcState = getPostBlock(x, h, z, currentPostX, currentPostZ, currentData);
            PlatformBlockState bState = getPostBlock(x, h + 1, z, currentPostX, currentPostZ, currentData);

            if (bState.isOccluding() && bState.isSolid()) {
                if (bcState.isSolid()) {
                    setPostBlock(x, h, z, bState, currentPostX, currentPostZ, currentData);
                    h--;
                }
            }
        } else {
            // Potholes
            g = 0;
            g += ha == h + 1 ? 1 : 0;
            g += hb == h + 1 ? 1 : 0;
            g += hc == h + 1 ? 1 : 0;
            g += hd == h + 1 ? 1 : 0;

            if (g >= 4) {
                PlatformBlockState ba = getPostBlock(x, ha, z, currentPostX, currentPostZ, currentData);
                PlatformBlockState bb = getPostBlock(x, hb, z, currentPostX, currentPostZ, currentData);
                PlatformBlockState bc = getPostBlock(x, hc, z, currentPostX, currentPostZ, currentData);
                PlatformBlockState bd = getPostBlock(x, hd, z, currentPostX, currentPostZ, currentData);
                g = 0;
                g = B.isSolid(ba) ? g + 1 : g;
                g = B.isSolid(bb) ? g + 1 : g;
                g = B.isSolid(bc) ? g + 1 : g;
                g = B.isSolid(bd) ? g + 1 : g;

                if (g >= 3) {
                    setPostBlock(x, h + 1, z, getPostBlock(x, h, z, currentPostX, currentPostZ, currentData), currentPostX, currentPostZ, currentData);
                    h++;
                }
            }
        }

        // Wall Patcher
        IrisBiome biome = context.getBiome().get(currentPostX, currentPostZ);

        if (getDimension().isPostProcessingWalls()) {
            if (!biome.getWall().getPalette().isEmpty()) {
                if (ha < h - 2 || hb < h - 2 || hc < h - 2 || hd < h - 2) {
                    boolean brokeGround = false;
                    int max = Math.abs(Math.max(h - ha, Math.max(h - hb, Math.max(h - hc, h - hd))));

                    for (int i = h; i > h - max; i--) {
                        PlatformBlockState d = biome.getWall().get(rng, x + i, i + h, z + i, getData());

                        if (d != null) {
                            if (isAirOrWater(x, i, z, currentPostX, currentPostZ, currentData)) {
                                if (brokeGround) {
                                    break;
                                }

                                continue;
                            }

                            setPostBlock(x, i, z, d, currentPostX, currentPostZ, currentData);
                            brokeGround = true;
                        }
                    }
                }
            }
        }

        // Slab
        if (getDimension().isPostProcessingSlabs()) {
            //@builder
            if ((ha == h + 1 && isSolidNonSlab(x + 1, ha, z, currentPostX, currentPostZ, currentData))
                    || (hb == h + 1 && isSolidNonSlab(x, hb, z + 1, currentPostX, currentPostZ, currentData))
                    || (hc == h + 1 && isSolidNonSlab(x - 1, hc, z, currentPostX, currentPostZ, currentData))
                    || (hd == h + 1 && isSolidNonSlab(x, hd, z - 1, currentPostX, currentPostZ, currentData)))
            //@done
            {
                IrisSlopeClip sc = biome.getSlab().getSlopeCondition();
                PlatformBlockState d = sc.isValid(getComplex().getSlopeStream().get(x, z)) ? biome.getSlab().get(rng, x, h, z, getData()) : null;

                if (d != null) {
                    boolean cancel = B.isAir(d);

                    if (IrisProceduralBlocks.materialKey(d).equals("minecraft:snow") && h + 1 <= getDimension().getFluidHeight()) {
                        cancel = true;
                    }

                    if (isSnowLayer(x, h, z, currentPostX, currentPostZ, currentData)) {
                        cancel = true;
                    }

                    if (!cancel && isAirOrWater(x, h + 1, z, currentPostX, currentPostZ, currentData)) {
                        setPostBlock(x, h + 1, z, d, currentPostX, currentPostZ, currentData);
                        h++;
                    }
                }
            }
        }

        // Waterlogging
        PlatformBlockState b = getPostBlock(x, h, z, currentPostX, currentPostZ, currentData);

        if (IrisProceduralBlocks.hasProperty(b, "waterlogged")) {
            boolean w = false;

            if (h <= getDimension().getFluidHeight() + 1) {
                if (isWaterOrWaterlogged(x, h + 1, z, currentPostX, currentPostZ, currentData)) {
                    w = true;
                } else if ((isWaterOrWaterlogged(x + 1, h, z, currentPostX, currentPostZ, currentData) || isWaterOrWaterlogged(x - 1, h, z, currentPostX, currentPostZ, currentData) || isWaterOrWaterlogged(x, h, z + 1, currentPostX, currentPostZ, currentData) || isWaterOrWaterlogged(x, h, z - 1, currentPostX, currentPostZ, currentData))) {
                    w = true;
                }
            }

            if (w != "true".equals(IrisProceduralBlocks.propertyValue(b, "waterlogged"))) {
                setPostBlock(x, h, z, b.withProperty("waterlogged", String.valueOf(w)), currentPostX, currentPostZ, currentData);
            }
        } else if (IrisProceduralBlocks.materialKey(b).equals("minecraft:air") && h <= getDimension().getFluidHeight()) {
            if ((isWaterOrWaterlogged(x + 1, h, z, currentPostX, currentPostZ, currentData) || isWaterOrWaterlogged(x - 1, h, z, currentPostX, currentPostZ, currentData) || isWaterOrWaterlogged(x, h, z + 1, currentPostX, currentPostZ, currentData) || isWaterOrWaterlogged(x, h, z - 1, currentPostX, currentPostZ, currentData))) {
                setPostBlock(x, h, z, States.WATER, currentPostX, currentPostZ, currentData);
            }
        }

        // Foliage
        b = getPostBlock(x, h + 1, z, currentPostX, currentPostZ, currentData);

        if (B.isVineBlock(b)) {
            PlatformBlockState result = b;
            int finalH = h + 1;

            for (String face : IrisProceduralBlocks.FACE_PROPERTIES) {
                if (!IrisProceduralBlocks.hasProperty(b, face)) {
                    continue;
                }
                int[] mod = IrisProceduralBlocks.faceOffset(face);
                PlatformBlockState d = getPostBlock(x + mod[0], finalH + mod[1], z + mod[2], currentPostX, currentPostZ, currentData);
                result = result.withProperty(face, String.valueOf(!B.isAir(d) && !B.isVineBlock(d)));
            }
            if (!result.equals(b)) {
                setPostBlock(x, h + 1, z, result, currentPostX, currentPostZ, currentData);
            }
        }

        if (B.isFoliage(b) || IrisProceduralBlocks.materialKey(b).equals("minecraft:dead_bush")) {
            PlatformBlockState onto = getPostBlock(x, h, z, currentPostX, currentPostZ, currentData);

            if (!B.canPlaceOnto(b, onto) && !B.isDecorant(b)) {
                setPostBlock(x, h + 1, z, States.AIR, currentPostX, currentPostZ, currentData);
            }
        }
    }

    public boolean isAir(int x, int y, int z, int currentPostX, int currentPostZ, Hunk<PlatformBlockState> currentData) {
        String material = IrisProceduralBlocks.materialKey(getPostBlock(x, y, z, currentPostX, currentPostZ, currentData));
        return material.equals("minecraft:air") || material.equals("minecraft:cave_air");
    }

    public boolean hasGravity(int x, int y, int z, int currentPostX, int currentPostZ, Hunk<PlatformBlockState> currentData) {
        String material = IrisProceduralBlocks.materialKey(getPostBlock(x, y, z, currentPostX, currentPostZ, currentData));
        return material.equals("minecraft:sand") || material.equals("minecraft:red_sand") || material.endsWith("_concrete_powder");
    }

    public boolean isSolid(int x, int y, int z, int currentPostX, int currentPostZ, Hunk<PlatformBlockState> currentData) {
        PlatformBlockState d = getPostBlock(x, y, z, currentPostX, currentPostZ, currentData);
        return B.isSolid(d) && !B.isVineBlock(d);
    }

    public boolean isSolidNonSlab(int x, int y, int z, int currentPostX, int currentPostZ, Hunk<PlatformBlockState> currentData) {
        PlatformBlockState d = getPostBlock(x, y, z, currentPostX, currentPostZ, currentData);
        return B.isSolid(d) && !IrisProceduralBlocks.materialKey(d).endsWith("_slab");
    }

    public boolean isAirOrWater(int x, int y, int z, int currentPostX, int currentPostZ, Hunk<PlatformBlockState> currentData) {
        String material = IrisProceduralBlocks.materialKey(getPostBlock(x, y, z, currentPostX, currentPostZ, currentData));
        return material.equals("minecraft:water") || material.equals("minecraft:air") || material.equals("minecraft:cave_air");
    }

    public boolean isSlab(int x, int y, int z, int currentPostX, int currentPostZ, Hunk<PlatformBlockState> currentData) {
        return IrisProceduralBlocks.materialKey(getPostBlock(x, y, z, currentPostX, currentPostZ, currentData)).endsWith("_slab");
    }

    public boolean isSnowLayer(int x, int y, int z, int currentPostX, int currentPostZ, Hunk<PlatformBlockState> currentData) {
        return IrisProceduralBlocks.materialKey(getPostBlock(x, y, z, currentPostX, currentPostZ, currentData)).equals("minecraft:snow");
    }

    public boolean isWater(int x, int y, int z, int currentPostX, int currentPostZ, Hunk<PlatformBlockState> currentData) {
        return IrisProceduralBlocks.materialKey(getPostBlock(x, y, z, currentPostX, currentPostZ, currentData)).equals("minecraft:water");
    }

    public boolean isWaterOrWaterlogged(int x, int y, int z, int currentPostX, int currentPostZ, Hunk<PlatformBlockState> currentData) {
        PlatformBlockState d = getPostBlock(x, y, z, currentPostX, currentPostZ, currentData);
        return IrisProceduralBlocks.materialKey(d).equals("minecraft:water") || "true".equals(IrisProceduralBlocks.propertyValue(d, "waterlogged"));
    }

    public boolean isLiquid(int x, int y, int z, int currentPostX, int currentPostZ, Hunk<PlatformBlockState> currentData) {
        return IrisProceduralBlocks.hasProperty(getPostBlock(x, y, z, currentPostX, currentPostZ, currentData), "level");
    }

    public void setPostBlock(int x, int y, int z, PlatformBlockState d, int currentPostX, int currentPostZ, Hunk<PlatformBlockState> currentData) {
        if (y < currentData.getHeight()) {
            currentData.set(x & 15, y, z & 15, d);
        }
    }

    public PlatformBlockState getPostBlock(int x, int y, int z, int cpx, int cpz, Hunk<PlatformBlockState> h) {
        PlatformBlockState b = h.getClosest(x & 15, y, z & 15);

        return b == null ? States.AIR : b;
    }
}
