package art.arcane.iris.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;

public final class IrisClientHud {
    private IrisClientHud() {
    }

    public static void render(GuiGraphicsExtractor graphics) {
        IrisToastPresenter.pump();
        IrisPregenHud.render(graphics);
        IrisWhatOverlay.render(graphics);
    }
}
