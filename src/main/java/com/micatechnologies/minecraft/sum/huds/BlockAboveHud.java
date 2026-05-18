package com.micatechnologies.minecraft.sum.huds;

import cc.polyfrost.oneconfig.hud.SingleTextHud;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * Display name of the block directly above the player's head (eye level + 1). Modeled
 * on EvergreenHUD's block-above element — useful for parkour / vertical-tunnel
 * navigation where you want to know what's about to drop on you.
 */
public class BlockAboveHud extends SingleTextHud {

    public BlockAboveHud() {
        super("Above:", true, 5, 5);
    }

    @Override
    protected String getText(boolean example) {
        if (example) {
            return "Stone";
        }
        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayer player = mc.player;
        World world = mc.world;
        if (player == null || world == null) {
            return "—";
        }
        BlockPos above = new BlockPos(player.posX, player.posY + player.getEyeHeight() + 1, player.posZ);
        IBlockState state = world.getBlockState(above);
        // Vanilla Block.getLocalizedName uses the item drop's stack — works for the
        // common block-shaped blocks; for technical blocks we fall back to the registry
        // name to avoid "tile.null.name".
        try {
            ItemStack stack = new ItemStack(state.getBlock(), 1, state.getBlock().getMetaFromState(state));
            if (!stack.isEmpty()) {
                return stack.getDisplayName();
            }
        } catch (Exception ignored) {
            // Some modded blocks throw from getMetaFromState in unusual states.
        }
        return state.getBlock().getLocalizedName();
    }
}
