package com.micatechnologies.minecraft.sum.serverconfig;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import org.lwjgl.input.Mouse;

/**
 * Read-only viewer for the server's {@link ServerConfigSnapshot}, surfaced from a button
 * on the SUM OneConfig page so admins can sanity-check the active server settings
 * in-game without opening {@code sum.cfg} on disk.
 *
 * <p>Sections mirror the categories in {@link com.micatechnologies.minecraft.sum.SumConfig}
 * (Sleep Vote, Beaches, Server Pauser, Movement Tolerance, Loyalty, Auto Dropper,
 * World Border, Roamer Walkable, Road Runner). Scrolls with the mouse wheel; no
 * keyboard input is captured beyond ESC-to-close (vanilla GuiScreen behavior).</p>
 */
public class GuiServerConfigViewer extends GuiScreen {

    private static final int GUI_W = 380;
    private static final int GUI_H = 240;
    private static final int CONTENT_PAD = 12;
    private static final int LINE_H = 11;

    private static final int BG_OUTER = 0xFF1A1D22;
    private static final int BG_INNER = 0xFF2A2D34;
    private static final int TEXT_TITLE = 0xFFE0E0E0;
    private static final int TEXT_SECTION = 0xFFD4B258;
    private static final int TEXT_KEY = 0xFF9098A0;
    private static final int TEXT_VAL = 0xFFC8D0D8;
    private static final int TEXT_HINT = 0xFFA0B870;
    private static final int TEXT_WARN = 0xFFE0A070;

    private static final int BUTTON_CLOSE = 200;

    private int guiLeft;
    private int guiTop;
    private int scrollY;
    private int contentHeight;

