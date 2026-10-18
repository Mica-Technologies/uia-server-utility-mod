package com.micatechnologies.minecraft.sum.picture;

import com.micatechnologies.minecraft.sum.atm.SumNetwork;
import com.micatechnologies.minecraft.sum.picture.PictureCache.Picture;
import com.micatechnologies.minecraft.sum.picture.PictureFrameSettings.Fit;
import java.io.IOException;
import java.util.Locale;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.util.math.BlockPos;
import org.lwjgl.input.Keyboard;

/**
 * Picture frame editor: the image link, the picture's size and position, and how it is drawn.
 *
 * <p>Numbers are typed, not slid, because people size pictures to the build ("3 wide, 2 high");
 * the +/- buttons step by half a block. "Match image shape" sets the height from the width and
 * the loaded image's proportions, which is what most people want from a frame and the main
 * thing the old picture-frame mod made fiddly. The status line shows whether the link loads,
 * from this client's own cache, before anything is saved.
 *
 * <p>Done sends {@link PacketPictureFrameUpdate}; the server re-validates and clamps every value,
 * so the limits shown here are a courtesy, not the enforcement.
 */
public class GuiPictureFrame extends GuiScreen {

    private static final int ID_DONE = 0;
    private static final int ID_CANCEL = 1;
    private static final int ID_FIT = 2;
    private static final int ID_ROTATE = 3;
    private static final int ID_GLOW = 4;
    private static final int ID_MATCH = 5;
    private static final int ID_W_MINUS = 10;
    private static final int ID_W_PLUS = 11;
    private static final int ID_H_MINUS = 12;
    private static final int ID_H_PLUS = 13;

    private final BlockPos pos;
    private final PictureFrameSettings initial;
    private final int maxSize;
    private final int maxRenderDistance;

    private GuiTextField urlField;
    private GuiTextField widthField;
    private GuiTextField heightField;
    private GuiTextField offsetXField;
    private GuiTextField offsetYField;
    private GuiTextField opacityField;
    private GuiTextField distanceField;
    private GuiButton fitButton;
    private GuiButton rotateButton;
    private GuiButton glowButton;

    private Fit fit;
    private int rotation;
    private boolean glow;

    /** The link as last seen, and when it last changed: see {@link #currentPicture}. */
    private String watchedUrl = "";
    private long urlChangedAt;
    private static final long LOOKUP_DELAY_MS = 600L;

    public GuiPictureFrame(BlockPos pos, PictureFrameSettings settings, int maxSize, int maxRenderDistance) {
        this.pos = pos;
        this.initial = settings;
        this.maxSize = maxSize;
        this.maxRenderDistance = maxRenderDistance;
        this.fit = settings.getFit();
        this.rotation = settings.getRotation();
        this.glow = settings.isGlow();
    }

    @Override
    public void initGui() {
        Keyboard.enableRepeatEvents(true);
        buttonList.clear();
        int cx = width / 2;
        int left = cx - 150;
        int top = Math.max(20, height / 2 - 105);

        urlField = field(0, left, top + 14, 300, initial.getUrl(), PictureFrameSettings.MAX_URL_LENGTH);
        urlField.setFocused(true);

        int row = top + 52;
        widthField = field(1, left + 22, row, 50, number(initial.getWidth()));
        buttonList.add(new GuiButton(ID_W_MINUS, left, row - 1, 20, 18, "-"));
        buttonList.add(new GuiButton(ID_W_PLUS, left + 74, row - 1, 20, 18, "+"));
        heightField = field(2, left + 128, row, 50, number(initial.getHeight()));
        buttonList.add(new GuiButton(ID_H_MINUS, left + 106, row - 1, 20, 18, "-"));
        buttonList.add(new GuiButton(ID_H_PLUS, left + 180, row - 1, 20, 18, "+"));
        buttonList.add(new GuiButton(ID_MATCH, left + 206, row - 1, 94, 18, "Match image shape"));

        row += 36;
        offsetXField = field(3, left, row, 60, number(initial.getOffsetX()));
        offsetYField = field(4, left + 70, row, 60, number(initial.getOffsetY()));
        opacityField = field(5, left + 140, row, 60,
            String.valueOf(Math.round(initial.getOpacity() * 100.0F)));
        distanceField = field(6, left + 210, row, 90, String.valueOf(initial.getRenderDistance()));

        row += 26;
        fitButton = new GuiButton(ID_FIT, left, row, 96, 20, "");
        rotateButton = new GuiButton(ID_ROTATE, left + 102, row, 96, 20, "");
        glowButton = new GuiButton(ID_GLOW, left + 204, row, 96, 20, "");
        buttonList.add(fitButton);
        buttonList.add(rotateButton);
        buttonList.add(glowButton);
        updateToggleLabels();

        row += 44;
        buttonList.add(new GuiButton(ID_DONE, cx - 102, row, 100, 20, "Done"));
        buttonList.add(new GuiButton(ID_CANCEL, cx + 2, row, 100, 20, "Cancel"));
    }

