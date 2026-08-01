package com.micatechnologies.minecraft.sum.atm;

import com.micatechnologies.minecraft.sum.bank.BankService;
import com.micatechnologies.minecraft.sum.economy.WalletService;
import java.util.Locale;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.resources.I18n;
import net.minecraft.entity.player.EntityPlayer;

/**
 * The ATM screen. Operates on the player's <b>bank account</b>, showing the wallet alongside it so
 * both sides of a transfer are visible.
 *
 * <p>Withdrawals have a destination toggle. <b>To Wallet</b> moves money into spendable pocket
 * money; <b>As Cash</b> pays out a physical note. Cash is how wallet money becomes an item worth
 * carrying or handing to someone: deposit it, then withdraw it as cash.
 */
public class GuiSumAtm extends GuiScreen {

    private static final int GUI_WIDTH = 176;
    private static final int GUI_HEIGHT = 218;

    private static final int BG_OUTER = 0xFF1A1D22;
    private static final int BG_INNER = 0xFF40444C;
    private static final int TEXT_TITLE = 0xFFE0E0E0;
    private static final int TEXT_BALANCE = 0xFFD4B258;
    private static final int TEXT_WALLET = 0xFF8FC98F;
    private static final int TEXT_ERROR = 0xFFE07070;
    private static final int TEXT_DIM = 0xFF9098A0;

    private static final int BUTTON_CLOSE_ID = 0;
    private static final int BUTTON_DEPOSIT_CASH_ID = 1;
    private static final int BUTTON_DEPOSIT_WALLET_ID = 2;
    private static final int BUTTON_DESTINATION_ID = 3;

    /** Withdraw button IDs are {@code WITHDRAW_BUTTON_ID_BASE + index} into
     *  {@link Bills#WITHDRAW_DENOMINATIONS}. */
    private static final int WITHDRAW_BUTTON_ID_BASE = 100;

    private final EntityPlayer player;
    private int guiLeft;
    private int guiTop;

    /** Where a withdrawal goes. Cash by default, matching what an ATM does in the real world. */
    private boolean withdrawAsCash = true;

    private GuiButton destinationButton;

    public GuiSumAtm(EntityPlayer player) {
        this.player = player;
    }

