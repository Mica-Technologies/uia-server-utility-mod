package com.micatechnologies.minecraft.sum.phone;

import com.micatechnologies.minecraft.sum.atm.GuiSumAtm;
import com.micatechnologies.minecraft.sum.atm.SumNetwork;
import com.micatechnologies.minecraft.sum.phone.cloud.Contact;
import com.micatechnologies.minecraft.sum.phone.cloud.Message;
import com.micatechnologies.minecraft.sum.phone.cloud.MessageThread;
import com.micatechnologies.minecraft.sum.phone.cloud.PhoneCloudAction;
import com.micatechnologies.minecraft.sum.phone.cloud.PhoneCloudData;
import com.micatechnologies.minecraft.sum.phone.cloud.PhoneCloudFetchRequest;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.resources.I18n;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;
import org.lwjgl.input.Keyboard;

/**
 * The phone home + app shell. A single {@link GuiScreen} that draws a phone-shaped chrome
 * frame and routes between six apps. Apps share a server-side per-player "cloud"
 * ({@link PhoneCloudData}) for contacts, messages, and notes; the cloud is fetched on open
 * and pushed back from the server after any mutation.
 *
 * <p>The same screen is used for the phone item and the desk phone block; the {@code
 * enableBanking} constructor flag hides the banking tile in shared-block mode.
 */
public class GuiSumPhone extends GuiScreen {

    // --- Phone chrome dimensions ------------------------------------------------------------
    private static final int PHONE_W = 144;
    private static final int PHONE_H = 224;
    private static final int CHROME_TOP = 14;
    private static final int CHROME_BOTTOM = 28;
    private static final int SCREEN_PAD_X = 6;

    // --- Palette ----------------------------------------------------------------------------
    private static final int BEZEL_OUTER = 0xFF0A0B0D;
    private static final int BEZEL_INNER = 0xFF1A1D22;
    private static final int SCREEN_BG = 0xFF202833;
    private static final int STATUS_BG = 0xFF101820;
    private static final int TEXT_LIGHT = 0xFFE0E0E0;
    private static final int TEXT_DIM = 0xFF9098A0;
    private static final int TEXT_TITLE = 0xFFFFFFFF;
    private static final int TEXT_ERROR = 0xFFE07070;
    private static final int TEXT_ACCENT = 0xFF6DB6E0;
    private static final int TEXT_NUMBER = 0xFFD4B258;
    private static final int HOME_BTN = 0xFF2A2D34;
    private static final int HOME_BTN_RING = 0xFF40444C;
    private static final int APP_TILE_BG = 0xFF2A3340;
    private static final int APP_TILE_HOVER = 0xFF3A4658;
    private static final int APP_TILE_DISABLED = 0xFF22272E;
    private static final int CALC_KEY_BG = 0xFF2A3340;
    private static final int CALC_KEY_OP_BG = 0xFF4A5468;
    private static final int CALC_KEY_EQ_BG = 0xFF4A6E5E;
    private static final int LIST_ROW_BG = 0xFF2A3340;
    private static final int LIST_ROW_HOVER = 0xFF3A4658;
    private static final int EDITOR_BG = 0xFF101820;
    private static final int BUBBLE_ME_BG = 0xFF2E5E80;
    private static final int BUBBLE_THEM_BG = 0xFF3A4350;
    private static final int BADGE_BG = 0xFFD04050;

    // --- App enum ---------------------------------------------------------------------------
    private enum App { HOME, BANKING, NOTES, CALCULATOR, WEATHER, CONTACTS, MESSAGES }
    private enum NotesMode    { LIST, EDIT }
    private enum ContactsMode { LIST, DETAIL, NEW }
    private enum MessagesMode { THREAD_LIST, THREAD_VIEW, NEW_PICK }

    // --- Button IDs -------------------------------------------------------------------------
    private static final int BTN_BACK = 1;
    private static final int BTN_NOTES_NEW = 10;
    private static final int BTN_NOTES_SAVE = 11;
    private static final int BTN_NOTES_DELETE = 12;
    private static final int BTN_CONTACTS_NEW = 20;
    private static final int BTN_CONTACTS_ADD_SUBMIT = 21;
    private static final int BTN_CONTACTS_MESSAGE = 22;
    private static final int BTN_CONTACTS_DELETE = 23;
    private static final int BTN_MESSAGES_NEW = 30;
    private static final int BTN_MESSAGES_SEND = 31;

    // Tile IDs use the same space as button IDs but live on TileRegion objects (we render
    // them ourselves rather than using vanilla 3D buttons).
    private static final int TILE_HOME_BUTTON = 1000;

    // --- Construction inputs ----------------------------------------------------------------
    private final EntityPlayer player;
    private final boolean enableBanking;

    private int guiLeft;
    private int guiTop;

    // --- Cloud state ------------------------------------------------------------------------

    /** Most recent cloud snapshot the client has seen, kept across open/close to avoid a
     *  loading flash on reopen. Mutated by {@link #receiveCloud}; the open instance reads it. */
    private static PhoneCloudData lastCloud;
    /** The currently-displayed phone screen, if any. Pushed sync messages refresh this. */
    private static GuiSumPhone activeInstance;

    /** The cloud the open screen is rendering. May be null briefly between open and the
     *  fetch reply if no prior snapshot exists. */
    private PhoneCloudData cloud;

    // --- App state --------------------------------------------------------------------------
    private App currentApp = App.HOME;

    private NotesMode notesMode = NotesMode.LIST;
    private int editingNoteIndex = -1;
    private final PhoneTextField noteEditor =
        new PhoneTextField(PhoneCloudData.MAX_NOTE_LENGTH, true);

    private ContactsMode contactsMode = ContactsMode.LIST;
    private Contact selectedContact;
    private final PhoneTextField contactNameInput = new PhoneTextField(16, false);

    private MessagesMode messagesMode = MessagesMode.THREAD_LIST;
    private UUID currentPartner;
    private final PhoneTextField composeInput = new PhoneTextField(Message.MAX_TEXT_LENGTH, false);

    // Cursor blink, shared across editors
    private boolean cursorOn = true;
    private long cursorFlipAt = 0L;

    // Tile click regions, recomputed every initGui()
    private final List<TileRegion> tiles = new ArrayList<>();

    /** Override number used by the home screen when the GUI was opened from a desk phone
     *  rather than the player's personal phone. Null means "show the player's number". */
    private final String deskPhoneNumberOverride;

    public GuiSumPhone(EntityPlayer player, boolean enableBanking) {
        this(player, enableBanking, null);
    }

    public GuiSumPhone(EntityPlayer player, boolean enableBanking, String deskPhoneNumberOverride) {
        this.player = player;
        this.enableBanking = enableBanking;
        this.cloud = lastCloud;
        this.deskPhoneNumberOverride = deskPhoneNumberOverride;
    }

    @Override
    public void initGui() {
        super.initGui();
        this.guiLeft = (this.width - PHONE_W) / 2;
        this.guiTop = (this.height - PHONE_H) / 2;
        activeInstance = this;
        rebuildButtons();
        // Always re-fetch on open — cached cloud may be stale.
        SumNetwork.CHANNEL.sendToServer(new PhoneCloudFetchRequest());
    }

