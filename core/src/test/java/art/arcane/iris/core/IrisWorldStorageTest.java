package art.arcane.iris.core;

import org.bukkit.NamespacedKey;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

public class IrisWorldStorageTest {
    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void derivesPaperDimensionRootFromNamespacedKey() throws Exception {
        File levelRoot = temporaryFolder.newFolder("world");
        NamespacedKey key = new NamespacedKey("iris", "runtime/studio");

        File dimensionRoot = IrisWorldStorage.dimensionRoot(levelRoot, key);

        assertEquals(new File(levelRoot, "dimensions/iris/runtime/studio").getAbsoluteFile(), dimensionRoot);
        assertEquals(key, IrisWorldStorage.keyFromDimensionRoot(levelRoot, dimensionRoot).orElseThrow());
    }

    @Test
    public void separatesLevelRootFromDimensionRoot() throws Exception {
        File levelRoot = temporaryFolder.newFolder("world");
        File dimensionRoot = new File(levelRoot, "dimensions/minecraft/overworld");

        assertEquals(levelRoot.getAbsoluteFile(), IrisWorldStorage.levelRoot(dimensionRoot));
    }

    @Test
    public void mapsLegacyBukkitNamesToPaperKeys() {
        assertEquals(NamespacedKey.minecraft("overworld"), IrisWorldStorage.keyFromLegacyName("world", "world"));
        assertEquals(NamespacedKey.minecraft("the_nether"), IrisWorldStorage.keyFromLegacyName("world_nether", "world"));
        assertEquals(NamespacedKey.minecraft("the_end"), IrisWorldStorage.keyFromLegacyName("world_the_end", "world"));
        assertEquals(NamespacedKey.minecraft("iris_world"), IrisWorldStorage.keyFromLegacyName("Iris World", "world"));
    }

    @Test
    public void rejectsKeysThatEscapeNamespaceStorage() throws Exception {
        File levelRoot = temporaryFolder.newFolder("world");
        NamespacedKey key = NamespacedKey.minecraft("../outside");

        assertThrows(IllegalArgumentException.class, () -> IrisWorldStorage.dimensionRoot(levelRoot, key));
    }
}
