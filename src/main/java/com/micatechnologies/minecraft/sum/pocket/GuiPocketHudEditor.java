package com.micatechnologies.minecraft.sum.pocket;

import java.io.IOException;
import javax.annotation.ParametersAreNonnullByDefault;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;

/**
 * Layout editor for the pocket HUD: drag the HUD block to reposition it, or use the
 * inline buttons to toggle visibility, change scale, and save. Closes automatically on
 * ESC with whatever the current edits are (mutation is direct on
 * {@link PocketHudConfig}); {@link #onGuiClosed} writes the config file to disk.
 *
 * <p>Drag/drop UX is modeled on Polyfrost's EvergreenHUD layout-editor — see README for
 * attribution.
 */
@ParametersAreNonnullByDefault
public class GuiPocketHudEditor extends GuiScreen {

    private static final int BUTTON_ID_TOGGLE = 0;
    private static final int BUTTON_ID_SCALE_DOWN = 1;
    private static final int BUTTON_ID_SCALE_UP = 2;
    private static final int BUTTON_ID_RESET = 3;
    private static final int BUTTON_ID_DONE = 4;

    private final PocketHudOverlay previewRenderer = new PocketHudOverlay();

    private GuiButton toggleButton;
    private GuiButton scaleDownButton;
    private GuiButton scaleUpButton;

    private boolean dragging = false;
    /** Mouse offset relative to the HUD block's top-left at drag start. */
    private int dragOffsetX = 0;
    private int dragOffsetY = 0;
    /** Saved-on-open snapshot so the {@link #BUTTON_ID_RESET} button can revert in one click. */
    private final boolean origEnabled;
    private final float origAnchorX;
    private final float origAnchorY;
    private final float origScale;

    public GuiPocketHudEditor() {
        this.origEnabled = PocketHudConfig.isEnabled();
        this.origAnchorX = PocketHudConfig.getAnchorX();
        this.origAnchorY = PocketHudConfig.getAnchorY();
        this.origScale = PocketHudConfig.getScale();
    }

    @Override
    public void initGui() {
        super.initGui();
        this.buttonList.clear();

        int btnW = 100;
        int btnH = 20;
        int x = this.width / 2 - btnW / 2;
        int y = this.height - 100;

        toggleButton = new GuiButton(BUTTON_ID_TOGGLE, x, y, btnW, btnH,
            "HUD: " + (PocketHudConfig.isEnabled() ? "On" : "Off"));
        this.addButton(toggleButton);

        scaleDownButton = new GuiButton(BUTTON_ID_SCALE_DOWN,
            x - 50, y + 24, 46, btnH, "Scale −");
        this.addButton(scaleDownButton);

        scaleUpButton = new GuiButton(BUTTON_ID_SCALE_UP,
            x + btnW + 4, y + 24, 46, btnH, "Scale +");
        this.addButton(scaleUpButton);

        this.addButton(new GuiButton(BUTTON_ID_RESET,
            x - 50, y + 48, 96, btnH, "Reset"));
        this.addButton(new GuiButton(BUTTON_ID_DONE,
            x + btnW + 4 - 46, y + 48, 96, btnH, "Done"));
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        switch (button.id) {
            case BUTTON_ID_TOGGLE:
                PocketHudConfig.setEnabled(!PocketHudConfig.isEnabled());
                toggleButton.displayString = "HUD: "
                    + (PocketHudConfig.isEnabled() ? "On" : "Off");
                break;
            case BUTTON_ID_SCALE_DOWN:
                PocketHudConfig.setScale(PocketHudConfig.getScale() - 0.1F);
                break;
            case BUTTON_ID_SCALE_UP:
                PocketHudConfig.setScale(PocketHudConfig.getScale() + 0.1F);
                break;
            case BUTTON_ID_RESET:
                PocketHudConfig.setEnabled(origEnabled);
                PocketHudConfig.setAnchor(origAnchorX, origAnchorY);
                PocketHudConfig.setScale(origScale);
                toggleButton.displayString = "HUD: "
                    + (PocketHudConfig.isEnabled() ? "On" : "Off");
                break;
            case BUTTON_ID_DONE:
                this.mc.player.closeScreen();
                break;
            default:
                super.actionPerformed(button);
        }
    }

