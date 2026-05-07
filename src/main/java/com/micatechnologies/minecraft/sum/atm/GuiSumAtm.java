package com.micatechnologies.minecraft.sum.atm;

import com.micatechnologies.minecraft.sum.economy.EconomyBridge;
import java.util.Locale;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.resources.I18n;
import net.minecraft.entity.player.EntityPlayer;

public class GuiSumAtm extends GuiScreen {

    private static final int GUI_WIDTH = 176;
    private static final int GUI_HEIGHT = 200;

    private static final int BG_OUTER = 0xFF1A1D22;
    private static final int BG_INNER = 0xFF40444C;
    private static final int TEXT_TITLE = 0xFFE0E0E0;
    private static final int TEXT_BALANCE = 0xFFD4B258;
    private static final int TEXT_ERROR = 0xFFE07070;
    private static final int TEXT_DIM = 0xFF9098A0;

    private static final int BUTTON_CLOSE_ID = 0;
    private static final int BUTTON_DEPOSIT_ALL_ID = 1;
    /** Withdraw button IDs are {@code WITHDRAW_BUTTON_ID_BASE + index} into
     *  {@link Bills#WITHDRAW_DENOMINATIONS}. */
    private static final int WITHDRAW_BUTTON_ID_BASE = 100;

    private final EntityPlayer player;
    private int guiLeft;
    private int guiTop;

    public GuiSumAtm(EntityPlayer player) {
        this.player = player;
    }

    @Override
    public void initGui() {
        super.initGui();
        this.guiLeft = (this.width - GUI_WIDTH) / 2;
        this.guiTop = (this.height - GUI_HEIGHT) / 2;
        this.buttonList.clear();

        if (!EconomyBridge.isAvailable()) {
            // Single Close button only when there's nothing to do.
            int closeWidth = 80;
            this.buttonList.add(new GuiButton(BUTTON_CLOSE_ID,
                guiLeft + (GUI_WIDTH - closeWidth) / 2, guiTop + GUI_HEIGHT - 28,
                closeWidth, 20, I18n.format("sum.atm.close")));
            return;
        }

        // Withdraw buttons in a 3x2 grid: $1, $5, $10 / $20, $50, $100
        int btnW = 50;
        int btnH = 20;
        int colGap = 4;
        int rowGap = 4;
        int rowsX = guiLeft + (GUI_WIDTH - (btnW * 3 + colGap * 2)) / 2;
        int row1Y = guiTop + 80;
        int row2Y = row1Y + btnH + rowGap;
        for (int i = 0; i < Bills.WITHDRAW_DENOMINATIONS.length; i++) {
            int denom = Bills.WITHDRAW_DENOMINATIONS[i];
            int x = rowsX + (i % 3) * (btnW + colGap);
            int y = (i < 3) ? row1Y : row2Y;
            this.buttonList.add(new GuiButton(WITHDRAW_BUTTON_ID_BASE + i, x, y, btnW, btnH,
                I18n.format("sum.atm.withdraw_button", "$" + denom)));
        }

        // Deposit All — full width below the withdraw grid.
        int depositW = 156;
        this.buttonList.add(new GuiButton(BUTTON_DEPOSIT_ALL_ID,
            guiLeft + (GUI_WIDTH - depositW) / 2, row2Y + btnH + 10,
            depositW, 20, I18n.format("sum.atm.deposit_all")));

        // Close.
        int closeWidth = 80;
        this.buttonList.add(new GuiButton(BUTTON_CLOSE_ID,
            guiLeft + (GUI_WIDTH - closeWidth) / 2, guiTop + GUI_HEIGHT - 28,
            closeWidth, 20, I18n.format("sum.atm.close")));
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button.id == BUTTON_CLOSE_ID) {
            this.mc.displayGuiScreen(null);
            return;
        }
        if (button.id == BUTTON_DEPOSIT_ALL_ID) {
            SumNetwork.CHANNEL.sendToServer(
                new AtmPacketTransaction(AtmPacketTransaction.ACTION_DEPOSIT_ALL, 0));
            return;
        }
        int withdrawIdx = button.id - WITHDRAW_BUTTON_ID_BASE;
        if (withdrawIdx >= 0 && withdrawIdx < Bills.WITHDRAW_DENOMINATIONS.length) {
            int denom = Bills.WITHDRAW_DENOMINATIONS[withdrawIdx];
            SumNetwork.CHANNEL.sendToServer(
                new AtmPacketTransaction(AtmPacketTransaction.ACTION_WITHDRAW, denom));
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        this.drawDefaultBackground();
        drawRect(guiLeft, guiTop, guiLeft + GUI_WIDTH, guiTop + GUI_HEIGHT, BG_OUTER);
        drawRect(guiLeft + 3, guiTop + 3, guiLeft + GUI_WIDTH - 3, guiTop + GUI_HEIGHT - 3, BG_INNER);

        drawCenteredString(this.fontRenderer, I18n.format("sum.atm.title"),
            this.width / 2, guiTop + 10, TEXT_TITLE);

        if (!EconomyBridge.isAvailable()) {
            drawCenteredString(this.fontRenderer, I18n.format("sum.atm.no_economy_mod"),
                this.width / 2, guiTop + 40, TEXT_ERROR);
            drawCenteredString(this.fontRenderer, I18n.format("sum.atm.no_economy_mod.hint"),
                this.width / 2, guiTop + 56, TEXT_DIM);
        } else {
            double balance = EconomyBridge.getBalance(player);
            String line;
            int color;
            if (Double.isNaN(balance)) {
                line = I18n.format("sum.atm.balance.unknown");
                color = TEXT_ERROR;
            } else {
                line = I18n.format("sum.atm.balance.label",
                    String.format(Locale.ROOT, "%.2f", balance));
                color = TEXT_BALANCE;
            }
            drawCenteredString(this.fontRenderer, I18n.format("sum.atm.balance.heading"),
                this.width / 2, guiTop + 30, TEXT_DIM);
            drawCenteredString(this.fontRenderer, line, this.width / 2, guiTop + 44, color);
            drawCenteredString(this.fontRenderer, I18n.format("sum.atm.withdraw_heading"),
                this.width / 2, guiTop + 68, TEXT_DIM);
        }

        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
