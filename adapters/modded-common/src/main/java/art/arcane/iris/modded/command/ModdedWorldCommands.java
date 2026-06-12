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

import art.arcane.iris.modded.IrisModdedChunkGenerator;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;

public final class ModdedWorldCommands {
    private static final Logger LOGGER = LoggerFactory.getLogger("Iris");
    private static final Predicate<CommandSourceStack> GATE = Commands.hasPermission(Commands.LEVEL_GAMEMASTERS);
    private static final SuggestionProvider<CommandSourceStack> ENABLED_DIMENSIONS = (CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) -> suggestEnabledDimensions(context, builder);

    private ModdedWorldCommands() {
    }

    public static LiteralArgumentBuilder<CommandSourceStack> tree(String name) {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal(name).requires(GATE);

        root.executes((CommandContext<CommandSourceStack> context) -> ModdedCommandHelp.send(context.getSource(), name));

        root.then(Commands.literal("status")
                .executes((CommandContext<CommandSourceStack> context) -> status(context.getSource())));
        root.then(Commands.literal("list")
                .executes((CommandContext<CommandSourceStack> context) -> list(context.getSource())));
        root.then(Commands.literal("ls")
                .executes((CommandContext<CommandSourceStack> context) -> list(context.getSource())));

        root.then(enableTree("enable"));
        root.then(enableTree("create"));
        root.then(replaceOverworldTree());

        root.then(Commands.literal("disable")
                .then(Commands.argument("dimension", StringArgumentType.word()).suggests(ENABLED_DIMENSIONS)
                        .executes((CommandContext<CommandSourceStack> context) -> disable(context.getSource(), StringArgumentType.getString(context, "dimension")))));
        root.then(Commands.literal("remove")
                .then(Commands.argument("dimension", StringArgumentType.word()).suggests(ENABLED_DIMENSIONS)
                        .executes((CommandContext<CommandSourceStack> context) -> disable(context.getSource(), StringArgumentType.getString(context, "dimension")))));
        root.then(Commands.literal("rm")
                .then(Commands.argument("dimension", StringArgumentType.word()).suggests(ENABLED_DIMENSIONS)
                        .executes((CommandContext<CommandSourceStack> context) -> disable(context.getSource(), StringArgumentType.getString(context, "dimension")))));

        return root;
    }

