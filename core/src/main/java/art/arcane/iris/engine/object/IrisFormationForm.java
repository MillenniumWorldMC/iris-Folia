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

package art.arcane.iris.engine.object;

import art.arcane.iris.engine.object.annotations.Desc;

@Desc("The overall silhouette of a procedural rock formation. Each form drives a distinct sculpting routine in the formation generator.")
public enum IrisFormationForm {
    @Desc("A tall, slender needle of rock that tapers smoothly to a point at the top (a sharp pinnacle or stone spire).")
    SPIRE,
    @Desc("A column with a pinched, eroded waist and a wide overhanging caprock balanced on top (the classic desert hoodoo / mushroom rock).")
    HOODOO,
    @Desc("Two stout legs joined by a spanning curved bridge of rock overhead (a natural stone arch).")
    ARCH,
    @Desc("A chunky, blocky stack that tapers gently as it rises, like an isolated pillar of rock standing in water (a sea stack).")
    SEA_STACK,
    @Desc("A rounded, lumpy boulder formed from a noise-perturbed ellipsoid that sits low on the terrain.")
    BOULDER,
    @Desc("A tightly packed cluster of several vertical, near-hexagonal columns of varying height (a basalt column formation / giant's causeway).")
    BASALT_COLUMN
}
