package com.micatechnologies.minecraft.sum.jobs;

import com.micatechnologies.minecraft.sum.atm.SumNetwork;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.resources.I18n;
import net.minecraft.entity.player.EntityPlayer;

/**
 * Job-board browse GUI. Lists active listings (paginated 4 per page) with role- and
 * status-aware action buttons:
 *
 * <ul>
 *   <li>Poster sees Cancel (OPEN/CLAIMED) or Pay / Reject (SUBMITTED).</li>
 *   <li>A non-poster sees Accept (OPEN); the claiming worker sees Done / Drop (CLAIMED) or
 *       Drop (SUBMITTED).</li>
 * </ul>
 *
 * <p>The listing snapshot is pushed by the server ({@link PacketOpenJobBoard}); after any action
 * the server re-pushes, so {@link #updateListings} keeps the open screen current without a manual
 * close/reopen.
 */
public class GuiJobBoard extends GuiScreen {

    private static final int GUI_W = 232;
    private static final int GUI_H = 222;
    private static final int LISTINGS_PER_PAGE = 4;
    private static final int LISTING_HEIGHT = 42;
    private static final int ACTION_BTN_W = 52;

    private static final int BG_OUTER = 0xFF1A1D22;
    private static final int BG_INNER = 0xFF40444C;
    private static final int LISTING_BG = 0xFF2A2D34;
    private static final int LISTING_BG_HOVER = 0xFF353944;
    private static final int TEXT_TITLE = 0xFFE0E0E0;
    private static final int TEXT_VALUE = 0xFFD4B258;
    private static final int TEXT_DIM = 0xFF9098A0;
    private static final int TEXT_HINT = 0xFFA0B870;
    private static final int STATUS_OPEN = 0xFF7AC07A;
    private static final int STATUS_CLAIMED = 0xFFD4B258;
    private static final int STATUS_SUBMITTED = 0xFFE0905A;

    private static final int BUTTON_PREV = 200;
    private static final int BUTTON_NEXT = 201;
    private static final int BUTTON_CLOSE = 202;
    private static final int ACTION_BTN_BASE = 100;   // id = base + row*2 + slot

    private final EntityPlayer player;
    private List<JobListing> listings;
    private int page = 0;
    private int guiLeft;
    private int guiTop;

    /** Maps an action-button id to {global listing index, PacketJobAction action}. */
    private final Map<Integer, int[]> actionButtons = new HashMap<>();

    public GuiJobBoard(EntityPlayer player, List<JobListing> listings) {
        this.player = player;
        this.listings = listings;
    }

    /** Server pushed a fresh snapshot (after an action) — swap the list and rebuild. */
    public void updateListings(List<JobListing> updated) {
        this.listings = updated;
        int maxPage = Math.max(0, (updated.size() - 1) / LISTINGS_PER_PAGE);
        if (page > maxPage) page = maxPage;
        initGui();
    }

    @Override
    public void initGui() {
        super.initGui();
        this.guiLeft = (this.width - GUI_W) / 2;
        this.guiTop = (this.height - GUI_H) / 2;
        this.buttonList.clear();
        this.actionButtons.clear();

        this.buttonList.add(new GuiButton(BUTTON_PREV, guiLeft + 8, guiTop + GUI_H - 26, 60, 20, "< Prev"));
        this.buttonList.add(new GuiButton(BUTTON_NEXT, guiLeft + GUI_W - 68, guiTop + GUI_H - 26, 60, 20, "Next >"));
        this.buttonList.add(new GuiButton(BUTTON_CLOSE, guiLeft + (GUI_W - 60) / 2, guiTop + GUI_H - 26, 60, 20, "Close"));

        int start = page * LISTINGS_PER_PAGE;
        for (int i = 0; i < LISTINGS_PER_PAGE && start + i < listings.size(); i++) {
            JobListing l = listings.get(start + i);
            List<Act> acts = actionsFor(l);
            int rowY = guiTop + 30 + i * LISTING_HEIGHT;
            int bx = guiLeft + GUI_W - 8 - ACTION_BTN_W;
            for (int slot = 0; slot < acts.size() && slot < 2; slot++) {
                int id = ACTION_BTN_BASE + i * 2 + slot;
                int by = rowY + 2 + slot * 18;
                this.buttonList.add(new GuiButton(id, bx, by, ACTION_BTN_W, 16, acts.get(slot).label));
                actionButtons.put(id, new int[]{start + i, acts.get(slot).action});
            }
        }

        this.buttonList.get(0).enabled = page > 0;
        this.buttonList.get(1).enabled = (page + 1) * LISTINGS_PER_PAGE < listings.size();
    }