    private GuiTextField field(int id, int x, int y, int w, String text) {
        return field(id, x, y, w, text, 16);
    }

    private GuiTextField field(int id, int x, int y, int w, String text, int maxLength) {
        GuiTextField f = new GuiTextField(id, fontRenderer, x, y, w, 16);
        f.setMaxStringLength(maxLength);
        f.setText(text);
        return f;
    }

    private GuiTextField[] fields() {
        return new GuiTextField[] {urlField, widthField, heightField, offsetXField, offsetYField,
            opacityField, distanceField};
    }

    private void updateToggleLabels() {
        fitButton.displayString = "Fit: " + fit.getLabel();
        rotateButton.displayString = "Rotate: " + (rotation * 90) + "°";
        glowButton.displayString = "Glow: " + (glow ? "On" : "Off");
    }

    @Override
    public void onGuiClosed() {
        Keyboard.enableRepeatEvents(false);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        switch (button.id) {
            case ID_DONE:
                SumNetwork.CHANNEL.sendToServer(new PacketPictureFrameUpdate(pos, build()));
                mc.displayGuiScreen(null);
                break;
            case ID_CANCEL:
                mc.displayGuiScreen(null);
                break;
            case ID_FIT:
                fit = fit.next();
                break;
            case ID_ROTATE:
                rotation = (rotation + 1) % 4;
                break;
            case ID_GLOW:
                glow = !glow;
                break;
            case ID_MATCH:
                matchImageShape();
                break;
            case ID_W_MINUS: step(widthField, -0.5F); break;
            case ID_W_PLUS:  step(widthField, 0.5F); break;
            case ID_H_MINUS: step(heightField, -0.5F); break;
            case ID_H_PLUS:  step(heightField, 0.5F); break;
            default: break;
        }
        updateToggleLabels();
    }

    private void step(GuiTextField field, float delta) {
        float value = parse(field.getText(), 1.0F) + delta;
        value = Math.max(PictureFrameSettings.MIN_SIZE, Math.min(maxSize, value));
        field.setText(number(value));
    }

    /** Height from width and the image's proportions, rotation included. */
    private void matchImageShape() {
        Picture p = currentPicture();
        if (p == null || p.getStatus() != PictureCache.Status.READY) return;
        double w = rotation % 2 == 0 ? p.getWidth() : p.getHeight();
        double h = rotation % 2 == 0 ? p.getHeight() : p.getWidth();
        float width = parse(widthField.getText(), 1.0F);
        float height = (float) (width * h / w);
        if (height > maxSize) {
            // Too tall at this width: keep the height at the limit and narrow the width instead.
            height = maxSize;
            width = (float) (height * w / h);
            widthField.setText(number(width));
        }
        heightField.setText(number(Math.max(PictureFrameSettings.MIN_SIZE, height)));
    }

    /**
     * The picture for the typed link, once the link has stopped changing for
     * {@link #LOOKUP_DELAY_MS}. Without the wait, typing a link out by hand would start a
     * download for every prefix of it that happens to be a valid URL.
     */
    private Picture currentPicture() {
        String url = urlField.getText().trim();
        long now = net.minecraft.client.Minecraft.getSystemTime();
        if (!url.equals(watchedUrl)) {
            watchedUrl = url;
            urlChangedAt = now;
        }
        if (url.isEmpty() || !PictureClientSettings.enabled() || now - urlChangedAt < LOOKUP_DELAY_MS) {
            return null;
        }
        if (PictureUrls.validate(url, java.util.Collections.emptyList()) != null) return null;
        return PictureCache.INSTANCE.get(url);
    }

    private PictureFrameSettings build() {
        float opacity = parse(opacityField.getText(), 100.0F) / 100.0F;
        int distance;
        try {
            distance = Integer.parseInt(distanceField.getText().trim());
        } catch (NumberFormatException e) {
            distance = initial.getRenderDistance();
        }
        return new PictureFrameSettings(urlField.getText().trim(),
            parse(widthField.getText(), 1.0F), parse(heightField.getText(), 1.0F),
            parse(offsetXField.getText(), 0.0F), parse(offsetYField.getText(), 0.0F),
            rotation, fit, glow, opacity, distance);
    }

