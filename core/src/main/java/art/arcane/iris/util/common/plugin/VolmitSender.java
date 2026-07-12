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

package art.arcane.iris.util.common.plugin;

import art.arcane.iris.spi.IrisLogging;
import art.arcane.iris.spi.IrisPlatforms;
import art.arcane.iris.platform.bukkit.BukkitPlatform;
import art.arcane.iris.core.IrisSettings;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.collection.KMap;
import art.arcane.volmlib.util.director.visual.DirectorVisualCommand;
import art.arcane.volmlib.util.director.visual.DirectorVisualCommand.DirectorVisualParameter;
import art.arcane.iris.util.common.format.C;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.math.M;
import art.arcane.volmlib.util.math.RNG;
import art.arcane.iris.util.common.scheduling.J;
import lombok.Getter;
import lombok.Setter;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Server;
import org.bukkit.Sound;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionAttachment;
import org.bukkit.permissions.PermissionAttachmentInfo;
import org.jetbrains.annotations.NotNull;
import org.bukkit.plugin.Plugin;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Represents a volume sender. A command sender with extra crap in it
 *
 * @author cyberpwn
 */
public class VolmitSender implements CommandSender {
    @Getter
    private static final KMap<String, String> helpCache = new KMap<>();
    private final CommandSender s;
    private String tag;
    @Getter
    @Setter
    private String command;

    /**
     * Wrap a command sender
     *
     * @param s the command sender
     */
    public VolmitSender(CommandSender s) {
        tag = "";
        this.s = s;
    }

    public VolmitSender(CommandSender s, String tag) {
        this.tag = tag;
        this.s = s;
    }

    public static long getTick() {
        return M.ms() / 16;
    }

    public static String pulse(String colorA, String colorB, double speed) {
        return "<gradient:" + colorA + ":" + colorB + ":" + pulse(speed) + ">";
    }

    public static String pulse(double speed) {
        return Form.f(invertSpread((((getTick() * 15D * speed) % 1000D) / 1000D)), 3).replaceAll("\\Q,\\E", ".").replaceAll("\\Q?\\E", "-");
    }

    public static double invertSpread(double v) {
        return ((1D - v) * 2D) - 1D;
    }

    public static <T> KList<T> paginate(KList<T> all, int linesPerPage, int page, AtomicBoolean hasNext) {
        int totalPages = (int) Math.ceil((double) all.size() / linesPerPage);
        page = page < 0 ? 0 : page >= totalPages ? totalPages - 1 : page;
        hasNext.set(page < totalPages - 1);
        KList<T> d = new KList<>();

        for (int i = linesPerPage * page; i < Math.min(all.size(), linesPerPage * (page + 1)); i++) {
            d.add(all.get(i));
        }

        return d;
    }

    /**
     * Get the command tag
     *
     * @return the command tag
     */
    public String getTag() {
        return tag;
    }

    /**
     * Set a command tag (prefix for sendMessage)
     *
     * @param tag the tag
     */
    public void setTag(String tag) {
        this.tag = tag;
    }

    /**
     * Is this sender a player?
     *
     * @return true if it is
     */
    public boolean isPlayer() {
        return getS() instanceof Player;
    }

    /**
     * Force cast to player (be sure to check first)
     *
     * @return a casted player
     */
    public Player player() {
        return (Player) getS();
    }

    /**
     * Get the origin sender this object is wrapping
     *
     * @return the command sender
     */
    public CommandSender getS() {
        return s;
    }

    @Override
    public boolean isPermissionSet(String name) {
        return s.isPermissionSet(name);
    }

    @Override
    public boolean isPermissionSet(Permission perm) {
        return s.isPermissionSet(perm);
    }

    @Override
    public boolean hasPermission(String name) {
        return s.hasPermission(name);
    }

    @Override
    public boolean hasPermission(Permission perm) {
        return s.hasPermission(perm);
    }

    @Override
    public PermissionAttachment addAttachment(Plugin plugin, String name, boolean value) {
        return s.addAttachment(plugin, name, value);
    }

    @Override
    public PermissionAttachment addAttachment(Plugin plugin) {
        return s.addAttachment(plugin);
    }

