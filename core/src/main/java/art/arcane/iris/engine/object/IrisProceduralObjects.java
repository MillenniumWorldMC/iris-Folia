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

import art.arcane.iris.engine.object.annotations.ArrayType;
import art.arcane.iris.engine.object.annotations.Desc;
import art.arcane.iris.engine.object.annotations.Snippet;
import art.arcane.volmlib.util.collection.KList;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Snippet("procedural-objects")
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
@Desc("Procedurally generated objects placed in a biome. Unlike the objects block (which loads iob files), these are generated from scratch at world-gen time.")
@Data
public class IrisProceduralObjects {
    @ArrayType(min = 1, type = IrisProceduralTree.class)
    @Desc("Procedurally generated trees placed in this biome.")
    private KList<IrisProceduralTree> trees = new KList<>();

    public boolean isEmpty() {
        return trees == null || trees.isEmpty();
    }
}