    @Override
    public void onGuiClosed() {
        if (activeInstance == this) activeInstance = null;
        super.onGuiClosed();
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    /** Called from the cloud-sync packet handler on the client thread. */
    public static void receiveCloud(PhoneCloudData snapshot) {
        lastCloud = snapshot;
        if (activeInstance != null) {
            activeInstance.cloud = snapshot;
            activeInstance.rebuildButtons();
        }
    }

    // ---------------------------------------------------------------------------------------
    // Button construction
    // ---------------------------------------------------------------------------------------

    private void rebuildButtons() {
        this.buttonList.clear();
        this.tiles.clear();

        // Home button (always)
        int homeSize = 18;
        int homeX = guiLeft + (PHONE_W - homeSize) / 2;
        int homeY = guiTop + PHONE_H - CHROME_BOTTOM + (CHROME_BOTTOM - homeSize) / 2;
        tiles.add(new TileRegion(TILE_HOME_BUTTON, homeX, homeY, homeSize, homeSize,
            TileKind.HOME_BUTTON, null, null));

        // Back button on every sub-screen — small button at the top-left of the screen area.
        if (showBackButton()) {
            this.buttonList.add(new GuiButton(BTN_BACK,
                guiLeft + SCREEN_PAD_X + 2, guiTop + CHROME_TOP + 14, 28, 12, "<"));
        }

        switch (currentApp) {
            case HOME:       addHomeTiles(); break;
            case NOTES:      addNotesButtons(); break;
            case CALCULATOR: addCalculatorKeys(); break;
            case CONTACTS:   addContactsButtons(); break;
            case MESSAGES:   addMessagesButtons(); break;
            default:         break;
        }
    }

    private boolean showBackButton() {
        if (currentApp == App.HOME || currentApp == App.BANKING) return false;
        if (currentApp == App.NOTES && notesMode == NotesMode.LIST) return false;
        if (currentApp == App.CALCULATOR) return false;
        if (currentApp == App.WEATHER) return false;
        if (currentApp == App.CONTACTS && contactsMode == ContactsMode.LIST) return false;
        if (currentApp == App.MESSAGES && messagesMode == MessagesMode.THREAD_LIST) return false;
        return true;
    }

    // ---------------------------------------------------------------------------------------
    // Home grid
    // ---------------------------------------------------------------------------------------

    private void addHomeTiles() {
        // The screen is 12 (status bar) + 12 (title) + 12 (number) = 36 of header, then the
        // grid, then we must stop before the bottom chrome at PHONE_H - CHROME_BOTTOM (= 196
        // for a 224-tall phone). With three 42-tall rows + two 4-gap, the grid is 134 tall —
        // fits between y=56 and y=190 with a 6px margin to the home-button chrome.
        int tileW = 50, tileH = 42;
        int colGap = 8, rowGap = 4;
        int gridW = tileW * 2 + colGap;
        int leftX = guiLeft + (PHONE_W - gridW) / 2;
        int topY = guiTop + CHROME_TOP + 42; // 4px clear of the "My #:" line

        int idx = 0;
        if (enableBanking) {
            addAppTile(idx++, leftX, topY, tileW, tileH, App.BANKING, "sum.phone.app.banking");
            addAppTile(idx++, leftX + tileW + colGap, topY, tileW, tileH, App.NOTES, "sum.phone.app.notes");
        } else {
            // Desk phone: no banking. Bump Notes into the first slot, slide the others up.
            addAppTile(idx++, leftX, topY, tileW, tileH, App.NOTES, "sum.phone.app.notes");
            addAppTile(idx++, leftX + tileW + colGap, topY, tileW, tileH, App.CALCULATOR, "sum.phone.app.calculator");
        }

        int row2Y = topY + tileH + rowGap;
        if (enableBanking) {
            addAppTile(idx++, leftX, row2Y, tileW, tileH, App.CALCULATOR, "sum.phone.app.calculator");
            addAppTile(idx++, leftX + tileW + colGap, row2Y, tileW, tileH, App.WEATHER, "sum.phone.app.weather");
        } else {
            addAppTile(idx++, leftX, row2Y, tileW, tileH, App.WEATHER, "sum.phone.app.weather");
            addAppTile(idx++, leftX + tileW + colGap, row2Y, tileW, tileH, App.MESSAGES, "sum.phone.app.messages");
        }

        int row3Y = row2Y + tileH + rowGap;
        if (enableBanking) {
            addAppTile(idx++, leftX, row3Y, tileW, tileH, App.MESSAGES, "sum.phone.app.messages");
            addAppTile(idx++, leftX + tileW + colGap, row3Y, tileW, tileH, App.CONTACTS, "sum.phone.app.contacts");
        } else {
            addAppTile(idx++, leftX, row3Y, tileW, tileH, App.CONTACTS, "sum.phone.app.contacts");
            // Last slot empty on desk phone (5 apps fit).
        }
    }

    private void addAppTile(int idx, int x, int y, int w, int h, App app, String i18nKey) {
        tiles.add(new TileRegion(100 + idx, x, y, w, h, TileKind.APP_TILE, app, i18nKey));
    }

    // ---------------------------------------------------------------------------------------
    // Notes app
    // ---------------------------------------------------------------------------------------

    private void addNotesButtons() {
        if (notesMode == NotesMode.LIST) {
            int btnW = 44, btnH = 14;
            this.buttonList.add(new GuiButton(BTN_NOTES_NEW,
                guiLeft + PHONE_W - SCREEN_PAD_X - 4 - btnW,
                guiTop + CHROME_TOP + 14, btnW, btnH,
                I18n.format("sum.phone.notes.new")));
            // Note rows
            List<String> notes = cloud != null ? cloud.notes : new ArrayList<>();
            int rowH = 22;
            int rowsTop = guiTop + CHROME_TOP + 32;
            int rowX = guiLeft + SCREEN_PAD_X + 4;
            int rowW = PHONE_W - 2 * SCREEN_PAD_X - 8;
            for (int i = 0; i < Math.min(5, notes.size()); i++) {
                tiles.add(new TileRegion(2000 + i,
                    rowX, rowsTop + i * (rowH + 2), rowW, rowH,
                    TileKind.NOTE_ROW, null, String.valueOf(i)));
            }
        } else {
            int btnW = 50, btnH = 14;
            int y = guiTop + PHONE_H - CHROME_BOTTOM - btnH - 4;
            this.buttonList.add(new GuiButton(BTN_NOTES_SAVE,
                guiLeft + SCREEN_PAD_X + 4, y, btnW, btnH,
                I18n.format("sum.phone.notes.save")));
            this.buttonList.add(new GuiButton(BTN_NOTES_DELETE,
                guiLeft + PHONE_W - SCREEN_PAD_X - 4 - btnW, y, btnW, btnH,
                I18n.format("sum.phone.notes.delete")));
        }
    }

    private void startEditingNote(int idx) {
        editingNoteIndex = idx;
        if (cloud != null && idx >= 0 && idx < cloud.notes.size()) {
            noteEditor.setValue(cloud.notes.get(idx));
        } else {
            noteEditor.setValue("");
        }
        notesMode = NotesMode.EDIT;
        rebuildButtons();
    }

    private void startNewNote() {
        if (cloud == null) return;
        if (cloud.notes.size() >= PhoneCloudData.MAX_NOTES) return;
        editingNoteIndex = -1;
        noteEditor.setValue("");
        notesMode = NotesMode.EDIT;
        rebuildButtons();
    }

    /** Apply the in-progress edit to the in-memory list AND ship to the server. */
    private void commitAndSaveNoteEdit() {
        if (cloud == null) return;
        String text = noteEditor.getValue();
        if (editingNoteIndex < 0) {
            if (!text.isEmpty()) cloud.notes.add(text);
        } else if (editingNoteIndex < cloud.notes.size()) {
            if (text.isEmpty()) cloud.notes.remove(editingNoteIndex);
            else cloud.notes.set(editingNoteIndex, text);
        }
        SumNetwork.CHANNEL.sendToServer(PhoneCloudAction.updateNotes(new ArrayList<>(cloud.notes)));
        editingNoteIndex = -1;
        noteEditor.setValue("");
        notesMode = NotesMode.LIST;
        rebuildButtons();
    }

    private void deleteCurrentNote() {
        if (cloud == null) return;
        if (editingNoteIndex >= 0 && editingNoteIndex < cloud.notes.size()) {
            cloud.notes.remove(editingNoteIndex);
            SumNetwork.CHANNEL.sendToServer(
                PhoneCloudAction.updateNotes(new ArrayList<>(cloud.notes)));
        }
        editingNoteIndex = -1;
        noteEditor.setValue("");
        notesMode = NotesMode.LIST;
        rebuildButtons();
    }

    // ---------------------------------------------------------------------------------------
    // Calculator (unchanged)
    // ---------------------------------------------------------------------------------------

    private String calcDisplay = "0";
    private double calcAccumulator = 0.0;
    private char calcPendingOp = '\0';
    private boolean calcFreshEntry = true;
    private boolean calcError = false;

    private enum CalcKey {
        DIGIT_0, DIGIT_1, DIGIT_2, DIGIT_3, DIGIT_4,
        DIGIT_5, DIGIT_6, DIGIT_7, DIGIT_8, DIGIT_9,
        DOT, ADD, SUB, MUL, DIV, EQUALS, CLEAR, NEGATE, PERCENT;

        boolean isOperator() {
            return this == ADD || this == SUB || this == MUL || this == DIV;
        }
        boolean isDigit() {
            return ordinal() <= DIGIT_9.ordinal();
        }
    }

    private void addCalculatorKeys() {
        int keyW = 28, keyH = 22, gap = 2;
        int gridW = keyW * 4 + gap * 3;
        int leftX = guiLeft + (PHONE_W - gridW) / 2;
        int topY = guiTop + CHROME_TOP + 14 + 22 + 4;

        addCalcKey(0, leftX,                          topY,                       keyW, keyH, "C",   CalcKey.CLEAR);
        addCalcKey(1, leftX + (keyW + gap),           topY,                       keyW, keyH, "+/-", CalcKey.NEGATE);
        addCalcKey(2, leftX + (keyW + gap) * 2,       topY,                       keyW, keyH, "%",   CalcKey.PERCENT);
        addCalcKey(3, leftX + (keyW + gap) * 3,       topY,                       keyW, keyH, "/",   CalcKey.DIV);

        int row1 = topY + keyH + gap;
        addCalcKey(4, leftX,                          row1, keyW, keyH, "7", CalcKey.DIGIT_7);
        addCalcKey(5, leftX + (keyW + gap),           row1, keyW, keyH, "8", CalcKey.DIGIT_8);
        addCalcKey(6, leftX + (keyW + gap) * 2,       row1, keyW, keyH, "9", CalcKey.DIGIT_9);
        addCalcKey(7, leftX + (keyW + gap) * 3,       row1, keyW, keyH, "*", CalcKey.MUL);

        int row2 = row1 + keyH + gap;
        addCalcKey(8,  leftX,                          row2, keyW, keyH, "4", CalcKey.DIGIT_4);
        addCalcKey(9,  leftX + (keyW + gap),           row2, keyW, keyH, "5", CalcKey.DIGIT_5);
        addCalcKey(10, leftX + (keyW + gap) * 2,       row2, keyW, keyH, "6", CalcKey.DIGIT_6);
        addCalcKey(11, leftX + (keyW + gap) * 3,       row2, keyW, keyH, "-", CalcKey.SUB);

        int row3 = row2 + keyH + gap;
        addCalcKey(12, leftX,                          row3, keyW, keyH, "1", CalcKey.DIGIT_1);
        addCalcKey(13, leftX + (keyW + gap),           row3, keyW, keyH, "2", CalcKey.DIGIT_2);
        addCalcKey(14, leftX + (keyW + gap) * 2,       row3, keyW, keyH, "3", CalcKey.DIGIT_3);
        addCalcKey(15, leftX + (keyW + gap) * 3,       row3, keyW, keyH, "+", CalcKey.ADD);

        int row4 = row3 + keyH + gap;
        int wideW = keyW * 2 + gap;
        addCalcKey(16, leftX,                          row4, wideW, keyH, "0", CalcKey.DIGIT_0);
        addCalcKey(17, leftX + wideW + gap,            row4, keyW, keyH,  ".", CalcKey.DOT);
        addCalcKey(18, leftX + wideW + gap + keyW + gap, row4, keyW, keyH, "=", CalcKey.EQUALS);
    }

    private void addCalcKey(int idx, int x, int y, int w, int h, String label, CalcKey key) {
        TileRegion r = new TileRegion(200 + idx, x, y, w, h, TileKind.CALC_KEY, null, label);
        r.calcKey = key;
        tiles.add(r);
    }

    // ---------------------------------------------------------------------------------------
    // Contacts app
    // ---------------------------------------------------------------------------------------

    private void addContactsButtons() {
        if (contactsMode == ContactsMode.LIST) {
            int btnW = 44, btnH = 14;
            this.buttonList.add(new GuiButton(BTN_CONTACTS_NEW,
                guiLeft + PHONE_W - SCREEN_PAD_X - 4 - btnW,
                guiTop + CHROME_TOP + 14, btnW, btnH,
                I18n.format("sum.phone.contacts.new")));
            // List rows
            List<Contact> list = cloud != null ? cloud.contacts : new ArrayList<>();
            int rowH = 18;
            int rowsTop = guiTop + CHROME_TOP + 32;
            int rowX = guiLeft + SCREEN_PAD_X + 4;
            int rowW = PHONE_W - 2 * SCREEN_PAD_X - 8;
            for (int i = 0; i < Math.min(7, list.size()); i++) {
                tiles.add(new TileRegion(3000 + i,
                    rowX, rowsTop + i * (rowH + 2), rowW, rowH,
                    TileKind.CONTACT_ROW, null, String.valueOf(i)));
            }
        } else if (contactsMode == ContactsMode.DETAIL) {
            int btnW = 56, btnH = 14;
            int y = guiTop + PHONE_H - CHROME_BOTTOM - btnH - 4;
            this.buttonList.add(new GuiButton(BTN_CONTACTS_MESSAGE,
                guiLeft + SCREEN_PAD_X + 4, y, btnW, btnH,
                I18n.format("sum.phone.contacts.message")));
            this.buttonList.add(new GuiButton(BTN_CONTACTS_DELETE,
                guiLeft + PHONE_W - SCREEN_PAD_X - 4 - btnW, y, btnW, btnH,
                I18n.format("sum.phone.contacts.delete")));
        } else if (contactsMode == ContactsMode.NEW) {
            int btnW = 50, btnH = 14;
            int y = guiTop + PHONE_H - CHROME_BOTTOM - btnH - 4;
            this.buttonList.add(new GuiButton(BTN_CONTACTS_ADD_SUBMIT,
                guiLeft + (PHONE_W - btnW) / 2, y, btnW, btnH,
                I18n.format("sum.phone.contacts.add")));
        }
    }

    private void openContact(Contact c) {
        selectedContact = c;
        contactsMode = ContactsMode.DETAIL;
        rebuildButtons();
    }

    private void startNewContact() {
        contactNameInput.setValue("");
        contactsMode = ContactsMode.NEW;
        rebuildButtons();
    }

    private void submitNewContact() {
        String name = contactNameInput.getValue().trim();
        if (name.isEmpty()) return;
        SumNetwork.CHANNEL.sendToServer(PhoneCloudAction.addContact(name));
        contactNameInput.setValue("");
        contactsMode = ContactsMode.LIST;
        rebuildButtons();
    }

    private void deleteSelectedContact() {
        if (selectedContact == null) return;
        SumNetwork.CHANNEL.sendToServer(PhoneCloudAction.removeContact(selectedContact.targetUuid));
        selectedContact = null;
        contactsMode = ContactsMode.LIST;
        rebuildButtons();
    }

    // ---------------------------------------------------------------------------------------
    // Messages app
    // ---------------------------------------------------------------------------------------

    private void addMessagesButtons() {
        if (messagesMode == MessagesMode.THREAD_LIST) {
            int btnW = 44, btnH = 14;
            this.buttonList.add(new GuiButton(BTN_MESSAGES_NEW,
                guiLeft + PHONE_W - SCREEN_PAD_X - 4 - btnW,
                guiTop + CHROME_TOP + 14, btnW, btnH,
                I18n.format("sum.phone.messages.new")));
            if (cloud != null) {
                int i = 0;
                int rowH = 24;
                int rowsTop = guiTop + CHROME_TOP + 32;
                int rowX = guiLeft + SCREEN_PAD_X + 4;
                int rowW = PHONE_W - 2 * SCREEN_PAD_X - 8;
                for (Map.Entry<UUID, MessageThread> e : cloud.threads.entrySet()) {
                    if (i >= 5) break;
                    tiles.add(new TileRegion(4000 + i,
                        rowX, rowsTop + i * (rowH + 2), rowW, rowH,
                        TileKind.THREAD_ROW, null, e.getKey().toString()));
                    i++;
                }
            }
        } else if (messagesMode == MessagesMode.NEW_PICK) {
            // Contact picker reuses the contacts list rows.
            List<Contact> list = cloud != null ? cloud.contacts : new ArrayList<>();
            int rowH = 18;
            int rowsTop = guiTop + CHROME_TOP + 32;
            int rowX = guiLeft + SCREEN_PAD_X + 4;
            int rowW = PHONE_W - 2 * SCREEN_PAD_X - 8;
            for (int i = 0; i < Math.min(7, list.size()); i++) {
                tiles.add(new TileRegion(4100 + i,
                    rowX, rowsTop + i * (rowH + 2), rowW, rowH,
                    TileKind.CONTACT_PICK_ROW, null, String.valueOf(i)));
            }
        } else if (messagesMode == MessagesMode.THREAD_VIEW) {
            int sendW = 38, btnH = 14;
            int y = guiTop + PHONE_H - CHROME_BOTTOM - btnH - 4;
            this.buttonList.add(new GuiButton(BTN_MESSAGES_SEND,
                guiLeft + PHONE_W - SCREEN_PAD_X - 4 - sendW, y, sendW, btnH,
                I18n.format("sum.phone.messages.send")));
        }
    }

    private void openThread(UUID partner) {
        currentPartner = partner;
        messagesMode = MessagesMode.THREAD_VIEW;
        composeInput.setValue("");
        if (cloud != null) {
            MessageThread t = cloud.threads.get(partner);
            if (t != null && t.unreadCount > 0) {
                // Client-side optimistic; server confirms via sync.
                t.unreadCount = 0;
                SumNetwork.CHANNEL.sendToServer(PhoneCloudAction.markRead(partner));
            }
        }
        rebuildButtons();
    }

    private void sendComposed() {
        if (currentPartner == null) return;
        String text = composeInput.getValue().trim();
        if (text.isEmpty()) return;
        SumNetwork.CHANNEL.sendToServer(PhoneCloudAction.sendMessage(currentPartner, text));
        composeInput.setValue("");
    }

    // ---------------------------------------------------------------------------------------
    // Drawing
    // ---------------------------------------------------------------------------------------

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        this.drawDefaultBackground();
        updateCursor();

        // Bezel
        drawRect(guiLeft - 1, guiTop - 1, guiLeft + PHONE_W + 1, guiTop + PHONE_H + 1, BEZEL_OUTER);
        drawRect(guiLeft, guiTop, guiLeft + PHONE_W, guiTop + PHONE_H, BEZEL_INNER);

        // Screen
        int sX1 = guiLeft + SCREEN_PAD_X;
        int sY1 = guiTop + CHROME_TOP;
        int sX2 = guiLeft + PHONE_W - SCREEN_PAD_X;
        int sY2 = guiTop + PHONE_H - CHROME_BOTTOM;
        drawRect(sX1, sY1, sX2, sY2, SCREEN_BG);

        // Speaker grille hint
        drawRect(guiLeft + PHONE_W / 2 - 12, guiTop + CHROME_TOP / 2 - 1,
                 guiLeft + PHONE_W / 2 + 12, guiTop + CHROME_TOP / 2 + 1, 0xFF000000);

        // Status bar
        drawStatusBar(sX1, sY1, sX2);

        // Content
        int contentTop = sY1 + 14;
        switch (currentApp) {
            case HOME:       drawHome(sX1, contentTop, sX2, sY2); break;
            case NOTES:      drawNotes(sX1, contentTop, sX2, sY2); break;
            case CALCULATOR: drawCalculator(sX1, contentTop, sX2, sY2); break;
            case WEATHER:    drawWeather(sX1, contentTop, sX2, sY2); break;
            case CONTACTS:   drawContacts(sX1, contentTop, sX2, sY2); break;
            case MESSAGES:   drawMessages(sX1, contentTop, sX2, sY2); break;
            case BANKING:    /* hand-off to GuiSumAtm; never visible */ break;
        }

        for (TileRegion r : tiles) drawTile(r, mouseX, mouseY);
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    private void updateCursor() {
        long now = System.currentTimeMillis();
        if (now - cursorFlipAt > 500L) {
            cursorOn = !cursorOn;
            cursorFlipAt = now;
        }
    }

    private void drawStatusBar(int x1, int y1, int x2) {
        drawRect(x1, y1, x2, y1 + 12, STATUS_BG);
        String clock = formatClock(player.world.getWorldTime());
        this.fontRenderer.drawString(clock, x1 + 4, y1 + 3, TEXT_LIGHT);
        String wx = weatherGlyph(player.world);
        int wxW = this.fontRenderer.getStringWidth(wx);
        this.fontRenderer.drawString(wx, x2 - 4 - wxW, y1 + 3, TEXT_DIM);
    }

    private void drawHome(int x1, int y1, int x2, int y2) {
        String title = enableBanking
            ? I18n.format("sum.phone.home.title")
            : I18n.format("sum.phone.home.title_desk");
        int tw = this.fontRenderer.getStringWidth(title);
        this.fontRenderer.drawString(title, (x1 + x2) / 2 - tw / 2, y1 + 4, TEXT_DIM);

        // Desk phones show their own number (anyone using the desk phone sees + shares
        // the same number). Personal phones fall back to the player's cloud number.
        String num;
        if (deskPhoneNumberOverride != null && !deskPhoneNumberOverride.isEmpty()) {
            num = deskPhoneNumberOverride;
        } else {
            num = cloud != null && cloud.phoneNumber != null && !cloud.phoneNumber.isEmpty()
                ? cloud.phoneNumber
                : I18n.format("sum.phone.home.loading");
        }
        String numLabel = I18n.format("sum.phone.home.number_label", num);
        int nw = this.fontRenderer.getStringWidth(numLabel);
        this.fontRenderer.drawString(numLabel, (x1 + x2) / 2 - nw / 2, y1 + 16, TEXT_NUMBER);
    }

    private void drawNotes(int x1, int y1, int x2, int y2) {
        if (notesMode == NotesMode.LIST) {
            this.fontRenderer.drawString(I18n.format("sum.phone.notes.title"),
                x1 + 4, y1 + 4, TEXT_TITLE);
            List<String> notes = cloud != null ? cloud.notes : new ArrayList<>();
            if (notes.isEmpty()) {
                String hint = I18n.format("sum.phone.notes.empty");
                int hw = this.fontRenderer.getStringWidth(hint);
                this.fontRenderer.drawString(hint, (x1 + x2) / 2 - hw / 2, y1 + 60, TEXT_DIM);
            }
        } else {
            String header = editingNoteIndex < 0
                ? I18n.format("sum.phone.notes.new_header")
                : I18n.format("sum.phone.notes.edit_header", editingNoteIndex + 1);
            this.fontRenderer.drawString(header, x1 + 36, y1 + 4, TEXT_TITLE);
            String count = noteEditor.length() + "/" + PhoneCloudData.MAX_NOTE_LENGTH;
            int cw = this.fontRenderer.getStringWidth(count);
            this.fontRenderer.drawString(count, x2 - 4 - cw, y1 + 4,
                noteEditor.length() >= PhoneCloudData.MAX_NOTE_LENGTH ? TEXT_ERROR : TEXT_DIM);

            int editorTop = y1 + 18;
            int editorBottom = y2 - 22;
            drawRect(x1 + 2, editorTop, x2 - 2, editorBottom, EDITOR_BG);
            noteEditor.renderMultiline(this.fontRenderer,
                x1 + 4, editorTop + 2, x2 - 4, editorBottom - 2, cursorOn);
        }
    }

    private void drawCalculator(int x1, int y1, int x2, int y2) {
        int dispH = 22;
        drawRect(x1 + 2, y1 + 4, x2 - 2, y1 + 4 + dispH, EDITOR_BG);
        String shown = calcError ? I18n.format("sum.phone.calc.error") : calcDisplay;
        int sw = this.fontRenderer.getStringWidth(shown);
        this.fontRenderer.drawString(shown, x2 - 6 - sw, y1 + 4 + (dispH - 8) / 2,
            calcError ? TEXT_ERROR : TEXT_LIGHT);
    }

    private void drawWeather(int x1, int y1, int x2, int y2) {
        this.fontRenderer.drawString(I18n.format("sum.phone.weather.title"),
            x1 + 36, y1 + 4, TEXT_TITLE);

        World world = player.world;
        boolean raining = world.isRaining();
        boolean thundering = world.isThundering();
        BlockPos pos = player.getPosition();
        Biome biome = world.getBiome(pos);

        String weather = thundering
            ? I18n.format("sum.phone.weather.thunder")
            : raining ? I18n.format("sum.phone.weather.rain")
                      : I18n.format("sum.phone.weather.clear");
        String tod = formatClock(world.getWorldTime() % 24000L);
        String biomeName = biome == null ? "?" : biome.getBiomeName();

        int rowY = y1 + 28;
        int lineH = 14;
        drawWeatherRow(x1, rowY,             I18n.format("sum.phone.weather.now"),    weather);
        drawWeatherRow(x1, rowY + lineH,     I18n.format("sum.phone.weather.time"),   tod);
        drawWeatherRow(x1, rowY + lineH * 2, I18n.format("sum.phone.weather.biome"),  biomeName);

        String hint = I18n.format("sum.phone.weather.hint");
        int hw = this.fontRenderer.getStringWidth(hint);
        this.fontRenderer.drawString(hint, (x1 + x2) / 2 - hw / 2, y2 - 14, TEXT_DIM);
    }

    private void drawWeatherRow(int x1, int y, String label, String value) {
        this.fontRenderer.drawString(label, x1 + 4, y, TEXT_DIM);
        int vw = this.fontRenderer.getStringWidth(value);
        this.fontRenderer.drawString(value,
            guiLeft + PHONE_W - SCREEN_PAD_X - 4 - vw, y, TEXT_LIGHT);
    }

    private void drawContacts(int x1, int y1, int x2, int y2) {
        if (contactsMode == ContactsMode.LIST) {
            this.fontRenderer.drawString(I18n.format("sum.phone.contacts.title"),
                x1 + 4, y1 + 4, TEXT_TITLE);
            if (cloud == null || cloud.contacts.isEmpty()) {
                String hint = I18n.format("sum.phone.contacts.empty");
                int hw = this.fontRenderer.getStringWidth(hint);
                this.fontRenderer.drawString(hint, (x1 + x2) / 2 - hw / 2, y1 + 60, TEXT_DIM);
            }
        } else if (contactsMode == ContactsMode.DETAIL) {
            if (selectedContact == null) return;
            // Header
            this.fontRenderer.drawString(I18n.format("sum.phone.contacts.detail_title"),
                x1 + 36, y1 + 4, TEXT_TITLE);
            // Avatar circle (simple colored square)
            int avSize = 36;
            int avX = (x1 + x2) / 2 - avSize / 2;
            int avY = y1 + 20;
            drawRect(avX, avY, avX + avSize, avY + avSize, 0xFF4A6E80);
            String initial = selectedContact.displayName == null || selectedContact.displayName.isEmpty()
                ? "?" : selectedContact.displayName.substring(0, 1).toUpperCase(Locale.ROOT);
            int iw = this.fontRenderer.getStringWidth(initial);
            this.fontRenderer.drawString(initial, avX + (avSize - iw) / 2, avY + (avSize - 8) / 2,
                TEXT_TITLE);
            // Name
            String name = selectedContact.displayName == null ? "?" : selectedContact.displayName;
            int nw = this.fontRenderer.getStringWidth(name);
            this.fontRenderer.drawString(name, (x1 + x2) / 2 - nw / 2, avY + avSize + 8, TEXT_LIGHT);
            // Number
            String num = selectedContact.cachedNumber == null || selectedContact.cachedNumber.isEmpty()
                ? I18n.format("sum.phone.contacts.no_number")
                : selectedContact.cachedNumber;
            int nuw = this.fontRenderer.getStringWidth(num);
            this.fontRenderer.drawString(num,
                (x1 + x2) / 2 - nuw / 2, avY + avSize + 20, TEXT_NUMBER);
        } else if (contactsMode == ContactsMode.NEW) {
            this.fontRenderer.drawString(I18n.format("sum.phone.contacts.new_title"),
                x1 + 36, y1 + 4, TEXT_TITLE);
            String prompt = I18n.format("sum.phone.contacts.new_prompt");
            this.fontRenderer.drawString(prompt, x1 + 4, y1 + 28, TEXT_DIM);

            int fieldX1 = x1 + 4;
            int fieldX2 = x2 - 4;
            int fieldY1 = y1 + 42;
            int fieldY2 = fieldY1 + 16;
            drawRect(fieldX1, fieldY1, fieldX2, fieldY2, EDITOR_BG);
            contactNameInput.renderSingleLine(this.fontRenderer,
                fieldX1 + 3, fieldY1, fieldX2 - 3, fieldY2, cursorOn);
        }
    }

    private void drawMessages(int x1, int y1, int x2, int y2) {
        if (messagesMode == MessagesMode.THREAD_LIST) {
            this.fontRenderer.drawString(I18n.format("sum.phone.messages.title"),
                x1 + 4, y1 + 4, TEXT_TITLE);
            if (cloud == null || cloud.threads.isEmpty()) {
                String hint = I18n.format("sum.phone.messages.empty");
                int hw = this.fontRenderer.getStringWidth(hint);
                this.fontRenderer.drawString(hint, (x1 + x2) / 2 - hw / 2, y1 + 60, TEXT_DIM);
            }
        } else if (messagesMode == MessagesMode.NEW_PICK) {
            this.fontRenderer.drawString(I18n.format("sum.phone.messages.pick_title"),
                x1 + 36, y1 + 4, TEXT_TITLE);
            if (cloud == null || cloud.contacts.isEmpty()) {
                String hint = I18n.format("sum.phone.messages.pick_empty");
                int hw = this.fontRenderer.getStringWidth(hint);
                this.fontRenderer.drawString(hint, (x1 + x2) / 2 - hw / 2, y1 + 60, TEXT_DIM);
            }
        } else if (messagesMode == MessagesMode.THREAD_VIEW) {
            drawThreadView(x1, y1, x2, y2);
        }
    }

    private void drawThreadView(int x1, int y1, int x2, int y2) {
        // Header: contact name (resolved from contacts) + number
        String partnerName = resolvePartnerLabel(currentPartner);
        this.fontRenderer.drawString(partnerName, x1 + 36, y1 + 4, TEXT_TITLE);

        // Compose row at bottom (text field + Send button is added in addMessagesButtons)
        int composeH = 18;
        int btnH = 14;
        int composeY1 = y2 - btnH - 4 - composeH - 2;
        int composeY2 = composeY1 + composeH;
        int composeX1 = x1 + 4;
        int composeX2 = x2 - 44; // leave room for Send button
        drawRect(composeX1, composeY1, composeX2, composeY2, EDITOR_BG);
        composeInput.renderSingleLine(this.fontRenderer,
            composeX1 + 3, composeY1, composeX2 - 3, composeY2, cursorOn);

        // Conversation area between header and compose
        int convoTop = y1 + 18;
        int convoBottom = composeY1 - 4;
        drawConvo(x1 + 2, convoTop, x2 - 2, convoBottom);
    }

    private void drawConvo(int x1, int y1, int x2, int y2) {
        if (cloud == null || currentPartner == null) return;
        MessageThread t = cloud.threads.get(currentPartner);
        if (t == null || t.messages.isEmpty()) {
            String hint = I18n.format("sum.phone.messages.thread_empty");
            int hw = this.fontRenderer.getStringWidth(hint);
            this.fontRenderer.drawString(hint, (x1 + x2) / 2 - hw / 2, (y1 + y2) / 2, TEXT_DIM);
            return;
        }

        int innerW = x2 - x1 - 6;
        int maxBubbleW = Math.max(40, innerW * 3 / 4);
        int lineH = this.fontRenderer.FONT_HEIGHT + 1;

        // Render bottom-up: walk messages newest→oldest, accumulating y from the bottom.
        int y = y2 - 2;
        for (int i = t.messages.size() - 1; i >= 0; i--) {
            Message m = t.messages.get(i);
            boolean me = m.senderUuid.equals(player.getUniqueID());
            List<String> wrapped = wrapText(m.text, maxBubbleW - 6);
            int bubbleH = wrapped.size() * lineH + 4;
            int bubbleW = 0;
            for (String line : wrapped) bubbleW = Math.max(bubbleW, this.fontRenderer.getStringWidth(line));
            bubbleW = Math.min(maxBubbleW, bubbleW + 6);

            int bubbleX = me ? (x2 - bubbleW - 2) : (x1 + 2);
            int bubbleY1 = y - bubbleH;
            int bubbleY2 = y;
            if (bubbleY1 < y1) break; // out of room

            drawRect(bubbleX, bubbleY1, bubbleX + bubbleW, bubbleY2,
                me ? BUBBLE_ME_BG : BUBBLE_THEM_BG);
            for (int j = 0; j < wrapped.size(); j++) {
                this.fontRenderer.drawString(wrapped.get(j),
                    bubbleX + 3, bubbleY1 + 2 + j * lineH, TEXT_LIGHT);
            }
            y = bubbleY1 - 4;
        }
    }

    /** Naive char-wrap (no word splitting) to a max pixel width. */
    private List<String> wrapText(String s, int maxWidth) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\n') {
                out.add(cur.toString());
                cur.setLength(0);
                continue;
            }
            int w = this.fontRenderer.getStringWidth(cur.toString() + c);
            if (w > maxWidth) {
                out.add(cur.toString());
                cur.setLength(0);
            }
            cur.append(c);
        }
        if (cur.length() > 0) out.add(cur.toString());
        return out;
    }

    private String resolvePartnerLabel(UUID partner) {
        if (cloud == null || partner == null) return "?";
        for (Contact c : cloud.contacts) {
            if (c.targetUuid.equals(partner)) {
                return c.displayName + (c.cachedNumber.isEmpty() ? "" : " · " + c.cachedNumber);
            }
        }
        // Not in contacts — try shortened UUID for now.
        return partner.toString().substring(0, 8);
    }

    private void drawTile(TileRegion r, int mouseX, int mouseY) {
        boolean hover = mouseX >= r.x && mouseX < r.x + r.w
                     && mouseY >= r.y && mouseY < r.y + r.h;
        switch (r.kind) {
            case HOME_BUTTON:
                drawRect(r.x, r.y, r.x + r.w, r.y + r.h, HOME_BTN_RING);
                drawRect(r.x + 2, r.y + 2, r.x + r.w - 2, r.y + r.h - 2,
                    hover ? 0xFF50545C : HOME_BTN);
                break;
            case APP_TILE: {
                boolean disabled = false;
                int bg = disabled ? APP_TILE_DISABLED : (hover ? APP_TILE_HOVER : APP_TILE_BG);
                drawRect(r.x, r.y, r.x + r.w, r.y + r.h, bg);
                drawAppGlyph(r.x, r.y, r.w, r.h, r.app);
                String label = I18n.format(r.label);
                int lw = this.fontRenderer.getStringWidth(label);
                this.fontRenderer.drawString(label,
                    r.x + (r.w - lw) / 2, r.y + r.h - 10,
                    disabled ? TEXT_DIM : TEXT_LIGHT);
                // Messages unread badge
                if (r.app == App.MESSAGES && cloud != null) {
                    int unread = cloud.totalUnread();
                    if (unread > 0) drawBadge(r.x + r.w - 8, r.y + 4, unread);
                }
                break;
            }
            case CALC_KEY: {
                int bg;
                if (r.calcKey == CalcKey.EQUALS) bg = CALC_KEY_EQ_BG;
                else if (r.calcKey != null && r.calcKey.isOperator()) bg = CALC_KEY_OP_BG;
                else bg = CALC_KEY_BG;
                if (hover) bg = brighten(bg);
                drawRect(r.x, r.y, r.x + r.w, r.y + r.h, bg);
                int lw = this.fontRenderer.getStringWidth(r.label);
                this.fontRenderer.drawString(r.label,
                    r.x + (r.w - lw) / 2, r.y + (r.h - 8) / 2, TEXT_LIGHT);
                break;
            }
            case NOTE_ROW: {
                int idx = Integer.parseInt(r.label);
                drawRect(r.x, r.y, r.x + r.w, r.y + r.h, hover ? LIST_ROW_HOVER : LIST_ROW_BG);
                String preview = "";
                if (cloud != null && idx < cloud.notes.size()) {
                    preview = firstLine(cloud.notes.get(idx));
                }
                if (preview.isEmpty()) preview = I18n.format("sum.phone.notes.untitled");
                this.fontRenderer.drawString(preview, r.x + 4, r.y + 4, TEXT_LIGHT);
                String num = "#" + (idx + 1);
                int nw = this.fontRenderer.getStringWidth(num);
                this.fontRenderer.drawString(num, r.x + r.w - 4 - nw, r.y + r.h - 10, TEXT_DIM);
                break;
            }
            case CONTACT_ROW:
            case CONTACT_PICK_ROW: {
                int idx = Integer.parseInt(r.label);
                if (cloud == null || idx >= cloud.contacts.size()) break;
                Contact c = cloud.contacts.get(idx);
                drawRect(r.x, r.y, r.x + r.w, r.y + r.h, hover ? LIST_ROW_HOVER : LIST_ROW_BG);
                this.fontRenderer.drawString(c.displayName == null ? "?" : c.displayName,
                    r.x + 4, r.y + 3, TEXT_LIGHT);
                String num = c.cachedNumber == null || c.cachedNumber.isEmpty()
                    ? "—" : c.cachedNumber;
                int nw = this.fontRenderer.getStringWidth(num);
                this.fontRenderer.drawString(num, r.x + r.w - 4 - nw, r.y + 3, TEXT_NUMBER);
                break;
            }
            case THREAD_ROW: {
                if (cloud == null) break;
                UUID partner = UUID.fromString(r.label);
                MessageThread t = cloud.threads.get(partner);
                if (t == null) break;
                drawRect(r.x, r.y, r.x + r.w, r.y + r.h, hover ? LIST_ROW_HOVER : LIST_ROW_BG);
                String name = resolvePartnerLabel(partner);
                this.fontRenderer.drawString(name, r.x + 4, r.y + 3, TEXT_LIGHT);
                Message last = t.lastMessage();
                String preview = last == null ? "" : last.text;
                if (preview.length() > 24) preview = preview.substring(0, 23) + "…";
                this.fontRenderer.drawString(preview, r.x + 4, r.y + 13, TEXT_DIM);
                if (t.unreadCount > 0) {
                    drawBadge(r.x + r.w - 8, r.y + 4, t.unreadCount);
                }
                break;
            }
        }
    }

    private void drawAppGlyph(int x, int y, int w, int h, App app) {
        // Glyph is 20×20 (was 22×22 when tiles were 50 tall); the smaller tile leaves about
        // 12px of vertical space below the glyph for the label drawn by drawTile.
        int gw = 20, gh = 20;
        int gx = x + (w - gw) / 2;
        int gy = y + 4;
        int color;
        String glyph;
        switch (app) {
            case BANKING:    color = 0xFFD4B258; glyph = "$"; break;
            case NOTES:      color = 0xFFE0C060; glyph = "N"; break;
            case CALCULATOR: color = 0xFF6DB6E0; glyph = "="; break;
            case WEATHER:    color = 0xFF8AB8E0; glyph = "*"; break;
            case MESSAGES:   color = 0xFF7AA070; glyph = "M"; break;
            case CONTACTS:   color = 0xFFB880D0; glyph = "C"; break;
            default:         color = 0xFF555555; glyph = "?"; break;
        }
        drawRect(gx, gy, gx + gw, gy + gh, color);
        int lw = this.fontRenderer.getStringWidth(glyph);
        this.fontRenderer.drawString(glyph, gx + (gw - lw) / 2, gy + (gh - 8) / 2, 0xFF000000);
    }

    private void drawBadge(int rightX, int topY, int count) {
        String s = count > 9 ? "9+" : Integer.toString(count);
        int w = this.fontRenderer.getStringWidth(s) + 4;
        int h = 8;
        drawRect(rightX - w, topY, rightX, topY + h, BADGE_BG);
        this.fontRenderer.drawString(s, rightX - w + 2, topY, TEXT_TITLE);
    }

    // ---------------------------------------------------------------------------------------
    // Input
    // ---------------------------------------------------------------------------------------

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        if (mouseButton == 0) {
            for (TileRegion r : tiles) {
                if (mouseX >= r.x && mouseX < r.x + r.w
                 && mouseY >= r.y && mouseY < r.y + r.h) {
                    onTileClick(r);
                    return;
                }
            }
        }
        super.mouseClicked(mouseX, mouseY, mouseButton);
    }

    private void onTileClick(TileRegion r) {
        switch (r.kind) {
            case HOME_BUTTON: onHomeButton(); return;
            case APP_TILE: if (r.app != null) openApp(r.app); return;
            case CALC_KEY: if (r.calcKey != null) onCalcKey(r.calcKey); return;
            case NOTE_ROW: {
                int idx = Integer.parseInt(r.label);
                if (cloud != null && idx < cloud.notes.size()) startEditingNote(idx);
                return;
            }
            case CONTACT_ROW: {
                int idx = Integer.parseInt(r.label);
                if (cloud != null && idx < cloud.contacts.size()) openContact(cloud.contacts.get(idx));
                return;
            }
            case CONTACT_PICK_ROW: {
                int idx = Integer.parseInt(r.label);
                if (cloud != null && idx < cloud.contacts.size()) {
                    openThread(cloud.contacts.get(idx).targetUuid);
                }
                return;
            }
            case THREAD_ROW:
                openThread(UUID.fromString(r.label));
                return;
        }
    }

    private void onHomeButton() {
        // Always returns to home — back-arrow handles intermediate sub-screens.
        currentApp = App.HOME;
        notesMode = NotesMode.LIST;
        contactsMode = ContactsMode.LIST;
        messagesMode = MessagesMode.THREAD_LIST;
        rebuildButtons();
    }

    private void onBackButton() {
        // Back goes up one level within the current app.
        if (currentApp == App.NOTES && notesMode == NotesMode.EDIT) {
            // Don't auto-save on back — require explicit Save. Discard is fine since Save was
            // available.
            editingNoteIndex = -1;
            noteEditor.setValue("");
            notesMode = NotesMode.LIST;
        } else if (currentApp == App.CONTACTS && contactsMode != ContactsMode.LIST) {
            contactsMode = ContactsMode.LIST;
            selectedContact = null;
        } else if (currentApp == App.MESSAGES && messagesMode != MessagesMode.THREAD_LIST) {
            messagesMode = MessagesMode.THREAD_LIST;
            currentPartner = null;
        } else {
            // For any other sub-state, fall back to home.
            currentApp = App.HOME;
        }
        rebuildButtons();
    }

    private void openApp(App app) {
        if (app == App.BANKING) {
            // Hand off to ATM GUI; replaces this screen.
            this.mc.displayGuiScreen(new GuiSumAtm(player));
            return;
        }
        if (app == App.NOTES) { notesMode = NotesMode.LIST; editingNoteIndex = -1; }
        if (app == App.CONTACTS) { contactsMode = ContactsMode.LIST; selectedContact = null; }
        if (app == App.MESSAGES) { messagesMode = MessagesMode.THREAD_LIST; currentPartner = null; }
        currentApp = app;
        rebuildButtons();
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        switch (button.id) {
            case BTN_BACK:                  onBackButton(); return;
            case BTN_NOTES_NEW:             startNewNote(); return;
            case BTN_NOTES_SAVE:            commitAndSaveNoteEdit(); return;
            case BTN_NOTES_DELETE:          deleteCurrentNote(); return;
            case BTN_CONTACTS_NEW:          startNewContact(); return;
            case BTN_CONTACTS_ADD_SUBMIT:   submitNewContact(); return;
            case BTN_CONTACTS_MESSAGE:
                if (selectedContact != null) openThread(selectedContact.targetUuid);
                return;
            case BTN_CONTACTS_DELETE:       deleteSelectedContact(); return;
            case BTN_MESSAGES_NEW:
                messagesMode = MessagesMode.NEW_PICK;
                rebuildButtons();
                return;
            case BTN_MESSAGES_SEND:         sendComposed(); return;
        }
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        // Route to the relevant text field for current screen.
        if (currentApp == App.NOTES && notesMode == NotesMode.EDIT) {
            if (noteEditor.handleKey(typedChar, keyCode)) return;
        } else if (currentApp == App.CONTACTS && contactsMode == ContactsMode.NEW) {
            if (keyCode == Keyboard.KEY_RETURN || keyCode == Keyboard.KEY_NUMPADENTER) {
                submitNewContact();
                return;
            }
            if (contactNameInput.handleKey(typedChar, keyCode)) return;
        } else if (currentApp == App.MESSAGES && messagesMode == MessagesMode.THREAD_VIEW) {
            if (keyCode == Keyboard.KEY_RETURN || keyCode == Keyboard.KEY_NUMPADENTER) {
                sendComposed();
                return;
            }
            if (composeInput.handleKey(typedChar, keyCode)) return;
        }
        super.keyTyped(typedChar, keyCode);
    }

    // ---------------------------------------------------------------------------------------
    // Calculator behavior (unchanged from v1)
    // ---------------------------------------------------------------------------------------

    private void onCalcKey(CalcKey key) {
        if (calcError && key != CalcKey.CLEAR) return;
        if (key.isDigit()) {
            int digit = key.ordinal() - CalcKey.DIGIT_0.ordinal();
            if (calcFreshEntry || calcDisplay.equals("0")) {
                calcDisplay = Integer.toString(digit);
                calcFreshEntry = false;
            } else if (calcDisplay.length() < 12) {
                calcDisplay = calcDisplay + digit;
            }
            return;
        }
        switch (key) {
            case DOT:
                if (calcFreshEntry) { calcDisplay = "0."; calcFreshEntry = false; }
                else if (!calcDisplay.contains(".")) calcDisplay = calcDisplay + ".";
                return;
            case CLEAR:
                calcDisplay = "0"; calcAccumulator = 0.0; calcPendingOp = '\0';
                calcFreshEntry = true; calcError = false;
                return;
            case NEGATE:
                if (calcDisplay.startsWith("-")) calcDisplay = calcDisplay.substring(1);
                else if (!calcDisplay.equals("0")) calcDisplay = "-" + calcDisplay;
                return;
            case PERCENT:
                try {
                    double v = Double.parseDouble(calcDisplay) / 100.0;
                    calcDisplay = formatNumber(v);
                    calcFreshEntry = true;
                } catch (NumberFormatException e) { calcError = true; }
                return;
            case ADD: applyPendingOp(); calcPendingOp = '+'; calcFreshEntry = true; return;
            case SUB: applyPendingOp(); calcPendingOp = '-'; calcFreshEntry = true; return;
            case MUL: applyPendingOp(); calcPendingOp = '*'; calcFreshEntry = true; return;
            case DIV: applyPendingOp(); calcPendingOp = '/'; calcFreshEntry = true; return;
            case EQUALS:
                applyPendingOp(); calcPendingOp = '\0'; calcFreshEntry = true; return;
            default: return;
        }
    }

    private void applyPendingOp() {
        double rhs;
        try { rhs = Double.parseDouble(calcDisplay); }
        catch (NumberFormatException e) { calcError = true; return; }
        if (calcPendingOp == '\0') { calcAccumulator = rhs; }
        else {
            switch (calcPendingOp) {
                case '+': calcAccumulator += rhs; break;
                case '-': calcAccumulator -= rhs; break;
                case '*': calcAccumulator *= rhs; break;
                case '/':
                    if (rhs == 0.0) { calcError = true; return; }
                    calcAccumulator /= rhs;
                    break;
                default: break;
            }
        }
        calcDisplay = formatNumber(calcAccumulator);
    }

    private static String formatNumber(double v) {
        if (Double.isNaN(v) || Double.isInfinite(v)) return "Err";
        if (v == Math.floor(v) && !Double.isInfinite(v) && Math.abs(v) < 1e12) {
            return Long.toString((long) v);
        }
        String s = String.format(Locale.ROOT, "%.10g", v);
        if (s.contains(".") && !s.contains("e") && !s.contains("E")) {
            int end = s.length();
            while (end > 0 && s.charAt(end - 1) == '0') end--;
            if (end > 0 && s.charAt(end - 1) == '.') end--;
            s = s.substring(0, end);
        }
        if (s.length() > 12) s = s.substring(0, 12);
        return s;
    }

    // ---------------------------------------------------------------------------------------
    // Misc helpers
    // ---------------------------------------------------------------------------------------

    private static String formatClock(long worldTime) {
        long todTicks = ((worldTime % 24000L) + 24000L) % 24000L;
        long minutesOfDay = ((todTicks + 6000L) % 24000L) * 60L / 1000L;
        long hours = minutesOfDay / 60L;
        long minutes = minutesOfDay % 60L;
        return String.format(Locale.ROOT, "%02d:%02d", hours, minutes);
    }

    private static String weatherGlyph(World w) {
        if (w.isThundering()) return "T";
        if (w.isRaining()) return "R";
        return "S";
    }

    private static int brighten(int argb) {
        int a = (argb >>> 24) & 0xFF;
        int r = Math.min(255, ((argb >> 16) & 0xFF) + 20);
        int g = Math.min(255, ((argb >> 8) & 0xFF) + 20);
        int b = Math.min(255, (argb & 0xFF) + 20);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private static String firstLine(String s) {
        int nl = s.indexOf('\n');
        String line = nl < 0 ? s : s.substring(0, nl);
        if (line.length() > 22) line = line.substring(0, 22) + "…";
        return line;
    }

    // ---------------------------------------------------------------------------------------
    // Tile region helper struct
    // ---------------------------------------------------------------------------------------

    private enum TileKind {
        HOME_BUTTON, APP_TILE, CALC_KEY, NOTE_ROW,
        CONTACT_ROW, CONTACT_PICK_ROW, THREAD_ROW
    }

    private static class TileRegion {
        final int id;
        final int x, y, w, h;
        final TileKind kind;
        final App app;
        final String label;
        CalcKey calcKey;

        TileRegion(int id, int x, int y, int w, int h, TileKind kind, App app, String label) {
            this.id = id; this.x = x; this.y = y; this.w = w; this.h = h;
            this.kind = kind; this.app = app; this.label = label;
        }
    }
}
