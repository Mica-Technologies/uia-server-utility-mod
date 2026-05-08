package com.micatechnologies.minecraft.sum;

import com.micatechnologies.minecraft.sum.atm.BlockAtmDriveThru;
import com.micatechnologies.minecraft.sum.atm.BlockAtmKiosk;
import com.micatechnologies.minecraft.sum.atm.BlockAtmWall;
import com.micatechnologies.minecraft.sum.bank.BlockBankCounter;
import com.micatechnologies.minecraft.sum.bank.BlockSafeDepositBox;
import com.micatechnologies.minecraft.sum.bank.BlockVaultDoor;
import com.micatechnologies.minecraft.sum.bank.BlockVelvetRope;
import com.micatechnologies.minecraft.sum.economy.BlockBillChanger;
import com.micatechnologies.minecraft.sum.economy.BlockBillsDisplay;
import com.micatechnologies.minecraft.sum.economy.ItemAccountAccess;
import com.micatechnologies.minecraft.sum.economy.ItemSumBill;
import com.micatechnologies.minecraft.sum.economy.ItemSumPacket;
import com.micatechnologies.minecraft.sum.roamer.BlockStormShelterSign;
import com.micatechnologies.minecraft.sum.roamer.ItemRoamerConfigurator;
import com.micatechnologies.minecraft.sum.shop.BlockSumShop;
import com.micatechnologies.minecraft.sum.trash.BlockTrashCan;
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
    public static ItemAccountAccess phone;
    public static ItemAccountAccess debitCard;
    public static BlockSumShop shop;
    public static BlockBillChanger billChanger;
    public static ItemSumPacket[] packets;
    public static BlockBillsDisplay billsDisplay;
    public static BlockTrashCan trashCan;
    public static BlockStormShelterSign stormShelterSign;

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
        phone = new ItemAccountAccess("phone");
        debitCard = new ItemAccountAccess("debit_card");
        shop = new BlockSumShop();
        billChanger = new BlockBillChanger();
        packets = new ItemSumPacket[BILL_DENOMINATIONS.length];
        for (int i = 0; i < BILL_DENOMINATIONS.length; i++) {
            packets[i] = new ItemSumPacket(BILL_DENOMINATIONS[i]);
        }
        billsDisplay = new BlockBillsDisplay();
        trashCan = new BlockTrashCan();
        stormShelterSign = new BlockStormShelterSign();
    }
}
