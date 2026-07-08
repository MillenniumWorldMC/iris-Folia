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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

public final class MainWorldService {
    private static final Logger LOGGER = LoggerFactory.getLogger("Iris");
    private static final String PRESET_NAMESPACE = "irisworldgen";
    private static final String MARKER_NAME = "mainworld.pending";
    private static final String[] VANILLA_DIMENSION_FOLDERS = {
            "region",
            "entities",
            "poi",
            "mantle",
            "dimensions/minecraft/overworld",
            "dimensions/minecraft/the_nether",
            "dimensions/minecraft/the_end",
            "DIM-1",
            "DIM1"
    };

    private MainWorldService() {
    }

    public static String presetIdFor(String packRef) {
        String value = packRef.trim();
        int colon = value.indexOf(':');
        String pack = colon >= 0 ? value.substring(0, colon) : value;
        String dimension = colon >= 0 ? value.substring(colon + 1) : value;
        String presetKey = dimension.equals(pack) ? pack : pack + "_" + dimension;
        return PRESET_NAMESPACE + ":" + presetKey;
    }

    public static void reconcileEarly() {
        try {
            ModdedModConfig config = ModdedModConfig.get();
            String pack = config.mainWorldPack();
            if (pack == null || pack.isBlank()) {
                return;
            }
            Path properties = instanceRoot().resolve("server.properties");
            String target = presetIdFor(pack);
            String currentType = readProperty(properties, "level-type");
            if (!target.equals(currentType)) {
                writeLevelProperties(properties, target, config.mainWorldSeed());
                markPending();
                LOGGER.warn("Iris main world '{}' staged: server.properties level-type set to {}. Restart again to generate it (this boot still uses the previous overworld; player data is kept).", pack, target);
                return;
            }
            if (!isPending()) {
                return;
            }
            String levelName = firstNonBlank(readProperty(properties, "level-name"), "world");
            wipeVanillaDimensions(instanceRoot().resolve(levelName));
            clearPending();
            LOGGER.warn("Iris main world '{}' generated fresh: cleared the previous overworld/nether/end so this boot regenerates them as {} (player data kept).", pack, target);
        } catch (Throwable e) {
            LOGGER.error("Iris main world reconciliation failed", e);
        }
    }

    public static boolean stage(String packRef, long seed) {
        try {
            Path properties = instanceRoot().resolve("server.properties");
            writeLevelProperties(properties, presetIdFor(packRef), seed);
            markPending();
            return true;
        } catch (IOException e) {
            LOGGER.error("Iris failed to stage the main world in server.properties", e);
            return false;
        }
    }

    public static void clearOverride() {
        try {
            clearPending();
        } catch (IOException e) {
            LOGGER.error("Iris failed to clear the pending main world marker", e);
        }
    }

    private static Path instanceRoot() {
        return ModdedEngineBootstrap.loader().configDir().getParent();
    }

    private static Path markerFile() {
        return ModdedEngineBootstrap.loader().configDir().resolve("irisworldgen").resolve(MARKER_NAME);
    }

    private static boolean isPending() {
        return Files.isRegularFile(markerFile());
    }

    private static void markPending() throws IOException {
        Path marker = markerFile();
        Files.createDirectories(marker.getParent());
        Files.writeString(marker, "pending", StandardCharsets.UTF_8);
    }

    private static void clearPending() throws IOException {
        Files.deleteIfExists(markerFile());
    }

    private static String readProperty(Path properties, String key) throws IOException {
        if (!Files.isRegularFile(properties)) {
            return null;
        }
        List<String> lines = Files.readAllLines(properties, StandardCharsets.UTF_8);
        String prefix = key + "=";
        for (String line : lines) {
            if (line.startsWith(prefix)) {
                return unescape(line.substring(prefix.length()).trim());
            }
        }
        return null;
    }

    private static void writeLevelProperties(Path properties, String target, long seed) throws IOException {
        List<String> lines = Files.isRegularFile(properties)
                ? new ArrayList<>(Files.readAllLines(properties, StandardCharsets.UTF_8))
                : new ArrayList<>();
        setProperty(lines, "level-type", escape(target));
        if (seed != 0L) {
            setProperty(lines, "level-seed", Long.toString(seed));
        }
        Files.write(properties, lines, StandardCharsets.UTF_8);
    }

    private static void setProperty(List<String> lines, String key, String value) {
        String prefix = key + "=";
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).startsWith(prefix)) {
                lines.set(i, prefix + value);
                return;
            }
        }
        lines.add(prefix + value);
    }

    private static void wipeVanillaDimensions(Path worldRoot) throws IOException {
        Files.deleteIfExists(worldRoot.resolve("level.dat"));
        Files.deleteIfExists(worldRoot.resolve("level.dat_old"));
        for (String folder : VANILLA_DIMENSION_FOLDERS) {
            deleteRecursively(worldRoot.resolve(folder));
        }
    }

    private static void deleteRecursively(Path path) throws IOException {
        if (!Files.exists(path)) {
            return;
        }
        List<Path> entries = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(path)) {
            walk.sorted(Comparator.comparingInt(Path::getNameCount).reversed()).forEach(entries::add);
        }
        for (Path entry : entries) {
            Files.deleteIfExists(entry);
        }
    }

    private static String escape(String value) {
        return value.replace(":", "\\:");
    }

    private static String unescape(String value) {
        return value.replace("\\:", ":").replace("\\=", "=");
    }

    private static String firstNonBlank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
