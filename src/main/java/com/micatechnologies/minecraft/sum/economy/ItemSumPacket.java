package com.micatechnologies.minecraft.sum.economy;

import com.micatechnologies.minecraft.sum.SumConstants;
import com.micatechnologies.minecraft.sum.SumRegistry;
import com.micatechnologies.minecraft.sum.SumTab;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;

/**
 * Bundled-bills item: one packet = {@link #BILLS_PER_PACKET} bills of a given denomination.
 * Players use packets to compress large amounts of cash into a single inventory slot — handy
 * for high-value purchases or offline bank vaults that would otherwise demand a 64-slot stack
 * of $500 bills.
 *
 * <p>Registered as {@code sum:packet_<denomination>} for each of the eight bill denominations.
 * The bill changer block ({@link com.micatechnologies.minecraft.sum.economy.BlockBillChanger})
 * is what produces and consumes packets — they cannot be crafted directly.
 */
public class ItemSumPacket extends Item {

    /** Number of bills in one packet. Chosen as 64 so a single inventory slot of bills (one
     *  full stack) bundles cleanly into a single packet. */
    public static final int BILLS_PER_PACKET = 64;

    private final int denomination;

    public ItemSumPacket(int denomination) {
        this.denomination = denomination;
        setRegistryName(SumConstants.MOD_NAMESPACE, "packet_" + denomination);
        setTranslationKey(SumConstants.MOD_NAMESPACE + ".packet_" + denomination);
        setMaxStackSize(64);
        setCreativeTab(SumTab.TAB);
        SumRegistry.registerItem(this);
    }

    public int getDenomination() {
        return denomination;
    }

    /** Total dollar value of one packet of this denomination. */
    public int getPacketValue() {
        return denomination * BILLS_PER_PACKET;
    }

    @Override
    public void addInformation(ItemStack stack, @Nullable World world, List<String> tooltip,
                               ITooltipFlag flag) {
        tooltip.add(TextFormatting.GRAY + "" + BILLS_PER_PACKET
            + " bills · " + TextFormatting.GREEN + "$" + getPacketValue());
    }

    /** Look up the SUM packet item for a given denomination. Returns null for unknown denoms. */
    @Nullable
    public static ItemSumPacket packetFor(int denomination) {
        if (SumTab.packets == null) return null;
        for (ItemSumPacket p : SumTab.packets) {
            if (p != null && p.denomination == denomination) return p;
        }
        return null;
    }
}
