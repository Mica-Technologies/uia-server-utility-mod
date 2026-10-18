package com.micatechnologies.minecraft.sum.jail;

import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.nbt.NBTTagCompound;

/**
 * A jailed player: who, which cell, who jailed them, and when the sentence ends.
 *
 * <p>{@link #releaseAtMillis} is wall-clock time, not play time, so a sentence keeps running while
 * the inmate is offline; a player cannot wait one out by logging off, but nor are they held past
 * it. {@code 0} means no end: they stay until someone runs {@code /unjail}.
 */
public class Inmate {

    public static final long NO_RELEASE = 0L;

    private final UUID playerId;
    private String playerName;
    private final String cellName;
    private final String jailedBy;
    private final long jailedAtMillis;
    private final long releaseAtMillis;

    public Inmate(UUID playerId, String playerName, String cellName, String jailedBy,
                  long jailedAtMillis, long releaseAtMillis) {
        this.playerId = playerId;
        this.playerName = playerName;
        this.cellName = cellName;
        this.jailedBy = jailedBy;
        this.jailedAtMillis = jailedAtMillis;
        this.releaseAtMillis = releaseAtMillis;
    }

    public UUID getPlayerId() { return playerId; }
    public String getPlayerName() { return playerName; }
    public String getCellName() { return cellName; }
    public String getJailedBy() { return jailedBy; }
    public long getJailedAtMillis() { return jailedAtMillis; }
    public long getReleaseAtMillis() { return releaseAtMillis; }

    /** Kept current on login so {@code /jails} and {@code /unjail} tab completion show the name in use. */
    void setPlayerName(String playerName) { this.playerName = playerName; }

    public boolean hasRelease() {
        return releaseAtMillis != NO_RELEASE;
    }

    public boolean isSentenceOver(long nowMillis) {
        return hasRelease() && nowMillis >= releaseAtMillis;
    }

    /** Milliseconds left to serve, or -1 for an open-ended sentence. Never negative otherwise. */
    public long remainingMillis(long nowMillis) {
        if (!hasRelease()) {
            return -1L;
        }
        return Math.max(0L, releaseAtMillis - nowMillis);
    }

    public NBTTagCompound writeToNBT() {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setUniqueId("Player", playerId);
        tag.setString("Name", playerName);
        tag.setString("Cell", cellName);
        tag.setString("JailedBy", jailedBy);
        tag.setLong("JailedAt", jailedAtMillis);
        tag.setLong("ReleaseAt", releaseAtMillis);
        return tag;
    }

    @Nullable
    public static Inmate readFromNBT(NBTTagCompound tag) {
        if (!tag.hasUniqueId("Player") || !tag.hasKey("Cell")) {
            return null;
        }
        return new Inmate(tag.getUniqueId("Player"), tag.getString("Name"), tag.getString("Cell"),
            tag.getString("JailedBy"), tag.getLong("JailedAt"), tag.getLong("ReleaseAt"));
    }
}
