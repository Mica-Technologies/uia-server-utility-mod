package com.micatechnologies.minecraft.sum.signpost;

import com.micatechnologies.minecraft.sum.atm.SumNetwork;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.util.math.BlockPos;
import org.lwjgl.input.Keyboard;

/**
 * Editor for a signpost's arms. Lays out one row per arm (angle field + label field +
 * remove button), with an "Add arm" button below the rows and Save/Cancel in the footer.
 *
 * <p>The screen reads the initial arm list from the client-side {@link TileEntitySignpost}
 * (synced via the TE's update tag). On Save, the entire arm list is sent to the server via
 * {@link PacketSignpostUpdate}; the server replaces the TE's arms wholesale.
 */
public class GuiSignpost extends GuiScreen {

    private static final int GUI_WIDTH = 256;
    private static final int GUI_HEIGHT = 196;
    private static final int ROW_HEIGHT = 18;
    private static final int ROW_START_Y = 32;
    private static final int FOOTER_Y_OFFSET = 24;

    private static final int BTN_ADD = 1;
    private static final int BTN_SAVE = 2;
    private static final int BTN_CANCEL = 3;
    private static final int BTN_REMOVE_BASE = 100;
    private static final int FIELD_ANGLE_BASE = 200;
    private static final int FIELD_LABEL_BASE = 300;

    private final BlockPos pos;
    private final List<EditableArm> editing = new ArrayList<>();
    private final List<GuiTextField> angleFields = new ArrayList<>();
    private final List<GuiTextField> labelFields = new ArrayList<>();
    private GuiButton addArmBtn;

    private int guiLeft;
    private int guiTop;

    public GuiSignpost(BlockPos pos, List<SignpostArm> initialArms) {
        this.pos = pos;
        for (SignpostArm a : initialArms) {
            editing.add(new EditableArm(a.getLabel(), a.getAngleDegrees()));
        }
    }

    @Override
    public void initGui() {
        super.initGui();
        Keyboard.enableRepeatEvents(true);
        guiLeft = (width - GUI_WIDTH) / 2;
        guiTop = (height - GUI_HEIGHT) / 2;
        rebuildFields();
    }

    /**
     * Tear down and rebuild text fields + buttons from the {@link #editing} model. Called on
     * initGui and after each add/remove that changes row count. Captures live field values
     * back to the model first so user typing isn't lost across the rebuild.
     */
    private void rebuildFields() {
        for (int i = 0; i < angleFields.size() && i < editing.size(); i++) {
            captureRow(i);
        }
        angleFields.clear();
        labelFields.clear();
        buttonList.clear();

        for (int i = 0; i < editing.size(); i++) {
            int rowY = guiTop + ROW_START_Y + i * ROW_HEIGHT;

            GuiTextField angleField = new GuiTextField(
                FIELD_ANGLE_BASE + i, fontRenderer, guiLeft + 24, rowY, 32, 14);
            angleField.setText(formatAngle(editing.get(i).angle));
            angleField.setMaxStringLength(6);
            angleFields.add(angleField);

            GuiTextField labelField = new GuiTextField(
                FIELD_LABEL_BASE + i, fontRenderer, guiLeft + 62, rowY, 152, 14);
            labelField.setText(editing.get(i).label);
            labelField.setMaxStringLength(32);
            labelFields.add(labelField);

            buttonList.add(new GuiButton(
                BTN_REMOVE_BASE + i, guiLeft + 220, rowY - 1, 20, 16, "X"));
        }

        int footerY = guiTop + GUI_HEIGHT - FOOTER_Y_OFFSET;
        addArmBtn = new GuiButton(BTN_ADD, guiLeft + 8, footerY, 80, 20, "+ Add arm");
        addArmBtn.enabled = editing.size() < TileEntitySignpost.MAX_ARMS;
        buttonList.add(addArmBtn);
        buttonList.add(new GuiButton(BTN_SAVE, guiLeft + GUI_WIDTH - 132, footerY, 60, 20, "Save"));
        buttonList.add(new GuiButton(BTN_CANCEL, guiLeft + GUI_WIDTH - 68, footerY, 60, 20, "Cancel"));
    }

    private void captureRow(int i) {
        if (i >= editing.size()) {
            return;
        }
        EditableArm arm = editing.get(i);
        try {
            arm.angle = Float.parseFloat(angleFields.get(i).getText().trim());
        } catch (NumberFormatException e) {
            // Keep last good value; user will see the saved-back value on rebuild.
        }
        arm.label = labelFields.get(i).getText();
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        for (int i = 0; i < angleFields.size(); i++) {
            captureRow(i);
        }
        if (button.id == BTN_ADD) {
            if (editing.size() < TileEntitySignpost.MAX_ARMS) {
                editing.add(new EditableArm("", 0.0F));
                rebuildFields();
            }
            return;
        }
        if (button.id == BTN_SAVE) {
            sendSave();
            mc.player.closeScreen();
            return;
        }
        if (button.id == BTN_CANCEL) {
            mc.player.closeScreen();
            return;
        }
        if (button.id >= BTN_REMOVE_BASE && button.id < BTN_REMOVE_BASE + TileEntitySignpost.MAX_ARMS) {
            int rowIndex = button.id - BTN_REMOVE_BASE;
            if (rowIndex >= 0 && rowIndex < editing.size()) {
                editing.remove(rowIndex);
                rebuildFields();
            }
        }
    }

