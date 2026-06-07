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

import art.arcane.iris.engine.object.IrisMaterialPalette;
import art.arcane.iris.engine.object.IrisProceduralTree;
import art.arcane.iris.engine.object.IrisTreeAzimuthMode;

import java.util.ArrayList;
import java.util.List;

public final class TreeTrunkBuilder {
    private TreeTrunkBuilder() {
    }

    public static double[][] build(TreeBlockCanvas canvas, IrisProceduralTree tree, int height) {
        boolean hasSecondary = hasSecondaryTrunk(tree);

        double[][] offsets = new double[height][2];
        double prevCx = 0.0;
        double prevCz = 0.0;

        for (int y = 0; y < height; y++) {
            double[] lean = leanOffset(tree, y, height);
            double cx = lean[0];
            double cz = lean[1];
            offsets[y][0] = cx;
            offsets[y][1] = cz;

            int w = widthAt(tree, y, height);
            TreeBlockCanvas.Axis axis = TreeFunctions.logAxis(cx - prevCx, 1.0, cz - prevCz);
            TreeBlockCanvas.Role role = trunkRole(tree, hasSecondary, y, height);

            for (int[] xz : squarePositions(cx, cz, w)) {
                canvas.setTrunk(xz[0], y, xz[1], role, axis);
            }

            if (y > 0) {
                double shift = Math.hypot(cx - prevCx, cz - prevCz);
                if (shift > 1.0) {
                    int steps = (int) Math.ceil(shift);
                    for (int s = 1; s < steps; s++) {
                        double tFill = s / (double) steps;
                        double icx = prevCx + (cx - prevCx) * tFill;
                        double icz = prevCz + (cz - prevCz) * tFill;
                        int fillY = (int) Math.round(y - 1 + tFill);
                        for (int[] xz : squarePositions(icx, icz, w)) {
                            if (!canvas.has(xz[0], fillY, xz[1])) {
                                canvas.setTrunk(xz[0], fillY, xz[1], role, axis);
                            }
                        }
                    }
                }
            }

            prevCx = cx;
            prevCz = cz;
        }

        markExposedEnds(canvas);
        return offsets;
    }

    private static void markExposedEnds(TreeBlockCanvas canvas) {
        List<TreeBlockCanvas.Vec> trunkPositions = new ArrayList<>(canvas.getTrunk());
        for (TreeBlockCanvas.Vec v : trunkPositions) {
            if (!canvas.has(v.x(), v.y() + 1, v.z()) || !canvas.has(v.x(), v.y() - 1, v.z())) {
                canvas.markExposed(v.x(), v.y(), v.z());
            }
        }
    }

    private static int widthAt(IrisProceduralTree tree, int y, int height) {
        double t = y / (double) Math.max(height - 1, 1);
        double multiplier = TreeFunctions.trunkWidthMultiplier(tree, t);
        return Math.max(1, (int) Math.round(tree.getTrunkWidth() * multiplier));
    }

    private static TreeBlockCanvas.Role trunkRole(IrisProceduralTree tree, boolean hasSecondary, int y, int height) {
        if (!hasSecondary) {
            return TreeBlockCanvas.Role.TRUNK;
        }
        double t = y / (double) Math.max(height - 1, 1);
        if (tree.getSecondaryTrunkStart() <= t && t <= tree.getSecondaryTrunkEnd()) {
            return TreeBlockCanvas.Role.SECONDARY_TRUNK;
        }
        return TreeBlockCanvas.Role.TRUNK;
    }

    private static boolean hasSecondaryTrunk(IrisProceduralTree tree) {
        if (paletteSet(tree.getSecondaryTrunkPalette())) {
            return true;
        }
        return tree.getSecondaryTrunk() != null && !tree.getSecondaryTrunk().isEmpty();
    }

    static boolean paletteSet(IrisMaterialPalette palette) {
        return palette != null && palette.getPalette() != null && !palette.getPalette().isEmpty();
    }

    private static double[] leanOffset(IrisProceduralTree tree, int y, int height) {
        if (tree.getLeanAngle() == 0.0 || height <= 1) {
            return new double[]{0.0, 0.0};
        }
        double t = y / (double) Math.max(height - 1, 1);
        double progress = TreeFunctions.curveProgress(tree.getTrunkCurve(), t, tree.getCurveSteepness());
        double maxReach = height * Math.tan(Math.toRadians(tree.getLeanAngle()));
        double reach = maxReach * progress;
        double azDeg = tree.getLeanAzimuthMode() == IrisTreeAzimuthMode.CONSTANT
                ? tree.getLeanAzimuth()
                : TreeFunctions.azimuthDegrees(tree.getLeanAzimuthMode(), t, -1, tree, tree.getLeanAzimuth(), null);
        double azRad = Math.toRadians(azDeg);
        double dx = reach * Math.sin(azRad);
        double dz = reach * Math.cos(azRad);
        return new double[]{dx, dz};
    }

    private static List<int[]> squarePositions(double cx, double cz, int width) {
        List<int[]> out = new ArrayList<>();
        if (width % 2 == 1) {
            int half = width / 2;
            int icx = (int) Math.round(cx);
            int icz = (int) Math.round(cz);
            for (int dx = -half; dx <= half; dx++) {
                for (int dz = -half; dz <= half; dz++) {
                    out.add(new int[]{icx + dx, icz + dz});
                }
            }
        } else {
            int ox = (int) Math.floor(cx) - width / 2 + 1;
            int oz = (int) Math.floor(cz) - width / 2 + 1;
            for (int dx = 0; dx < width; dx++) {
                for (int dz = 0; dz < width; dz++) {
                    out.add(new int[]{ox + dx, oz + dz});
                }
            }
        }
        return out;
    }

    static String stripState(String block) {
        if (block == null) {
            return null;
        }
        int idx = block.indexOf('[');
        return idx < 0 ? block : block.substring(0, idx);
    }
}
