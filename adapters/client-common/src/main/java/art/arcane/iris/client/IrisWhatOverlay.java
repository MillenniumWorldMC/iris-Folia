package art.arcane.iris.client;

import art.arcane.iris.spi.protocol.IrisMessage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import java.util.ArrayList;
import java.util.List;

public final class IrisWhatOverlay {
    private static final int PANEL_COLOR = 0xC0101010;
    private static final int TITLE_COLOR = 0xFF66BB6A;
    private static final int TEXT_COLOR = 0xFFFFFFFF;
    private static final int MUTED_COLOR = 0xFFC7C7C7;
    private static final int PADDING = 4;
    private static final int ROW_GAP = 2;
    private static final int CURSOR_OFFSET = 12;

    private IrisWhatOverlay() {
    }

    public static void render(GuiGraphicsExtractor graphics) {
        if (!IrisClient.whatVisible() || !IrisClient.cursorAvailable()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.player == null) {
            return;
        }
        LocalPlayer player = minecraft.player;
        int blockX = player.getBlockX();
        int blockZ = player.getBlockZ();
        HitResult hit = minecraft.hitResult;
        if (hit != null && hit.getType() == HitResult.Type.BLOCK && hit instanceof BlockHitResult blockHit) {
            BlockPos pos = blockHit.getBlockPos();
            blockX = pos.getX();
            blockZ = pos.getZ();
        }
        IrisClient.cursor().requestFor(blockX, blockZ);

        IrisMessage.CursorInfo info = IrisClient.cursor().latest();
        List<String> lines = new ArrayList<>();
        if (info == null) {
            lines.add("querying " + blockX + ", " + blockZ + "...");
        } else {
            lines.add("Biome: " + display(info.biomeKey()));
            lines.add("Region: " + display(info.regionKey()));
            if (info.caveBiomeKey() != null && !info.caveBiomeKey().isEmpty()) {
                lines.add("Cave: " + display(info.caveBiomeKey()));
            }
            lines.add("Height: " + info.height() + "   (" + info.blockX() + ", " + info.blockZ() + ")");
        }
        draw(graphics, minecraft.font, lines);
    }

    private static void draw(GuiGraphicsExtractor graphics, Font font, List<String> lines) {
        int lineHeight = font.lineHeight;
        int contentWidth = font.width("Iris What");
        for (String line : lines) {
            contentWidth = Math.max(contentWidth, font.width(line));
        }
        int originX = graphics.guiWidth() / 2 + CURSOR_OFFSET;
        int originY = graphics.guiHeight() / 2 + CURSOR_OFFSET;
        int rows = lines.size() + 1;
        int contentHeight = lineHeight * rows + ROW_GAP * rows;
        graphics.fill(originX - PADDING, originY - PADDING, originX + contentWidth + PADDING, originY + contentHeight + PADDING, PANEL_COLOR);

        int cursorY = originY;
        graphics.text(font, "Iris What", originX, cursorY, TITLE_COLOR);
        cursorY += lineHeight + ROW_GAP;
        for (String line : lines) {
            graphics.text(font, line, originX, cursorY, line.startsWith("Height") ? MUTED_COLOR : TEXT_COLOR);
            cursorY += lineHeight + ROW_GAP;
        }
    }

    private static String display(String key) {
        return key == null || key.isEmpty() ? "-" : key;
    }
}