    @Override
    public void initGui() {
        super.initGui();
        this.guiLeft = (this.width - GUI_W) / 2;
        this.guiTop = (this.height - GUI_H) / 2;
        this.buttonList.clear();
        this.buttonList.add(new GuiButton(BUTTON_CLOSE,
            guiLeft + (GUI_W - 60) / 2, guiTop + GUI_H - 26, 60, 20, "Close"));
        this.scrollY = 0;
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        if (button.id == BUTTON_CLOSE) {
            this.mc.displayGuiScreen(null);
        }
    }

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        int wheel = Mouse.getEventDWheel();
        if (wheel != 0) {
            // ~3 lines per notch
            int delta = wheel > 0 ? -3 * LINE_H : 3 * LINE_H;
            int max = Math.max(0, contentHeight - (GUI_H - 60));
            scrollY = Math.max(0, Math.min(max, scrollY + delta));
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        this.drawDefaultBackground();
        drawRect(guiLeft, guiTop, guiLeft + GUI_W, guiTop + GUI_H, BG_OUTER);
        drawRect(guiLeft + 3, guiTop + 24, guiLeft + GUI_W - 3, guiTop + GUI_H - 30, BG_INNER);

        drawCenteredString(this.fontRenderer, "Server Config",
            guiLeft + GUI_W / 2, guiTop + 8, TEXT_TITLE);

        ServerConfigSnapshot s = ServerConfigMirror.latest();
        Long received = ServerConfigMirror.receivedAtMs();

        // Clip the scrolling content area.
        int clipTop = guiTop + 26;
        int clipBottom = guiTop + GUI_H - 32;
        net.minecraft.client.renderer.GlStateManager.enableDepth();
        // Use scissor for a hard clip — content lines drawn outside the rect get cut.
        net.minecraft.client.renderer.GlStateManager.pushMatrix();
        org.lwjgl.opengl.GL11.glEnable(org.lwjgl.opengl.GL11.GL_SCISSOR_TEST);
        net.minecraft.client.gui.ScaledResolution sr = new net.minecraft.client.gui.ScaledResolution(this.mc);
        int sf = sr.getScaleFactor();
        int sx = (guiLeft + 3) * sf;
        int sy = (this.height - clipBottom) * sf;
        int sw = (GUI_W - 6) * sf;
        int sh = (clipBottom - clipTop) * sf;
        org.lwjgl.opengl.GL11.glScissor(sx, sy, sw, sh);

        int y = clipTop + 4 - scrollY;
        int startY = y;

        if (s == null) {
            drawString(this.fontRenderer,
                "No snapshot received yet. Log in to a server to populate.",
                guiLeft + CONTENT_PAD, y, TEXT_WARN);
            y += LINE_H * 2;
        } else {
            if (received != null) {
                String stamp = "Snapshot at "
                    + new SimpleDateFormat("HH:mm:ss").format(new Date(received))
                    + " — values reflect server state at your last login. "
                    + "Re-login after /sum reloadconfig to refresh.";
                List<String> lines = this.fontRenderer.listFormattedStringToWidth(stamp, GUI_W - CONTENT_PAD * 2);
                for (String line : lines) {
                    drawString(this.fontRenderer, line, guiLeft + CONTENT_PAD, y, TEXT_HINT);
                    y += LINE_H;
                }
                y += 4;
            }

            y = section(y, "Sleep Vote");
            y = kv(y, "Enabled", bool(s.sleepVoteEnabled));
            y = kv(y, "Threshold", s.sleepVoteThreshold + "%");

            y = section(y, "Beaches (auto-flood broken blocks near water)");
            y = kv(y, "Enabled", bool(s.beachesEnabled));
            y = kv(y, "Animated flooding", bool(s.beachesAnimated));
            y = kv(y, "Infinite-bucket water", bool(s.beachesInfiniteBucket));
            y = kv(y, "Realistic erosion", bool(s.beachesRealistic));
            y = kvList(y, "Affected blocks", s.beachesAffectedBlocks);

            y = section(y, "Server Pauser");
            y = kv(y, "Enabled", bool(s.pauserEnabled));

            y = section(y, "Movement Tolerance");
            y = kv(y, "Enabled", bool(s.movementToleranceEnabled));
            y = kv(y, "Multiplier",
                String.format(Locale.ROOT, "%.2f×", s.movementToleranceMultiplier));

            y = section(y, "Loyalty Rewards");
            y = kv(y, "Enabled", bool(s.loyaltyEnabled));
            y = kvList(y, "Milestones (lifetime)", s.loyaltyMilestones);
            y = kvList(y, "Milestones (per-session)", s.loyaltySessionMilestones);

            y = section(y, "Auto Dropper");
            y = kv(y, "Enabled", bool(s.autoDropperEnabled));
            y = kv(y, "Tick interval", s.autoDropperTickInterval + " ticks");

            y = section(y, "World Border");
            y = kv(y, "Enabled", bool(s.borderEnabled));
            y = kvList(y, "Per-dim entries", s.borderEntries);

            y = section(y, "Roamer NPCs");
            y = kvList(y, "Walkable blocks", s.roamerWalkableBlocks);

            y = section(y, "RoadRunner Speed Blocks");
            y = kvList(y, "Block multipliers", s.roadrunnerSpeedBlocks);
        }

        contentHeight = y - startY;

        org.lwjgl.opengl.GL11.glDisable(org.lwjgl.opengl.GL11.GL_SCISSOR_TEST);
        net.minecraft.client.renderer.GlStateManager.popMatrix();

        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    private int section(int y, String label) {
        y += 4;
        drawString(this.fontRenderer, label, guiLeft + CONTENT_PAD, y, TEXT_SECTION);
        return y + LINE_H + 1;
    }

    private int kv(int y, String key, String value) {
        drawString(this.fontRenderer, key + ":", guiLeft + CONTENT_PAD + 6, y, TEXT_KEY);
        drawString(this.fontRenderer, value, guiLeft + CONTENT_PAD + 140, y, TEXT_VAL);
        return y + LINE_H;
    }

    /** Wrap a string list: key as a header, then one indented value per row. Empty
     *  list renders as "(none)". */
    private int kvList(int y, String key, List<String> values) {
        if (values == null || values.isEmpty()) {
            return kv(y, key, "(none)");
        }
        drawString(this.fontRenderer, key + ":", guiLeft + CONTENT_PAD + 6, y, TEXT_KEY);
        y += LINE_H;
        // De-dup-friendly: keep insertion order but skip blank entries.
        List<String> shown = new ArrayList<>(values.size());
        for (String v : values) {
            if (v != null && !v.isEmpty()) shown.add(v);
        }
        for (String v : shown) {
            String trimmed = this.fontRenderer.trimStringToWidth(v, GUI_W - CONTENT_PAD * 2 - 20);
            drawString(this.fontRenderer, "• " + trimmed,
                guiLeft + CONTENT_PAD + 18, y, TEXT_VAL);
            y += LINE_H;
        }
        return y;
    }

    private static String bool(boolean b) {
        return b ? "on" : "off";
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
