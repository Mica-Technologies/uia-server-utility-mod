package com.micatechnologies.minecraft.sum.shop;

import com.micatechnologies.minecraft.sum.atm.SumNetwork;
import com.micatechnologies.minecraft.sum.economy.EconomyBridge;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
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

    /** Text column right of the item icon, and the width it has before the Buy button. */
    private static final int NAME_X = 32;
    private static final int NAME_W = 74;
    private static final int NAME_LINES = 2;
    private static final int LINE_H = 10;
    private static final String ELLIPSIS = "...";

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
        // Top-aligned with the item name column so the price row below (y=42) stays clear of it
        // even at the maximum $1,000,000.00.
        this.buttonList.add(new GuiButton(BTN_BUY, guiLeft + 110, guiTop + 20, 58, 20, "Buy"));
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        if (button.id == BTN_BUY) {
            SumNetwork.CHANNEL.sendToServer(new PacketShopBuy(shop.getPos()));
        } else {
            super.actionPerformed(button);
        }
    }

    /**
     * Adds a full-name tooltip over the item icon and its text column. Two lines covers most
     * display names, but not every one — hovering is the escape hatch for the rest, and it is
     * what a player expects from an item icon anyway.
     */
    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        super.drawScreen(mouseX, mouseY, partialTicks);
        ItemStack template = shop.getSaleTemplate();
        if (!template.isEmpty()
            && mouseX >= guiLeft + 10 && mouseX < guiLeft + NAME_X + NAME_W
            && mouseY >= guiTop + 20 && mouseY < guiTop + 20 + NAME_LINES * LINE_H) {
            drawHoveringText(Collections.singletonList(template.getDisplayName()), mouseX, mouseY);
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
        // Title row — a server shop has no owner to name.
        if (shop.isServerShop()) {
            drawString(this.fontRenderer, "Server Shop", 8, 6, TEXT_TITLE);
        } else {
            drawString(this.fontRenderer, "Shop", 8, 6, TEXT_TITLE);
            drawString(this.fontRenderer,
                fit("Owner: " + shop.getOwnerName(), GUI_W - 8 - NAME_X), NAME_X, 6, TEXT_DIM);
        }

        ItemStack template = shop.getSaleTemplate();
        if (!template.isEmpty()) {
            // Render the floating item icon at (10, 20)
            RenderHelper.enableGUIStandardItemLighting();
            this.itemRender.zLevel = 100.0F;
            this.itemRender.renderItemAndEffectIntoGUI(template, 10, 20);
            this.itemRender.zLevel = 0.0F;
            RenderHelper.disableStandardItemLighting();

            drawWrapped(template.getDisplayName(), NAME_X, 20, NAME_W, NAME_LINES, TEXT_VALUE);
            // Price sits below the icon rather than beside it: on its own row it has the full
            // panel width instead of competing with the name for the gap before the Buy button.
            drawString(this.fontRenderer,
                "x" + shop.getSaleAmount() + " for $"
                + String.format(Locale.ROOT, "%.2f", shop.getSaleCost()),
                10, 42, TEXT_VALUE);
        } else {
            drawString(this.fontRenderer, "Nothing for sale.", 10, 26, TEXT_BAD);
        }

        // Stock status
        String stockStr = shop.isInfiniteStock()
            ? "Always in stock"
            : ("In stock: " + shop.getStockCount());
        int stockColor = (shop.isInfiniteStock() || shop.hasEnoughStock()) ? TEXT_OK : TEXT_BAD;
        drawString(this.fontRenderer, stockStr, 8, 52, stockColor);

        // Balance
        if (EconomyBridge.isAvailable()) {
            double balance = EconomyBridge.getBalance(player);
            String balStr = Double.isNaN(balance) ? "—" : String.format(Locale.ROOT, "%.2f", balance);
            drawString(this.fontRenderer, "Balance: $" + balStr, 8, 62, TEXT_DIM);
        }

        // Inventory label (vanilla y=72)
        drawString(this.fontRenderer, "Inventory", 8, 72, TEXT_DIM);
    }

    /**
     * Draws {@code text} over at most {@code maxLines} lines of {@code maxWidth} pixels,
     * ellipsising the last one if it still doesn't fit. Item display names are arbitrary length
     * — modded tools and anything renamed on an anvil run long — and the Buy button sits
     * immediately to the right, so drawn as one unbroken string they overrun both it and the
     * edge of the panel.
     */
    private void drawWrapped(String text, int x, int y, int maxWidth, int maxLines, int colour) {
        List<String> lines = this.fontRenderer.listFormattedStringToWidth(text, maxWidth);
        for (int i = 0; i < lines.size() && i < maxLines; i++) {
            boolean truncating = i == maxLines - 1 && lines.size() > maxLines;
            drawString(this.fontRenderer, truncating ? ellipsise(lines.get(i), maxWidth)
                : lines.get(i), x, y + i * LINE_H, colour);
        }
    }

    /** Single-line trim for text that has no room to wrap at all. */
    private String fit(String text, int maxWidth) {
        return this.fontRenderer.getStringWidth(text) <= maxWidth
            ? text : ellipsise(text, maxWidth);
    }

    private String ellipsise(String text, int maxWidth) {
        int room = maxWidth - this.fontRenderer.getStringWidth(ELLIPSIS);
        return this.fontRenderer.trimStringToWidth(text, Math.max(0, room)).trim() + ELLIPSIS;
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