    @Override
    public PermissionAttachment addAttachment(Plugin plugin, String name, boolean value, int ticks) {
        return s.addAttachment(plugin, name, value, ticks);
    }

    @Override
    public PermissionAttachment addAttachment(Plugin plugin, int ticks) {
        return s.addAttachment(plugin, ticks);
    }

    @Override
    public void removeAttachment(PermissionAttachment attachment) {
        s.removeAttachment(attachment);
    }

    @Override
    public void recalculatePermissions() {
        s.recalculatePermissions();
    }

    @Override
    public Set<PermissionAttachmentInfo> getEffectivePermissions() {
        return s.getEffectivePermissions();
    }

    @Override
    public boolean isOp() {
        return s.isOp();
    }

    @Override
    public void setOp(boolean value) {
        s.setOp(value);
    }

    public void hr() {
        s.sendMessage("========================================================");
    }

    public void sendTitle(String title, String subtitle, int i, int s, int o) {
        try {
            player().sendTitle(
                    LegacyComponentSerializer.legacySection().serialize(createComponent(title)),
                    LegacyComponentSerializer.legacySection().serialize(createComponent(subtitle)),
                    i / 50, s / 50, o / 50);
        } catch (Throwable ignored) {
        }
    }

    public void sendProgress(double percent, String thing) {
        //noinspection IfStatementWithIdenticalBranches
        if (percent < 0) {
            int l = 44;
            int g = (int) (1D * l);
            sendTitle(C.IRIS + thing + " ", 0, 500, 250);
            sendActionNoProcessing("" + "" + pulse("#00ff80", "#00373d", 1D) + "<underlined> " + Form.repeat(" ", g) + "<reset>" + Form.repeat(" ", l - g));
        } else {
            int l = 44;
            int g = (int) (percent * l);
            sendTitle(C.IRIS + thing + " " + C.BLUE + "<font:minecraft:uniform>" + Form.pc(percent, 0), 0, 500, 250);
            sendActionNoProcessing("" + "" + pulse("#00ff80", "#00373d", 1D) + "<underlined> " + Form.repeat(" ", g) + "<reset>" + Form.repeat(" ", l - g));
        }
    }