    @Override
    public void initGui() {
        super.initGui();
        this.guiLeft = (this.width - GUI_WIDTH) / 2;
        this.guiTop = (this.height - GUI_HEIGHT) / 2;
        this.buttonList.clear();

        if (!isBankAvailable()) {
            // Single Close button only when there's nothing to do.
            int closeWidth = 80;
            this.buttonList.add(new GuiButton(BUTTON_CLOSE_ID,
                guiLeft + (GUI_WIDTH - closeWidth) / 2, guiTop + GUI_HEIGHT - 28,
                closeWidth, 20, I18n.format("sum.atm.close")));
            return;
        }

        int btnW = 50;
        int btnH = 20;
        int colGap = 4;
        int rowGap = 4;
        int rowsX = guiLeft + (GUI_WIDTH - (btnW * 3 + colGap * 2)) / 2;

        // The destination toggle sits above the denominations, since it changes what they do.
        int toggleW = 156;
        this.destinationButton = new GuiButton(BUTTON_DESTINATION_ID,
            guiLeft + (GUI_WIDTH - toggleW) / 2, guiTop + 74, toggleW, 20, destinationLabel());
        this.buttonList.add(destinationButton);

        // Withdraw buttons in a 3x2 grid: $1, $5, $10 / $20, $50, $100
        int row1Y = guiTop + 108;
        int row2Y = row1Y + btnH + rowGap;
        for (int i = 0; i < Bills.WITHDRAW_DENOMINATIONS.length; i++) {
            int denom = Bills.WITHDRAW_DENOMINATIONS[i];
            int x = rowsX + (i % 3) * (btnW + colGap);
            int y = (i < 3) ? row1Y : row2Y;
            this.buttonList.add(new GuiButton(WITHDRAW_BUTTON_ID_BASE + i, x, y, btnW, btnH,
                I18n.format("sum.atm.withdraw_button", "$" + denom)));
        }

        // Two deposit buttons side by side below the withdraw grid.
        int depositW = 76;
        int depositY = row2Y + btnH + 10;
        int depositX = guiLeft + (GUI_WIDTH - (depositW * 2 + colGap)) / 2;
        this.buttonList.add(new GuiButton(BUTTON_DEPOSIT_WALLET_ID, depositX, depositY,
            depositW, 20, I18n.format("sum.atm.deposit_wallet")));
        this.buttonList.add(new GuiButton(BUTTON_DEPOSIT_CASH_ID,
            depositX + depositW + colGap, depositY,
            depositW, 20, I18n.format("sum.atm.deposit_cash")));

        int closeWidth = 80;
        this.buttonList.add(new GuiButton(BUTTON_CLOSE_ID,
            guiLeft + (GUI_WIDTH - closeWidth) / 2, guiTop + GUI_HEIGHT - 26,
            closeWidth, 20, I18n.format("sum.atm.close")));
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button.id == BUTTON_CLOSE_ID) {
            this.mc.displayGuiScreen(null);
            return;
        }
        if (button.id == BUTTON_DESTINATION_ID) {
            withdrawAsCash = !withdrawAsCash;
            if (destinationButton != null) {
                destinationButton.displayString = destinationLabel();
            }
            return;
        }
        if (button.id == BUTTON_DEPOSIT_CASH_ID) {
            SumNetwork.CHANNEL.sendToServer(
                new AtmPacketTransaction(AtmPacketTransaction.ACTION_DEPOSIT_CASH, 0));
            return;
        }
        if (button.id == BUTTON_DEPOSIT_WALLET_ID) {
            // 0 means "the whole wallet" — the common case, and it avoids a text field.
            SumNetwork.CHANNEL.sendToServer(
                new AtmPacketTransaction(AtmPacketTransaction.ACTION_DEPOSIT_WALLET, 0));
            return;
        }
        int withdrawIdx = button.id - WITHDRAW_BUTTON_ID_BASE;
        if (withdrawIdx >= 0 && withdrawIdx < Bills.WITHDRAW_DENOMINATIONS.length) {
            int denom = Bills.WITHDRAW_DENOMINATIONS[withdrawIdx];
            int action = withdrawAsCash
                ? AtmPacketTransaction.ACTION_WITHDRAW_CASH
                : AtmPacketTransaction.ACTION_WITHDRAW_TO_WALLET;
            SumNetwork.CHANNEL.sendToServer(new AtmPacketTransaction(action, denom));
        }
    }

    private String destinationLabel() {
        return I18n.format(withdrawAsCash
            ? "sum.atm.destination.cash"
            : "sum.atm.destination.wallet");
    }

    /** The bank is usable when a balance can be read for this player. */
    private boolean isBankAvailable() {
        return !Double.isNaN(BankService.getBalance(player));
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        this.drawDefaultBackground();
        drawRect(guiLeft, guiTop, guiLeft + GUI_WIDTH, guiTop + GUI_HEIGHT, BG_OUTER);
        drawRect(guiLeft + 3, guiTop + 3, guiLeft + GUI_WIDTH - 3, guiTop + GUI_HEIGHT - 3, BG_INNER);

        drawCenteredString(this.fontRenderer, I18n.format("sum.atm.title"),
            this.width / 2, guiTop + 10, TEXT_TITLE);

        if (!isBankAvailable()) {
            drawCenteredString(this.fontRenderer, I18n.format("sum.atm.no_bank"),
                this.width / 2, guiTop + 40, TEXT_ERROR);
            drawCenteredString(this.fontRenderer, I18n.format("sum.atm.no_bank.hint"),
                this.width / 2, guiTop + 56, TEXT_DIM);
        } else {
            drawCenteredString(this.fontRenderer, I18n.format("sum.atm.balance.heading"),
                this.width / 2, guiTop + 26, TEXT_DIM);
            drawCenteredString(this.fontRenderer,
                I18n.format("sum.atm.balance.label", money(BankService.getBalance(player))),
                this.width / 2, guiTop + 38, TEXT_BALANCE);

            // The wallet is shown too: every button here moves money between the two, so seeing
            // only one side would make the result hard to predict.
            drawCenteredString(this.fontRenderer,
                I18n.format("sum.atm.wallet.label", money(WalletService.getTotal(player))),
                this.width / 2, guiTop + 54, TEXT_WALLET);

            drawCenteredString(this.fontRenderer, I18n.format("sum.atm.withdraw_heading"),
                this.width / 2, guiTop + 98, TEXT_DIM);
        }

        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    private static String money(double value) {
        return Double.isNaN(value) ? "?" : String.format(Locale.ROOT, "%,.2f", value);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
