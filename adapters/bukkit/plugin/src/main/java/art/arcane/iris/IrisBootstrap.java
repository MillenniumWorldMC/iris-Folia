package art.arcane.iris;

import art.arcane.iris.core.pack.DefaultPackBootstrapProvisioner;
import art.arcane.iris.core.pack.DefaultPackBootstrapProvisioner.ProvisionResult;
import io.papermc.paper.datapack.Datapack;
import io.papermc.paper.datapack.DatapackRegistrar;
import io.papermc.paper.datapack.DiscoveredDatapack;
import io.papermc.paper.plugin.bootstrap.BootstrapContext;
import io.papermc.paper.plugin.bootstrap.PluginBootstrap;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;

import java.io.IOException;
import java.nio.file.Path;

@SuppressWarnings("UnstableApiUsage")
public final class IrisBootstrap implements PluginBootstrap {
    static final String PACK_ID = "generated";

    @Override
    public void bootstrap(BootstrapContext context) {
        ProvisionResult provisioned = provision(context);
        Path datapackRoot = provisioned.datapackRoot();
        context.getLogger().info("Iris startup datapack is {} at {}", provisioned.status(), datapackRoot);
        context.getLifecycleManager().registerEventHandler(LifecycleEvents.DATAPACK_DISCOVERY, event -> {
            try {
                discoverPack(event.registrar(), datapackRoot);
            } catch (IOException e) {
                throw new IllegalStateException("Unable to discover the Iris startup datapack at " + datapackRoot, e);
            }
        });
    }

    static DiscoveredDatapack discoverPack(DatapackRegistrar registrar, Path datapackRoot) throws IOException {
        DiscoveredDatapack datapack = registrar.discoverPack(
                datapackRoot,
                PACK_ID,
                configurer -> configurer
                        .autoEnableOnServerStart(true)
                        .position(true, Datapack.Position.TOP)
        );
        if (datapack == null) {
            throw new IllegalStateException("Paper did not accept the Iris startup datapack at " + datapackRoot);
        }
        return datapack;
    }

    private static ProvisionResult provision(BootstrapContext context) {
        try {
            return DefaultPackBootstrapProvisioner.provision(
                    context.getDataDirectory(),
                    message -> context.getLogger().info(message)
            );
        } catch (IOException e) {
            throw new IllegalStateException("Unable to provision the Iris startup datapack", e);
        }
    }
}