    /** The action buttons a listing offers the viewing player, given role and status. */
    private List<Act> actionsFor(JobListing l) {
        List<Act> acts = new ArrayList<>();
        UUID me = player.getUniqueID();
        boolean poster = l.isPoster(me);
        boolean worker = l.isWorker(me);
        switch (l.status) {
            case OPEN:
                if (poster) acts.add(new Act("Cancel", PacketJobAction.ACTION_REMOVE));
                else acts.add(new Act("Accept", PacketJobAction.ACTION_ACCEPT));
                break;
            case CLAIMED:
                if (poster) {
                    acts.add(new Act("Cancel", PacketJobAction.ACTION_REMOVE));
                } else if (worker) {
                    acts.add(new Act("Done", PacketJobAction.ACTION_SUBMIT));
                    acts.add(new Act("Drop", PacketJobAction.ACTION_ABANDON));
                }
                break;
            case SUBMITTED:
                if (poster) {
                    acts.add(new Act("Pay", PacketJobAction.ACTION_APPROVE));
                    acts.add(new Act("Reject", PacketJobAction.ACTION_REJECT));
                } else if (worker) {
                    acts.add(new Act("Drop", PacketJobAction.ACTION_ABANDON));
                }
                break;
            default:
                break;
        }
        return acts;
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
        int[] target = actionButtons.get(button.id);
        if (target != null && target[0] < listings.size()) {
            JobListing l = listings.get(target[0]);
            // Fire-and-forget: the server validates, mutates escrow, and re-pushes a snapshot
            // that lands in updateListings(), so we don't mutate the local list here.
            SumNetwork.CHANNEL.sendToServer(new PacketJobAction(target[1], l.id));
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

        // Reward (gold) + poster (dim) on the top line.
        String rewardLine = "$" + String.format(Locale.ROOT, "%.2f", l.reward);
        drawString(this.fontRenderer, rewardLine, x + 6, y + 4, TEXT_VALUE);
        drawString(this.fontRenderer, "by " + l.posterName,
            x + 6 + this.fontRenderer.getStringWidth(rewardLine) + 8, y + 4, TEXT_DIM);

        // Description, trimmed to leave room for the action buttons on the right.
        int maxWidth = w - ACTION_BTN_W - 16;
        String desc = this.fontRenderer.trimStringToWidth(l.description, maxWidth);
        drawString(this.fontRenderer, desc, x + 6, y + 16, TEXT_TITLE);

        // Status word + worker name, drawn under the buttons' left edge.
        int statusX = x + 6;
        String statusWord;
        int statusColor;
        switch (l.status) {
            case CLAIMED:   statusWord = "CLAIMED";  statusColor = STATUS_CLAIMED; break;
            case SUBMITTED: statusWord = "REVIEW";   statusColor = STATUS_SUBMITTED; break;
            case OPEN:
            default:        statusWord = "OPEN";     statusColor = STATUS_OPEN; break;
        }
        drawString(this.fontRenderer, statusWord, statusX, y + 28, statusColor);
        if (l.workerUuid != null && !l.workerName.isEmpty()) {
            drawString(this.fontRenderer, "→ " + l.workerName,
                statusX + this.fontRenderer.getStringWidth(statusWord) + 8, y + 28, TEXT_DIM);
        } else {
            drawString(this.fontRenderer, formatExpiry(l), statusX
                + this.fontRenderer.getStringWidth(statusWord) + 8, y + 28, TEXT_HINT);
        }
    }

    private static String formatExpiry(JobListing l) {
        return JobListing.formatRemaining(l.expiresAt - System.currentTimeMillis());
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    /** A single action button offered for a listing. */
    private static final class Act {
        final String label;
        final int action;

        Act(String label, int action) {
            this.label = label;
            this.action = action;
        }
    }
}
