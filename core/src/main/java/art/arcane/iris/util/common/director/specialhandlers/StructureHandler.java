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

package art.arcane.iris.util.common.director.specialhandlers;

import art.arcane.iris.core.nms.INMS;
import art.arcane.iris.engine.framework.Engine;
import art.arcane.iris.engine.framework.IrisStructureLocator;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.iris.util.common.director.DirectorParameterHandler;
import art.arcane.volmlib.util.director.exceptions.DirectorParsingException;

import java.util.stream.Collectors;

public class StructureHandler implements DirectorParameterHandler<String> {
    @Override
    public KList<String> getPossibilities() {
        KList<String> keys = new KList<>();

        try {
            for (String k : INMS.get().getStructureKeys()) {
                if (k != null && !k.isEmpty()) {
                    keys.addIfMissing(k);
                }
            }
        } catch (Throwable ignored) {
        }

        try {
            Engine e = engine();
            if (e != null) {
                for (String k : IrisStructureLocator.placedKeys(e)) {
                    if (k != null && !k.isEmpty()) {
                        keys.addIfMissing(k);
                    }
                }
            }
        } catch (Throwable ignored) {
        }

        return keys;
    }

    @Override
    public String toString(String structure) {
        return structure;
    }

    @Override
    public String parse(String in, boolean force) throws DirectorParsingException {
        KList<String> options = getPossibilities(in);

        if (options.isEmpty()) {
            return in;
        }
        try {
            return options.stream().filter((i) -> toString(i).equalsIgnoreCase(in)).collect(Collectors.toList()).get(0);
        } catch (Throwable e) {
            return in;
        }
    }

    @Override
    public boolean supports(Class<?> type) {
        return type.equals(String.class);
    }

    @Override
    public String getRandomDefault() {
        String f = getPossibilities().getRandom();

        return f == null ? "minecraft_ancient_city" : f;
    }
}
