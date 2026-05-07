package com.micatechnologies.minecraft.sum.atm;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.network.IGuiHandler;

/**
 * GUI dispatcher for SUM blocks. Use {@link net.minecraft.entity.player.EntityPlayer#openGui}
 * with one of the {@code GUI_*} constants from a server-side block activation handler. Forge
 * routes the call to {@link #getServerGuiElement} on the integrated/dedicated server and
 * {@link #getClientGuiElement} on the client.
 */
public class SumGuiHandler implements IGuiHandler {

    public static final int GUI_ATM = 0;

    @Override
    public Object getServerGuiElement(int id, EntityPlayer player, World world, int x, int y, int z) {
        // ATM is GuiScreen-only (no inventory slots), so no Container is needed server-side.
        return null;
    }

    @Override
    public Object getClientGuiElement(int id, EntityPlayer player, World world, int x, int y, int z) {
        if (id == GUI_ATM) {
            return new GuiSumAtm(player);
        }
        return null;
    }
}