    @Override
    public void onGuiClosed() {
        PocketHudConfig.save();
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();

        // Title + drag-hint label, centered at the top.
        drawCenteredString(this.fontRenderer, "Pocket HUD Layout", this.width / 2, 14, 0xFFFFFFFF);
        drawCenteredString(this.fontRenderer,
            "Drag the HUD to reposition. ESC to save.",
            this.width / 2, 28, 0xFFC0C0C0);

        // Show the current scale next to the buttons.
        String scaleStr = String.format("%.2f×", PocketHudConfig.getScale());
        drawCenteredString(this.fontRenderer, "Scale: " + scaleStr,
            this.width / 2, this.height - 100 + 28, 0xFFD0D0D0);

        super.drawScreen(mouseX, mouseY, partialTicks);

        // Render a preview of the actual HUD using the same routine the live overlay
        // uses, so the editor shows exactly what's about to be saved.
        if (PocketHudConfig.isEnabled() && this.mc.player != null) {
            PocketInventory pocket = PocketInventory.get(this.mc.player);
            if (pocket != null) {
                ScaledResolution sr = new ScaledResolution(this.mc);
                int[] origin = PocketHudOverlay.computeOrigin(sr.getScaledWidth(), sr.getScaledHeight());
                previewRenderer.renderSlotsAt(pocket, origin[0], origin[1],
                    PocketHudConfig.getScale());

                // Draw a faint outline around the HUD so the player can see what to grab
                // even when slots are empty.
                int w = (int) (PocketHudOverlay.BLOCK_W * PocketHudConfig.getScale());
                int h = (int) (PocketHudOverlay.BLOCK_H * PocketHudConfig.getScale());
                int color = dragging ? 0xFFFFFF80 : 0x80FFFFFF;
                drawRect(origin[0] - 1, origin[1] - 1, origin[0] + w + 1, origin[1], color);
                drawRect(origin[0] - 1, origin[1] + h, origin[0] + w + 1, origin[1] + h + 1, color);
                drawRect(origin[0] - 1, origin[1], origin[0], origin[1] + h, color);
                drawRect(origin[0] + w, origin[1], origin[0] + w + 1, origin[1] + h, color);
            }
        }
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        super.mouseClicked(mouseX, mouseY, mouseButton);
        if (mouseButton != 0 || !PocketHudConfig.isEnabled()) {
            return;
        }
        // Start dragging only if the click landed inside the HUD's bounding rect.
        ScaledResolution sr = new ScaledResolution(this.mc);
        int[] origin = PocketHudOverlay.computeOrigin(sr.getScaledWidth(), sr.getScaledHeight());
        int w = (int) (PocketHudOverlay.BLOCK_W * PocketHudConfig.getScale());
        int h = (int) (PocketHudOverlay.BLOCK_H * PocketHudConfig.getScale());
        if (mouseX >= origin[0] && mouseX < origin[0] + w
            && mouseY >= origin[1] && mouseY < origin[1] + h) {
            dragging = true;
            dragOffsetX = mouseX - origin[0];
            dragOffsetY = mouseY - origin[1];
        }
    }

    @Override
    protected void mouseClickMove(int mouseX, int mouseY, int clickedMouseButton,
                                  long timeSinceLastClick) {
        if (!dragging) {
            return;
        }
        ScaledResolution sr = new ScaledResolution(this.mc);
        int screenW = sr.getScaledWidth();
        int screenH = sr.getScaledHeight();
        int w = (int) (PocketHudOverlay.BLOCK_W * PocketHudConfig.getScale());
        int h = (int) (PocketHudOverlay.BLOCK_H * PocketHudConfig.getScale());

        // Compute new top-left pixel from mouse + offset, then translate back to a
        // fractional anchor over (screen - block) so the saved config survives a window
        // resize.
        int newX = mouseX - dragOffsetX;
        int newY = mouseY - dragOffsetY;
        int rangeX = Math.max(1, screenW - w);
        int rangeY = Math.max(1, screenH - h);
        PocketHudConfig.setAnchor(newX / (float) rangeX, newY / (float) rangeY);
    }

    @Override
    protected void mouseReleased(int mouseX, int mouseY, int state) {
        super.mouseReleased(mouseX, mouseY, state);
        dragging = false;
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
