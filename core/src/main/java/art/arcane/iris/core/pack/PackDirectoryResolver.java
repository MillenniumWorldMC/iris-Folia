package art.arcane.iris.core.pack;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;

public final class PackDirectoryResolver {
    private PackDirectoryResolver() {
    }

    public static File resolveExisting(File packsRoot, String packName) {
        if (packsRoot == null || packName == null || packName.isBlank()) {
            return null;
        }
        Path root = packsRoot.toPath().toAbsolutePath().normalize();
        Path candidate = root.resolve(packName).normalize();
        if (!root.equals(candidate.getParent())) {
            return null;
        }
        if (Files.isSymbolicLink(candidate) || !Files.isDirectory(candidate, LinkOption.NOFOLLOW_LINKS)) {
            return null;
        }
        return candidate.toFile();
    }
}
