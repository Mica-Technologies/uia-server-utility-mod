package com.micatechnologies.minecraft.sum;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;

public class SumCommonProxy implements SumProxy {

    @Override
    public void preInit(FMLPreInitializationEvent event) {
    }

    @Override
    public void init(FMLInitializationEvent event) {
    }

    @Override
    public void setCustomModelResourceLocation(Item item, int meta, String id) {
    }

    @Override
    public void openAccountAccessGui(EntityPlayer player) {
        // No-op on dedicated server; only the local client renders GUIs.
    }
}
