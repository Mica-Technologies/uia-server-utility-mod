package com.micatechnologies.minecraft.sum.shop;

import com.micatechnologies.minecraft.sum.atm.SumNetwork;
import java.io.IOException;
import java.util.Locale;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.player.EntityPlayer;

/**
 * Owner-side shop GUI. 10 inventory slots (template + 3x3 stock) + numeric +/- controls for
 * sale amount and price + a Withdraw Funds button + a sold-out / stock readout. Reuses
 * vanilla's chest-ish container background since SUM doesn't ship its own GUI texture.
 */
public class GuiShopOwner extends GuiContainer {

    private static final int GUI_W = 176;
    private static final int GUI_H = ContainerShopOwner.GUI_HEIGHT;

    private static final int BG_OUTER = 0xFF1A1D22;
    private static final int BG_INNER = 0xFF40444C;
    private static final int TEXT_TITLE = 0xFFE0E0E0;
    private static final int TEXT_VALUE = 0xFFD4B258;
    private static final int TEXT_DIM = 0xFF9098A0;
    private static final int TEXT_OK = 0xFF8AD888;
    private static final int TEXT_BAD = 0xFFE07070;

    private static final int BTN_AMOUNT_DOWN = 100;
    private static final int BTN_AMOUNT_UP = 101;
    private static final int BTN_COST_DOWN = 102;
    private static final int BTN_COST_UP = 103;
    private static final int BTN_WITHDRAW = 104;
    private static final int BTN_TOGGLE_INFINITE = 105;

    /** Per-click adjustment increments. Shift+click multiplies (handled in actionPerformed). */
    private static final int AMOUNT_STEP = 1;
    private static final double COST_STEP = 1.0;
    private static final int AMOUNT_STEP_SHIFT = 8;
    private static final double COST_STEP_SHIFT = 10.0;

    private final TileEntityShop shop;
    private final EntityPlayer player;

    public GuiShopOwner(ContainerShopOwner container, EntityPlayer player) {
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
        int left = guiLeft;
        int top = guiTop;

        // -/+ amount buttons — row at y=88, h=20, ends y=108
        this.buttonList.add(new GuiButton(BTN_AMOUNT_DOWN, left + 26, top + 88, 20, 20, "-"));
        this.buttonList.add(new GuiButton(BTN_AMOUNT_UP, left + 56, top + 88, 20, 20, "+"));

        // -/+ cost buttons — same row
        this.buttonList.add(new GuiButton(BTN_COST_DOWN, left + 100, top + 88, 20, 20, "-"));
        this.buttonList.add(new GuiButton(BTN_COST_UP, left + 130, top + 88, 20, 20, "+"));

        // Withdraw funds — y=112, h=20
        this.buttonList.add(new GuiButton(BTN_WITHDRAW, left + 8, top + 112, 80, 20, "Withdraw"));

        // Toggle infinite (admin only)
        if (player.canUseCommand(2, "sum.shop.infinite")) {
            this.buttonList.add(new GuiButton(BTN_TOGGLE_INFINITE,
                left + 92, top + 112, 76, 20,
                shop.isInfiniteStock() ? "Infinite: ON" : "Infinite: OFF"));
        }
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        boolean shift = isShiftKeyDown();
        int aStep = shift ? AMOUNT_STEP_SHIFT : AMOUNT_STEP;
        double cStep = shift ? COST_STEP_SHIFT : COST_STEP;
        switch (button.id) {
            case BTN_AMOUNT_DOWN:
                send(PacketShopOwnerAction.ACTION_SET_AMOUNT, shop.getSaleAmount() - aStep);
                break;
            case BTN_AMOUNT_UP:
                send(PacketShopOwnerAction.ACTION_SET_AMOUNT, shop.getSaleAmount() + aStep);
                break;
            case BTN_COST_DOWN:
                send(PacketShopOwnerAction.ACTION_SET_COST, shop.getSaleCost() - cStep);
                break;
            case BTN_COST_UP:
                send(PacketShopOwnerAction.ACTION_SET_COST, shop.getSaleCost() + cStep);
                break;
            case BTN_WITHDRAW:
                send(PacketShopOwnerAction.ACTION_WITHDRAW, 0);
                break;
            case BTN_TOGGLE_INFINITE:
                send(PacketShopOwnerAction.ACTION_TOGGLE_INFINITE, 0);
                break;
            default:
                super.actionPerformed(button);
        }
    }

    private void send(int action, double value) {
        SumNetwork.CHANNEL.sendToServer(
            new PacketShopOwnerAction(shop.getPos(), action, value));
    }

    @Override
    protected void drawGuiContainerBackgroundLayer(float partialTicks, int mouseX, int mouseY) {
        // Custom dark panel background instead of dispenser.png — slot positions match
        // vanilla 3x3 dispenser with one extra "template" slot to the left.
        drawRect(guiLeft, guiTop, guiLeft + GUI_W, guiTop + GUI_H, BG_OUTER);
        drawRect(guiLeft + 3, guiTop + 3, guiLeft + GUI_W - 3, guiTop + GUI_H - 3, BG_INNER);

        // Slot frames so the empty slots are visible on the dark panel.
        // Template slot
        drawSlotFrame(guiLeft + 25, guiTop + 16);
        // Stock 3x3
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                drawSlotFrame(guiLeft + 79 + col * 18, guiTop + 16 + row * 18);
            }
        }
        // Player inv 3x9 — slots start at PLAYER_INV_Y in the container; the frame
        // sits one pixel above and to the left of the slot's draw position.
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                drawSlotFrame(guiLeft + 7 + col * 18,
                    guiTop + ContainerShopOwner.PLAYER_INV_Y - 1 + row * 18);
            }
        }
        // Hotbar
        for (int col = 0; col < 9; col++) {
            drawSlotFrame(guiLeft + 7 + col * 18,
                guiTop + ContainerShopOwner.PLAYER_HOTBAR_Y - 1);
        }
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
    }

    private void drawSlotFrame(int x, int y) {
        drawRect(x, y, x + 18, y + 18, 0xFF202326);
        drawRect(x + 1, y + 1, x + 17, y + 17, 0xFF303338);
    }

    @Override
    protected void drawGuiContainerForegroundLayer(int mouseX, int mouseY) {
        // Slot-row headers (above slots, which start at y=17)
        drawString(this.fontRenderer, "Item", 26, 6, TEXT_DIM);
        drawString(this.fontRenderer, "Stock", 80, 6, TEXT_DIM);

        // Sale amount + price labels (above the +/- buttons at y=88)
        drawString(this.fontRenderer, "Per: x" + shop.getSaleAmount(), 26, 76, TEXT_VALUE);
        drawString(this.fontRenderer,
            "Price: $" + String.format(Locale.ROOT, "%.2f", shop.getSaleCost()),
            100, 76, TEXT_VALUE);

        // Funds + stock readouts under the action button row (buttons end at y=132)
        String fundsStr = "Funds: $" + String.format(Locale.ROOT, "%.2f", shop.getFundsAccumulated());
        drawString(this.fontRenderer, fundsStr, 8, 134, TEXT_VALUE);
        String stockStr = shop.isInfiniteStock()
            ? "Stock: ∞"
            : ("Stock: " + shop.getStockCount());
        int stockColor = (shop.isInfiniteStock() || shop.hasEnoughStock()) ? TEXT_OK : TEXT_BAD;
        drawString(this.fontRenderer, stockStr, 110, 134, stockColor);

        // Inventory label (just above player slots)
        drawString(this.fontRenderer, "Inventory", 8,
            ContainerShopOwner.PLAYER_INV_Y - 12, TEXT_DIM);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
