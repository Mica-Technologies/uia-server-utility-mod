package com.micatechnologies.minecraft.sum.plots;

import com.micatechnologies.minecraft.sum.atm.SumNetwork;
import com.micatechnologies.minecraft.sum.economy.EconomyBridge;
import java.io.IOException;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.math.BlockPos;

/**
 * Plot-browser GUI. Lists FOR_SALE plots in the player's current dimension (filtered
 * server-side; the {@link PlotSnapshot}s arrive via {@link PacketOpenPlotBrowser}),
 * paginated 4 per page with a Buy button per row.
 *
 * <p>Visual + interaction structure mirrors {@code GuiJobBoard}. The big differences:
 * the data is push-from-server (not read from a client-visible saved-data) and the
 * server re-pushes a refreshed list after a successful buy, so {@link #updateSnapshots}
 * patches the live screen in place rather than the user having to reopen it.
 */
public class GuiPlotBrowser extends GuiScreen {

    private static final int GUI_W = 256;
    private static final int GUI_H = 222;
    private static final int LISTINGS_PER_PAGE = 4;
    private static final int LISTING_HEIGHT = 42;

    private static final int BG_OUTER = 0xFF1A1D22;
    private static final int BG_INNER = 0xFF40444C;
    private static final int LISTING_BG = 0xFF2A2D34;
    private static final int LISTING_BG_HOVER = 0xFF353944;
    private static final int TEXT_TITLE = 0xFFE0E0E0;
    private static final int TEXT_VALUE = 0xFFD4B258;
    private static final int TEXT_DIM = 0xFF9098A0;
    private static final int TEXT_HINT = 0xFFA0B870;
    private static final int TEXT_AFFORD = 0xFFA0E070;
    private static final int TEXT_UNAFFORD = 0xFFE07070;

    private static final int BUTTON_PREV = 200;
    private static final int BUTTON_NEXT = 201;
    private static final int BUTTON_CLOSE = 202;
    private static final int BUTTON_BUY_BASE = 100;  // +0..3 for the four visible rows

    private final EntityPlayer player;
    private List<PlotSnapshot> snapshots;
    private int page = 0;
    private int guiLeft;
    private int guiTop;

    public GuiPlotBrowser(EntityPlayer player, List<PlotSnapshot> snapshots) {
        this.player = player;
        this.snapshots = snapshots;
    }

    /** Called when the server re-pushes a refreshed snapshot list (e.g. after a buy). */
    public void updateSnapshots(List<PlotSnapshot> snapshots) {
        this.snapshots = snapshots;
        // Clamp page in case the buy removed the last row on the current page.
        int maxPage = Math.max(0, (snapshots.size() - 1) / LISTINGS_PER_PAGE);
        if (page > maxPage) page = maxPage;
        initGui();
    }