    public void sendAction(String action) {
        try {
            player().spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(LegacyComponentSerializer.legacySection().serialize(createNoPrefixComponent(action))));
        } catch (Throwable ignored) {
        }
    }

    public void sendActionNoProcessing(String action) {
        try {
            player().spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(LegacyComponentSerializer.legacySection().serialize(createNoPrefixComponentNoProcessing(action))));
        } catch (Throwable ignored) {
        }
    }

    public void sendTitle(String subtitle, int i, int s, int o) {
        try {
            player().sendTitle(
                    " ",
                    LegacyComponentSerializer.legacySection().serialize(createNoPrefixComponent(subtitle)),
                    i / 50, s / 50, o / 50);
        } catch (Throwable ignored) {
        }
    }

    private Component createNoPrefixComponent(String message) {
        if (!IrisSettings.get().getGeneral().canUseCustomColors(this)) {
            String t = C.translateAlternateColorCodes('&', MiniMessage.miniMessage().stripTags(message));
            return MiniMessage.miniMessage().deserialize(C.mini(t));
        }

        String t = C.translateAlternateColorCodes('&', message);
        String a = C.aura(t, IrisSettings.get().getGeneral().getSpinh(), IrisSettings.get().getGeneral().getSpins(), IrisSettings.get().getGeneral().getSpinb(), 0.36);
        return MiniMessage.miniMessage().deserialize(a);
    }

    private Component createNoPrefixComponentNoProcessing(String message) {
        return MiniMessage.builder().postProcessor(c -> c).build().deserialize(C.mini(message));
    }

    private Component createComponent(String message) {
        if (!IrisSettings.get().getGeneral().canUseCustomColors(this)) {
            String t = C.translateAlternateColorCodes('&', MiniMessage.miniMessage().stripTags(getTag() + message));
            return MiniMessage.miniMessage().deserialize(C.mini(t));
        }

        String t = C.translateAlternateColorCodes('&', getTag() + message);
        String a = C.aura(t, IrisSettings.get().getGeneral().getSpinh(), IrisSettings.get().getGeneral().getSpins(), IrisSettings.get().getGeneral().getSpinb());
        return MiniMessage.miniMessage().deserialize(a);
    }

    private Component createComponentRaw(String message) {
        if (!IrisSettings.get().getGeneral().canUseCustomColors(this)) {
            String t = C.translateAlternateColorCodes('&', MiniMessage.miniMessage().stripTags(getTag() + message));
            return MiniMessage.miniMessage().deserialize(C.mini(t));
        }

        String t = C.translateAlternateColorCodes('&', getTag() + message);
        return MiniMessage.miniMessage().deserialize(C.mini(t));
    }

    public <T> void showWaiting(String passive, CompletableFuture<T> f) {
        AtomicInteger v = new AtomicInteger();
        AtomicReference<T> g = new AtomicReference<>();
        v.set(J.ar(() -> {
            if (f.isDone() && g.get() != null) {
                J.car(v.get());
                sendAction(" ");
                return;
            }

            sendProgress(-1, passive);
        }, 0));
        J.a(() -> {
            try {
                g.set(f.get());
            } catch (InterruptedException e) {
                e.printStackTrace();
            } catch (ExecutionException e) {
                e.printStackTrace();
            }
        });

    }

    @Override
    public void sendMessage(String message) {
        if (s instanceof CommandDummy) {
            return;
        }

        if ((!IrisSettings.get().getGeneral().isUseCustomColorsIngame() && s instanceof Player) || !IrisSettings.get().getGeneral().isUseConsoleCustomColors()) {
            s.sendMessage(C.translateAlternateColorCodes('&', getTag() + message));
            return;
        }

        if (message.contains("<NOMINI>")) {
            s.sendMessage(C.translateAlternateColorCodes('&', getTag() + message.replaceAll("\\Q<NOMINI>\\E", "")));
            return;
        }

        deliver(createComponent(message));
    }

    public void sendMessageBasic(String message) {
        s.sendMessage(C.translateAlternateColorCodes('&', getTag() + message));
    }

    public void sendMessageRaw(String message) {
        if (s instanceof CommandDummy) {
            return;
        }

        if ((!IrisSettings.get().getGeneral().isUseCustomColorsIngame() && s instanceof Player) || !IrisSettings.get().getGeneral().isUseConsoleCustomColors()) {
            s.sendMessage(C.translateAlternateColorCodes('&', message));
            return;
        }

        if (message.contains("<NOMINI>")) {
            s.sendMessage(message.replaceAll("\\Q<NOMINI>\\E", ""));
            return;
        }

        deliver(createComponentRaw(message));
    }

    public void sendComponent(Component component) {
        if (s instanceof CommandDummy) {
            return;
        }
        deliver(component);
    }

    private void deliver(Component component) {
        if (sendNative(s, component)) {
            return;
        }
        s.sendMessage(LegacyComponentSerializer.legacySection().serialize(component));
    }

    private static volatile boolean nativeProbed = false;
    private static Object nativeGson;
    private static java.lang.reflect.Method nativeDeserialize;
    private static java.lang.reflect.Method nativeSendMessage;
    private static final java.util.concurrent.atomic.AtomicBoolean SEND_LOGGED = new java.util.concurrent.atomic.AtomicBoolean(false);

    private static void adventureDebug(String message) {
        try {
            java.io.File f = IrisPlatforms.get().dataFile("adventure-debug.txt");
            java.nio.file.Files.writeString(f.toPath(), message + "\n", java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
        } catch (Throwable ignored) {
        }
    }

    private static String nativeAdventure(String suffix) {
        return new String(new char[]{'n', 'e', 't', '.', 'k', 'y', 'o', 'r', 'i', '.', 'a', 'd', 'v', 'e', 'n', 't', 'u', 'r', 'e', '.', 't', 'e', 'x', 't', '.'}) + suffix;
    }

    private static void probeNative() {
        String step = "start";
        try {
            String[] candidates = {nativeAdventure("serializer.gson.GsonComponentSerializer"), nativeAdventure("serializer.json.JSONComponentSerializer")};
            String[] accessors = {"gson", "json"};
            Class<?> serializer = null;
            for (int i = 0; i < candidates.length; i++) {
                try {
                    step = "forName " + candidates[i];
                    Class<?> c = Class.forName(candidates[i]);
                    step = "accessor " + accessors[i] + " on " + candidates[i];
                    nativeGson = c.getMethod(accessors[i]).invoke(null);
                    serializer = c;
                    break;
                } catch (Throwable ignore) {
                }
            }
            if (serializer == null) {
                throw new ClassNotFoundException("no native adventure json serializer exposed");
            }
            step = "find deserialize on " + serializer.getName();
            java.lang.reflect.Method exact = null;
            java.lang.reflect.Method loose = null;
            for (java.lang.reflect.Method m : serializer.getMethods()) {
                if (m.getName().equals("deserialize") && m.getParameterCount() == 1) {
                    Class<?> p = m.getParameterTypes()[0];
                    if (p == String.class) {
                        exact = m;
                        break;
                    }
                    if (p.isAssignableFrom(String.class)) {
                        loose = m;
                    }
                }
            }
            nativeDeserialize = exact != null ? exact : loose;
            if (nativeDeserialize == null) {
                throw new NoSuchMethodException("deserialize(String-compatible) not found");
            }
            step = "forName Component";
            Class<?> componentType = Class.forName(nativeAdventure("Component"));
            step = "getMethod CommandSender.sendMessage(Component)";
            nativeSendMessage = CommandSender.class.getMethod("sendMessage", componentType);
        } catch (Throwable e) {
            nativeSendMessage = null;
            adventureDebug("PROBE FAIL at [" + step + "]: " + e.getClass().getName() + ": " + e.getMessage());
        }
        nativeProbed = true;
    }

    private static boolean sendNative(CommandSender target, Component component) {
        if (!nativeProbed) {
            probeNative();
        }
        if (nativeSendMessage == null) {
            return false;
        }
        try {
            String json = GsonComponentSerializer.gson().serialize(component);
            Object nativeComponent = nativeDeserialize.invoke(nativeGson, json);
            nativeSendMessage.invoke(target, nativeComponent);
            return true;
        } catch (Throwable e) {
            if (SEND_LOGGED.compareAndSet(false, true)) {
                Throwable cause = e.getCause() != null ? e.getCause() : e;
                adventureDebug("SEND FAIL: " + cause.getClass().getName() + ": " + cause.getMessage());
            }
            return false;
        }
    }

    @Override
    public void sendMessage(String[] messages) {
        for (String str : messages)
            sendMessage(str);
    }

    @Override
    public void sendMessage(UUID uuid, String message) {
        sendMessage(message);
    }

    @Override
    public void sendMessage(UUID uuid, String[] messages) {
        sendMessage(messages);
    }

    @Override
    public Server getServer() {
        return s.getServer();
    }

    @Override
    public String getName() {
        return s.getName();
    }

    @NotNull
    @Override
    public Component name() {
        return s.name();
    }

    @Override
    public Spigot spigot() {
        return s.spigot();
    }

    private String pickRandoms(int max, DirectorVisualCommand i) {
        KList<String> m = new KList<>();
        for (int ix = 0; ix < max; ix++) {
            m.add((i.isNode()
                    ? (i.getNode().getParameters().isNotEmpty())
                    ? "<#c2f7d2>✦ <#5ef288>"
                    + i.getParentPath()
                    + " <#32bfad>"
                    + i.getName() + " "
                    + i.getNode().getParameters().shuffleCopy(RNG.r).convert((f)
                            -> (f.isRequired() || RNG.r.b(0.5)
                            ? "<#f2e15e>" + f.getNames().getRandom() + "="
                            + "<#5ef288>" + f.example()
                            : ""))
                    .toString(" ")
                    : ""
                    : ""));
        }

        return m.removeDuplicates().convert((iff) -> iff.replaceAll("\\Q  \\E", " ")).toString("\n");
    }

    static String escapeMiniMessageQuotedText(String text) {
        return text.replace("\\", "\\\\").replace("'", "\\'");
    }

    public void sendHeader(String name, int overrideLength) {
        int len = overrideLength;
        int h = name.length() + 2;
        String s = Form.repeat(" ", len - h - 4);
        String si = Form.repeat("(", 3);
        String so = Form.repeat(")", 3);
        String sf = "[";
        String se = "]";

        if (name.trim().isEmpty()) {
            sendMessageRaw("<font:minecraft:uniform><strikethrough><gradient:#34eb6b:#32bfad>" + sf + s + "<reset><font:minecraft:uniform><strikethrough><gradient:#32bfad:#34eb6b>" + s + se);
        } else {
            sendMessageRaw("<font:minecraft:uniform><strikethrough><gradient:#34eb6b:#32bfad>" + sf + s + si + "<reset> <gradient:#32bfad:#34eb6b>" + name + "<reset> <font:minecraft:uniform><strikethrough><gradient:#32bfad:#34eb6b>" + so + s + se);
        }
    }

    public void sendHeader(String name) {
        sendHeader(name, 44);
    }

    public void sendDirectorHelp(DirectorVisualCommand v) {
        sendDirectorHelp(v, 0);
    }

    public void sendDirectorHelp(DirectorVisualCommand v, int page) {
        if (!isPlayer()) {
            for (DirectorVisualCommand i : v.getNodes()) {
                sendDirectorHelpNode(i);
            }

            return;
        }

        sendMessageRaw("\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n");

        if (v.getNodes().isNotEmpty()) {
            sendHeader(v.getPath() + (page > 0 ? (" {" + (page + 1) + "}") : ""));
            if (isPlayer() && v.getParent() != null) {
                String backHover = escapeMiniMessageQuotedText("<#2b7a3f>Click to go back to <#32bfad>" + Form.capitalize(v.getParent().getName()) + " Help");
                sendMessageRaw("<hover:show_text:'" + backHover + "'><click:run_command:" + v.getParent().getPath() + "><font:minecraft:uniform><#6fe98f>〈 Back</click></hover>");
            }

            AtomicBoolean next = new AtomicBoolean(false);
            for (DirectorVisualCommand i : paginate(v.getNodes(), 17, page, next)) {
                sendDirectorHelpNode(i);
            }

            String s = "";
            int l = 75 - (page > 0 ? 10 : 0) - (next.get() ? 10 : 0);

            if (page > 0) {
                String previousPageHover = escapeMiniMessageQuotedText("<green>Click to go back to page " + page);
                s += "<hover:show_text:'" + previousPageHover + "'><click:run_command:" + v.getPath() + " help=" + page + "><gradient:#34eb6b:#1f8f4d>〈 Page " + page + "</click></hover><reset> ";
            }

            s += "<reset><font:minecraft:uniform><strikethrough><gradient:#32bfad:#34eb6b>" + Form.repeat(" ", l) + "<reset>";

            if (next.get()) {
                String nextPageHover = escapeMiniMessageQuotedText("<green>Click to go to back to page " + (page + 2));
                s += " <hover:show_text:'" + nextPageHover + "'><click:run_command:" + v.getPath() + " help=" + (page + 2) + "><gradient:#1f8f4d:#34eb6b>Page " + (page + 2) + " ❭</click></hover>";
            }

            sendMessageRaw(s);
        } else {
            sendMessage(C.RED + "There are no subcommands in this group! Contact support, this is a command design issue!");
        }
    }

    public void sendDirectorHelpNode(DirectorVisualCommand i) {
        if (isPlayer() || s instanceof CommandDummy) {
            sendMessageRaw(helpCache.computeIfAbsent(i.getPath(), (k) -> {
                String newline = "<reset>\n";

                String realText = i.getPath() + " >" + "<#46826a>⇀<gradient:#5ef288:#32bfad> " + i.getName();
                String hoverTitle = i.getNames().copy().reverse().convert((f) -> "<#5ef288>" + f).toString(", ");
                String description = "<#3fe05a>✎ <#6ad97d><font:minecraft:uniform>" + i.getDescription();
                String usage = "<#bbe03f>✒ <#a8e0a2><font:minecraft:uniform>";
                String onClick;
                if (i.isNode()) {
                    if (i.getNode().getParameters().isEmpty()) {
                        usage += "There are no parameters. Click to type command.";
                        onClick = "suggest_command";
                    } else {
                        usage += "Hover over all of the parameters to learn more.";
                        onClick = "suggest_command";
                    }
                } else {
                    usage += "This is a command category. Click to run.";
                    onClick = "run_command";
                }

                String suggestion = "";
                String suggestions = "";
                if (i.isNode() && i.getNode().getParameters().isNotEmpty()) {
                    suggestion += newline + "<#c2f7d2>✦ <#5ef288><font:minecraft:uniform>" + i.getParentPath() + " <#32bfad>" + i.getName() + " "
                            + i.getNode().getParameters().convert((f) -> "<#5ef288>" + f.example()).toString(" ");
                    suggestions += newline + "<font:minecraft:uniform>" + pickRandoms(Math.min(i.getNode().getParameters().size() + 1, 5), i);
                }

                StringBuilder nodes = new StringBuilder();
                if (i.isNode()) {
                    for (DirectorVisualParameter p : i.getNode().getParameters()) {
                        String nTitle = "<gradient:#5ef288:#32bfad>" + p.getName();
                        String nHoverTitle = p.getNames().convert((ff) -> "<#5ef288>" + ff).toString(", ");
                        String nDescription = "<#3fe05a>✎ <#6ad97d><font:minecraft:uniform>" + p.getDescription();
                        String nUsage;
                        String fullTitle;
                        IrisLogging.debug("Contextual: " + p.isContextual() + " / player: " + isPlayer());
                        if (p.isContextual() && (isPlayer() || s instanceof CommandDummy)) {
                            fullTitle = "<#ffcc00>[" + nTitle + "<#ffcc00>] ";
                            nUsage = "<#ff9900>➱ <#ffcc00><font:minecraft:uniform>The value may be derived from environment context.";
                        } else if (p.isRequired()) {
                            fullTitle = "<red>[" + nTitle + "<red>] ";
                            nUsage = "<#db4321>⚠ <#faa796><font:minecraft:uniform>This parameter is required.";
                        } else if (p.hasDefault()) {
                            fullTitle = "<#4f4f4f>⊰" + nTitle + "<#4f4f4f>⊱";
                            nUsage = "<#3fbe6f>✔ <#9de5b6><font:minecraft:uniform>Defaults to \"" + p.getParam().defaultValue() + "\" if undefined.";
                        } else {
                            fullTitle = "<#4f4f4f>⊰" + nTitle + "<#4f4f4f>⊱";
                            nUsage = "<#3fbe6f>✔ <#9de5b6><font:minecraft:uniform>This parameter is optional.";
                        }
                        String type = "<#4fbf7f>✢ <#8ad9af><font:minecraft:uniform>This parameter is of type " + p.getType().getSimpleName() + ".";
                        String parameterHover = escapeMiniMessageQuotedText(nHoverTitle + newline + nDescription + newline + nUsage + newline + type);

                        nodes
                                .append("<hover:show_text:'")
                                .append(parameterHover)
                                .append("'>")
                                .append(fullTitle)
                                .append("</hover>");
                    }
                } else {
                    nodes = new StringBuilder("<gradient:#b7eecb:#9de5b6> - Category of Commands");
                }

                String entryHover = escapeMiniMessageQuotedText(
                        hoverTitle + newline +
                                description + newline +
                                usage +
                                suggestion +
                                suggestions
                );

                return "<hover:show_text:'" +
                        entryHover +
                        "'>" +
                        "<click:" +
                        onClick +
                        ":" +
                        realText +
                        "</click>" +
                        "</hover>" +
                        " " +
                        nodes;
            }));
        } else {
            sendMessage(i.getPath());
        }
    }

    public void playSound(Sound sound, float volume, float pitch) {
        if (isPlayer()) {
            player().playSound(player().getLocation(), sound, volume, pitch);
        }
    }
}
