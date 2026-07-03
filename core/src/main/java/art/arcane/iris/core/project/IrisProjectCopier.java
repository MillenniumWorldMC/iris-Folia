/*
 * Iris is a World Generator for Minecraft Servers
 * Copyright (c) 2026 Arcane Arts (Volmit Software)
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

package art.arcane.iris.core.project;

import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.io.IO;
import art.arcane.volmlib.util.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.stream.Stream;

public final class IrisProjectCopier {
    private IrisProjectCopier() {
    }

    public static void copyProject(File sourcePack, File targetPack, String sourceKey, String targetKey) throws IOException {
        Path source = sourcePack.toPath();
        try (Stream<Path> walk = Files.walk(source)) {
            for (Path path : walk.sorted(Comparator.naturalOrder()).toList()) {
                String relative = source.relativize(path).toString();
                if (relative.isEmpty() || relative.equals(".git") || relative.startsWith(".git" + File.separator) || relative.endsWith(".code-workspace")) {
                    continue;
                }
                Path destination = targetPack.toPath().resolve(relative);
                if (Files.isDirectory(path)) {
                    Files.createDirectories(destination);
                } else {
                    Files.createDirectories(destination.getParent());
                    Files.copy(path, destination, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }

        File oldDimension = new File(targetPack, "dimensions/" + sourceKey + ".json");
        File newDimension = new File(targetPack, "dimensions/" + targetKey + ".json");
        if (oldDimension.isFile() && !oldDimension.equals(newDimension)) {
            Files.copy(oldDimension.toPath(), newDimension.toPath(), StandardCopyOption.REPLACE_EXISTING);
            Files.delete(oldDimension.toPath());
        }
        if (newDimension.isFile()) {
            JSONObject json = new JSONObject(IO.readAll(newDimension));
            if (json.has("name")) {
                json.put("name", Form.capitalizeWords(targetKey.replaceAll("\\Q-\\E", " ")));
                IO.writeAll(newDimension, json.toString(4));
            }
        }
    }
}
