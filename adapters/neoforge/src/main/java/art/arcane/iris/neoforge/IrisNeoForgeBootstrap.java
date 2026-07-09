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

package art.arcane.iris.neoforge;

import art.arcane.iris.modded.IrisModdedChunkGenerator;
import art.arcane.iris.modded.ModdedDeathLoot;
import art.arcane.iris.modded.ModdedEngineBootstrap;
import art.arcane.iris.modded.ModdedForcedDatapack;
import art.arcane.iris.modded.command.IrisModdedCommands;
import art.arcane.iris.modded.command.ModdedWandService;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.packs.PackType;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.fml.loading.FMLLoader;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AddPackFindersEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.registries.DeferredRegister;

@Mod("irisworldgen")
public final class IrisNeoForgeBootstrap {
    public IrisNeoForgeBootstrap(IEventBus modBus) {
        ModdedEngineBootstrap.bootCommon(new NeoForgeModdedLoader(), "NeoForge " + FMLLoader.getCurrent().getVersionInfo().neoForgeVersion(), () -> {
            DeferredRegister<MapCodec<? extends ChunkGenerator>> chunkGenerators = DeferredRegister.create(Registries.CHUNK_GENERATOR, "irisworldgen");
            chunkGenerators.register("iris", () -> IrisModdedChunkGenerator.CODEC);
            chunkGenerators.register(modBus);
        });

        modBus.addListener((AddPackFindersEvent event) -> {
            if (event.getPackType() == PackType.SERVER_DATA) {
                event.addRepositorySource(ModdedForcedDatapack.repositorySource());
            }
        });

        NeoForgeProtocolNetworking.register(modBus);

        if (FMLEnvironment.getDist() == Dist.CLIENT) {
            IrisNeoForgeClient.init(modBus);
        }

        NeoForge.EVENT_BUS.addListener((ServerStartingEvent event) -> ModdedEngineBootstrap.start(event.getServer()));
        NeoForge.EVENT_BUS.addListener((ServerStoppingEvent event) -> ModdedEngineBootstrap.stop());
        NeoForge.EVENT_BUS.addListener((RegisterCommandsEvent event) -> IrisModdedCommands.register(event.getDispatcher()));
        NeoForge.EVENT_BUS.addListener((LivingDropsEvent event) -> ModdedDeathLoot.handle(event.getEntity()));
        NeoForge.EVENT_BUS.addListener((PlayerInteractEvent.LeftClickBlock event) -> {
            if (ModdedWandService.attackBlock(event.getEntity(), event.getLevel(), event.getHand(), event.getPos())) {
                event.setCanceled(true);
            }
        });
        NeoForge.EVENT_BUS.addListener((PlayerInteractEvent.RightClickBlock event) -> {
            if (ModdedWandService.useBlock(event.getEntity(), event.getLevel(), event.getHand(), event.getPos())) {
                event.setCanceled(true);
                event.setCancellationResult(InteractionResult.SUCCESS);
            }
        });
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post event) -> {
            ModdedEngineBootstrap.tick(event.getServer());
            ModdedWandService.serverTick(event.getServer());
        });
    }
}
