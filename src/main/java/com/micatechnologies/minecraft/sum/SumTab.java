package com.micatechnologies.minecraft.sum;

import com.micatechnologies.minecraft.sum.atm.BlockAtmDriveThru;
import com.micatechnologies.minecraft.sum.atm.BlockAtmKiosk;
import com.micatechnologies.minecraft.sum.atm.BlockAtmWall;
import com.micatechnologies.minecraft.sum.bank.BlockBankCounter;
import com.micatechnologies.minecraft.sum.bank.BlockSafeDepositBox;
import com.micatechnologies.minecraft.sum.bank.BlockVaultDoor;
import com.micatechnologies.minecraft.sum.bank.BlockVelvetRope;
import com.micatechnologies.minecraft.sum.economy.ItemSumBill;
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
    public static ItemSumBill[] bills;

    /** Denominations the SUM bill set ships in dollars. Iterated in this order during
     *  registration so the creative tab orders bills low-to-high. */
    private static final int[] BILL_DENOMINATIONS = {1, 5, 10, 20, 50, 100, 200, 500};

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
        bills = new ItemSumBill[BILL_DENOMINATIONS.length];
        for (int i = 0; i < BILL_DENOMINATIONS.length; i++) {
            bills[i] = new ItemSumBill(BILL_DENOMINATIONS[i]);
        }
    }
}
