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

package art.arcane.iris.engine.object.formation;

import art.arcane.iris.engine.object.IrisFormation;
import art.arcane.iris.engine.object.tree.TreeFunctions;
import art.arcane.volmlib.util.math.RNG;

public final class FormationShapeBuilder {
    private FormationShapeBuilder() {
    }

    public static void spire(FormationCanvas canvas, IrisFormation f, int height, double baseRadius, RNG rng) {
        double topRadius = f.getTopWidth();
        column(canvas, f, height, baseRadius, topRadius, 0, 0, rng, true);
    }

    public static void seaStack(FormationCanvas canvas, IrisFormation f, int height, double baseRadius, RNG rng) {
        double topRadius = Math.max(1.0, baseRadius * 0.55);
        if (f.getTopWidth() > 0) {
            topRadius = f.getTopWidth();
        }
        column(canvas, f, height, baseRadius, topRadius, 0, 0, rng, true);
    }

    public static void hoodoo(FormationCanvas canvas, IrisFormation f, int height, double baseRadius, RNG rng) {
        double topRadius = f.getTopWidth() > 0 ? f.getTopWidth() : Math.max(1.0, baseRadius * 0.7);
        column(canvas, f, height, baseRadius, topRadius, 0, 0, rng, false);

        int capRadius = f.getHoodooCapRadius();
        if (capRadius <= 0) {
            return;
        }
        int capHeight = Math.max(1, f.getHoodooCapHeight());
        double lean = Math.toRadians(f.getLean());
        double azimuth = Math.toRadians(f.getLeanAzimuth());
        double shear = Math.tan(lean) * height;
        double topOffX = Math.cos(azimuth) * shear;
        double topOffZ = Math.sin(azimuth) * shear;
        double wideRadius = topRadius + capRadius;

        for (int cy = 0; cy < capHeight; cy++) {
            int y = height + cy;
            double shrink = capHeight <= 1 ? 0 : (cy / (double) (capHeight - 1)) * 0.5;
            double r = wideRadius * (1.0 - shrink);
            disc(canvas, f, (int) Math.round(topOffX), y, (int) Math.round(topOffZ), r, true, rng);
        }
    }

    public static void boulder(FormationCanvas canvas, IrisFormation f, int height, double baseRadius, RNG rng) {
        double rx = baseRadius;
        double ry = Math.max(2.0, height * 0.5);
        double rz = baseRadius * (0.8 + rng.d(0.0, 0.3));
        double roughness = Math.max(0.0, Math.min(1.0, f.getRoughness()));
        long noiseSeed = f.getSeed() + 4201L;

        int maxR = (int) Math.ceil(Math.max(rx, Math.max(ry, rz))) + 2;
        for (int x = -maxR; x <= maxR; x++) {
            for (int y = 0; y <= (int) Math.ceil(ry * 2) + 1; y++) {
                for (int z = -maxR; z <= maxR; z++) {
                    double nx = x / rx;
                    double ny = (y - ry) / ry;
                    double nz = z / rz;
                    double d = nx * nx + ny * ny + nz * nz;
                    double wobble = (TreeFunctions.valueNoise3D(x, y, z, noiseSeed) - 0.5) * roughness * 0.9;
                    if (d + wobble <= 1.0) {
                        canvas.setBody(x, y, z);
                    }
                }
            }
        }
    }

    public static void arch(FormationCanvas canvas, IrisFormation f, int height, double baseRadius, RNG rng) {
        int span = Math.max(2, f.getArchSpan());
        int thickness = Math.max(1, f.getArchThickness());
        int legHeight = Math.max(2, (int) Math.round(height * 0.55));
        int halfSpan = span / 2;
        double legRadius = Math.max(1.0, thickness / 2.0 + baseRadius * 0.25);
        long noiseSeed = f.getSeed() + 7777L;
        double roughness = Math.max(0.0, Math.min(1.0, f.getRoughness()));

        for (int side = -1; side <= 1; side += 2) {
            int legX = side * (halfSpan + (int) Math.ceil(legRadius));
            for (int y = 0; y < legHeight; y++) {
                disc(canvas, f, legX, y, 0, legRadius, false, rng);
            }
        }

        int archTop = legHeight + (int) Math.round(span * 0.45);
        int leftX = -(halfSpan + (int) Math.ceil(legRadius));
        int rightX = halfSpan + (int) Math.ceil(legRadius);
        double archWidth = (rightX - leftX) / 2.0;
        double centerX = (leftX + rightX) / 2.0;
        double archHeight = archTop - legHeight;
        int half = Math.max(1, thickness / 2);

        for (int x = leftX; x <= rightX; x++) {
            double nx = (x - centerX) / archWidth;
            if (nx < -1.0 || nx > 1.0) {
                continue;
            }
            double curveY = legHeight + archHeight * Math.sqrt(Math.max(0.0, 1.0 - nx * nx));
            int yc = (int) Math.round(curveY);
            for (int dy = -half; dy <= half; dy++) {
                for (int z = -half; z <= half; z++) {
                    double wobble = (TreeFunctions.valueNoise3D(x, yc + dy, z, noiseSeed) - 0.5) * roughness;
                    if (z * z + dy * dy <= half * half + 0.5 + wobble) {
                        canvas.setBody(x, Math.max(0, yc + dy), z);
                    }
                }
            }
        }
    }

