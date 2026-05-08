package com.micatechnologies.minecraft.sum;

import com.micatechnologies.minecraft.sum.atm.BlockAtmDriveThru;
import com.micatechnologies.minecraft.sum.atm.BlockAtmKiosk;
import com.micatechnologies.minecraft.sum.atm.BlockAtmWall;
import com.micatechnologies.minecraft.sum.bank.BlockBankCounter;
import com.micatechnologies.minecraft.sum.bank.BlockSafeDepositBox;
import com.micatechnologies.minecraft.sum.bank.BlockVaultDoor;
import com.micatechnologies.minecraft.sum.bank.BlockVelvetRope;
import com.micatechnologies.minecraft.sum.roamer.ItemRoamerConfigurator;
import com.micatechnologies.minecraft.sum.roamer.ItemRoamerSpawnEgg;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.ItemStack;

public class SumTab {

    public static final CreativeTabs TAB = new CreativeTabs(SumConstants.MOD_NAMESPACE) {
        @Override
        public ItemStack createIcon() {
            return new ItemStack(SumTab.roamerSpawnEgg);
        }
    };

    public static ItemRoamerSpawnEgg roamerSpawnEgg;
    public static ItemRoamerConfigurator roamerConfigurator;
    public static BlockAtmKiosk atmKiosk;
    public static BlockAtmWall atmWall;
    public static BlockAtmDriveThru atmDriveThru;
    public static BlockBankCounter bankCounter;
    public static BlockSafeDepositBox safeDepositBox;
    public static BlockVelvetRope velvetRope;
    public static BlockVaultDoor vaultDoor;

    public static void initTabElements() {
        roamerSpawnEgg = new ItemRoamerSpawnEgg();
        roamerConfigurator = new ItemRoamerConfigurator();
        atmKiosk = new BlockAtmKiosk();
        atmWall = new BlockAtmWall();
        atmDriveThru = new BlockAtmDriveThru();
        bankCounter = new BlockBankCounter();
        safeDepositBox = new BlockSafeDepositBox();
        velvetRope = new BlockVelvetRope();
        vaultDoor = new BlockVaultDoor();
    }
}
