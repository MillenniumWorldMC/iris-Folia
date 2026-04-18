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

package art.arcane.iris.util.common.director;

import art.arcane.iris.util.common.format.C;
import art.arcane.iris.util.common.plugin.VolmitSender;
import art.arcane.volmlib.util.director.annotations.Director;
import art.arcane.volmlib.util.director.annotations.Param;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class DirectorHelp {
    private DirectorHelp() {
    }

    public static void print(VolmitSender sender, Class<?> commandRoot) {
        Director rootAnnotation = commandRoot.getAnnotation(Director.class);
        String rootName = rootAnnotation == null || rootAnnotation.name().isEmpty()
                ? lowercaseDefault(commandRoot.getSimpleName())
                : rootAnnotation.name();
        String rootDesc = rootAnnotation == null ? "" : rootAnnotation.description();

        sender.sendMessage(C.IRIS + "/" + rootName + C.GRAY + " — " + rootDesc);

        List<Method> methods = new ArrayList<>();
        for (Method m : commandRoot.getDeclaredMethods()) {
            if (m.isAnnotationPresent(Director.class)) {
                methods.add(m);
            }
        }
        methods.sort(Comparator.comparing(m -> methodName(m)));

        for (Method m : methods) {
            Director d = m.getAnnotation(Director.class);
            String name = methodName(m);
            String aliases = formatAliases(d.aliases());
            sender.sendMessage(C.WHITE + "  " + name + aliases + C.GRAY + " — " + d.description());
            for (Parameter p : m.getParameters()) {
                Param pa = p.getAnnotation(Param.class);
                if (pa == null) continue;
                String key = pa.name().isEmpty() ? p.getName() : pa.name();
                String type = simpleTypeName(p.getType());
                String def = pa.defaultValue().isEmpty() ? "" : C.GRAY + " (default: " + pa.defaultValue() + ")";
                String pAliases = formatAliases(pa.aliases());
                sender.sendMessage(C.GRAY + "      " + C.AQUA + key + "=" + C.YELLOW + "<" + type + ">"
                        + C.GRAY + pAliases + C.GRAY + " — " + pa.description() + def);
            }
        }

        List<Field> subGroups = new ArrayList<>();
        for (Field f : commandRoot.getDeclaredFields()) {
            if (f.getType().isAnnotationPresent(Director.class)) {
                subGroups.add(f);
            }
        }
        if (!subGroups.isEmpty()) {
            sender.sendMessage(C.IRIS + "  Subcommand groups:");
            for (Field f : subGroups) {
                Director sub = f.getType().getAnnotation(Director.class);
                String subName = sub.name().isEmpty() ? lowercaseDefault(f.getType().getSimpleName()) : sub.name();
                sender.sendMessage(C.WHITE + "    /" + rootName + " " + subName
                        + C.GRAY + " — " + sub.description() + C.GRAY + "  (try: /" + rootName + " " + subName + " help)");
            }
        }
    }

    private static String methodName(Method m) {
        Director d = m.getAnnotation(Director.class);
        if (d != null && !d.name().isEmpty()) return d.name();
        return m.getName();
    }

    private static String formatAliases(String[] aliases) {
        if (aliases == null || aliases.length == 0) return "";
        List<String> valid = new ArrayList<>();
        for (String a : aliases) {
            if (a != null && !a.isEmpty()) valid.add(a);
        }
        if (valid.isEmpty()) return "";
        return C.GRAY + " [" + String.join(", ", valid) + "]";
    }

    private static String simpleTypeName(Class<?> type) {
        if (type.isEnum()) {
            Object[] constants = type.getEnumConstants();
            List<String> names = new ArrayList<>();
            for (Object c : constants) names.add(((Enum<?>) c).name());
            return String.join("|", names);
        }
        return type.getSimpleName();
    }

    private static String lowercaseDefault(String simpleName) {
        String s = simpleName.startsWith("Command") ? simpleName.substring("Command".length()) : simpleName;
        return s.isEmpty() ? simpleName.toLowerCase() : s.toLowerCase();
    }
}
