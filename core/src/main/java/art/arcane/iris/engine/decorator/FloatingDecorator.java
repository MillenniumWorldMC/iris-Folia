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

package art.arcane.iris.engine.decorator;

import art.arcane.iris.Iris;
import art.arcane.iris.engine.framework.Engine;
import art.arcane.iris.engine.object.IrisBiome;
import art.arcane.iris.engine.object.IrisDecorationPart;
import art.arcane.iris.engine.object.IrisDecorator;
import art.arcane.iris.util.project.hunk.Hunk;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.math.RNG;
import org.bukkit.block.data.Bisected;
import org.bukkit.block.data.BlockData;

import java.util.concurrent.atomic.AtomicLong;

/*
 * Floating island decoration path. Bypasses all canGoOn, slope, whitelist, and blacklist
 * gating from IrisSurfaceDecorator — the island top IS the biome's designated surface by
 * construction, so those material-compatibility checks are never meaningful here.
 */
public class FloatingDecorator {
    public static final AtomicLong decCandidatesNull = new AtomicLong();

    private static final long SEED_SALT = 29356788L;
    private static final long PART_SALT = 10439677L;

    public static int decorateColumn(Engine engine, IrisBiome target, IrisDecorationPart part,
                                     int xf, int zf, int realX, int realZ,
                                     int height, int max, Hunk<BlockData> data, RNG rng) {
        long gSeed = engine.getSeedManager().getDecorator() + SEED_SALT - (part.ordinal() * PART_SALT);
        RNG gRNG = new RNG(gSeed);
        KList<IrisDecorator> candidates = new KList<>();

        for (IrisDecorator d : target.getDecorators()) {
            try {
                if (d.getPartOf().equals(part) && d.getBlockData(target, gRNG, realX, realZ, engine.getData()) != null) {
                    candidates.add(d);
                }
            } catch (Throwable e) {
                Iris.reportError(e);
            }
        }

        if (candidates.isEmpty()) {
            decCandidatesNull.incrementAndGet();
            return 0;
        }

        IrisDecorator decorator = candidates.get(rng.nextInt(candidates.size()));

        if (!decorator.isStacking()) {
            return placeSimple(decorator, target, xf, zf, realX, realZ, height, max, data, rng, engine);
        } else {
            return placeStacked(decorator, target, xf, zf, realX, realZ, height, max, data, rng, engine);
        }
    }

    private static int placeSimple(IrisDecorator decorator, IrisBiome target,
                                   int xf, int zf, int realX, int realZ,
                                   int height, int max, Hunk<BlockData> data, RNG rng, Engine engine) {
        BlockData bd = decorator.getBlockData100(target, rng, realX, height, realZ, engine.getData());
        if (bd == null) {
            return 0;
        }

        if (bd instanceof Bisected) {
            BlockData top = bd.clone();
            ((Bisected) top).setHalf(Bisected.Half.TOP);
            try {
                if (max > 2) {
                    data.set(xf, height + 2, zf, top);
                }
            } catch (Throwable e) {
                Iris.reportError(e);
            }
            bd = bd.clone();
            ((Bisected) bd).setHalf(Bisected.Half.BOTTOM);
        }

        if (max > 1) {
            data.set(xf, height + 1, zf, bd);
            return 1;
        }
        return 0;
    }

    private static int placeStacked(IrisDecorator decorator, IrisBiome target,
                                    int xf, int zf, int realX, int realZ,
                                    int height, int max, Hunk<BlockData> data, RNG rng, Engine engine) {
        int stack = decorator.getHeight(rng, realX, realZ, engine.getData());

        if (decorator.isScaleStack()) {
            stack = Math.min((int) Math.ceil((double) max * ((double) stack / 100)), decorator.getAbsoluteMaxStack());
        } else {
            stack = Math.min(max, stack);
        }

        int placed = 0;
        for (int i = 0; i < stack; i++) {
            int h = height + 1 + i;
            if (h >= height + max) {
                break;
            }
            double threshold = stack == 1 ? 0.0 : ((double) i) / (stack - 1);
            BlockData bd = threshold >= decorator.getTopThreshold()
                    ? decorator.getBlockDataForTop(target, rng, realX, height + i, realZ, engine.getData())
                    : decorator.getBlockData100(target, rng, realX, height + i, realZ, engine.getData());
            if (bd == null) {
                break;
            }
            data.set(xf, h, zf, bd);
            placed++;
        }
        return placed;
    }
}
