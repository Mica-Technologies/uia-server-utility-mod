package com.micatechnologies.minecraft.sum;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;

public interface SumProxy {

    void preInit(FMLPreInitializationEvent event);

    void init(FMLInitializationEvent event);

    void setCustomModelResourceLocation(Item item, int meta, String id);

    /** Opens the ATM GUI on the local client. Server-side proxy is a no-op (the local client
     *  is the one that displays GUIs; dedicated servers don't render). Used by items that need
     *  to open the GUI without going through {@code player.openGui}, which has been observed to
     *  silently drop GUI-open packets when invoked from {@link Item#onItemRightClick}. */
    void openAccountAccessGui(EntityPlayer player);

    /** Opens the multi-app phone GUI on the local client. {@code enableBanking=false} hides
     *  the banking app — used for the shared desk phone block (which isn't bound to an
     *  account). Server-side proxy is a no-op. */
    void openPhoneGui(EntityPlayer player, boolean enableBanking);
}
