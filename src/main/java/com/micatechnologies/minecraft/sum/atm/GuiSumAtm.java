package com.micatechnologies.minecraft.sum.atm;

import com.micatechnologies.minecraft.sum.bank.BankService;
import com.micatechnologies.minecraft.sum.economy.WalletService;
import com.micatechnologies.minecraft.sum.huds.snapshot.PlayerStatusSnapshot;
import com.micatechnologies.minecraft.sum.huds.snapshot.PlayerStatusTracker;
import java.io.IOException;
import java.util.Locale;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.resources.I18n;
import net.minecraft.entity.player.EntityPlayer;
import org.lwjgl.input.Keyboard;

/**
 * The ATM screen. Operates on the player's <b>bank account</b>, showing the wallet alongside it so
 * both sides of a transfer are visible.
 *
 * <p>Two ways to move money. The amount box handles any figure — withdraw $137.50 to the wallet,
 * deposit $20 of it back — while the denomination buttons stay as one-click shortcuts for the
 * common cash amounts.
 *
 * <p>Withdrawals have a destination toggle. <b>To Wallet</b> moves money into spendable pocket
 * money; <b>As Cash</b> pays out physical notes, broken into denominations the way a real cash
 * machine would. Cash is how wallet money becomes an item worth carrying or handing to someone:
 * deposit it, then withdraw it as cash.
 */
public class GuiSumAtm extends GuiScreen {

    private static final int GUI_WIDTH = 176;
    private static final int GUI_HEIGHT = 264;

    private static final int BG_OUTER = 0xFF1A1D22;
    private static final int BG_INNER = 0xFF40444C;
    private static final int TEXT_TITLE = 0xFFE0E0E0;
    private static final int TEXT_BALANCE = 0xFFD4B258;
    private static final int TEXT_WALLET = 0xFF8FC98F;
    private static final int TEXT_ERROR = 0xFFE07070;
    private static final int TEXT_DIM = 0xFF9098A0;

    private static final int BUTTON_CLOSE_ID = 0;
    private static final int BUTTON_DEPOSIT_ALL_CASH_ID = 1;
    private static final int BUTTON_DEPOSIT_ALL_WALLET_ID = 2;
    private static final int BUTTON_DESTINATION_ID = 3;
    private static final int BUTTON_WITHDRAW_AMOUNT_ID = 4;
    private static final int BUTTON_DEPOSIT_AMOUNT_ID = 5;

    /** Quick-withdraw button IDs are {@code QUICK_BUTTON_ID_BASE + index} into
     *  {@link Bills#WITHDRAW_DENOMINATIONS}. */
    private static final int QUICK_BUTTON_ID_BASE = 100;

    /** Enough for "1234567.89" and then some; the server clamps to the real balance anyway. */
    private static final int AMOUNT_MAX_LENGTH = 12;

    private final EntityPlayer player;
    private int guiLeft;
    private int guiTop;

    /** Where a withdrawal goes. Cash by default, matching what an ATM does in the real world. */
    private boolean withdrawAsCash = true;

    private GuiButton destinationButton;
    private GuiTextField amountField;

    public GuiSumAtm(EntityPlayer player) {
        this.player = player;
    }

