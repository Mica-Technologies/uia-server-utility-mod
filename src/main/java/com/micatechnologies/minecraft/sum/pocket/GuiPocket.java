package com.micatechnologies.minecraft.sum.pocket;

import javax.annotation.ParametersAreNonnullByDefault;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.inventory.Slot;

/**
 * GuiContainer for the pocket slots + standard player inventory. Drawn programmatically
 * with the same primitive-rect palette as the trash-can GUI so there's no texture asset to
 * ship — keeps the visual style consistent across SUM utility GUIs.
 *
 * <p>Layout: a row of three pocket slots at the top, the standard 3×9 player inventory
 * below it, the hotbar at the bottom, and an "Edit HUD…" button in the top-right corner
 * that hops to {@link GuiPocketHudEditor}.</p>
 */
@ParametersAreNonnullByDefault
public class GuiPocket extends GuiContainer {

    private static final int GUI_W = 176;
    private static final int GUI_H = 133;

    private static final int BG_OUTER = 0xFF1A1D22;
    private static final int BG_INNER = 0xFF40444C;
    private static final int SLOT_OUTER = 0xFF202326;
    private static final int SLOT_INNER = 0xFF303338;

    private static final int TEXT_TITLE = 0xFFE0E0E0;
    private static final int TEXT_DIM = 0xFF9098A0;

    private static final int BUTTON_ID_EDIT_HUD = 0;

    private static final String[] SLOT_LABELS = {"Phone", "Debit card", "Bills"};

    public GuiPocket(ContainerPocket container) {
        super(container);
        this.xSize = GUI_W;
        this.ySize = GUI_H;
    }

    @Override
    public void initGui() {
        super.initGui();
        this.buttonList.clear();
        int btnW = 70;
        int btnH = 14;
        this.addButton(new GuiButton(BUTTON_ID_EDIT_HUD,
            this.guiLeft + this.xSize - btnW - 7, this.guiTop + 4,
            btnW, btnH, "Edit HUD…"));
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button.id == BUTTON_ID_EDIT_HUD) {
            // Closing the container before swapping screens matters: the editor is a plain
            // GuiScreen, not a GuiContainer, so leaving the container open would leave the
            // server thinking the player is still browsing slots.
            this.mc.player.closeScreen();
            this.mc.displayGuiScreen(new GuiPocketHudEditor());
        }
    }

    @Override
    protected void drawGuiContainerBackgroundLayer(float partialTicks, int mouseX, int mouseY) {
        drawRect(guiLeft, guiTop, guiLeft + GUI_W, guiTop + GUI_H, BG_OUTER);
        drawRect(guiLeft + 3, guiTop + 3, guiLeft + GUI_W - 3, guiTop + GUI_H - 3, BG_INNER);

        // Pocket slot frames.
        for (int i = 0; i < PocketInventory.SLOT_COUNT; i++) {
            int sx = guiLeft + ContainerPocket.POCKET_X + i * ContainerPocket.POCKET_STEP;
            int sy = guiTop + ContainerPocket.POCKET_Y;
            drawSlotFrame(sx - 1, sy - 1);
        }
        // Player inventory + hotbar frames mirror trash-can layout.
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                drawSlotFrame(guiLeft + 7 + col * 18, guiTop + ContainerPocket.INV_Y - 1 + row * 18);
            }
        }
        for (int col = 0; col < 9; col++) {
            drawSlotFrame(guiLeft + 7 + col * 18, guiTop + ContainerPocket.HOTBAR_Y - 1);
        }
        GlStateManager.color(1F, 1F, 1F, 1F);
    }

    private void drawSlotFrame(int x, int y) {
        drawRect(x, y, x + 18, y + 18, SLOT_OUTER);
        drawRect(x + 1, y + 1, x + 17, y + 17, SLOT_INNER);
    }

    @Override
    protected void drawGuiContainerForegroundLayer(int mouseX, int mouseY) {
        drawString(this.fontRenderer, "Pocket", 8, 6, TEXT_TITLE);
        drawString(this.fontRenderer, "Inventory", 8, ContainerPocket.INV_Y - 11, TEXT_DIM);

        // Empty-slot hover hints — once a slot has an item the vanilla item tooltip takes
        // over and these aren't drawn.
        for (int i = 0; i < PocketInventory.SLOT_COUNT && i < SLOT_LABELS.length; i++) {
            Slot slot = this.inventorySlots.inventorySlots.get(i);
            if (slot.getHasStack()) {
                continue;
            }
            int sx = this.guiLeft + slot.xPos;
            int sy = this.guiTop + slot.yPos;
            if (mouseX >= sx && mouseX < sx + 16 && mouseY >= sy && mouseY < sy + 16) {
                this.drawHoveringText(java.util.Collections.singletonList(SLOT_LABELS[i]),
                    mouseX - this.guiLeft, mouseY - this.guiTop);
            }
        }
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
