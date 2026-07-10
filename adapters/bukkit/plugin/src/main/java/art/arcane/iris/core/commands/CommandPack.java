/*
 * Iris is a World Generator for Minecraft Bukkit Servers
 * Copyright (c) 2022 Arcane Arts (Volmit Software)
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

package art.arcane.iris.core.commands;

import art.arcane.iris.Iris;
import art.arcane.iris.core.pack.PackDirectoryResolver;
import art.arcane.iris.core.pack.PackResourceCleanup;
import art.arcane.iris.core.pack.PackValidationRegistry;
import art.arcane.iris.core.pack.PackValidationResult;
import art.arcane.iris.core.pack.PackValidator;
import art.arcane.iris.util.common.director.DirectorExecutor;
import art.arcane.iris.util.common.format.C;
import art.arcane.iris.util.common.plugin.VolmitSender;
import art.arcane.volmlib.util.director.annotations.Director;
import art.arcane.volmlib.util.director.annotations.Param;

import java.io.File;
import java.util.List;

@Director(name = "pack", aliases = {"pk"}, description = "Pack validation and maintenance")
public class CommandPack implements DirectorExecutor {
    @Director(description = "Validate a pack (or all packs) and re-publish results", aliases = {"v"})
    public void validate(
            @Param(description = "The pack folder name to validate (leave empty for all)", defaultValue = "")
            String pack
    ) {
        VolmitSender s = sender();
        File packsRoot = Iris.instance.getDataFolder("packs");
        if (!packsRoot.isDirectory()) {
            s.sendMessage(C.RED + "packs/ folder not found.");
            return;
        }

        if (pack == null || pack.isBlank()) {
            File[] dirs = packsRoot.listFiles(File::isDirectory);
            if (dirs == null || dirs.length == 0) {
                s.sendMessage(C.YELLOW + "No packs to validate.");
                return;
            }
            int broken = 0;
            for (File dir : dirs) {
                PackValidationResult result = runValidate(s, dir);
                if (result != null && !result.isLoadable()) {
                    broken++;
                }
            }
            s.sendMessage(C.GREEN + "Validation complete. Broken packs: " + broken + "/" + dirs.length);
            return;
        }

        File target = PackDirectoryResolver.resolveExisting(packsRoot, pack);
        if (target == null) {
            s.sendMessage(C.RED + "Pack '" + pack + "' not found under packs/.");
            return;
        }
        runValidate(s, target);
    }

    @Director(description = "Preview or apply unused-resource cleanup", aliases = {"c"})
    public void cleanup(
            @Param(description = "The pack folder name to clean")
            String pack,
            @Param(description = "preview or apply", defaultValue = "preview")
            String mode
    ) {
        VolmitSender s = sender();
        File packFolder = findPack(s, pack);
        if (packFolder == null) {
            return;
        }
        if ("apply".equalsIgnoreCase(mode)) {
            PackResourceCleanup.ApplyResult result = PackResourceCleanup.apply(packFolder);
            if (!result.success()) {
                s.sendMessage(C.RED + result.error());
                reportPaths(s, result.quarantinedPaths(), "still quarantined");
                return;
            }
            if (!result.changed()) {
                s.sendMessage(C.GREEN + "No cleanup candidates found for pack '" + pack + "'.");
                return;
            }
            s.sendMessage(C.GREEN + "Quarantined " + result.quarantinedPaths().size()
                    + " cleanup candidate(s) under " + result.quarantinePath() + ".");
            reportPaths(s, result.quarantinedPaths(), "quarantined");
            return;
        }
        if (!"preview".equalsIgnoreCase(mode)) {
            s.sendMessage(C.RED + "Cleanup mode must be preview or apply.");
            return;
        }
        PackResourceCleanup.Preview preview = PackResourceCleanup.preview(packFolder);
        if (!preview.success()) {
            s.sendMessage(C.RED + preview.error());
            return;
        }
        if (!preview.hasCandidates()) {
            s.sendMessage(C.GREEN + "No cleanup candidates found for pack '" + pack + "'.");
            return;
        }
        s.sendMessage(C.YELLOW + "Cleanup preview for pack '" + pack + "': "
                + preview.candidatePaths().size() + " candidate(s). No files were changed.");
        reportPaths(s, preview.candidatePaths(), "candidate");
        s.sendMessage(C.GRAY + "Run /iris pack cleanup " + pack + " mode=apply to quarantine these candidates after a fresh scan.");
    }

    @Director(description = "Preview or apply restoration of the latest quarantine", aliases = {"r"})
    public void restore(
            @Param(description = "The pack folder name to restore")
            String pack,
            @Param(description = "preview or apply", defaultValue = "preview")
            String mode
    ) {
        VolmitSender s = sender();
        File packFolder = findPack(s, pack);
        if (packFolder == null) {
            return;
        }
        if ("apply".equalsIgnoreCase(mode)) {
            PackResourceCleanup.RestoreResult result = PackResourceCleanup.restoreLatest(packFolder);
            if (!result.conflicts().isEmpty()) {
                s.sendMessage(C.RED + "Restore refused because " + result.conflicts().size() + " destination(s) already exist.");
                reportPaths(s, result.conflicts(), "conflict");
                return;
            }
            if (!result.success()) {
                s.sendMessage(C.RED + result.error());
                return;
            }
            if (!result.changed()) {
                s.sendMessage(C.YELLOW + "Nothing to restore for pack '" + pack + "'.");
                return;
            }
            s.sendMessage(C.GREEN + "Restored " + result.restoredPaths().size()
                    + " file(s) from " + result.dumpPath() + ".");
            reportPaths(s, result.restoredPaths(), "restored");
            return;
        }
        if (!"preview".equalsIgnoreCase(mode)) {
            s.sendMessage(C.RED + "Restore mode must be preview or apply.");
            return;
        }
        PackResourceCleanup.RestorePreview preview = PackResourceCleanup.previewRestore(packFolder);
        if (!preview.success()) {
            s.sendMessage(C.RED + preview.error());
            return;
        }
        if (!preview.hasFiles()) {
            s.sendMessage(C.YELLOW + "Nothing to restore for pack '" + pack + "'.");
            return;
        }
        s.sendMessage(C.YELLOW + "Restore preview for " + preview.dumpPath() + ": "
                + preview.filePaths().size() + " file(s). No files were changed.");
        reportPaths(s, preview.filePaths(), "file");
        if (!preview.conflicts().isEmpty()) {
            s.sendMessage(C.RED + "Restore is blocked by " + preview.conflicts().size() + " existing destination(s).");
            reportPaths(s, preview.conflicts(), "conflict");
            return;
        }
        s.sendMessage(C.GRAY + "Run /iris pack restore " + pack + " mode=apply to restore after a fresh conflict check.");
    }

    @Director(description = "Show cached validation status for a pack", aliases = {"s"})
    public void status(
            @Param(description = "The pack folder name", defaultValue = "")
            String pack
    ) {
        VolmitSender s = sender();
        if (pack == null || pack.isBlank()) {
            if (PackValidationRegistry.snapshot().isEmpty()) {
                s.sendMessage(C.YELLOW + "No validation results recorded. Run /iris pack validate first.");
                return;
            }
            PackValidationRegistry.snapshot().forEach((name, result) -> {
                String tag = result.isLoadable() ? (C.GREEN + "OK") : (C.RED + "BROKEN");
                s.sendMessage(tag + C.RESET + " " + name
                        + C.GRAY + " (blocking=" + result.getBlockingErrors().size()
                        + ", warnings=" + result.getWarnings().size() + ")");
            });
            return;
        }
        PackValidationResult result = PackValidationRegistry.get(pack);
        if (result == null) {
            s.sendMessage(C.YELLOW + "No validation result for '" + pack + "'. Run /iris pack validate " + pack + ".");
            return;
        }
        reportResult(s, result);
    }

    private PackValidationResult runValidate(VolmitSender s, File packFolder) {
        try {
            PackValidationResult result = PackValidator.validate(packFolder);
            PackValidationRegistry.publish(result);
            reportResult(s, result);
            return result;
        } catch (Throwable e) {
            Iris.reportError("Pack validation failed for '" + packFolder.getName() + "'", e);
            s.sendMessage(C.RED + "Validation of '" + packFolder.getName() + "' failed: " + e.getMessage());
            return null;
        }
    }

    private void reportResult(VolmitSender s, PackValidationResult result) {
        if (result.isLoadable()) {
            s.sendMessage(C.GREEN + "Pack '" + result.getPackName() + "' is loadable."
                    + C.GRAY + " (warnings=" + result.getWarnings().size() + ")");
        } else {
            s.sendMessage(C.RED + "Pack '" + result.getPackName() + "' is BROKEN:");
            for (String reason : result.getBlockingErrors()) {
                s.sendMessage(C.RED + "  - " + reason);
            }
        }
        int wMax = Math.min(10, result.getWarnings().size());
        for (int i = 0; i < wMax; i++) {
            s.sendMessage(C.YELLOW + "  ! " + result.getWarnings().get(i));
        }
        if (result.getWarnings().size() > wMax) {
            s.sendMessage(C.GRAY + "  ... and " + (result.getWarnings().size() - wMax) + " more warning(s).");
        }
    }

    private File findPack(VolmitSender sender, String pack) {
        if (pack == null || pack.isBlank()) {
            sender.sendMessage(C.RED + "You must specify a pack name.");
            return null;
        }
        File packFolder = PackDirectoryResolver.resolveExisting(Iris.instance.getDataFolder("packs"), pack);
        if (packFolder == null) {
            sender.sendMessage(C.RED + "Pack '" + pack + "' not found under packs/.");
            return null;
        }
        return packFolder;
    }

    private void reportPaths(VolmitSender sender, List<String> paths, String label) {
        int max = Math.min(10, paths.size());
        for (int i = 0; i < max; i++) {
            sender.sendMessage(C.GRAY + "  - " + label + ": " + paths.get(i));
        }
        if (paths.size() > max) {
            sender.sendMessage(C.GRAY + "  ... and " + (paths.size() - max) + " more.");
        }
    }
}
