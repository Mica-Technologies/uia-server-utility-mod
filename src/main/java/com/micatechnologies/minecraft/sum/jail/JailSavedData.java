package com.micatechnologies.minecraft.sum.jail;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.World;
import net.minecraft.world.storage.MapStorage;
import net.minecraft.world.storage.WorldSavedData;
import net.minecraftforge.common.util.Constants;

/**
 * Every jail cell and every inmate, saved once for the whole server.
 *
 * <p>Unlike plots this lives in the global {@link World#getMapStorage()}, not per dimension: an
 * inmate is a fact about a player, not about a world, and a cell in the Nether must still hold a
 * player who logs in to the overworld. Each cell records its own dimension instead.
 */
public class JailSavedData extends WorldSavedData {

    private static final String NAME = "sum_jail";

    /** Keyed by {@link JailCell#key}; insertion order is the order {@code /jails} lists them. */
    private final Map<String, JailCell> cells = new LinkedHashMap<>();
    private final Map<UUID, Inmate> inmates = new LinkedHashMap<>();

    public JailSavedData() { super(NAME); }
    public JailSavedData(String name) { super(name); }

    public static JailSavedData get(World world) {
        MapStorage storage = world.getMapStorage();
        if (storage == null) return new JailSavedData();
        JailSavedData data = (JailSavedData) storage.getOrLoadData(JailSavedData.class, NAME);
        if (data == null) {
            data = new JailSavedData();
            storage.setData(NAME, data);
        }
        return data;
    }

    // --- Cells ---

    @Nullable
    public JailCell getCell(String name) { return cells.get(JailCell.key(name)); }

    public Collection<JailCell> getCells() { return cells.values(); }

    /** Adds or replaces the cell of that name. Returns the cell it replaced, if any. */
    @Nullable
    public JailCell putCell(JailCell cell) {
        JailCell previous = cells.put(JailCell.key(cell.getName()), cell);
        markDirty();
        return previous;
    }

    public boolean removeCell(String name) {
        if (cells.remove(JailCell.key(name)) == null) return false;
        markDirty();
        return true;
    }

    /**
     * The cell to use when {@code /jail} names none: the one holding the fewest inmates, the
     * earliest-made on a tie, so a facility fills its cells evenly rather than stacking everyone
     * into the first.
     */
    @Nullable
    public JailCell leastOccupiedCell() {
        JailCell best = null;
        int bestCount = Integer.MAX_VALUE;
        for (JailCell cell : cells.values()) {
            int count = countInmates(cell.getName());
            if (count < bestCount) {
                best = cell;
                bestCount = count;
            }
        }
        return best;
    }

    // --- Inmates ---

    @Nullable
    public Inmate getInmate(UUID playerId) { return inmates.get(playerId); }

    public boolean isJailed(UUID playerId) { return inmates.containsKey(playerId); }

    public Collection<Inmate> getInmates() { return inmates.values(); }

    @Nullable
    public Inmate findInmateByName(String name) {
        for (Inmate inmate : inmates.values()) {
            if (inmate.getPlayerName().equalsIgnoreCase(name)) return inmate;
        }
        return null;
    }

    public List<Inmate> getInmatesIn(String cellName) {
        List<Inmate> out = new ArrayList<>();
        for (Inmate inmate : inmates.values()) {
            if (inmate.getCellName().equalsIgnoreCase(cellName)) out.add(inmate);
        }
        return out;
    }

    public int countInmates(String cellName) {
        return getInmatesIn(cellName).size();
    }

    public void putInmate(Inmate inmate) {
        inmates.put(inmate.getPlayerId(), inmate);
        markDirty();
    }

    @Nullable
    public Inmate removeInmate(UUID playerId) {
        Inmate removed = inmates.remove(playerId);
        if (removed != null) markDirty();
        return removed;
    }

    /** Updates the stored name when an inmate logs in under a new one. */
    public void refreshName(UUID playerId, String currentName) {
        Inmate inmate = inmates.get(playerId);
        if (inmate != null && !inmate.getPlayerName().equals(currentName)) {
            inmate.setPlayerName(currentName);
            markDirty();
        }
    }

    // --- NBT ---

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        cells.clear();
        inmates.clear();
        NBTTagList cellList = nbt.getTagList("Cells", Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < cellList.tagCount(); i++) {
            JailCell cell = JailCell.readFromNBT(cellList.getCompoundTagAt(i));
            if (cell != null) cells.put(JailCell.key(cell.getName()), cell);
        }
        NBTTagList inmateList = nbt.getTagList("Inmates", Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < inmateList.tagCount(); i++) {
            Inmate inmate = Inmate.readFromNBT(inmateList.getCompoundTagAt(i));
            if (inmate != null) inmates.put(inmate.getPlayerId(), inmate);
        }
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound nbt) {
        NBTTagList cellList = new NBTTagList();
        for (JailCell cell : cells.values()) cellList.appendTag(cell.writeToNBT());
        nbt.setTag("Cells", cellList);
        NBTTagList inmateList = new NBTTagList();
        for (Inmate inmate : inmates.values()) inmateList.appendTag(inmate.writeToNBT());
        nbt.setTag("Inmates", inmateList);
        return nbt;
    }
}
