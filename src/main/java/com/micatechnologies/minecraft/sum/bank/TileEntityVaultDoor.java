package com.micatechnologies.minecraft.sum.bank;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ITickable;

/**
 * Server-side state for {@link BlockVaultDoor}: owner UUID + name (for display), SHA-256
 * passcode hash (cleartext never stored), and an auto-close timer that runs on the
 * world tick.
 */
public class TileEntityVaultDoor extends TileEntity implements ITickable {

    /** How long the door stays open after a successful unlock, in world ticks (20/sec). */
    private static final long AUTO_CLOSE_TICKS = 100L;

    @Nullable
    private UUID ownerUuid;
    private String ownerName = "";
    private String passcodeHash = "";
    /** Tick count at which the door was last opened, or -1 if currently closed. */
    private long openedAtTick = -1L;

    public boolean isClaimed() {
        return ownerUuid != null;
    }

    @Nullable
    public UUID getOwnerUuid() {
        return ownerUuid;
    }

    public String getOwnerName() {
        return ownerName;
    }

    public boolean isOwner(EntityPlayer player) {
        return ownerUuid != null && ownerUuid.equals(player.getUniqueID());
    }

    public boolean hasPasscode() {
        return !passcodeHash.isEmpty();
    }

    /** Claims an unowned vault for {@code player}. Idempotent on already-owned vaults
     *  (returns false instead of overwriting). */
    public boolean claim(EntityPlayer player) {
        if (ownerUuid != null) {
            return false;
        }
        this.ownerUuid = player.getUniqueID();
        this.ownerName = player.getName();
        markDirty();
        return true;
    }

    /** Wipes ownership and passcode. Used by {@code /sum vault disown}. */
    public void disown() {
        this.ownerUuid = null;
        this.ownerName = "";
        this.passcodeHash = "";
        markDirty();
    }

    /** Sets the passcode (empty string clears it). The cleartext is hashed; the hash
     *  is what we persist. */
    public void setPasscode(String passcode) {
        this.passcodeHash = passcode == null || passcode.isEmpty() ? "" : hash(passcode);
        markDirty();
    }

    public boolean checkPasscode(String input) {
        return !passcodeHash.isEmpty() && passcodeHash.equals(hash(input));
    }

    /** Marks the door as just-opened so the auto-close timer starts ticking. */
    public void onOpened() {
        this.openedAtTick = world.getTotalWorldTime();
        markDirty();
    }

    @Override
    public void update() {
        if (world.isRemote) {
            return;
        }
        if (openedAtTick < 0L) {
            return;
        }
        // Defensive: if the block somehow flipped to closed without going through closeDoor
        // (e.g. broken + replaced), reset and bail.
        if (!(world.getBlockState(pos).getBlock() instanceof BlockVaultDoor)
            || !world.getBlockState(pos).getValue(BlockVaultDoor.OPEN)) {
            openedAtTick = -1L;
            markDirty();
            return;
        }
        if (world.getTotalWorldTime() - openedAtTick > AUTO_CLOSE_TICKS) {
            BlockVaultDoor.closeDoor(world, pos);
            openedAtTick = -1L;
            markDirty();
        }
    }

    private static String hash(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] bytes = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is mandated by every JRE since 1.4. Unreachable in practice.
            return "";
        }
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound nbt) {
        super.writeToNBT(nbt);
        if (ownerUuid != null) {
            nbt.setUniqueId("owner", ownerUuid);
        }
        nbt.setString("ownerName", ownerName);
        nbt.setString("passcodeHash", passcodeHash);
        nbt.setLong("openedAtTick", openedAtTick);
        return nbt;
    }

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        super.readFromNBT(nbt);
        this.ownerUuid = nbt.hasUniqueId("owner") ? nbt.getUniqueId("owner") : null;
        this.ownerName = nbt.getString("ownerName");
        this.passcodeHash = nbt.getString("passcodeHash");
        this.openedAtTick = nbt.hasKey("openedAtTick") ? nbt.getLong("openedAtTick") : -1L;
    }
}
