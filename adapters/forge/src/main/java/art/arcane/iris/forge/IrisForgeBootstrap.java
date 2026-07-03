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

package art.arcane.iris.forge;

import art.arcane.iris.modded.IrisModdedChunkGenerator;
import art.arcane.iris.modded.ModdedEngineBootstrap;
import art.arcane.iris.modded.ModdedForcedDatapack;
import art.arcane.iris.modded.command.IrisModdedCommands;
import art.arcane.iris.modded.command.ModdedWandService;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.packs.PackType;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraftforge.event.AddPackFindersEvent;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLLoader;
import net.minecraftforge.registries.DeferredRegister;

import java.util.function.Predicate;

@Mod("irisworldgen")
public final class IrisForgeBootstrap {
    public IrisForgeBootstrap(FMLJavaModLoadingContext context) {
        ModdedEngineBootstrap.bootCommon(new ForgeModdedLoader(), "Forge " + FMLLoader.versionInfo().forgeVersion(), () -> {
            DeferredRegister<MapCodec<? extends ChunkGenerator>> chunkGenerators = DeferredRegister.create(Registries.CHUNK_GENERATOR, "irisworldgen");
            chunkGenerators.register("iris", () -> IrisModdedChunkGenerator.CODEC);
            chunkGenerators.register(context.getModBusGroup());
        });

        ServerStartingEvent.BUS.addListener((ServerStartingEvent event) -> ModdedEngineBootstrap.start(event.getServer()));
        ServerStoppingEvent.BUS.addListener((ServerStoppingEvent event) -> ModdedEngineBootstrap.stop());
        AddPackFindersEvent.BUS.addListener((AddPackFindersEvent event) -> {
            if (event.getPackType() == PackType.SERVER_DATA) {
                event.addRepositorySource(ModdedForcedDatapack.repositorySource());
            }
        });
        RegisterCommandsEvent.BUS.addListener((RegisterCommandsEvent event) -> IrisModdedCommands.register(event.getDispatcher()));
        PlayerInteractEvent.LeftClickBlock.BUS.addListener((Predicate<PlayerInteractEvent.LeftClickBlock>) (PlayerInteractEvent.LeftClickBlock event) ->
                ModdedWandService.attackBlock(event.getEntity(), event.getLevel(), event.getHand(), event.getPos()));
        PlayerInteractEvent.RightClickBlock.BUS.addListener((Predicate<PlayerInteractEvent.RightClickBlock>) (PlayerInteractEvent.RightClickBlock event) ->
                ModdedWandService.useBlock(event.getEntity(), event.getLevel(), event.getHand(), event.getPos()));
        TickEvent.ServerTickEvent.Post.BUS.addListener((TickEvent.ServerTickEvent.Post event) -> {
            ModdedEngineBootstrap.tick(event.server());
            ModdedWandService.serverTick(event.server());
        });
    }
}