    public static void basaltColumns(FormationCanvas canvas, IrisFormation f, int height, double baseRadius, RNG rng) {
        int count = Math.max(2, f.getBasaltColumns());
        int colRadius = Math.max(1, f.getBasaltColumnRadius());
        double variance = Math.max(0.0, Math.min(1.0, f.getBasaltHeightVariance()));
        int spread = (int) Math.ceil(baseRadius);

        for (int c = 0; c < count; c++) {
            double angle = rng.d(0.0, Math.PI * 2.0);
            double dist = rng.d(0.0, spread);
            int ox = (int) Math.round(Math.cos(angle) * dist);
            int oz = (int) Math.round(Math.sin(angle) * dist);
            double hVar = 1.0 - variance + rng.d(0.0, variance * 2.0);
            int colHeight = Math.max(3, (int) Math.round(height * hVar));

            for (int y = 0; y < colHeight; y++) {
                for (int x = -colRadius; x <= colRadius; x++) {
                    for (int z = -colRadius; z <= colRadius; z++) {
                        if (Math.abs(x) + Math.abs(z) > colRadius) {
                            continue;
                        }
                        canvas.setBody(ox + x, y, oz + z);
                    }
                }
            }
            if (colRadius > 0) {
                canvas.setCap(ox, colHeight - 1, oz);
            }
        }
    }

    private static void column(FormationCanvas canvas, IrisFormation f, int height, double baseRadius, double topRadius, int extraBaseX, int extraBaseZ, RNG rng, boolean capTop) {
        double lean = Math.toRadians(f.getLean());
        double azimuth = Math.toRadians(f.getLeanAzimuth());
        double roughness = Math.max(0.0, Math.min(1.0, f.getRoughness()));
        double jitter = Math.max(0.0, Math.min(1.0, f.getJitter()));
        long noiseSeed = f.getSeed() + 9001L;

        for (int y = 0; y < height; y++) {
            double t = height <= 1 ? 1.0 : (y / (double) (height - 1));
            double radius = FormationProfiles.radiusAt(f, baseRadius, topRadius, t);
            double shear = Math.tan(lean) * y;
            int cx = extraBaseX + (int) Math.round(Math.cos(azimuth) * shear);
            int cz = extraBaseZ + (int) Math.round(Math.sin(azimuth) * shear);
            boolean cap = capTop && y >= height - 2;
            ringDisc(canvas, f, cx, y, cz, radius, roughness, jitter, noiseSeed, cap, rng);
        }
    }

    private static void ringDisc(FormationCanvas canvas, IrisFormation f, int cx, int y, int cz, double radius, double roughness, double jitter, long noiseSeed, boolean cap, RNG rng) {
        int r = (int) Math.ceil(radius) + 1;
        for (int x = -r; x <= r; x++) {
            for (int z = -r; z <= r; z++) {
                double dist = Math.sqrt(x * x + z * z);
                double perturb = (TreeFunctions.valueNoise3D(cx + x, y, cz + z, noiseSeed) - 0.5) * roughness * (radius + 1.0);
                double effective = radius + perturb;
                if (dist <= effective) {
                    if (jitter > 0 && dist > effective - 1.0 && rng.chance(jitter * 0.5)) {
                        continue;
                    }
                    if (cap) {
                        canvas.setCap(cx + x, y, cz + z);
                    } else {
                        canvas.setBody(cx + x, y, cz + z);
                    }
                }
            }
        }
    }

    private static void disc(FormationCanvas canvas, IrisFormation f, int cx, int y, int cz, double radius, boolean cap, RNG rng) {
        int r = (int) Math.ceil(radius) + 1;
        for (int x = -r; x <= r; x++) {
            for (int z = -r; z <= r; z++) {
                if (Math.sqrt(x * x + z * z) <= radius) {
                    if (cap) {
                        canvas.setCap(cx + x, y, cz + z);
                    } else {
                        canvas.setBody(cx + x, y, cz + z);
                    }
                }
            }
        }
    }
}
