package art.arcane.iris.modded.command;

import art.arcane.iris.core.gui.PregeneratorJob;
import art.arcane.volmlib.util.format.Form;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;

import java.util.UUID;

public final class ModdedPregenBossBar {
    private static final UUID BAR_ID = UUID.fromString("14150000-1e15-4a2b-9c3d-1115e9a1cafe");
    private static final int UPDATE_INTERVAL_TICKS = 10;

    private static volatile ServerBossEvent bar;
    private static volatile UUID viewer;
    private static int sinceUpdate;

    private ModdedPregenBossBar() {
    }

    public static synchronized void begin(ServerPlayer player) {
        clear();
        if (player == null) {
            return;
        }
        viewer = player.getUUID();
        bar = new ServerBossEvent(BAR_ID, Component.literal("Iris Pregen  starting..."), BossEvent.BossBarColor.GREEN, BossEvent.BossBarOverlay.PROGRESS);
        bar.setProgress(0.0F);
        bar.addPlayer(player);
        sinceUpdate = UPDATE_INTERVAL_TICKS;
    }

    public static void tick(MinecraftServer server) {
        ServerBossEvent active = bar;
        if (active == null) {
            return;
        }
        PregeneratorJob.PregenProgress progress = PregeneratorJob.progressSnapshot();
        if (progress == null) {
            clear();
            return;
        }
        if (++sinceUpdate < UPDATE_INTERVAL_TICKS) {
            return;
        }
        sinceUpdate = 0;
        reattach(server, active);
        active.setProgress((float) clamp01(progress.percent() / 100.0D));
        active.setColor(progress.paused() ? BossEvent.BossBarColor.YELLOW : BossEvent.BossBarColor.GREEN);
        active.setName(nameFor(progress));
    }

    private static void reattach(MinecraftServer server, ServerBossEvent active) {
        UUID id = viewer;
        if (id == null || server == null) {
            return;
        }
        ServerPlayer player = server.getPlayerList().getPlayer(id);
        if (player != null && !active.getPlayers().contains(player)) {
            active.addPlayer(player);
        }
    }

    private static Component nameFor(PregeneratorJob.PregenProgress progress) {
        MutableComponent name = Component.empty();
        name.append(ModdedCommandFeedback.text("Iris Pregen  ", ModdedCommandFeedback.DARK_GREEN));
        name.append(ModdedCommandFeedback.text(Form.f(progress.generated()) + "/" + Form.f(progress.totalChunks()), ModdedCommandFeedback.VALUE));
        name.append(ModdedCommandFeedback.text("  " + String.format("%.1f", progress.percent()) + "%", ModdedCommandFeedback.USAGE));
        if (progress.paused()) {
            name.append(ModdedCommandFeedback.text("  PAUSED", ModdedCommandFeedback.REQUIRED));
            return name;
        }
        name.append(ModdedCommandFeedback.text("  " + Form.f((int) progress.chunksPerSecond()) + "/s", ModdedCommandFeedback.VALUE));
        if (progress.eta() > 0L) {
            name.append(ModdedCommandFeedback.text("  ETA " + Form.duration(progress.eta(), 1), ModdedCommandFeedback.DARK_GREEN));
        }
        if (progress.failed() > 0L) {
            name.append(ModdedCommandFeedback.text("  failed " + Form.f(progress.failed()), ModdedCommandFeedback.REQUIRED));
        }
        return name;
    }

    private static double clamp01(double value) {
        if (value < 0.0D) {
            return 0.0D;
        }
        if (value > 1.0D) {
            return 1.0D;
        }
        return value;
    }

    public static synchronized void clear() {
        ServerBossEvent existing = bar;
        if (existing != null) {
            existing.removeAllPlayers();
            existing.setVisible(false);
        }
        bar = null;
        viewer = null;
        sinceUpdate = 0;
    }
}
