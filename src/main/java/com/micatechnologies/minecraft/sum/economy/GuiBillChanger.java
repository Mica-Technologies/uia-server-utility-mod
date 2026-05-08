package com.micatechnologies.minecraft.sum.economy;

import com.micatechnologies.minecraft.sum.atm.SumNetwork;
import java.io.IOException;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.renderer.GlStateManager;

/**
 * Bill-changer GUI. Two slots (input + output) with two action buttons between them.
 * Bundle compresses 64 bills into a packet; Unbundle splits a packet back into 64 bills.
 */
public class GuiBillChanger extends GuiContainer {

    private static final int GUI_W = 176;
    private static final int GUI_H = ContainerBillChanger.GUI_HEIGHT;

    private static final int BG_OUTER = 0xFF1A1D22;
    private static final int BG_INNER = 0xFF40444C;
    private static final int TEXT_TITLE = 0xFFE0E0E0;
    private static final int TEXT_DIM = 0xFF9098A0;

    private static final int BTN_BUNDLE = 100;
    private static final int BTN_UNBUNDLE = 101;

    private final TileEntityBillChanger te;

    public GuiBillChanger(ContainerBillChanger container) {
        super(container);
        this.te = container.getTileEntity();
        this.xSize = GUI_W;
        this.ySize = GUI_H;
    }

    @Override
    public void initGui() {
        super.initGui();
        this.buttonList.clear();
        this.buttonList.add(new GuiButton(BTN_BUNDLE, guiLeft + 24, guiTop + 50, 60, 20,
            "Bundle ▶"));
        this.buttonList.add(new GuiButton(BTN_UNBUNDLE, guiLeft + 92, guiTop + 50, 60, 20,
            "◀ Unbundle"));
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        if (button.id == BTN_BUNDLE) {
            SumNetwork.CHANNEL.sendToServer(new PacketBillChangerAction(
                te.getPos(), PacketBillChangerAction.ACTION_BUNDLE));
        } else if (button.id == BTN_UNBUNDLE) {
            SumNetwork.CHANNEL.sendToServer(new PacketBillChangerAction(
                te.getPos(), PacketBillChangerAction.ACTION_UNBUNDLE));
        } else {
            super.actionPerformed(button);
        }
    }

    @Override
    protected void drawGuiContainerBackgroundLayer(float partialTicks, int mouseX, int mouseY) {
        drawRect(guiLeft, guiTop, guiLeft + GUI_W, guiTop + GUI_H, BG_OUTER);
        drawRect(guiLeft + 3, guiTop + 3, guiLeft + GUI_W - 3, guiTop + GUI_H - 3, BG_INNER);

        // Slot frames for input + output (positions match ContainerBillChanger)
        drawSlotFrame(guiLeft + 52, guiTop + 23);   // input
        drawSlotFrame(guiLeft + 106, guiTop + 23);  // output

        // Player inv 3x9 + hotbar
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                drawSlotFrame(guiLeft + 7 + col * 18,
                    guiTop + ContainerBillChanger.PLAYER_INV_Y - 1 + row * 18);
            }
        }
        for (int col = 0; col < 9; col++) {
            drawSlotFrame(guiLeft + 7 + col * 18,
                guiTop + ContainerBillChanger.PLAYER_HOTBAR_Y - 1);
        }
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
    }

    private void drawSlotFrame(int x, int y) {
        drawRect(x, y, x + 18, y + 18, 0xFF202326);
        drawRect(x + 1, y + 1, x + 17, y + 17, 0xFF303338);
    }

    @Override
    protected void drawGuiContainerForegroundLayer(int mouseX, int mouseY) {
        drawString(this.fontRenderer, "Bill Changer", 8, 6, TEXT_TITLE);

        // Slot labels
        drawString(this.fontRenderer, "Input", 46, 14, TEXT_DIM);
        drawString(this.fontRenderer, "Output", 100, 14, TEXT_DIM);

        // Inventory label
        drawString(this.fontRenderer, "Inventory", 8,
            ContainerBillChanger.PLAYER_INV_Y - 12, TEXT_DIM);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
