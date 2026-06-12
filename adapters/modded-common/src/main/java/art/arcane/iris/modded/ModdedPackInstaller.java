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
import org.zeroturnaround.zip.ZipUtil;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import java.util.stream.Stream;

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

        Path target = configDir.resolve("irisworldgen").resolve("packs").resolve(pack);
        String url = "https://codeload.github.com/IrisDimensions/" + pack + "/zip/refs/heads/" + branch;

        try {
            Path work = Files.createTempDirectory("iris-pack-dl");
            Path zip = work.resolve(pack + ".zip");
            HttpClient client = HttpClient.newBuilder()
                    .followRedirects(HttpClient.Redirect.NORMAL)
                    .connectTimeout(Duration.ofSeconds(30))
                    .build();
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofMinutes(5))
                    .GET()
                    .build();
            HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());

            if (response.statusCode() != 200) {
                feedback.accept("Pack download failed: HTTP " + response.statusCode() + " from " + url);
                return false;
            }

            try (InputStream in = response.body()) {
                Files.copy(in, zip, StandardCopyOption.REPLACE_EXISTING);
            }

            Path extracted = work.resolve("extracted");
            ZipUtil.unpack(zip.toFile(), extracted.toFile());
            Path root = singleRoot(extracted);
            Files.createDirectories(target.getParent());
            deleteRecursively(target);
            copyRecursively(root, target);
            deleteRecursively(work);
            feedback.accept("Iris installed pack '" + pack + "' (branch " + branch + ") into " + target);
            return true;
        } catch (IOException | InterruptedException error) {
            LOGGER.error("Iris pack download failed for IrisDimensions/{} ({})", pack, branch, error);
            feedback.accept("Pack download failed: " + error.getClass().getSimpleName() + (error.getMessage() == null ? "" : " - " + error.getMessage()));

            if (error instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return false;
        }
    }

    private static Path singleRoot(Path extracted) throws IOException {
        try (Stream<Path> entries = Files.list(extracted)) {
            List<Path> children = entries.toList();

            if (children.size() == 1 && Files.isDirectory(children.get(0))) {
                return children.get(0);
            }

            return extracted;
        }
    }

    private static void copyRecursively(Path source, Path target) throws IOException {
        try (Stream<Path> walk = Files.walk(source)) {
            for (Path path : walk.toList()) {
                Path destination = target.resolve(source.relativize(path).toString());

                if (Files.isDirectory(path)) {
                    Files.createDirectories(destination);
                } else {
                    Files.createDirectories(destination.getParent());
                    Files.copy(path, destination, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }

    private static void deleteRecursively(Path root) throws IOException {
        if (!Files.exists(root)) {
            return;
        }

        try (Stream<Path> walk = Files.walk(root)) {
            for (Path path : walk.sorted(Comparator.reverseOrder()).toList()) {
                Files.delete(path);
            }
        }
    }
}
