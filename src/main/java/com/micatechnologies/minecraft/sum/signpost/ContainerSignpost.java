package com.micatechnologies.minecraft.sum.signpost;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;

/**
 * Empty container for the signpost GUI — exists only to satisfy the {@code openGui}
 * plumbing. Signposts have no inventory; the GUI sends arm changes via
 * {@link PacketSignpostUpdate} directly.
 */
public class ContainerSignpost extends Container {

    public ContainerSignpost() {}

    @Override
    public boolean canInteractWith(EntityPlayer player) {
        return true;
    }
}