    // --- Input ---

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (keyCode == Keyboard.KEY_ESCAPE) {
            mc.displayGuiScreen(null);
            return;
        }
        if (keyCode == Keyboard.KEY_TAB) {
            GuiTextField[] all = fields();
            for (int i = 0; i < all.length; i++) {
                if (all[i].isFocused()) {
                    all[i].setFocused(false);
                    all[(i + (isShiftKeyDown() ? all.length - 1 : 1)) % all.length].setFocused(true);
                    return;
                }
            }
            urlField.setFocused(true);
            return;
        }
        if ((keyCode == Keyboard.KEY_RETURN || keyCode == Keyboard.KEY_NUMPADENTER) && !urlField.isFocused()) {
            actionPerformed(buttonList.stream().filter(b -> b.id == ID_DONE).findFirst().orElse(null));
            return;
        }
        for (GuiTextField f : fields()) {
            if (f.isFocused()) {
                if (f == urlField || isNumberInput(typedChar, keyCode)) {
                    f.textboxKeyTyped(typedChar, keyCode);
                }
                return;
            }
        }
    }

    private static boolean isNumberInput(char c, int keyCode) {
        return Character.isDigit(c) || c == '.' || c == '-' || c == 0
            || keyCode == Keyboard.KEY_BACK || keyCode == Keyboard.KEY_DELETE
            || keyCode == Keyboard.KEY_LEFT || keyCode == Keyboard.KEY_RIGHT
            || keyCode == Keyboard.KEY_HOME || keyCode == Keyboard.KEY_END
            || GuiScreen.isKeyComboCtrlA(keyCode) || GuiScreen.isKeyComboCtrlC(keyCode)
            || GuiScreen.isKeyComboCtrlV(keyCode) || GuiScreen.isKeyComboCtrlX(keyCode);
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        super.mouseClicked(mouseX, mouseY, mouseButton);
        for (GuiTextField f : fields()) {
            f.mouseClicked(mouseX, mouseY, mouseButton);
        }
    }

    @Override
    public void updateScreen() {
        for (GuiTextField f : fields()) {
            f.updateCursorCounter();
        }
    }

    // --- Drawing ---

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        int cx = width / 2;
        int left = cx - 150;
        int top = Math.max(20, height / 2 - 105);
        drawCenteredString(fontRenderer, "Picture Frame", cx, top - 12, 0xFFFFFF);

        drawString(fontRenderer, "Image link (PNG, JPEG, GIF or WebP)", left, top + 3, 0xA0A0A0);
        drawString(fontRenderer, status(), left, top + 33, 0xC0C0C0);
        drawString(fontRenderer, "Width (blocks)", left, top + 42, 0xA0A0A0);
        drawString(fontRenderer, "Height", left + 106, top + 42, 0xA0A0A0);
        drawString(fontRenderer, "Shift right", left, top + 78, 0xA0A0A0);
        drawString(fontRenderer, "Shift up", left + 70, top + 78, 0xA0A0A0);
        drawString(fontRenderer, "Opacity %", left + 140, top + 78, 0xA0A0A0);
        drawString(fontRenderer, "View distance", left + 210, top + 78, 0xA0A0A0);
        drawString(fontRenderer, "Up to " + maxSize + " blocks a side, visible up to "
            + maxRenderDistance + " blocks on this server.", left, top + 142, 0x808080);

        for (GuiTextField f : fields()) {
            f.drawTextBox();
        }
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    private String status() {
        String url = urlField.getText().trim();
        if (url.isEmpty()) return "§7No link: the frame will be empty.";
        String problem = PictureUrls.validate(url, java.util.Collections.emptyList());
        if (problem != null) return "§c" + problem;
        if (!PictureClientSettings.enabled()) return "§ePictures are turned off in your SUM settings.";
        Picture p = currentPicture();
        if (p == null) return "§7...";
        switch (p.getStatus()) {
            case READY:
                return "§aLoaded, " + p.getWidth() + " x " + p.getHeight() + " px.";
            case FAILED:
                return "§c" + fontRenderer.trimStringToWidth(p.getError(), 290);
            case LOADING:
            default:
                return "§7Loading...";
        }
    }

    // --- Numbers ---

    private static float parse(String text, float fallback) {
        try {
            float v = Float.parseFloat(text.trim());
            return Float.isNaN(v) || Float.isInfinite(v) ? fallback : v;
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static String number(float v) {
        if (v == Math.rint(v)) return String.valueOf((int) v);
        String s = String.format(Locale.ROOT, "%.3f", v);
        return s.replaceAll("0+$", "").replaceAll("\\.$", "");
    }
}
