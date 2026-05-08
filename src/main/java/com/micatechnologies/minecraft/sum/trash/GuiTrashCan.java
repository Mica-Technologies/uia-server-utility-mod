package com.micatechnologies.minecraft.sum.trash;

import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.renderer.GlStateManager;

/** 1×9 trash GUI with player inventory underneath. Single warning line: items are gone on close. */
public class GuiTrashCan extends GuiContainer {

    private static final int GUI_W = 176;
    private static final int GUI_H = 133;

    private static final int BG_OUTER = 0xFF1A1D22;
    private static final int BG_INNER = 0xFF40444C;
    private static final int TEXT_TITLE = 0xFFE0E0E0;
    private static final int TEXT_DIM = 0xFF9098A0;
    private static final int TEXT_WARN = 0xFFE0B070;

    public GuiTrashCan(ContainerTrashCan container) {
        super(container);
        this.xSize = GUI_W;
        this.ySize = GUI_H;
    }

    @Override
    protected void drawGuiContainerBackgroundLayer(float partialTicks, int mouseX, int mouseY) {
        drawRect(guiLeft, guiTop, guiLeft + GUI_W, guiTop + GUI_H, BG_OUTER);
        drawRect(guiLeft + 3, guiTop + 3, guiLeft + GUI_W - 3, guiTop + GUI_H - 3, BG_INNER);

        // Trash row
        for (int col = 0; col < ContainerTrashCan.TRASH_SLOTS; col++) {
            drawSlotFrame(guiLeft + 7 + col * 18, guiTop + 16);
        }
        // Player inv 3 rows
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                drawSlotFrame(guiLeft + 7 + col * 18, guiTop + 50 + row * 18);
            }
        }
        // Hotbar
        for (int col = 0; col < 9; col++) {
            drawSlotFrame(guiLeft + 7 + col * 18, guiTop + 108);
        }
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
    }

    private void drawSlotFrame(int x, int y) {
        drawRect(x, y, x + 18, y + 18, 0xFF202326);
        drawRect(x + 1, y + 1, x + 17, y + 17, 0xFF303338);
    }

    @Override
    protected void drawGuiContainerForegroundLayer(int mouseX, int mouseY) {
        drawString(this.fontRenderer, "Trash", 8, 6, TEXT_TITLE);
        drawString(this.fontRenderer, "Closing destroys what's inside.", 60, 6, TEXT_WARN);
        drawString(this.fontRenderer, "Inventory", 8, 39, TEXT_DIM);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
