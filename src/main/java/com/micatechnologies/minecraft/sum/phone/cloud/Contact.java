package com.micatechnologies.minecraft.sum.phone.cloud;

import java.util.UUID;
import net.minecraft.nbt.NBTTagCompound;

/**
 * One entry in a player's {@link PhoneCloudData#contacts} list. {@code displayName} is what the
 * owning player chose to call this contact (defaults to the target's MC name); {@code
 * cachedNumber} is the target's phone number at the time the contact was saved, kept so the UI
 * can show numbers without round-tripping to the server. The cache is refreshed by the server
 * each time it builds a cloud snapshot for the owner.
 */
public class Contact {

    public UUID targetUuid;
    public String displayName;
    public String cachedNumber;

    public Contact() {}

    public Contact(UUID targetUuid, String displayName, String cachedNumber) {
        this.targetUuid = targetUuid;
        this.displayName = displayName;
        this.cachedNumber = cachedNumber == null ? "" : cachedNumber;
    }

    public NBTTagCompound writeNbt() {
        NBTTagCompound t = new NBTTagCompound();
        t.setUniqueId("uuid", targetUuid);
        t.setString("name", displayName == null ? "" : displayName);
        t.setString("num", cachedNumber == null ? "" : cachedNumber);
        return t;
    }

    public static Contact readNbt(NBTTagCompound t) {
        Contact c = new Contact();
        c.targetUuid = t.getUniqueId("uuid");
        c.displayName = t.getString("name");
        c.cachedNumber = t.getString("num");
        return c;
    }
}
