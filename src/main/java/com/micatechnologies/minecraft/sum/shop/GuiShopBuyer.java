package com.micatechnologies.minecraft.sum.shop;

import com.micatechnologies.minecraft.sum.atm.SumNetwork;
import com.micatechnologies.minecraft.sum.economy.EconomyBridge;
import java.io.IOException;
import java.util.Locale;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;

/**
 * Buyer-side shop GUI. Item icon + name + per-sale count + price on the left, Buy button on
 * the right, stock status and player balance below. The actual transaction runs server-side
 * via {@link PacketShopBuy}.
 */
public class GuiShopBuyer extends GuiContainer {

    private static final int GUI_W = 176;
    private static final int GUI_H = 188;

    private static final int BG_OUTER = 0xFF1A1D22;
    private static final int BG_INNER = 0xFF40444C;
    private static final int TEXT_TITLE = 0xFFE0E0E0;
    private static final int TEXT_VALUE = 0xFFD4B258;
    private static final int TEXT_DIM = 0xFF9098A0;
    private static final int TEXT_OK = 0xFF8AD888;
    private static final int TEXT_BAD = 0xFFE07070;

    private static final int BTN_BUY = 200;

    private final TileEntityShop shop;
    private final EntityPlayer player;

    public GuiShopBuyer(ContainerShopBuyer container, EntityPlayer player) {
        super(container);
        this.shop = container.getShop();
        this.player = player;
        this.xSize = GUI_W;
        this.ySize = GUI_H;
    }

    @Override
    public void initGui() {
        super.initGui();
        this.buttonList.clear();
        this.buttonList.add(new GuiButton(BTN_BUY, guiLeft + 110, guiTop + 24, 58, 20, "Buy"));
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        if (button.id == BTN_BUY) {
            SumNetwork.CHANNEL.sendToServer(new PacketShopBuy(shop.getPos()));
        } else {
            super.actionPerformed(button);
        }
    }

    @Override
    protected void drawGuiContainerBackgroundLayer(float partialTicks, int mouseX, int mouseY) {
        drawRect(guiLeft, guiTop, guiLeft + GUI_W, guiTop + GUI_H, BG_OUTER);
        drawRect(guiLeft + 3, guiTop + 3, guiLeft + GUI_W - 3, guiTop + GUI_H - 3, BG_INNER);

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                drawSlotFrame(guiLeft + 7 + col * 18, guiTop + 83 + row * 18);
            }
        }
        for (int col = 0; col < 9; col++) {
            drawSlotFrame(guiLeft + 7 + col * 18, guiTop + 141);
        }
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
    }

    private void drawSlotFrame(int x, int y) {
        drawRect(x, y, x + 18, y + 18, 0xFF202326);
        drawRect(x + 1, y + 1, x + 17, y + 17, 0xFF303338);
    }

    @Override
    protected void drawGuiContainerForegroundLayer(int mouseX, int mouseY) {
        // Title row
        drawString(this.fontRenderer, "Shop", 8, 6, TEXT_TITLE);
        drawString(this.fontRenderer, "Owner: " + shop.getOwnerName(),
            32, 6, TEXT_DIM);

        ItemStack template = shop.getSaleTemplate();
        if (!template.isEmpty()) {
            // Render the floating item icon at (10, 22)
            RenderHelper.enableGUIStandardItemLighting();
            this.itemRender.zLevel = 100.0F;
            this.itemRender.renderItemAndEffectIntoGUI(template, 10, 22);
            this.itemRender.zLevel = 0.0F;
            RenderHelper.disableStandardItemLighting();

            String name = template.getDisplayName();
            drawString(this.fontRenderer, name, 32, 22, TEXT_VALUE);
            drawString(this.fontRenderer,
                "x" + shop.getSaleAmount() + " for $"
                + String.format(Locale.ROOT, "%.2f", shop.getSaleCost()),
                32, 32, TEXT_VALUE);
        } else {
            drawString(this.fontRenderer, "Nothing for sale.", 10, 26, TEXT_BAD);
        }

        // Stock status
        String stockStr = shop.isInfiniteStock()
            ? "Always in stock"
            : ("In stock: " + shop.getStockCount());
        int stockColor = (shop.isInfiniteStock() || shop.hasEnoughStock()) ? TEXT_OK : TEXT_BAD;
        drawString(this.fontRenderer, stockStr, 8, 50, stockColor);

        // Balance
        if (EconomyBridge.isAvailable()) {
            double balance = EconomyBridge.getBalance(player);
            String balStr = Double.isNaN(balance) ? "—" : String.format(Locale.ROOT, "%.2f", balance);
            drawString(this.fontRenderer, "Balance: $" + balStr, 8, 60, TEXT_DIM);
        }

        // Inventory label (vanilla y=72)
        drawString(this.fontRenderer, "Inventory", 8, 72, TEXT_DIM);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