    private void sendSave() {
        List<SignpostArm> finalArms = new ArrayList<>(editing.size());
        for (EditableArm e : editing) {
            finalArms.add(new SignpostArm(e.label, e.angle));
        }
        SumNetwork.CHANNEL.sendToServer(new PacketSignpostUpdate(pos, finalArms));
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (keyCode == Keyboard.KEY_ESCAPE) {
            mc.player.closeScreen();
            return;
        }
        // Tab cycles focus through fields, top-to-bottom, angle-then-label.
        if (keyCode == Keyboard.KEY_TAB) {
            cycleFocus();
            return;
        }
        for (GuiTextField f : angleFields) {
            if (f.textboxKeyTyped(typedChar, keyCode)) {
                return;
            }
        }
        for (GuiTextField f : labelFields) {
            if (f.textboxKeyTyped(typedChar, keyCode)) {
                return;
            }
        }
    }

    private void cycleFocus() {
        List<GuiTextField> order = new ArrayList<>();
        for (int i = 0; i < editing.size(); i++) {
            order.add(angleFields.get(i));
            order.add(labelFields.get(i));
        }
        if (order.isEmpty()) {
            return;
        }
        int focused = -1;
        for (int i = 0; i < order.size(); i++) {
            if (order.get(i).isFocused()) {
                focused = i;
                break;
            }
        }
        int next = (focused + 1) % order.size();
        for (int i = 0; i < order.size(); i++) {
            order.get(i).setFocused(i == next);
        }
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int button) throws IOException {
        super.mouseClicked(mouseX, mouseY, button);
        for (GuiTextField f : angleFields) {
            f.mouseClicked(mouseX, mouseY, button);
        }
        for (GuiTextField f : labelFields) {
            f.mouseClicked(mouseX, mouseY, button);
        }
    }

    @Override
    public void updateScreen() {
        super.updateScreen();
        for (GuiTextField f : angleFields) {
            f.updateCursorCounter();
        }
        for (GuiTextField f : labelFields) {
            f.updateCursorCounter();
        }
    }

    @Override
    public void onGuiClosed() {
        super.onGuiClosed();
        Keyboard.enableRepeatEvents(false);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        int x1 = guiLeft;
        int y1 = guiTop;
        int x2 = guiLeft + GUI_WIDTH;
        int y2 = guiTop + GUI_HEIGHT;
        // Outer border + inner panel.
        drawRect(x1, y1, x2, y2, 0xFF1A1A1A);
        drawRect(x1 + 1, y1 + 1, x2 - 1, y2 - 1, 0xFF323232);

        String title = "Signpost (" + editing.size() + "/" + TileEntitySignpost.MAX_ARMS + ")";
        fontRenderer.drawString(title, guiLeft + 8, guiTop + 8, 0xFFFFFF);
        fontRenderer.drawString("Angle", guiLeft + 24, guiTop + 22, 0xCCCCCC);
        fontRenderer.drawString("Label", guiLeft + 62, guiTop + 22, 0xCCCCCC);

        // Compass hint on the right of the header
        fontRenderer.drawString("0=N 90=E 180=S 270=W",
            guiLeft + GUI_WIDTH - 8 - fontRenderer.getStringWidth("0=N 90=E 180=S 270=W"),
            guiTop + 8, 0x808080);

        for (int i = 0; i < editing.size(); i++) {
            int rowY = guiTop + ROW_START_Y + i * ROW_HEIGHT;
            // Row index gutter
            fontRenderer.drawString(String.valueOf(i), guiLeft + 12, rowY + 3, 0xAAAAAA);
            angleFields.get(i).drawTextBox();
            labelFields.get(i).drawTextBox();
        }

        if (editing.isEmpty()) {
            String empty = "No arms yet — click '+ Add arm' to create one.";
            fontRenderer.drawString(empty,
                guiLeft + (GUI_WIDTH - fontRenderer.getStringWidth(empty)) / 2,
                guiTop + ROW_START_Y + 20,
                0x808080);
        }

        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    private static String formatAngle(float angle) {
        if (angle == Math.floor(angle)) {
            return Integer.toString((int) angle);
        }
        return String.format("%.1f", angle);
    }

    private static class EditableArm {
        String label;
        float angle;

        EditableArm(String label, float angle) {
            this.label = label;
            this.angle = angle;
        }
    }
}
