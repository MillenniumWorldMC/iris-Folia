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

package art.arcane.iris.engine.object.tree;

import art.arcane.iris.engine.object.IrisProceduralTree;
import art.arcane.volmlib.util.math.RNG;

import java.util.ArrayList;
import java.util.List;

public final class TreeRootBuilder {
    private TreeRootBuilder() {
    }

    public static void build(TreeBlockCanvas canvas, IrisProceduralTree tree, int height, long baseSeed) {
        List<int[]> cells = new ArrayList<>();
        for (TreeBlockCanvas.Vec v : canvas.getTrunk()) {
            if (v.y() == 0) {
                cells.add(new int[]{v.x(), v.z()});
            }
        }
        if (cells.isEmpty()) {
            return;
        }

        int depth = rootDepth(height);
        RNG rng = new RNG(baseSeed ^ 0x5009L);

        double cx = 0;
        double cz = 0;
        for (int[] c : cells) {
            cx += c[0];
            cz += c[1];
        }
        cx /= cells.size();
        cz /= cells.size();

        double baseRadius = 1.0;
        for (int[] c : cells) {
            baseRadius = Math.max(baseRadius, Math.hypot(c[0] - cx, c[1] - cz) + 0.5);
        }

        int icx = (int) Math.round(cx);
        int icz = (int) Math.round(cz);

        for (int k = 1; k <= depth; k++) {
            double frac = k / (double) depth;
            double keepR = baseRadius * (1.0 - 0.6 * frac);
            for (int[] c : cells) {
                if (Math.hypot(c[0] - cx, c[1] - cz) <= keepR + 1e-6) {
                    placeRoot(canvas, c[0], -k, c[1]);
                }
            }
            placeRoot(canvas, icx, -k, icz);
        }

        int nLegs = Math.max(4, Math.min(12, cells.size() + 2));
        int legLen = depth;
        double flare = baseRadius + Math.max(2.0, depth * 0.6);
        for (int li = 0; li < nLegs; li++) {
            double ang = 2.0 * Math.PI * li / nLegs + rng.d(-0.25, 0.25);
            double ca = Math.cos(ang);
            double sa = Math.sin(ang);
            int prevX = (int) Math.round(cx + baseRadius * ca);
            int prevZ = (int) Math.round(cz + baseRadius * sa);
            for (int k = 1; k <= legLen; k++) {
                double frac = k / (double) legLen;
                double r = baseRadius + (flare - baseRadius) * frac;
                int x = (int) Math.round(cx + r * ca);
                int z = (int) Math.round(cz + r * sa);
                placeRoot(canvas, x, -k, z);
                if (x != prevX || z != prevZ) {
                    placeRoot(canvas, prevX, -k, prevZ);
                }
                prevX = x;
                prevZ = z;
            }
        }
    }

    private static void placeRoot(TreeBlockCanvas canvas, int x, int y, int z) {
        if (!canvas.has(x, y, z)) {
            canvas.setTrunk(x, y, z, TreeBlockCanvas.Role.TRUNK, TreeBlockCanvas.Axis.Y);
        }
    }

    private static int rootDepth(int height) {
        return Math.max(2, Math.min(16, (int) Math.round(0.18 * height)));
    }
}
