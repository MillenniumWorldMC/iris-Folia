package art.arcane.iris;

import io.papermc.paper.datapack.Datapack;
import io.papermc.paper.datapack.DatapackRegistrar;
import io.papermc.paper.datapack.DatapackSource;
import io.papermc.paper.datapack.DiscoveredDatapack;
import io.papermc.paper.plugin.configuration.PluginMeta;
import net.kyori.adventure.text.Component;
import org.bukkit.FeatureFlag;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class IrisBootstrapTest {
    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void discoverPackAutoEnablesAtFixedTop() throws Exception {
        File datapackDirectory = temporaryFolder.newFolder("datapack");
        RecordingRegistrar registrar = new RecordingRegistrar(DiscoveryBehavior.ACCEPT);

        DiscoveredDatapack discovered = IrisBootstrap.discoverPack(registrar, datapackDirectory.toPath());

        assertSame(registrar.discoveredDatapack, discovered);
        assertEquals(datapackDirectory.toPath(), registrar.discoveredPath);
        assertEquals(IrisBootstrap.PACK_ID, registrar.discoveredId);
        assertTrue(registrar.configurer.autoEnableOnServerStart);
        assertTrue(registrar.configurer.fixedPosition);
        assertEquals(Datapack.Position.TOP, registrar.configurer.position);
    }

    @Test
    public void rejectedDiscoveryReportsDatapackPath() throws Exception {
        File datapackDirectory = temporaryFolder.newFolder("datapack");
        RecordingRegistrar registrar = new RecordingRegistrar(DiscoveryBehavior.REJECT);

        IllegalStateException failure = assertThrows(
                IllegalStateException.class,
                () -> IrisBootstrap.discoverPack(registrar, datapackDirectory.toPath())
        );

        assertTrue(failure.getMessage().contains(datapackDirectory.toPath().toString()));
    }

    @Test
    public void discoveryIoFailurePropagates() throws Exception {
        File datapackDirectory = temporaryFolder.newFolder("datapack");
        RecordingRegistrar registrar = new RecordingRegistrar(DiscoveryBehavior.FAIL);

        IOException failure = assertThrows(
                IOException.class,
                () -> IrisBootstrap.discoverPack(registrar, datapackDirectory.toPath())
        );

        assertEquals("discovery failed", failure.getMessage());
    }

    private enum DiscoveryBehavior {
        ACCEPT,
        REJECT,
        FAIL
    }

    private static final class RecordingConfigurer implements DatapackRegistrar.Configurer {
        private boolean autoEnableOnServerStart;
        private boolean fixedPosition;
        private Datapack.Position position;

        @Override
        public DatapackRegistrar.Configurer title(Component title) {
            return this;
        }

        @Override
        public DatapackRegistrar.Configurer autoEnableOnServerStart(boolean autoEnableOnServerStart) {
            this.autoEnableOnServerStart = autoEnableOnServerStart;
            return this;
        }

        @Override
        public DatapackRegistrar.Configurer position(boolean fixed, Datapack.Position position) {
            this.fixedPosition = fixed;
            this.position = position;
            return this;
        }
    }

    private static final class RecordingRegistrar implements DatapackRegistrar {
        private final DiscoveryBehavior behavior;
        private final RecordingConfigurer configurer;
        private final DiscoveredDatapack discoveredDatapack;
        private Path discoveredPath;
        private String discoveredId;

        private RecordingRegistrar(DiscoveryBehavior behavior) {
            this.behavior = behavior;
            this.configurer = new RecordingConfigurer();
            this.discoveredDatapack = new StubDiscoveredDatapack();
        }

        @Override
        public boolean hasPackDiscovered(String name) {
            return false;
        }

        @Override
        public DiscoveredDatapack getDiscoveredPack(String name) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean removeDiscoveredPack(String name) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Map<String, DiscoveredDatapack> getDiscoveredPacks() {
            return Collections.emptyMap();
        }

        @Override
        public DiscoveredDatapack discoverPack(URI uri, String id, Consumer<Configurer> configurer) {
            throw new UnsupportedOperationException();
        }

        @Override
        public DiscoveredDatapack discoverPack(Path path, String id, Consumer<Configurer> configurer) throws IOException {
            this.discoveredPath = path;
            this.discoveredId = id;
            if (behavior == DiscoveryBehavior.FAIL) {
                throw new IOException("discovery failed");
            }
            configurer.accept(this.configurer);
            return behavior == DiscoveryBehavior.ACCEPT ? discoveredDatapack : null;
        }

        @Override
        public DiscoveredDatapack discoverPack(PluginMeta pluginMeta, URI uri, String id, Consumer<Configurer> configurer) {
            throw new UnsupportedOperationException();
        }

        @Override
        public DiscoveredDatapack discoverPack(PluginMeta pluginMeta, Path path, String id, Consumer<Configurer> configurer) {
            throw new UnsupportedOperationException();
        }
    }

    private static final class StubDiscoveredDatapack implements DiscoveredDatapack {
        @Override
        public String getName() {
            return IrisBootstrap.PACK_ID;
        }

        @Override
        public Component getTitle() {
            return Component.text(IrisBootstrap.PACK_ID);
        }

        @Override
        public Component getDescription() {
            return Component.empty();
        }

        @Override
        public boolean isRequired() {
            return true;
        }

        @Override
        public Datapack.Compatibility getCompatibility() {
            return Datapack.Compatibility.COMPATIBLE;
        }

        @Override
        public Set<FeatureFlag> getRequiredFeatures() {
            return Collections.emptySet();
        }

        @Override
        public DatapackSource getSource() {
            throw new UnsupportedOperationException();
        }
    }
}
