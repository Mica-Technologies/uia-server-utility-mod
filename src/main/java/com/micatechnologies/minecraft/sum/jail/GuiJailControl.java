package com.micatechnologies.minecraft.sum.jail;

import com.micatechnologies.minecraft.sum.atm.SumNetwork;
import com.micatechnologies.minecraft.sum.jail.JailControlSnapshot.CellView;
import com.micatechnologies.minecraft.sum.jail.JailControlSnapshot.InmateView;
import com.micatechnologies.minecraft.sum.jail.PacketJailControlAction.Action;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;

/**
 * The Jail Control Tool's screen: cells down the left, the chosen cell's details, actions and
 * inmates on the right. Every button sends a {@link PacketJailControlAction}; the server answers
 * with a fresh snapshot, which {@link #update} applies, so the screen never shows a guess.
 *
 * <p>Time left counts down locally from the snapshot, and the screen asks for a fresh snapshot
 * every few seconds while open so new inmates and alarms appear on their own.
 */
public class GuiJailControl extends GuiScreen {

    private static final int MAX_CELL_ROWS = 8;
    private static final int MAX_INMATE_ROWS = 6;
    private static final int REFRESH_TICKS = 100;

    private static final int ID_CELL_BASE = 100;
    private static final int ID_RELEASE_BASE = 200;
    private static final int ID_UP = 1;
    private static final int ID_DOWN = 2;
    private static final int ID_OPEN = 3;
    private static final int ID_CLOSE = 4;
    private static final int ID_GOTO = 5;
    private static final int ID_SILENCE = 6;
    private static final int ID_LINK = 7;
    private static final int ID_DONE = 8;

    private JailControlSnapshot snapshot;
    private long snapshotAt;
    private int selected;
    private int scroll;
    private int ticks;
    private final List<InmateView> shownInmates = new ArrayList<>();
    // Rows that fit the screen, worked out in initGui: a 1080p window at GUI scale 3, or any
    // small window, has room for fewer than the maximum, and the Done button must stay on it.
    private int cellRows = MAX_CELL_ROWS;
    private int inmateRows = MAX_INMATE_ROWS;

    public GuiJailControl(JailControlSnapshot snapshot) {
        this.snapshot = snapshot;
        this.snapshotAt = Minecraft.getSystemTime();
    }

    /** Applies a fresh snapshot, keeping the same cell selected if it still exists. */
    void update(JailControlSnapshot fresh) {
        String current = selectedCell() == null ? null : selectedCell().name;
        snapshot = fresh;
        snapshotAt = Minecraft.getSystemTime();
        selected = 0;
        for (int i = 0; i < fresh.cells.size(); i++) {
            if (fresh.cells.get(i).name.equals(current)) selected = i;
        }
        initGui();
    }

    private CellView selectedCell() {
        return selected >= 0 && selected < snapshot.cells.size() ? snapshot.cells.get(selected) : null;
    }

    private int left() { return width / 2 - 180; }
    private int top() { return Math.max(16, height / 2 - 110); }

