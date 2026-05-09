package com.micatechnologies.minecraft.sum.signpost;

import com.micatechnologies.minecraft.sum.SumConstants;
import com.micatechnologies.minecraft.sum.SumRegistry;
import com.micatechnologies.minecraft.sum.SumTab;
import javax.annotation.Nullable;
import net.minecraft.block.Block;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemBlock;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

/**
 * Wayfinding signpost block — a wooden post that holds up to 7 named directional arms. Arms
 * are added/removed via {@code /sum signpost} commands; right-clicking the block dumps the
 * current arm list to chat. Arms render in-world via {@link TESRSignpost}.
 */
public class BlockSignpost extends Block {

    private static final AxisAlignedBB POST_AABB =
        new AxisAlignedBB(0.4, 0.0, 0.4, 0.6, 1.0, 0.6);

    public BlockSignpost() {
        super(Material.WOOD);
        setRegistryName(SumConstants.MOD_NAMESPACE, "signpost");
        setTranslationKey(SumConstants.MOD_NAMESPACE + ".signpost");
        setHardness(1.5F);
        setSoundType(SoundType.WOOD);
        setCreativeTab(SumTab.TAB);

        SumRegistry.registerBlock(this);
        ItemBlock itemBlock = new ItemBlock(this);
        itemBlock.setRegistryName(getRegistryName());
        SumRegistry.registerItem(itemBlock);
    }

    @Override
    public boolean hasTileEntity(IBlockState state) {
        return true;
    }

    @Override
    @Nullable
    public TileEntity createTileEntity(World world, IBlockState state) {
        return new TileEntitySignpost();
    }

    @Override
    public AxisAlignedBB getBoundingBox(IBlockState state, IBlockAccess source, BlockPos pos) {
        return POST_AABB;
    }

    @Override
    @Nullable
    public AxisAlignedBB getCollisionBoundingBox(IBlockState state, IBlockAccess world, BlockPos pos) {
        return POST_AABB;
    }

    @Override
    public boolean isOpaqueCube(IBlockState state) {
        return false;
    }

    @Override
    public boolean isFullCube(IBlockState state) {
        return false;
    }

    @Override
    @SuppressWarnings("deprecation")
    public net.minecraft.util.BlockRenderLayer getRenderLayer() {
        return net.minecraft.util.BlockRenderLayer.CUTOUT;
    }

    @Override
    public boolean onBlockActivated(World world, BlockPos pos, IBlockState state,
                                    EntityPlayer player, EnumHand hand, EnumFacing facing,
                                    float hitX, float hitY, float hitZ) {
        if (world.isRemote) {
            return true;
        }
        TileEntity te = world.getTileEntity(pos);
        if (!(te instanceof TileEntitySignpost)) {
            return false;
        }
        TileEntitySignpost signpost = (TileEntitySignpost) te;
        if (signpost.getArms().isEmpty()) {
            player.sendMessage(new TextComponentString(TextFormatting.GRAY
                + "Empty signpost. Use "
                + TextFormatting.WHITE + "/sum signpost add <angle> <label>"
                + TextFormatting.GRAY + " while looking at the post."));
            return true;
        }
        player.sendMessage(new TextComponentString(TextFormatting.GOLD
            + "Signpost (" + signpost.getArms().size() + "/" + TileEntitySignpost.MAX_ARMS + " arms):"));
        int i = 0;
        for (SignpostArm arm : signpost.getArms()) {
            player.sendMessage(new TextComponentString(
                TextFormatting.WHITE + "  " + i + ". "
                    + TextFormatting.AQUA + (arm.getLabel().isEmpty() ? "(no label)" : arm.getLabel())
                    + TextFormatting.GRAY + " @ " + String.format("%.0f", arm.getAngleDegrees()) + "°"));
            i++;
        }
        return true;
    }
}
