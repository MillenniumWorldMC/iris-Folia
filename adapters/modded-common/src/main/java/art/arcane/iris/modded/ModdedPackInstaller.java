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

package art.arcane.iris.modded;

import art.arcane.iris.core.pack.PackDownloader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.function.Consumer;
import java.util.regex.Pattern;

public final class ModdedPackInstaller {
    private static final Logger LOGGER = LoggerFactory.getLogger("Iris");
    private static final Pattern PACK_NAME = Pattern.compile("[a-z0-9_-]+");
    private static final Pattern BRANCH_NAME = Pattern.compile("[A-Za-z0-9._-]+");

    private ModdedPackInstaller() {
    }

    public static boolean install(Path configDir, String pack, String branch, Consumer<String> feedback) {
        if (pack == null || !PACK_NAME.matcher(pack).matches()) {
            feedback.accept("Invalid pack name '" + pack + "' (allowed: a-z, 0-9, _ and -)");
            return false;
        }
        if (branch == null || !BRANCH_NAME.matcher(branch).matches()) {
            feedback.accept("Invalid branch name '" + branch + "' (allowed: letters, digits, . _ and -)");
            return false;
        }

        File packs = configDir.resolve("irisworldgen").resolve("packs").toFile();
        try {
            if (PackDownloader.isDefaultOverworld(pack)) {
                return PackDownloader.downloadDefaultOverworld(packs, true, feedback) != null;
            }
            return PackDownloader.download(packs, "IrisDimensions/" + pack, branch, true, false, feedback) != null;
        } catch (IOException error) {
            LOGGER.error("Iris pack download failed for IrisDimensions/{} ({})", pack, branch, error);
            feedback.accept("Pack download failed: " + error.getClass().getSimpleName() + (error.getMessage() == null ? "" : " - " + error.getMessage()));
            return false;
        }
    }
}