    @Override
    public void initGui() {
        super.initGui();
        // Without this, held-down backspace deletes a single character.
        Keyboard.enableRepeatEvents(true);
        this.guiLeft = (this.width - GUI_WIDTH) / 2;
        this.guiTop = (this.height - GUI_HEIGHT) / 2;
        this.buttonList.clear();

        if (!isBankAvailable()) {
            // Single Close button only when there's nothing to do.
            int closeWidth = 80;
            this.amountField = null;
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
        int fullW = 156;
        int fullX = guiLeft + (GUI_WIDTH - fullW) / 2;

        // Destination toggle first: it changes what every withdraw control below does.
        this.destinationButton = new GuiButton(BUTTON_DESTINATION_ID,
            fullX, guiTop + 72, fullW, 20, destinationLabel());
        this.buttonList.add(destinationButton);

        // Amount box, preserving whatever was typed across a resize.
        String previous = amountField == null ? "" : amountField.getText();
        this.amountField = new GuiTextField(0, this.fontRenderer, fullX + 44, guiTop + 100,
            fullW - 44, 16);
        this.amountField.setMaxStringLength(AMOUNT_MAX_LENGTH);
        this.amountField.setText(previous);

        // Act on the typed amount.
        int halfW = (fullW - colGap) / 2;
        this.buttonList.add(new GuiButton(BUTTON_WITHDRAW_AMOUNT_ID, fullX, guiTop + 122,
            halfW, 20, I18n.format("sum.atm.withdraw_amount")));
        this.buttonList.add(new GuiButton(BUTTON_DEPOSIT_AMOUNT_ID, fullX + halfW + colGap,
            guiTop + 122, halfW, 20, I18n.format("sum.atm.deposit_amount")));

        // Quick cash: a 3x2 grid of $1, $5, $10 / $20, $50, $100.
        int row1Y = guiTop + 158;
        int row2Y = row1Y + btnH + rowGap;
        for (int i = 0; i < Bills.WITHDRAW_DENOMINATIONS.length; i++) {
            int denom = Bills.WITHDRAW_DENOMINATIONS[i];
            int x = rowsX + (i % 3) * (btnW + colGap);
            int y = (i < 3) ? row1Y : row2Y;
            this.buttonList.add(new GuiButton(QUICK_BUTTON_ID_BASE + i, x, y, btnW, btnH,
                I18n.format("sum.atm.withdraw_button", "$" + denom)));
        }

        // Deposit-everything shortcuts.
        int depositY = row2Y + btnH + 8;
        this.buttonList.add(new GuiButton(BUTTON_DEPOSIT_ALL_WALLET_ID, fullX, depositY,
            halfW, 20, I18n.format("sum.atm.deposit_all_wallet")));
        this.buttonList.add(new GuiButton(BUTTON_DEPOSIT_ALL_CASH_ID, fullX + halfW + colGap,
            depositY, halfW, 20, I18n.format("sum.atm.deposit_all_cash")));

        int closeWidth = 80;
        this.buttonList.add(new GuiButton(BUTTON_CLOSE_ID,
            guiLeft + (GUI_WIDTH - closeWidth) / 2, guiTop + GUI_HEIGHT - 26,
            closeWidth, 20, I18n.format("sum.atm.close")));
    }

    @Override
    public void onGuiClosed() {
        super.onGuiClosed();
        Keyboard.enableRepeatEvents(false);
    }

    @Override
    public void updateScreen() {
        super.updateScreen();
        if (amountField != null) {
            amountField.updateCursorCounter();
        }
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        super.mouseClicked(mouseX, mouseY, mouseButton);
        if (amountField != null) {
            amountField.mouseClicked(mouseX, mouseY, mouseButton);
        }
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (amountField != null && amountField.isFocused()) {
            if (keyCode == Keyboard.KEY_RETURN || keyCode == Keyboard.KEY_NUMPADENTER) {
                // Enter is the obvious "do the thing I typed" gesture.
                submitWithdraw();
                return;
            }
            if (keyCode == Keyboard.KEY_ESCAPE) {
                this.mc.displayGuiScreen(null);
                return;
            }
            if (isAmountChar(typedChar) || isEditingKey(keyCode)) {
                amountField.textboxKeyTyped(typedChar, keyCode);
                return;
            }
            // Swallow anything else so stray letters never land in a money field.
            return;
        }
        super.keyTyped(typedChar, keyCode);
    }

    /** Digits and a single decimal point are all a money field should accept. */
    private boolean isAmountChar(char c) {
        if (c >= '0' && c <= '9') {
            return true;
        }
        return c == '.' && !amountField.getText().contains(".");
    }

    private static boolean isEditingKey(int keyCode) {
        return keyCode == Keyboard.KEY_BACK || keyCode == Keyboard.KEY_DELETE
            || keyCode == Keyboard.KEY_LEFT || keyCode == Keyboard.KEY_RIGHT
            || keyCode == Keyboard.KEY_HOME || keyCode == Keyboard.KEY_END;
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
        if (button.id == BUTTON_WITHDRAW_AMOUNT_ID) {
            submitWithdraw();
            return;
        }
        if (button.id == BUTTON_DEPOSIT_AMOUNT_ID) {
            double amount = typedAmount();
            if (amount > 0.0) {
                send(AtmPacketTransaction.ACTION_DEPOSIT_WALLET, amount);
                clearAmount();
            }
            return;
        }
        if (button.id == BUTTON_DEPOSIT_ALL_CASH_ID) {
            send(AtmPacketTransaction.ACTION_DEPOSIT_CASH, 0.0);
            return;
        }
        if (button.id == BUTTON_DEPOSIT_ALL_WALLET_ID) {
            // A non-positive amount means "everything" on the server.
            send(AtmPacketTransaction.ACTION_DEPOSIT_WALLET, 0.0);
            return;
        }
        int quickIdx = button.id - QUICK_BUTTON_ID_BASE;
        if (quickIdx >= 0 && quickIdx < Bills.WITHDRAW_DENOMINATIONS.length) {
            send(withdrawAction(), Bills.WITHDRAW_DENOMINATIONS[quickIdx]);
        }
    }

    private void submitWithdraw() {
        double amount = typedAmount();
        if (amount > 0.0) {
            send(withdrawAction(), amount);
            clearAmount();
        }
    }

    private int withdrawAction() {
        return withdrawAsCash
            ? AtmPacketTransaction.ACTION_WITHDRAW_CASH
            : AtmPacketTransaction.ACTION_WITHDRAW_TO_WALLET;
    }

    private void send(int action, double amount) {
        SumNetwork.CHANNEL.sendToServer(new AtmPacketTransaction(action, amount));
    }

    /** @return the typed amount, or 0 when the box is empty or unparseable. */
    private double typedAmount() {
        if (amountField == null) {
            return 0.0;
        }
        String raw = amountField.getText().trim();
        if (raw.isEmpty() || ".".equals(raw)) {
            return 0.0;
        }
        try {
            return Double.parseDouble(raw);
        } catch (NumberFormatException e) {
            // The field filters input, so this only happens for something like "1." — treat it
            // as nothing typed rather than showing an error for a half-finished number.
            return 0.0;
        }
    }

    private void clearAmount() {
        if (amountField != null) {
            amountField.setText("");
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

    /**
     * The service's explanation for an unusable account, from the periodic status snapshot.
     *
     * <p>Comes over the snapshot rather than being worked out here because only the server can
     * ask the economy service why; the client has no connection to it at all.
     */
    private String bankNotice() {
        PlayerStatusSnapshot snap = PlayerStatusTracker.latest;
        return (snap == null || snap.bankNotice == null) ? "" : snap.bankNotice;
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
            // Prefer the economy service's own explanation, which usually names the exact
            // command to run. The generic fallback reads as a network fault, and the real
            // reason is nearly always that the player has not linked their account.
            String notice = bankNotice();
            if (notice.isEmpty()) {
                drawCenteredString(this.fontRenderer, I18n.format("sum.atm.no_bank.hint"),
                    this.width / 2, guiTop + 56, TEXT_DIM);
            } else {
                int y = guiTop + 56;
                for (String line : this.fontRenderer.listFormattedStringToWidth(
                        notice, GUI_WIDTH - 16)) {
                    drawCenteredString(this.fontRenderer, line, this.width / 2, y, TEXT_DIM);
                    y += 11;
                }
            }
            super.drawScreen(mouseX, mouseY, partialTicks);
            return;
        }

        drawCenteredString(this.fontRenderer, I18n.format("sum.atm.balance.heading"),
            this.width / 2, guiTop + 26, TEXT_DIM);
        drawCenteredString(this.fontRenderer,
            I18n.format("sum.atm.balance.label", money(BankService.getBalance(player))),
            this.width / 2, guiTop + 38, TEXT_BALANCE);

        // The wallet is shown too: every control here moves money between the two, so seeing
        // only one side would make the result hard to predict.
        drawCenteredString(this.fontRenderer,
            I18n.format("sum.atm.wallet.label", money(WalletService.getTotal(player))),
            this.width / 2, guiTop + 54, TEXT_WALLET);

        if (amountField != null) {
            this.fontRenderer.drawString(I18n.format("sum.atm.amount_label"),
                guiLeft + 10, guiTop + 104, TEXT_DIM);
            amountField.drawTextBox();
        }

        drawCenteredString(this.fontRenderer, I18n.format("sum.atm.quick_heading"),
            this.width / 2, guiTop + 148, TEXT_DIM);

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