    @Override
    public void initGui() {
        super.initGui();
        this.guiLeft = (this.width - GUI_W) / 2;
        this.guiTop = (this.height - GUI_H) / 2;
        this.buttonList.clear();

        this.buttonList.add(new GuiButton(BUTTON_PREV, guiLeft + 8,
            guiTop + GUI_H - 26, 60, 20, "< Prev"));
        this.buttonList.add(new GuiButton(BUTTON_CLOSE, guiLeft + (GUI_W - 60) / 2,
            guiTop + GUI_H - 26, 60, 20, "Close"));
        this.buttonList.add(new GuiButton(BUTTON_NEXT, guiLeft + GUI_W - 68,
            guiTop + GUI_H - 26, 60, 20, "Next >"));

        // Per-row Buy buttons.
        int start = page * LISTINGS_PER_PAGE;
        for (int i = 0; i < LISTINGS_PER_PAGE && start + i < snapshots.size(); i++) {
            int rowY = guiTop + 30 + i * LISTING_HEIGHT;
            GuiButton buy = new GuiButton(BUTTON_BUY_BASE + i,
                guiLeft + GUI_W - 68, rowY + 4, 60, 16, "Buy");
            // Gray out if the player can't afford it. The capability sync keeps the
            // client-side balance reasonably current.
            buy.enabled = canAfford(snapshots.get(start + i));
            this.buttonList.add(buy);
        }

        // Nav at edges.
        this.buttonList.get(0).enabled = page > 0;
        this.buttonList.get(2).enabled = (page + 1) * LISTINGS_PER_PAGE < snapshots.size();
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        if (button.id == BUTTON_CLOSE) {
            this.mc.displayGuiScreen(null);
            return;
        }
        if (button.id == BUTTON_PREV) {
            if (page > 0) { page--; initGui(); }
            return;
        }
        if (button.id == BUTTON_NEXT) {
            if ((page + 1) * LISTINGS_PER_PAGE < snapshots.size()) { page++; initGui(); }
            return;
        }
        if (button.id >= BUTTON_BUY_BASE && button.id < BUTTON_BUY_BASE + LISTINGS_PER_PAGE) {
            int idx = page * LISTINGS_PER_PAGE + (button.id - BUTTON_BUY_BASE);
            if (idx < snapshots.size()) {
                PlotSnapshot s = snapshots.get(idx);
                SumNetwork.CHANNEL.sendToServer(
                    new PacketPlotAction(PacketPlotAction.ACTION_BUY, s.plotId));
                // Disable immediately to prevent double-click; the server will push a
                // refreshed snapshot list back.
                button.enabled = false;
            }
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        this.drawDefaultBackground();
        drawRect(guiLeft, guiTop, guiLeft + GUI_W, guiTop + GUI_H, BG_OUTER);
        drawRect(guiLeft + 3, guiTop + 3, guiLeft + GUI_W - 3, guiTop + GUI_H - 3, BG_INNER);

        String title = "Plots For Sale";
        drawCenteredString(this.fontRenderer, title,
            guiLeft + GUI_W / 2, guiTop + 8, TEXT_TITLE);

        // Wallet hint top-right.
        double balance = currentBalance();
        if (!Double.isNaN(balance)) {
            String wallet = "$" + String.format(Locale.ROOT, "%.2f", balance);
            int walletX = guiLeft + GUI_W - 8 - this.fontRenderer.getStringWidth(wallet);
            drawString(this.fontRenderer, wallet, walletX, guiTop + 8, TEXT_VALUE);
        }

        if (snapshots.isEmpty()) {
            drawCenteredString(this.fontRenderer, "No plots for sale in this dimension.",
                guiLeft + GUI_W / 2, guiTop + 60, TEXT_DIM);
            List<String> hintLines = this.fontRenderer.listFormattedStringToWidth(
                "An admin can create one with /sum plots create. "
                + "Browse all plots with /sum plots list.", GUI_W - 24);
            int hintY = guiTop + 76;
            for (String line : hintLines) {
                drawCenteredString(this.fontRenderer, line,
                    guiLeft + GUI_W / 2, hintY, TEXT_HINT);
                hintY += this.fontRenderer.FONT_HEIGHT + 1;
            }
        } else {
            int start = page * LISTINGS_PER_PAGE;
            int end = Math.min(start + LISTINGS_PER_PAGE, snapshots.size());
            for (int i = start; i < end; i++) {
                drawRow(snapshots.get(i), i - start, mouseX, mouseY);
            }
            int totalPages = (snapshots.size() + LISTINGS_PER_PAGE - 1) / LISTINGS_PER_PAGE;
            String pageStr = "Page " + (page + 1) + " / " + totalPages;
            drawCenteredString(this.fontRenderer, pageStr,
                guiLeft + GUI_W / 2, guiTop + GUI_H - 40, TEXT_DIM);
        }

        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    private void drawRow(PlotSnapshot s, int rowIndex, int mouseX, int mouseY) {
        int x = guiLeft + 8;
        int y = guiTop + 30 + rowIndex * LISTING_HEIGHT;
        int w = GUI_W - 16;
        int h = LISTING_HEIGHT - 4;

        boolean hover = mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
        drawRect(x, y, x + w, y + h, hover ? LISTING_BG_HOVER : LISTING_BG);

        // Top row: name + price.
        String name = this.fontRenderer.trimStringToWidth(s.displayName, w - 90);
        drawString(this.fontRenderer, name, x + 6, y + 4, TEXT_TITLE);
        String price = "$" + String.format(Locale.ROOT, "%.2f", s.price);
        int priceColor = canAfford(s) ? TEXT_AFFORD : TEXT_UNAFFORD;
        int priceX = x + w - 74 - this.fontRenderer.getStringWidth(price);
        drawString(this.fontRenderer, price, priceX, y + 4, priceColor);

        // Middle row: coords of bbox center + volume.
        BlockPos c = s.center();
        String coords = "@" + c.getX() + ", " + c.getY() + ", " + c.getZ();
        drawString(this.fontRenderer, coords, x + 6, y + 16, TEXT_DIM);
        String vol = "vol " + formatVolume(s.volume);
        drawString(this.fontRenderer, vol,
            x + 6 + this.fontRenderer.getStringWidth(coords) + 8, y + 16, TEXT_DIM);

        // Bottom row: previous owner (if any) + distance from player.
        StringBuilder bottom = new StringBuilder();
        if (!s.ownerName.isEmpty()) {
            bottom.append("was ").append(s.ownerName);
        } else {
            bottom.append("unowned");
        }
        BlockPos pp = player.getPosition();
        int dx = pp.getX() - c.getX();
        int dz = pp.getZ() - c.getZ();
        int dist = (int) Math.round(Math.sqrt((double) dx * dx + (double) dz * dz));
        bottom.append("  •  ").append(dist).append("m away");
        drawString(this.fontRenderer, bottom.toString(), x + 6, y + 28, TEXT_HINT);
    }

    private double currentBalance() {
        // EconomyBridge picks the right backend (EconomyInc IMoney vs. SUM ISumMoney) and
        // works client-side because both capabilities are server-pushed on login / mutation.
        return EconomyBridge.getBalance(player);
    }

    private boolean canAfford(PlotSnapshot s) {
        double b = currentBalance();
        return !Double.isNaN(b) && b >= s.price;
    }

    private static String formatVolume(long v) {
        if (v < 10_000L) return Long.toString(v);
        if (v < 10_000_000L) return (v / 1_000L) + "k";
        return (v / 1_000_000L) + "m";
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
