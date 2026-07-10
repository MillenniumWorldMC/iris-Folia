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
import art.arcane.iris.core.gui.PregeneratorJob;
import art.arcane.iris.core.pregenerator.PregenTask;
import art.arcane.iris.core.tools.IrisToolbelt;
import art.arcane.iris.util.common.director.DirectorExecutor;
import art.arcane.volmlib.util.director.annotations.Director;
import art.arcane.volmlib.util.director.annotations.Param;
import art.arcane.iris.util.common.format.C;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.math.Position2;
import org.bukkit.World;
import org.bukkit.util.Vector;

@Director(name = "pregen", aliases = "pregenerate", description = "Pregenerate your Iris worlds!")
public class CommandPregen implements DirectorExecutor {
    @Director(description = "Pregenerate a world")
    public void start(
            @Param(description = "The radius of the pregen in blocks", aliases = "size")
            int radius,
            @Param(description = "The world to pregen", contextual = true)
            World world,
            @Param(aliases = "middle", description = "The center location of the pregen. Use \"me\" for your current location", defaultValue = "0,0")
            Vector center,
            @Param(description = "Open the Iris pregen gui", defaultValue = "true")
            boolean gui,
            @Param(name = "serial", description = "Generate only one chunk at a time", defaultValue = "false")
            boolean serial
            ) {
        if (radius <= 0) {
            sender().sendMessage(C.RED + "Pregen radius must be greater than zero blocks.");
            return;
        }
        if (serial && !IrisToolbelt.supportsStrictSerialPregeneration()) {
            sender().sendMessage(C.RED + "Strict serial pregeneration requires Paper or a Paper-compatible server.");
            return;
        }

        try {
            if (sender().isPlayer() && access() == null) {
                sender().sendMessage(C.RED + "The engine access for this world is null!");
                sender().sendMessage(C.RED + "Please make sure the world is loaded & the engine is initialized. Generate a new chunk, for example.");
            }
            PregenTask task = PregenTask
                    .builder()
                    .center(new Position2(center.getBlockX(), center.getBlockZ()))
                    .gui(gui)
                    .radiusX(radius)
                    .radiusZ(radius)
                    .build();
            if (serial) {
                IrisToolbelt.pregenerateSerial(task, world);
            } else {
                IrisToolbelt.pregenerate(task, world);
            }
            String msg = C.GREEN + "Pregen started in " + C.GOLD + world.getName() + C.GREEN + " of " + C.GOLD + (radius * 2) + C.GREEN + " by " + C.GOLD + (radius * 2) + C.GREEN + " blocks from " + C.GOLD + center.getX() + "," + center.getZ();
            sender().sendMessage(msg);
            Iris.info(msg);
        } catch (Throwable e) {
            sender().sendMessage(C.RED + "Failed to start pregeneration. See console for details.");
            Iris.reportError(e);
            e.printStackTrace();
        }
    }

    @Director(description = "Stop the active pregeneration task", aliases = "x")
    public void stop() {
        if (PregeneratorJob.shutdownInstance()) {
            String message = C.BLUE + "Pregen stop requested; finishing active work before cancellation.";
            sender().sendMessage(message);
            Iris.info(message);
        } else {
            sender().sendMessage(C.YELLOW + "No active pregeneration tasks to stop");
        }
    }

    @Director(description = "Pause / continue the active pregeneration task", aliases = {"resume"})
    public void pause() {
        if (PregeneratorJob.pauseResume()) {
            sender().sendMessage(C.GREEN + "Paused/unpaused pregeneration task, now: " + (PregeneratorJob.isPaused() ? "Paused" : "Running") + ".");
        } else {
            sender().sendMessage(C.YELLOW + "No active pregeneration tasks to pause/unpause.");
        }
    }

    @Director(description = "Show the active pregeneration status")
    public void status() {
        PregeneratorJob.PregenProgress progress = PregeneratorJob.progressSnapshot();
        if (progress == null) {
            sender().sendMessage(C.YELLOW + "No active pregeneration task.");
            return;
        }

        String world = progress.worldName() == null ? "?" : progress.worldName();
        sender().sendMessage(C.GREEN + "Pregen " + C.GOLD + world + C.GREEN + ": " + C.GOLD + Form.f(progress.generated()) + "/" + Form.f(progress.totalChunks())
                + C.GREEN + " (" + C.GOLD + String.format("%.1f", progress.percent()) + "%" + C.GREEN + ")"
                + (progress.paused() ? C.YELLOW + " PAUSED" : ""));
        sender().sendMessage(C.GREEN + "Speed: " + C.GOLD + Form.f((int) progress.chunksPerSecond()) + "/s" + C.GREEN
                + " ETA: " + C.GOLD + Form.duration(progress.eta(), 2) + C.GREEN
                + " Elapsed: " + C.GOLD + Form.duration(progress.elapsed(), 2) + C.GREEN
                + " Method: " + C.GOLD + progress.method()
                + (progress.failed() > 0 ? C.RED + " Failed: " + Form.f(progress.failed()) : ""));
    }
}
