package com.micatechnologies.minecraft.sum.bank;

import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.resources.I18n;
import net.minecraft.util.ResourceLocation;

public class GuiSafeDeposit extends GuiContainer {

    /** Vanilla 3x3 dispenser background - matches the slot layout in
     *  {@link ContainerSafeDeposit}, so we don't have to ship our own GUI texture. */
    private static final ResourceLocation TEXTURE =
        new ResourceLocation("textures/gui/container/dispenser.png");

    public GuiSafeDeposit(ContainerSafeDeposit container) {
        super(container);
        this.xSize = 176;
        this.ySize = 166;
    }

    @Override
    protected void drawGuiContainerBackgroundLayer(float partialTicks, int mouseX, int mouseY) {
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        this.mc.getTextureManager().bindTexture(TEXTURE);
        drawTexturedModalRect(this.guiLeft, this.guiTop, 0, 0, this.xSize, this.ySize);
    }

    @Override
    protected void drawGuiContainerForegroundLayer(int mouseX, int mouseY) {
        this.fontRenderer.drawString(I18n.format("sum.safe_deposit.title"), 8, 6, 0x404040);
        this.fontRenderer.drawString(I18n.format("container.inventory"),
            8, this.ySize - 96 + 2, 0x404040);
    }
}
