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

import art.arcane.iris.core.pack.PackValidationRegistry;
import art.arcane.iris.core.pack.PackValidationResult;
import art.arcane.iris.core.pack.PackValidator;
import art.arcane.iris.modded.ModdedEngineBootstrap;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

public final class ModdedPackCommands {
    private static final Logger LOGGER = LoggerFactory.getLogger("Iris");
    private static final Predicate<CommandSourceStack> GATE = Commands.hasPermission(Commands.LEVEL_GAMEMASTERS);

    private ModdedPackCommands() {
    }

    public static LiteralArgumentBuilder<CommandSourceStack> tree() {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("pack").requires(GATE);

        root.then(Commands.literal("validate")
                .executes((CommandContext<CommandSourceStack> context) -> validate(context.getSource(), null))
                .then(Commands.argument("pack", StringArgumentType.word()).suggests(IrisModdedCommands.PACK_NAMES)
                        .executes((CommandContext<CommandSourceStack> context) -> validate(context.getSource(), StringArgumentType.getString(context, "pack")))));

        root.then(Commands.literal("restore")
                .then(Commands.argument("pack", StringArgumentType.word()).suggests(IrisModdedCommands.PACK_NAMES)
                        .executes((CommandContext<CommandSourceStack> context) -> restore(context.getSource(), StringArgumentType.getString(context, "pack")))));

        root.then(Commands.literal("status")
                .executes((CommandContext<CommandSourceStack> context) -> status(context.getSource(), null))
                .then(Commands.argument("pack", StringArgumentType.word()).suggests(IrisModdedCommands.PACK_NAMES)
                        .executes((CommandContext<CommandSourceStack> context) -> status(context.getSource(), StringArgumentType.getString(context, "pack")))));

        return root;
    }

    static File packsRoot() {
        return ModdedEngineBootstrap.loader().configDir().resolve("irisworldgen").resolve("packs").toFile();
    }

    private static int validate(CommandSourceStack source, String pack) {
        File packsRoot = packsRoot();
        if (!packsRoot.isDirectory()) {
            IrisModdedCommands.fail(source, "Packs folder not found: " + packsRoot.getAbsolutePath());
            return 0;
        }

        List<File> targets = new ArrayList<>();
        if (pack == null || pack.isBlank()) {
            File[] dirs = packsRoot.listFiles(File::isDirectory);
            if (dirs == null || dirs.length == 0) {
                IrisModdedCommands.fail(source, "No packs to validate under " + packsRoot.getAbsolutePath());
                return 0;
            }
            for (File dir : dirs) {
                targets.add(dir);
            }
        } else {
            File target = new File(packsRoot, pack);
            if (!target.isDirectory()) {
                IrisModdedCommands.fail(source, "Pack '" + pack + "' not found under " + packsRoot.getAbsolutePath());
                return 0;
            }
            targets.add(target);
        }

        MinecraftServer server = source.getServer();
        IrisModdedCommands.ok(source, "Validating " + targets.size() + " pack(s)...");
        Thread thread = new Thread(() -> {
            int broken = 0;
            for (File target : targets) {
                try {
                    PackValidationResult result = PackValidator.validate(target);
                    PackValidationRegistry.publish(result);
                    if (!result.isLoadable()) {
                        broken++;
                    }
                    server.execute(() -> report(source, result));
                } catch (Throwable e) {
                    LOGGER.error("Iris pack validation failed for {}", target.getName(), e);
                    server.execute(() -> IrisModdedCommands.fail(source, "Validation of '" + target.getName() + "' failed: " + e.getMessage()));
                    broken++;
                }
            }
            int brokenTotal = broken;
            server.execute(() -> IrisModdedCommands.ok(source, "Validation complete. Broken packs: " + brokenTotal + "/" + targets.size()));
        }, "Iris Pack Validator");
        thread.setDaemon(true);
        thread.start();
        return 1;
    }

    private static int restore(CommandSourceStack source, String pack) {
        File packFolder = new File(packsRoot(), pack);
        if (!packFolder.isDirectory()) {
            IrisModdedCommands.fail(source, "Pack '" + pack + "' not found under " + packsRoot().getAbsolutePath());
            return 0;
        }
        int restored = PackValidator.restoreTrash(packFolder);
        if (restored == 0) {
            IrisModdedCommands.fail(source, "Nothing to restore for pack '" + pack + "'.");
            return 0;
        }
        IrisModdedCommands.ok(source, "Restored " + restored + " file(s) from the most recent trash dump for pack '" + pack + "'.");
        IrisModdedCommands.ok(source, "Re-run /iris pack validate " + pack + " to re-check.");
        return 1;
    }

    private static int status(CommandSourceStack source, String pack) {
        if (pack == null || pack.isBlank()) {
            Map<String, PackValidationResult> snapshot = PackValidationRegistry.snapshot();
            if (snapshot.isEmpty()) {
                IrisModdedCommands.fail(source, "No validation results recorded. Run /iris pack validate first.");
                return 0;
            }
            for (Map.Entry<String, PackValidationResult> entry : snapshot.entrySet()) {
                PackValidationResult result = entry.getValue();
                String tag = result.isLoadable() ? "OK" : "BROKEN";
                IrisModdedCommands.ok(source, tag + " " + entry.getKey()
                        + " (blocking=" + result.getBlockingErrors().size()
                        + ", warnings=" + result.getWarnings().size()
                        + ", trashed=" + result.getRemovedUnusedFiles().size() + ")");
            }
            return 1;
        }
        PackValidationResult result = PackValidationRegistry.get(pack);
        if (result == null) {
            IrisModdedCommands.fail(source, "No validation result for '" + pack + "'. Run /iris pack validate " + pack + ".");
            return 0;
        }
        report(source, result);
        return 1;
    }

    private static void report(CommandSourceStack source, PackValidationResult result) {
        if (result.isLoadable()) {
            IrisModdedCommands.ok(source, "Pack '" + result.getPackName() + "' is loadable."
                    + " (warnings=" + result.getWarnings().size()
                    + ", trashed=" + result.getRemovedUnusedFiles().size() + ")");
        } else {
            IrisModdedCommands.fail(source, "Pack '" + result.getPackName() + "' is BROKEN:");
            for (String reason : result.getBlockingErrors()) {
                IrisModdedCommands.fail(source, "  - " + reason);
            }
        }
        int warningMax = Math.min(10, result.getWarnings().size());
        for (int i = 0; i < warningMax; i++) {
            IrisModdedCommands.ok(source, "  ! " + result.getWarnings().get(i));
        }
        if (result.getWarnings().size() > warningMax) {
            IrisModdedCommands.ok(source, "  ... and " + (result.getWarnings().size() - warningMax) + " more warning(s).");
        }
        int trashMax = Math.min(10, result.getRemovedUnusedFiles().size());
        for (int i = 0; i < trashMax; i++) {
            IrisModdedCommands.ok(source, "  ~ trashed " + result.getRemovedUnusedFiles().get(i));
        }
        if (result.getRemovedUnusedFiles().size() > trashMax) {
            IrisModdedCommands.ok(source, "  ... and " + (result.getRemovedUnusedFiles().size() - trashMax) + " more trashed file(s).");
        }
    }
}
