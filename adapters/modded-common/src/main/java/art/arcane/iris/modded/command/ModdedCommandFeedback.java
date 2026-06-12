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

import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

final class ModdedCommandFeedback {
    private static final long MESSAGE_SOUND_COOLDOWN_MS = 650L;
    private static final long TAB_SOUND_COOLDOWN_MS = 175L;
    private static final Map<UUID, Long> MESSAGE_SOUNDS = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> TAB_SOUNDS = new ConcurrentHashMap<>();

    private ModdedCommandFeedback() {
    }

    static void ok(CommandSourceStack source, String message) {
        source.sendSuccess(() -> Component.literal(message).withStyle(ChatFormatting.GREEN), false);
        playSuccess(source);
    }

    static void fail(CommandSourceStack source, String message) {
        source.sendFailure(Component.literal(message).withStyle(ChatFormatting.RED));
        playFailure(source);
    }

    static void send(CommandSourceStack source, Component component) {
        source.sendSuccess(() -> component, false);
    }

    static void tab(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null || !claim(TAB_SOUNDS, player.getUUID(), TAB_SOUND_COOLDOWN_MS)) {
            return;
        }

        player.level().playSound(null, player.blockPosition(), SoundEvents.ITEM_FRAME_ROTATE_ITEM, SoundSource.PLAYERS, 0.25F, 1.7F);
    }

    private static void playSuccess(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null || !claim(MESSAGE_SOUNDS, player.getUUID(), MESSAGE_SOUND_COOLDOWN_MS)) {
            return;
        }

        ServerLevel level = player.level();
        level.playSound(null, player.blockPosition(), SoundEvents.AMETHYST_CLUSTER_BREAK, SoundSource.PLAYERS, 0.77F, 1.65F);
        level.playSound(null, player.blockPosition(), SoundEvents.RESPAWN_ANCHOR_CHARGE, SoundSource.PLAYERS, 0.125F, 2.99F);
    }

    private static void playFailure(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null || !claim(MESSAGE_SOUNDS, player.getUUID(), MESSAGE_SOUND_COOLDOWN_MS)) {
            return;
        }

        ServerLevel level = player.level();
        level.playSound(null, player.blockPosition(), SoundEvents.AMETHYST_CLUSTER_BREAK, SoundSource.PLAYERS, 0.77F, 0.25F);
        level.playSound(null, player.blockPosition(), SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 0.2F, 0.45F);
    }

    private static boolean claim(Map<UUID, Long> sounds, UUID uuid, long cooldownMs) {
        long now = System.currentTimeMillis();
        Long previous = sounds.get(uuid);
        if (previous != null && now - previous.longValue() < cooldownMs) {
            return false;
        }

        sounds.put(uuid, now);
        return true;
    }
}
