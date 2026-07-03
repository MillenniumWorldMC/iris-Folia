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

package art.arcane.iris.core.pack;

import art.arcane.iris.core.loader.IrisData;
import art.arcane.iris.engine.object.IrisDimension;
import art.arcane.iris.spi.IrisLogging;
import art.arcane.iris.util.common.misc.WebCache;
import art.arcane.volmlib.util.io.IO;
import org.zeroturnaround.zip.ZipUtil;
import org.zeroturnaround.zip.commons.FileUtils;

import java.io.File;
import java.io.IOException;
import java.util.UUID;
import java.util.function.Consumer;

public final class PackDownloader {
    private PackDownloader() {
    }

    public static String download(File packsFolder, String repo, String branch, boolean forceOverwrite, boolean directUrl, Consumer<String> feedback) throws IOException {
        String url = directUrl ? branch : "https://codeload.github.com/" + repo + "/zip/refs/heads/" + branch;
        feedback.accept("Downloading " + url + " "); //The extra space stops a bug in adventure API from repeating the last letter of the URL
        File zip = WebCache.getNonCachedFile("pack-" + repo, url);
        File temp = WebCache.getTemp();
        File work = new File(temp, "dl-" + UUID.randomUUID());

        if (zip == null || !zip.exists()) {
            feedback.accept("Failed to find pack at " + url);
            feedback.accept("Make sure you specified the correct repo and branch!");
            feedback.accept("For example: /iris download overworld branch=stable");
            return null;
        }
        feedback.accept("Unpacking " + repo);
        try {
            ZipUtil.unpack(zip, work);
        } catch (Throwable e) {
            IrisLogging.reportError(e);
            e.printStackTrace();
            feedback.accept(
                    """
                            Issue when unpacking. Please check/do the following:
                            1. Do you have a functioning internet connection?
                            2. Did the download corrupt?
                            3. Try deleting the */plugins/iris/packs folder and re-download.
                            4. Download the pack from the GitHub repo: https://github.com/IrisDimensions/overworld
                            5. Contact support (if all other options do not help)"""
            );
        }
        File dir = null;
        File[] zipFiles = work.listFiles();

        if (zipFiles == null) {
            feedback.accept("No files were extracted from the zip file.");
            return null;
        }

        try {
            dir = zipFiles.length > 1 ? work : zipFiles[0].isDirectory() ? zipFiles[0] : null;
        } catch (NullPointerException e) {
            IrisLogging.reportError(e);
            feedback.accept("Error when finding home directory. Are there any non-text characters in the file name?");
            return null;
        }

        if (dir == null) {
            feedback.accept("Invalid Format. Missing root folder or too many folders!");
            return null;
        }

        IrisData data = IrisData.get(dir);
        String[] dimensions = data.getDimensionLoader().getPossibleKeys();

        if (dimensions == null || dimensions.length == 0) {
            feedback.accept("No dimension file found in the extracted zip file.");
            feedback.accept("Check it is there on GitHub and report this to staff!");
            return null;
        }

        if (dimensions.length != 1) {
            feedback.accept("Dimensions folder must have 1 file in it");
            return null;
        }

        IrisDimension d = data.getDimensionLoader().load(dimensions[0]);
        data.close();

        if (d == null) {
            feedback.accept("Invalid dimension (folder) in dimensions folder");
            return null;
        }

        String key = d.getLoadKey();
        feedback.accept("Importing " + d.getName() + " (" + key + ")");
        File packEntry = new File(packsFolder, key);

        if (forceOverwrite) {
            IO.delete(packEntry);
        }

        if (IrisData.loadAnyDimension(key, null) != null) {
            feedback.accept("Another dimension in the packs folder is already using the key " + key + " IMPORT FAILED!");
            return null;
        }

        File[] existingEntries = packEntry.listFiles();
        if (packEntry.exists() && existingEntries != null && existingEntries.length > 0) {
            feedback.accept("Another pack is using the key " + key + ". IMPORT FAILED!");
            return null;
        }

        FileUtils.copyDirectory(dir, packEntry);

        IrisData.getLoaded(packEntry)
                .ifPresent(IrisData::hotloaded);

        feedback.accept("Successfully Aquired " + d.getName());
        validateDownloaded(packEntry, feedback);
        return key;
    }

    private static void validateDownloaded(File packEntry, Consumer<String> feedback) {
        try {
            PackValidationResult result = PackValidator.validate(packEntry);
            PackValidationRegistry.publish(result);

            if (!result.isLoadable()) {
                feedback.accept("Pack '" + result.getPackName() + "' FAILED validation - world/studio creation will be refused. Reasons:");
                for (String reason : result.getBlockingErrors()) {
                    feedback.accept("  - " + reason);
                }
            } else if (!result.getWarnings().isEmpty() || !result.getRemovedUnusedFiles().isEmpty()) {
                feedback.accept("Pack '" + result.getPackName() + "' validated ("
                        + result.getRemovedUnusedFiles().size() + " unused file(s) quarantined to .iris-trash/, "
                        + result.getWarnings().size() + " warning(s)).");
            } else {
                feedback.accept("Pack '" + result.getPackName() + "' validated.");
            }
        } catch (Throwable e) {
            IrisLogging.reportError("Pack validation failed for '" + packEntry.getName() + "'", e);
        }
    }
}