    @Override
    public void initGui() {
        buttonList.clear();
        int left = left();
        int top = top();
        cellRows = Math.max(3, Math.min(MAX_CELL_ROWS, (height - top - 70) / 22));
        int doneY = top + 14 + (cellRows + 1) * 22 + 4;
        // Less one line, for the "and N more" note under a full list.
        inmateRows = Math.max(1, Math.min(MAX_INMATE_ROWS, (doneY - (top + 112)) / 20 - 1));
        scroll = Math.max(0, Math.min(scroll, snapshot.cells.size() - cellRows));
        for (int row = 0; row < cellRows && scroll + row < snapshot.cells.size(); row++) {
            int index = scroll + row;
            CellView cell = snapshot.cells.get(index);
            String label = (cell.sounding ? "§c! " : "") + cell.name + " (" + cell.inmates.size() + ")";
            GuiButton b = new GuiButton(ID_CELL_BASE + index, left, top + 14 + row * 22, 110, 20, label);
            b.enabled = index != selected;
            buttonList.add(b);
        }
        buttonList.add(new GuiButton(ID_UP, left, top + 14 + cellRows * 22, 54, 20, "▲"));
        buttonList.add(new GuiButton(ID_DOWN, left + 56, top + 14 + cellRows * 22, 54, 20, "▼"));

        CellView cell = selectedCell();
        int right = left + 120;
        shownInmates.clear();
        if (cell != null) {
            int y = top + 50;
            buttonList.add(new GuiButton(ID_OPEN, right, y, 78, 20, "Open doors"));
            buttonList.add(new GuiButton(ID_CLOSE, right + 80, y, 78, 20, "Close doors"));
            buttonList.add(new GuiButton(ID_GOTO, right + 160, y, 78, 20, "Go to cell"));
            y += 22;
            GuiButton silence = new GuiButton(ID_SILENCE, right, y, 118, 20, "Silence alarm");
            silence.enabled = cell.sounding;
            buttonList.add(silence);
            boolean linking = cell.name.equalsIgnoreCase(snapshot.linkCell);
            GuiButton link = new GuiButton(ID_LINK, right + 120, y, 118, 20,
                linking ? "Linking here" : "Link here");
            link.enabled = !linking;
            buttonList.add(link);
            for (int i = 0; i < cell.inmates.size() && i < inmateRows; i++) {
                shownInmates.add(cell.inmates.get(i));
                buttonList.add(new GuiButton(ID_RELEASE_BASE + i, right + 178, top + 112 + i * 20,
                    60, 18, "Release"));
            }
        }
        buttonList.add(new GuiButton(ID_DONE, width / 2 - 50, top + 14 + (cellRows + 1) * 22 + 4,
            100, 20, "Done"));
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        CellView cell = selectedCell();
        String cellName = cell == null ? "" : cell.name;
        if (button.id >= ID_RELEASE_BASE) {
            int i = button.id - ID_RELEASE_BASE;
            if (i < shownInmates.size()) {
                send(Action.RELEASE, cellName, shownInmates.get(i).playerId);
            }
            return;
        }
        if (button.id >= ID_CELL_BASE) {
            selected = button.id - ID_CELL_BASE;
            initGui();
            return;
        }
        switch (button.id) {
            case ID_UP: scroll--; initGui(); break;
            case ID_DOWN: scroll++; initGui(); break;
            case ID_OPEN: send(Action.OPEN_DOORS, cellName, null); break;
            case ID_CLOSE: send(Action.CLOSE_DOORS, cellName, null); break;
            case ID_GOTO: send(Action.GOTO, cellName, null); mc.displayGuiScreen(null); break;
            case ID_SILENCE: send(Action.SILENCE, cellName, null); break;
            case ID_LINK: send(Action.SELECT_FOR_LINKING, cellName, null); break;
            case ID_DONE: mc.displayGuiScreen(null); break;
            default: break;
        }
    }

    private static void send(Action action, String cell, java.util.UUID player) {
        SumNetwork.CHANNEL.sendToServer(new PacketJailControlAction(action, cell, player));
    }

    @Override
    public void updateScreen() {
        if (++ticks % REFRESH_TICKS == 0) {
            send(Action.REFRESH, "", null);
        }
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        int left = left();
        int top = top();
        drawCenteredString(fontRenderer, "Jail Control", width / 2, top - 10, 0xFFFFFF);
        drawString(fontRenderer, "Cells", left, top + 2, 0xA0A0A0);

        CellView cell = selectedCell();
        int right = left + 120;
        if (cell == null) {
            drawString(fontRenderer, "No cells yet. Select one with the Jail Wand and run /setjail.",
                right, top + 14, 0xC0C0C0);
        } else {
            drawString(fontRenderer, "§l" + cell.name + (cell.sounding ? "  §c§lJAILBREAK ALARM" : ""),
                right, top + 14, 0xFFFFFF);
            drawString(fontRenderer, "Dim " + cell.dimension + "  " + ItemJailWand.describe(cell.min)
                + " to " + ItemJailWand.describe(cell.max), right, top + 26, 0xA0A0A0);
            drawString(fontRenderer, cell.doors + " door(s), " + cell.alarms + " alarm relay(s) linked",
                right, top + 37, 0xA0A0A0);
            drawString(fontRenderer, cell.inmates.isEmpty() ? "Empty." : "Inmates:", right, top + 100, 0xC0C0C0);
            long elapsed = Minecraft.getSystemTime() - snapshotAt;
            for (int i = 0; i < shownInmates.size(); i++) {
                InmateView inmate = shownInmates.get(i);
                String left2 = inmate.remainingMillis < 0 ? "until released"
                    : JailService.formatDuration(Math.max(0L, inmate.remainingMillis - elapsed)) + " left";
                drawString(fontRenderer, (inmate.online ? "§a" : "§7") + inmate.name
                    + "§r  " + left2, right, top + 117 + i * 20, 0xE0E0E0);
            }
            if (cell.inmates.size() > inmateRows) {
                drawString(fontRenderer, "and " + (cell.inmates.size() - inmateRows) + " more (see /jails)",
                    right, top + 112 + inmateRows * 20, 0xA0A0A0);
            }
        }
        super.drawScreen(mouseX, mouseY, partialTicks);
    }
}
