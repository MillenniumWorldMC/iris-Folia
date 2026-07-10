package art.arcane.iris.core.pack;

import org.junit.Assume;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class PackDirectoryResolverTest {
    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void resolvesOnlyExistingDirectChildren() throws Exception {
        File packs = temporaryFolder.newFolder("packs");
        File overworld = new File(packs, "overworld");
        Files.createDirectory(overworld.toPath());

        assertEquals(overworld.getAbsoluteFile(), PackDirectoryResolver.resolveExisting(packs, "overworld"));
        assertEquals(overworld.getAbsoluteFile(), PackDirectoryResolver.resolveExisting(packs, "./overworld"));
        assertNull(PackDirectoryResolver.resolveExisting(packs, "missing"));
        assertNull(PackDirectoryResolver.resolveExisting(packs, ""));
    }

    @Test
    public void rejectsTraversalAbsoluteAndNestedPaths() throws Exception {
        File packs = temporaryFolder.newFolder("pack-root");
        File outside = temporaryFolder.newFolder("outside");
        File nested = new File(packs, "nested/pack");
        Files.createDirectories(nested.toPath());

        assertNull(PackDirectoryResolver.resolveExisting(packs, "../outside"));
        assertNull(PackDirectoryResolver.resolveExisting(packs, outside.getAbsolutePath()));
        assertNull(PackDirectoryResolver.resolveExisting(packs, "nested/pack"));
        assertNull(PackDirectoryResolver.resolveExisting(packs, "."));
    }

    @Test
    public void rejectsSymbolicLinkChildren() throws Exception {
        File packs = temporaryFolder.newFolder("symlink-root");
        File outside = temporaryFolder.newFolder("symlink-target");
        Path link = new File(packs, "linked").toPath();
        try {
            Files.createSymbolicLink(link, outside.toPath());
        } catch (IOException | UnsupportedOperationException | SecurityException e) {
            Assume.assumeNoException(e);
        }

        assertNull(PackDirectoryResolver.resolveExisting(packs, "linked"));
    }
}
