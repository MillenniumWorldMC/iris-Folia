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

import art.arcane.iris.core.pack.PackDirectoryResolver;
import art.arcane.iris.core.pack.PackResourceCleanup;
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

    public static LiteralArgumentBuilder<CommandSourceStack> tree(String name) {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal(name).requires(GATE);

        root.executes((CommandContext<CommandSourceStack> context) -> ModdedCommandHelp.send(context.getSource(), name));

        root.then(Commands.literal("validate")
                .executes((CommandContext<CommandSourceStack> context) -> validate(context.getSource(), null))
                .then(Commands.argument("pack", StringArgumentType.word()).suggests(IrisModdedCommands.PACK_NAMES)
                        .executes((CommandContext<CommandSourceStack> context) -> validate(context.getSource(), StringArgumentType.getString(context, "pack")))));
        root.then(Commands.literal("v")
                .executes((CommandContext<CommandSourceStack> context) -> validate(context.getSource(), null))
                .then(Commands.argument("pack", StringArgumentType.word()).suggests(IrisModdedCommands.PACK_NAMES)
                        .executes((CommandContext<CommandSourceStack> context) -> validate(context.getSource(), StringArgumentType.getString(context, "pack")))));

        root.then(Commands.literal("cleanup")
                .then(Commands.argument("pack", StringArgumentType.word()).suggests(IrisModdedCommands.PACK_NAMES)
                        .executes((CommandContext<CommandSourceStack> context) -> cleanup(context.getSource(), StringArgumentType.getString(context, "pack"), false))
                        .then(Commands.literal("apply")
                                .executes((CommandContext<CommandSourceStack> context) -> cleanup(context.getSource(), StringArgumentType.getString(context, "pack"), true)))));
        root.then(Commands.literal("c")
                .then(Commands.argument("pack", StringArgumentType.word()).suggests(IrisModdedCommands.PACK_NAMES)
                        .executes((CommandContext<CommandSourceStack> context) -> cleanup(context.getSource(), StringArgumentType.getString(context, "pack"), false))
                        .then(Commands.literal("apply")
                                .executes((CommandContext<CommandSourceStack> context) -> cleanup(context.getSource(), StringArgumentType.getString(context, "pack"), true)))));

        root.then(Commands.literal("restore")
                .then(Commands.argument("pack", StringArgumentType.word()).suggests(IrisModdedCommands.PACK_NAMES)
                        .executes((CommandContext<CommandSourceStack> context) -> restore(context.getSource(), StringArgumentType.getString(context, "pack"), false))
                        .then(Commands.literal("apply")
                                .executes((CommandContext<CommandSourceStack> context) -> restore(context.getSource(), StringArgumentType.getString(context, "pack"), true)))));
        root.then(Commands.literal("r")
                .then(Commands.argument("pack", StringArgumentType.word()).suggests(IrisModdedCommands.PACK_NAMES)
                        .executes((CommandContext<CommandSourceStack> context) -> restore(context.getSource(), StringArgumentType.getString(context, "pack"), false))
                        .then(Commands.literal("apply")
                                .executes((CommandContext<CommandSourceStack> context) -> restore(context.getSource(), StringArgumentType.getString(context, "pack"), true)))));

        root.then(Commands.literal("status")
                .executes((CommandContext<CommandSourceStack> context) -> status(context.getSource(), null))
                .then(Commands.argument("pack", StringArgumentType.word()).suggests(IrisModdedCommands.PACK_NAMES)
                        .executes((CommandContext<CommandSourceStack> context) -> status(context.getSource(), StringArgumentType.getString(context, "pack")))));
        root.then(Commands.literal("s")
                .executes((CommandContext<CommandSourceStack> context) -> status(context.getSource(), null))
                .then(Commands.argument("pack", StringArgumentType.word()).suggests(IrisModdedCommands.PACK_NAMES)
                        .executes((CommandContext<CommandSourceStack> context) -> status(context.getSource(), StringArgumentType.getString(context, "pack")))));

        return root;
    }

    public static File packsRoot() {
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
            File target = PackDirectoryResolver.resolveExisting(packsRoot, pack);
            if (target == null) {
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

    private static int cleanup(CommandSourceStack source, String pack, boolean apply) {
        File packFolder = PackDirectoryResolver.resolveExisting(packsRoot(), pack);
        if (packFolder == null) {
            IrisModdedCommands.fail(source, "Pack '" + pack + "' not found under " + packsRoot().getAbsolutePath());
            return 0;
        }
        MinecraftServer server = source.getServer();
        Thread thread = new Thread(() -> {
            if (apply) {
                PackResourceCleanup.ApplyResult result = PackResourceCleanup.apply(packFolder);
                server.execute(() -> reportCleanupApply(source, pack, result));
            } else {
                PackResourceCleanup.Preview result = PackResourceCleanup.preview(packFolder);
                server.execute(() -> reportCleanupPreview(source, pack, result));
            }
        }, "Iris Pack Cleanup");
        thread.setDaemon(true);
        thread.start();
        return 1;
    }

    private static int restore(CommandSourceStack source, String pack, boolean apply) {
        File packFolder = PackDirectoryResolver.resolveExisting(packsRoot(), pack);
        if (packFolder == null) {
            IrisModdedCommands.fail(source, "Pack '" + pack + "' not found under " + packsRoot().getAbsolutePath());
            return 0;
        }
        MinecraftServer server = source.getServer();
        Thread thread = new Thread(() -> {
            if (apply) {
                PackResourceCleanup.RestoreResult result = PackResourceCleanup.restoreLatest(packFolder);
                server.execute(() -> reportRestoreApply(source, pack, result));
            } else {
                PackResourceCleanup.RestorePreview result = PackResourceCleanup.previewRestore(packFolder);
                server.execute(() -> reportRestorePreview(source, pack, result));
            }
        }, "Iris Pack Restore");
        thread.setDaemon(true);
        thread.start();
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
                        + ", warnings=" + result.getWarnings().size() + ")");
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
                    + " (warnings=" + result.getWarnings().size() + ")");
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
    }

    private static void reportCleanupPreview(CommandSourceStack source, String pack, PackResourceCleanup.Preview result) {
        if (!result.success()) {
            IrisModdedCommands.fail(source, result.error());
            return;
        }
        if (!result.hasCandidates()) {
            IrisModdedCommands.ok(source, "No cleanup candidates found for pack '" + pack + "'.");
            return;
        }
        IrisModdedCommands.ok(source, "Cleanup preview for pack '" + pack + "': "
                + result.candidatePaths().size() + " candidate(s). No files were changed.");
        reportPaths(source, result.candidatePaths(), "candidate");
        IrisModdedCommands.ok(source, "Run /iris pack cleanup " + pack + " apply to quarantine after a fresh scan.");
    }

    private static void reportCleanupApply(CommandSourceStack source, String pack, PackResourceCleanup.ApplyResult result) {
        if (!result.success()) {
            IrisModdedCommands.fail(source, result.error());
            reportPaths(source, result.quarantinedPaths(), "still quarantined");
            return;
        }
        if (!result.changed()) {
            IrisModdedCommands.ok(source, "No cleanup candidates found for pack '" + pack + "'.");
            return;
        }
        IrisModdedCommands.ok(source, "Quarantined " + result.quarantinedPaths().size()
                + " cleanup candidate(s) under " + result.quarantinePath() + ".");
        reportPaths(source, result.quarantinedPaths(), "quarantined");
    }

    private static void reportRestorePreview(CommandSourceStack source, String pack, PackResourceCleanup.RestorePreview result) {
        if (!result.success()) {
            IrisModdedCommands.fail(source, result.error());
            return;
        }
        if (!result.hasFiles()) {
            IrisModdedCommands.ok(source, "Nothing to restore for pack '" + pack + "'.");
            return;
        }
        IrisModdedCommands.ok(source, "Restore preview for " + result.dumpPath() + ": "
                + result.filePaths().size() + " file(s). No files were changed.");
        reportPaths(source, result.filePaths(), "file");
        if (!result.conflicts().isEmpty()) {
            IrisModdedCommands.fail(source, "Restore is blocked by " + result.conflicts().size() + " existing destination(s).");
            reportPaths(source, result.conflicts(), "conflict");
            return;
        }
        IrisModdedCommands.ok(source, "Run /iris pack restore " + pack + " apply to restore after a fresh conflict check.");
    }

    private static void reportRestoreApply(CommandSourceStack source, String pack, PackResourceCleanup.RestoreResult result) {
        if (!result.conflicts().isEmpty()) {
            IrisModdedCommands.fail(source, "Restore refused because " + result.conflicts().size() + " destination(s) already exist.");
            reportPaths(source, result.conflicts(), "conflict");
            return;
        }
        if (!result.success()) {
            IrisModdedCommands.fail(source, result.error());
            return;
        }
        if (!result.changed()) {
            IrisModdedCommands.ok(source, "Nothing to restore for pack '" + pack + "'.");
            return;
        }
        IrisModdedCommands.ok(source, "Restored " + result.restoredPaths().size()
                + " file(s) from " + result.dumpPath() + ".");
        reportPaths(source, result.restoredPaths(), "restored");
    }

    private static void reportPaths(CommandSourceStack source, List<String> paths, String label) {
        int max = Math.min(10, paths.size());
        for (int i = 0; i < max; i++) {
            IrisModdedCommands.ok(source, "  - " + label + ": " + paths.get(i));
        }
        if (paths.size() > max) {
            IrisModdedCommands.ok(source, "  ... and " + (paths.size() - max) + " more.");
        }
    }
}
