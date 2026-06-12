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

package art.arcane.iris.modded.command;

import art.arcane.iris.core.loader.IrisData;
import art.arcane.iris.core.nms.datapack.DataVersion;
import art.arcane.iris.engine.object.IrisDimension;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

final class ModdedWorldDatapackWriter {
    static final String WORLD_PACK_NAME = "iris";
    private static final String DEFAULT_NAMESPACE = "irisworldgen";
    private static final String TYPE_NAMESPACE = "irisworldgen";

    private ModdedWorldDatapackWriter() {
    }

    static WriteResult enable(MinecraftServer server, String targetDimension, String packName, String packDimension) throws IOException {
        ResourceTarget target = ResourceTarget.parse(targetDimension);
        String cleanPackName = cleanPackSegment(packName, "pack");
        String cleanPackDimension = cleanPackSegment(packDimension, "pack dimension");
        File packFolder = new File(ModdedPackCommands.packsRoot(), cleanPackName);
        if (!packFolder.isDirectory()) {
            throw new IllegalArgumentException("Pack '" + cleanPackName + "' was not found under " + ModdedPackCommands.packsRoot().getAbsolutePath());
        }

        IrisData data = IrisData.get(packFolder);
        IrisDimension dimension = data.getDimensionLoader().load(cleanPackDimension);
        if (dimension == null) {
            throw new IllegalArgumentException("Pack '" + cleanPackName + "' does not contain dimensions/" + cleanPackDimension + ".json");
        }

        String typeKey = IrisDimension.sanitizeDimensionTypeKeyValue(cleanPackDimension);
        File typeFile = dimensionTypeFile(server, typeKey);
        File dimensionFile = dimensionFile(server, target);
        File mcmeta = packMetaFile(server);

        writeParented(typeFile, dimension.getDimensionType().toJson(DataVersion.getLatest().get()));
        writeParented(dimensionFile, dimensionJson(cleanPackDimension, typeKey));
        writePackMeta(mcmeta);

        return new WriteResult(target.id(), cleanPackName, cleanPackDimension, dimensionFile, typeFile, mcmeta);
    }

    static File disable(MinecraftServer server, String targetDimension) throws IOException {
        ResourceTarget target = ResourceTarget.parse(targetDimension);
        File dimensionFile = dimensionFile(server, target);
        if (!dimensionFile.isFile()) {
            throw new IllegalArgumentException("No Iris world datapack dimension exists for " + target.id());
        }
        Files.delete(dimensionFile.toPath());
        return dimensionFile;
    }

    static List<String> enabledDimensions(MinecraftServer server) throws IOException {
        File dataRoot = new File(irisPackFolder(server), "data");
        if (!dataRoot.isDirectory()) {
            return List.of();
        }

        List<String> dimensions = new ArrayList<>();
        File[] namespaces = dataRoot.listFiles(File::isDirectory);
        if (namespaces == null) {
            return List.of();
        }

        for (File namespace : namespaces) {
            File dimensionRoot = new File(namespace, "dimension");
            if (!dimensionRoot.isDirectory()) {
                continue;
            }
            collectDimensionFiles(namespace.getName(), dimensionRoot, dimensions);
        }
        Collections.sort(dimensions);
        return dimensions;
    }

    static File packMetaFile(MinecraftServer server) {
        return new File(irisPackFolder(server), "pack.mcmeta");
    }

    private static void collectDimensionFiles(String namespace, File dimensionRoot, List<String> dimensions) throws IOException {
        Path root = dimensionRoot.toPath();
        try (Stream<Path> stream = Files.walk(root)) {
            stream.filter(Files::isRegularFile)
                    .filter((Path path) -> path.getFileName().toString().endsWith(".json"))
                    .forEach((Path path) -> {
                        String relative = root.relativize(path).toString().replace(File.separatorChar, '/');
                        String idPath = relative.substring(0, relative.length() - ".json".length());
                        dimensions.add(namespace + ":" + idPath);
                    });
        }
    }

    private static File worldDatapacksFolder(MinecraftServer server) {
        return server.getWorldPath(LevelResource.DATAPACK_DIR).toFile();
    }

    private static File irisPackFolder(MinecraftServer server) {
        return new File(worldDatapacksFolder(server), WORLD_PACK_NAME);
    }

    private static File dimensionTypeFile(MinecraftServer server, String typeKey) {
        return new File(irisPackFolder(server), "data/" + TYPE_NAMESPACE + "/dimension_type/" + typeKey + ".json");
    }

    private static File dimensionFile(MinecraftServer server, ResourceTarget target) {
        return new File(new File(irisPackFolder(server), "data/" + target.namespace() + "/dimension"), target.path() + ".json");
    }

    private static void writePackMeta(File output) throws IOException {
        int packFormat = DataVersion.getLatest().getPackFormat();
        String json = "{\n"
                + "  \"pack\": {\n"
                + "    \"description\": \"Iris world and dimension type definitions.\",\n"
                + "    \"pack_format\": " + packFormat + ",\n"
                + "    \"min_format\": " + packFormat + ",\n"
                + "    \"max_format\": " + packFormat + "\n"
                + "  }\n"
                + "}\n";
        writeParented(output, json);
    }

    private static void writeParented(File output, String text) throws IOException {
        File parent = output.getParentFile();
        if (parent != null) {
            parent.mkdirs();
        }
        Files.writeString(output.toPath(), text, StandardCharsets.UTF_8);
    }

    private static String dimensionJson(String packDimension, String typeKey) {
        return "{\n"
                + "  \"type\": \"" + TYPE_NAMESPACE + ":" + typeKey + "\",\n"
                + "  \"generator\": {\n"
                + "    \"type\": \"irisworldgen:iris\",\n"
                + "    \"dimension\": \"" + escape(packDimension) + "\",\n"
                + "    \"biome_source\": {\n"
                + "      \"type\": \"minecraft:fixed\",\n"
                + "      \"biome\": \"minecraft:plains\"\n"
                + "    }\n"
                + "  }\n"
                + "}\n";
    }

    private static String cleanPackSegment(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Missing " + label + ".");
        }
        String clean = value.trim();
        if (clean.contains("/") || clean.contains("..") || clean.contains("\\") || clean.contains(":")) {
            throw new IllegalArgumentException("Invalid " + label + " '" + value + "'.");
        }
        return clean;
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    record WriteResult(String targetDimension, String packName, String packDimension, File dimensionFile, File typeFile, File packMetaFile) {
    }

    private record ResourceTarget(String namespace, String path) {
        static ResourceTarget parse(String value) {
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException("Missing dimension id.");
            }

            String normalized = value.trim().toLowerCase(Locale.ROOT);
            String namespace = DEFAULT_NAMESPACE;
            String path = normalized;
            int colon = normalized.indexOf(':');
            if (colon >= 0) {
                namespace = normalized.substring(0, colon);
                path = normalized.substring(colon + 1);
            }

            if (!namespace.matches("[a-z0-9_.-]+") || !path.matches("[a-z0-9_./-]+") || path.startsWith("/") || path.endsWith("/") || path.contains("..")) {
                throw new IllegalArgumentException("Invalid dimension id '" + value + "'. Use name or namespace:path.");
            }
            return new ResourceTarget(namespace, path);
        }

        String id() {
            return namespace + ":" + path;
        }
    }
}
