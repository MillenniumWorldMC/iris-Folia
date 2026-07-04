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

import art.arcane.iris.core.gui.GuiHost;
import art.arcane.iris.engine.decorator.DecoratorPlatformHooks;
import art.arcane.iris.engine.framework.Engine;
import art.arcane.iris.engine.framework.EngineWorldManager;
import art.arcane.iris.engine.framework.EngineWorldManagerProvider;
import art.arcane.iris.engine.framework.PreservationRegistry;
import art.arcane.iris.engine.object.BlockDataMergeSupport;
import art.arcane.iris.engine.object.IrisObjectRotation;
import art.arcane.iris.engine.object.TileData;
import art.arcane.iris.modded.api.ModdedCustomContentRegistry;
import art.arcane.iris.modded.command.ModdedGuiHost;
import art.arcane.iris.modded.command.ModdedObjectUndo;
import art.arcane.iris.modded.command.ModdedPregenBossBar;
import art.arcane.iris.modded.command.ModdedPregenJob;
import art.arcane.iris.modded.command.ModdedStudioCommands;
import art.arcane.iris.modded.command.ModdedWandService;
import art.arcane.iris.modded.service.ModdedChunkUpdateService;
import art.arcane.iris.modded.service.ModdedEngineMaintenanceService;
import art.arcane.iris.modded.service.ModdedLogFilterService;
import art.arcane.iris.modded.service.ModdedPreservationService;
import art.arcane.iris.modded.service.ModdedSettingsHotloadService;
import art.arcane.iris.modded.service.ModdedStudioHotloadService;
import art.arcane.iris.spi.IrisPlatforms;
import art.arcane.iris.spi.IrisServices;
import net.minecraft.server.MinecraftServer;
import org.bukkit.Chunk;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ModdedEngineBootstrap {
    private static final Logger LOGGER = LoggerFactory.getLogger("Iris");
    private static final String[] CORE_SELF_TEST_CLASSES = {
        "art.arcane.iris.engine.IrisEngine",
        "art.arcane.iris.util.common.data.B",
        "art.arcane.iris.core.loader.IrisData"
    };
    private static final Object LOCK = new Object();
    private static final ModdedServiceManager SERVICE_MANAGER = new ModdedServiceManager();
    private static volatile ModdedLoader loader;
    private static volatile ModdedPlatform platform;
    private static volatile MinecraftServer currentServer;

    private ModdedEngineBootstrap() {
    }

    public static ModdedServiceManager services() {
        return SERVICE_MANAGER;
    }

    public static ModdedScheduler schedulerOrNull() {
        ModdedPlatform bound = platform;
        return bound == null ? null : bound.moddedScheduler();
    }

    public static void tick(MinecraftServer server) {
        ModdedScheduler.tick(server);
        ModdedStartup.runOnce(server);
        ModdedPrimaryWorldRouter.tick(server);
        SERVICE_MANAGER.tick(server);
        ModdedPregenBossBar.tick(server);
    }

    public static void start(MinecraftServer server) {
        currentServer = server;
        bind();
        ModdedStartup.reset();
        ModdedScheduler scheduler = schedulerOrNull();
        if (scheduler != null) {
            scheduler.reset();
        }
        SERVICE_MANAGER.enableAll();
    }

    public static void stop() {
        ModdedPregenJob.shutdown();
        ModdedObjectUndo.clearAll();
        ModdedWandService.clearAll();
        ModdedStudioCommands.clear();
        ModdedWorldEngines.shutdown();
        ModdedPrimaryWorldRouter.clear();
        SERVICE_MANAGER.disableAll();
        ModdedDimensionManager.clear();
        ModdedScheduler scheduler = schedulerOrNull();
        if (scheduler != null) {
            scheduler.reset();
        }
        ModdedStartup.reset();
        currentServer = null;
    }

    public static void bootCommon(ModdedLoader moddedLoader, String loaderDescription, Runnable chunkGeneratorRegistration) {
        loader = moddedLoader;
        ModdedIrisLog.info("Iris " + moddedLoader.modVersion() + " bootstrapping on Minecraft " + moddedLoader.minecraftVersion() + " (" + loaderDescription + ")");
        selfTest(moddedLoader.getClass().getClassLoader());
        bind();
        chunkGeneratorRegistration.run();
        ModdedIrisLog.info("Iris chunk generator registered as irisworldgen:iris");
        armParityProbe();
        armWorldCheck();
    }

    private static void armParityProbe() {
        String parity = System.getProperty("iris.parity");
        if (parity == null) {
            return;
        }
        ModdedIrisLog.info("Iris parity probe armed: " + parity);
        ModdedParityProbe.schedule(parity);
    }

    private static void armWorldCheck() {
        if (System.getProperty("iris.worldcheck") == null) {
            return;
        }
        ModdedIrisLog.info("Iris world check armed");
        ModdedWorldCheck.schedule();
    }

    public static ModdedLoader loader() {
        ModdedLoader bound = loader;
        if (bound == null) {
            throw new IllegalStateException("Iris modded loader is not initialized; the loader bootstrap must call ModdedEngineBootstrap.bootCommon first");
        }
        return bound;
    }

    public static MinecraftServer currentServer() {
        MinecraftServer tracked = currentServer;
        return tracked != null ? tracked : loader().currentServer();
    }

    private static void selfTest(ClassLoader classLoader) {
        int loadedClasses = 0;
        for (String className : CORE_SELF_TEST_CLASSES) {
            try {
                Class.forName(className, true, classLoader);
                loadedClasses++;
            } catch (Throwable error) {
                LOGGER.error("Iris core self-test failed to initialize {}", className, error);
            }
        }

        if (loadedClasses != CORE_SELF_TEST_CLASSES.length) {
            throw new IllegalStateException("Iris core self-test failed: only " + loadedClasses + " of " + CORE_SELF_TEST_CLASSES.length + " engine classes initialized");
        }

        ModdedIrisLog.info("Iris core loaded (" + loadedClasses + " classes ok)");
    }

    public static ModdedPlatform bind() {
        ModdedPlatform bound = platform;
        if (bound != null) {
            return bound;
        }
        synchronized (LOCK) {
            if (platform != null) {
                return platform;
            }
            ModdedLoader boundLoader = loader();
            GuiHost.suppressDesktop(boundLoader.clientEnvironment());
            ModdedPlatform created = new ModdedPlatform(boundLoader);
            IrisPlatforms.bind(created);
            ModdedDimensionManager.bindAccess(new ModdedServerLevels());
            IrisObjectRotation.bindFallbackRotator(new ModdedStateRotator());
            BlockDataMergeSupport.bindFallbackMerger(new ModdedStateMerger());
            TileData.bindFallbackReader(new ModdedTileReader(boundLoader::currentServer));
            ModdedGuiHost.install();
            ModdedDecoratorHooks decoratorHooks = new ModdedDecoratorHooks();
            DecoratorPlatformHooks.bind(decoratorHooks, decoratorHooks);
            ModdedPreservationService preservation = SERVICE_MANAGER.register(ModdedPreservationService.class, new ModdedPreservationService());
            SERVICE_MANAGER.register(ModdedLogFilterService.class, new ModdedLogFilterService());
            SERVICE_MANAGER.register(ModdedEngineMaintenanceService.class, new ModdedEngineMaintenanceService());
            SERVICE_MANAGER.register(ModdedSettingsHotloadService.class, new ModdedSettingsHotloadService());
            SERVICE_MANAGER.register(ModdedStudioHotloadService.class, new ModdedStudioHotloadService());
            SERVICE_MANAGER.register(ModdedChunkUpdateService.class, new ModdedChunkUpdateService());
            IrisServices.register(PreservationRegistry.class, preservation);
            IrisServices.register(EngineWorldManagerProvider.class, (EngineWorldManagerProvider) (Engine engine) -> new InertWorldManager());
            ModdedCustomContentRegistry.discover();
            platform = created;
            SERVICE_MANAGER.enableAll();
            if (boundLoader.clientEnvironment()) {
                ModdedStartup.prefetchDefaultPack();
            }
            ModdedIrisSplash.print(boundLoader);
            return created;
        }
    }

    private static final class InertWorldManager implements EngineWorldManager {
        @Override
        public void close() {
        }

        @Override
        public int getEntityCount() {
            return 0;
        }

        @Override
        public int getChunkCount() {
            return 0;
        }

        @Override
        public double getEntitySaturation() {
            return 0;
        }

        @Override
        public void onTick() {
        }

        @Override
        public void onSave() {
        }

        @Override
        public void onBlockBreak(BlockBreakEvent e) {
        }

        @Override
        public void onBlockPlace(BlockPlaceEvent e) {
        }

        @Override
        public void onChunkLoad(Chunk e, boolean generated) {
        }

        @Override
        public void onChunkUnload(Chunk e) {
        }

        @Override
        public void teleportAsync(PlayerTeleportEvent e) {
        }
    }
}
