package com.micatechnologies.minecraft.sum.jobs;

import com.micatechnologies.minecraft.sum.atm.SumNetwork;
import java.io.IOException;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.resources.I18n;
import net.minecraft.entity.player.EntityPlayer;

/**
 * Job-board browse GUI. Lists active listings, paginated 4 per page, with prev/next page
 * arrows. Each listing has a Remove button visible only to the original poster (so anyone
 * can browse but only you can pull down your own postings).
 */
public class GuiJobBoard extends GuiScreen {

    private static final int GUI_W = 232;
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

    private static final int BUTTON_PREV = 200;
    private static final int BUTTON_NEXT = 201;
    private static final int BUTTON_CLOSE = 202;
    private static final int BUTTON_REMOVE_BASE = 100;  // +0..3 for the four visible listings

    private final EntityPlayer player;
    private List<JobListing> listings;
    private int page = 0;
    private int guiLeft;
    private int guiTop;

    public GuiJobBoard(EntityPlayer player, List<JobListing> listings) {
        this.player = player;
        this.listings = listings;
    }

    @Override
    public void initGui() {
        super.initGui();
        this.guiLeft = (this.width - GUI_W) / 2;
        this.guiTop = (this.height - GUI_H) / 2;
        this.buttonList.clear();

        // Page nav at bottom
        this.buttonList.add(new GuiButton(BUTTON_PREV, guiLeft + 8, guiTop + GUI_H - 26, 60, 20, "< Prev"));
        this.buttonList.add(new GuiButton(BUTTON_NEXT, guiLeft + GUI_W - 68, guiTop + GUI_H - 26, 60, 20, "Next >"));
        this.buttonList.add(new GuiButton(BUTTON_CLOSE, guiLeft + (GUI_W - 60) / 2, guiTop + GUI_H - 26, 60, 20, "Close"));

        // Per-listing Remove buttons (only for the original poster)
        int start = page * LISTINGS_PER_PAGE;
        for (int i = 0; i < LISTINGS_PER_PAGE && start + i < listings.size(); i++) {
            JobListing l = listings.get(start + i);
            if (l.posterUuid.equals(player.getUniqueID())) {
                int rowY = guiTop + 30 + i * LISTING_HEIGHT;
                this.buttonList.add(new GuiButton(BUTTON_REMOVE_BASE + i,
                    guiLeft + GUI_W - 68, rowY + 4, 60, 16, "Remove"));
            }
        }

        // Disable nav buttons when at edges
        this.buttonList.get(0).enabled = page > 0;  // prev
        this.buttonList.get(1).enabled = (page + 1) * LISTINGS_PER_PAGE < listings.size();  // next
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
            if ((page + 1) * LISTINGS_PER_PAGE < listings.size()) { page++; initGui(); }
            return;
        }
        if (button.id >= BUTTON_REMOVE_BASE && button.id < BUTTON_REMOVE_BASE + LISTINGS_PER_PAGE) {
            int idx = page * LISTINGS_PER_PAGE + (button.id - BUTTON_REMOVE_BASE);
            if (idx < listings.size()) {
                JobListing l = listings.get(idx);
                SumNetwork.CHANNEL.sendToServer(new PacketJobAction(
                    PacketJobAction.ACTION_REMOVE, l.id));
                // Optimistically remove from local list and re-init the GUI.
                listings.remove(idx);
                if (page > 0 && page * LISTINGS_PER_PAGE >= listings.size()) page--;
                initGui();
            }
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        this.drawDefaultBackground();
        drawRect(guiLeft, guiTop, guiLeft + GUI_W, guiTop + GUI_H, BG_OUTER);
        drawRect(guiLeft + 3, guiTop + 3, guiLeft + GUI_W - 3, guiTop + GUI_H - 3, BG_INNER);

        drawCenteredString(this.fontRenderer, I18n.format("sum.jobboard.title"),
            guiLeft + GUI_W / 2, guiTop + 8, TEXT_TITLE);

        if (listings.isEmpty()) {
            drawCenteredString(this.fontRenderer, "No listings posted.",
                guiLeft + GUI_W / 2, guiTop + 60, TEXT_DIM);
            List<String> hintLines = this.fontRenderer.listFormattedStringToWidth(
                "Post one with /sum job post <reward> <description>.", GUI_W - 24);
            int hintY = guiTop + 76;
            for (String line : hintLines) {
                drawCenteredString(this.fontRenderer, line,
                    guiLeft + GUI_W / 2, hintY, TEXT_HINT);
                hintY += this.fontRenderer.FONT_HEIGHT + 1;
            }
        } else {
            int start = page * LISTINGS_PER_PAGE;
            int end = Math.min(start + LISTINGS_PER_PAGE, listings.size());
            for (int i = start; i < end; i++) {
                drawListing(listings.get(i), i - start, mouseX, mouseY);
            }
            // Page indicator
            int totalPages = (listings.size() + LISTINGS_PER_PAGE - 1) / LISTINGS_PER_PAGE;
            String pageStr = "Page " + (page + 1) + " / " + totalPages;
            drawCenteredString(this.fontRenderer, pageStr,
                guiLeft + GUI_W / 2, guiTop + GUI_H - 40, TEXT_DIM);
        }

        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    private void drawListing(JobListing l, int rowIndex, int mouseX, int mouseY) {
        int x = guiLeft + 8;
        int y = guiTop + 30 + rowIndex * LISTING_HEIGHT;
        int w = GUI_W - 16;
        int h = LISTING_HEIGHT - 4;

        boolean hover = mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
        drawRect(x, y, x + w, y + h, hover ? LISTING_BG_HOVER : LISTING_BG);

        // Reward (gold) on the left, poster name (dim) at top-right.
        String rewardLine = "$" + String.format(Locale.ROOT, "%.2f", l.reward);
        drawString(this.fontRenderer, rewardLine, x + 6, y + 4, TEXT_VALUE);
        drawString(this.fontRenderer, "by " + l.posterName,
            x + 6 + this.fontRenderer.getStringWidth(rewardLine) + 8, y + 4, TEXT_DIM);

        // Description, truncated to fit
        String desc = l.description;
        int maxWidth = w - 80;  // leave room for the Remove button
        desc = this.fontRenderer.trimStringToWidth(desc, maxWidth);
        drawString(this.fontRenderer, desc, x + 6, y + 16, TEXT_TITLE);

        // Time-remaining hint
        long remainingMs = l.expiresAt - System.currentTimeMillis();
        String expires;
        if (remainingMs <= 0) {
            expires = "expired";
        } else if (remainingMs < 60_000L) {
            expires = "<1m left";
        } else if (remainingMs < 3_600_000L) {
            expires = (remainingMs / 60_000L) + "m left";
        } else if (remainingMs < 86_400_000L) {
            expires = (remainingMs / 3_600_000L) + "h left";
        } else {
            expires = (remainingMs / 86_400_000L) + "d left";
        }
        drawString(this.fontRenderer, expires, x + 6, y + 28, TEXT_HINT);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