    private static LiteralArgumentBuilder<CommandSourceStack> enableTree(String name) {
        return Commands.literal(name)
                .then(Commands.argument("dimension", StringArgumentType.word())
                        .then(Commands.argument("pack", StringArgumentType.word()).suggests(IrisModdedCommands.PACK_NAMES)
                                .executes((CommandContext<CommandSourceStack> context) -> enable(context.getSource(),
                                        StringArgumentType.getString(context, "dimension"),
                                        StringArgumentType.getString(context, "pack"),
                                        StringArgumentType.getString(context, "pack")))
                                .then(Commands.argument("packDimension", StringArgumentType.word())
                                        .executes((CommandContext<CommandSourceStack> context) -> enable(context.getSource(),
                                                StringArgumentType.getString(context, "dimension"),
                                                StringArgumentType.getString(context, "pack"),
                                                StringArgumentType.getString(context, "packDimension"))))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> replaceOverworldTree() {
        return Commands.literal("replace-overworld")
                .then(Commands.argument("pack", StringArgumentType.word()).suggests(IrisModdedCommands.PACK_NAMES)
                        .executes((CommandContext<CommandSourceStack> context) -> enable(context.getSource(),
                                "minecraft:overworld",
                                StringArgumentType.getString(context, "pack"),
                                StringArgumentType.getString(context, "pack")))
                        .then(Commands.argument("packDimension", StringArgumentType.word())
                                .executes((CommandContext<CommandSourceStack> context) -> enable(context.getSource(),
                                        "minecraft:overworld",
                                        StringArgumentType.getString(context, "pack"),
                                        StringArgumentType.getString(context, "packDimension")))));
    }

    private static int enable(CommandSourceStack source, String targetDimension, String packName, String packDimension) {
        try {
            ModdedWorldDatapackWriter.WriteResult result = ModdedWorldDatapackWriter.enable(source.getServer(), targetDimension, packName, packDimension);
            IrisModdedCommands.ok(source, "Enabled Iris world " + result.targetDimension() + " using pack '" + result.packName() + "' dimension '" + result.packDimension() + "'.");
            IrisModdedCommands.ok(source, "Wrote " + result.dimensionFile().getPath());
            IrisModdedCommands.ok(source, "Wrote " + result.typeFile().getPath());
            IrisModdedCommands.ok(source, "Wrote " + result.packMetaFile().getPath());
            IrisModdedCommands.ok(source, "Restart the server, then use /execute in " + result.targetDimension() + " run tp <player> 0 100 0 or a portal/modded dimension tool.");
            if ("minecraft:overworld".equals(result.targetDimension())) {
                IrisModdedCommands.ok(source, "minecraft:overworld replacement was explicitly requested.");
            }
            return 1;
        } catch (IOException e) {
            LOGGER.error("Iris world datapack write failed for {}", targetDimension, e);
            IrisModdedCommands.fail(source, "Failed to write Iris world datapack: " + e.getMessage());
            return 0;
        } catch (IllegalArgumentException e) {
            IrisModdedCommands.fail(source, e.getMessage());
            return 0;
        }
    }

    private static int disable(CommandSourceStack source, String targetDimension) {
        try {
            File removed = ModdedWorldDatapackWriter.disable(source.getServer(), targetDimension);
            IrisModdedCommands.ok(source, "Removed " + removed.getPath());
            IrisModdedCommands.ok(source, "Restart the server for the dimension removal to apply.");
            return 1;
        } catch (IOException e) {
            LOGGER.error("Iris world datapack removal failed for {}", targetDimension, e);
            IrisModdedCommands.fail(source, "Failed to remove Iris world datapack entry: " + e.getMessage());
            return 0;
        } catch (IllegalArgumentException e) {
            IrisModdedCommands.fail(source, e.getMessage());
            return 0;
        }
    }

    private static int status(CommandSourceStack source) {
        list(source);
        int loaded = 0;
        MinecraftServer server = source.getServer();
        for (ServerLevel level : server.getAllLevels()) {
            if (level.getChunkSource().getGenerator() instanceof IrisModdedChunkGenerator generator) {
                loaded++;
                IrisModdedCommands.ok(source, "Loaded Iris level: " + level.dimension().identifier() + " -> pack dimension '" + generator.dimensionKey() + "'");
            }
        }
        if (loaded == 0) {
            IrisModdedCommands.fail(source, "No Iris dimensions are currently loaded. Enabled dimensions require a server restart before Minecraft loads them.");
        }
        return loaded > 0 ? 1 : 0;
    }

    private static int list(CommandSourceStack source) {
        try {
            List<String> dimensions = ModdedWorldDatapackWriter.enabledDimensions(source.getServer());
            IrisModdedCommands.ok(source, "Enabled Iris world datapack dimensions: " + dimensions.size());
            for (String dimension : dimensions) {
                IrisModdedCommands.ok(source, "  - " + dimension);
            }
            if (dimensions.isEmpty()) {
                IrisModdedCommands.ok(source, "Use /iris world enable <dimension> <pack> to create one without replacing the main world.");
            }
            return 1;
        } catch (IOException e) {
            LOGGER.error("Iris world datapack listing failed", e);
            IrisModdedCommands.fail(source, "Failed to list Iris world datapack dimensions: " + e.getMessage());
            return 0;
        }
    }

    private static CompletableFuture<Suggestions> suggestEnabledDimensions(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        try {
            return SharedSuggestionProvider.suggest(ModdedWorldDatapackWriter.enabledDimensions(context.getSource().getServer()), builder);
        } catch (IOException e) {
            return Suggestions.empty();
        }
    }
}
