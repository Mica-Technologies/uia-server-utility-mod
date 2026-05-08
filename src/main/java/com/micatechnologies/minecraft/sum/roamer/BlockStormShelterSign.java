package com.micatechnologies.minecraft.sum.roamer;

import com.micatechnologies.minecraft.sum.SumConstants;
import com.micatechnologies.minecraft.sum.SumRegistry;
import com.micatechnologies.minecraft.sum.SumTab;
import net.minecraft.block.Block;
import net.minecraft.block.ITileEntityProvider;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

/**
 * Storm-shelter sign: a thin floor plaque that registers its position into
 * {@link RoamerShelterCache} so roamers responding to a storm alarm consider it as a shelter
 * candidate without first having to organically discover the spot. Position registration
 * happens both on initial placement and on chunk reload (via the TE's {@code onLoad}), so
 * existing signs survive server restarts.
 *
 * <p>Closes the loop in the existing storm-shelter system: admins put a sign in a known-good
 * spot, roamers seek it more reliably than they'd find it by random search.
 */
public class BlockStormShelterSign extends Block implements ITileEntityProvider {

    /** Thin plaque AABB — 2/16 of a block tall, full footprint. Roamers can walk over it,
     *  and isShelterPosition's "can't see sky" check is satisfied if the sign is placed
     *  inside a building (i.e. there's a roof above). */
    private static final AxisAlignedBB PLATE_AABB = new AxisAlignedBB(
        0.0, 0.0, 0.0, 1.0, 2.0 / 16.0, 1.0);

    public BlockStormShelterSign() {
        super(Material.IRON);
        setRegistryName(SumConstants.MOD_NAMESPACE, "storm_shelter_sign");
        setTranslationKey(SumConstants.MOD_NAMESPACE + ".storm_shelter_sign");
        setHardness(0.8F);
        setResistance(5.0F);
        setSoundType(SoundType.METAL);
        setCreativeTab(SumTab.TAB);

        SumRegistry.registerBlock(this);
        ItemBlock itemBlock = new ItemBlock(this);
        itemBlock.setRegistryName(getRegistryName());
        SumRegistry.registerItem(itemBlock);
    }

    @Override
    public TileEntity createNewTileEntity(World world, int meta) {
        return new TileEntityStormShelterSign();
    }

    @Override
    public void onBlockPlacedBy(World world, BlockPos pos, IBlockState state,
                                EntityLivingBase placer, ItemStack stack) {
        if (!world.isRemote) {
            RoamerShelterCache.recordSignedShelter(pos);
        }
    }

    @Override
    public AxisAlignedBB getBoundingBox(IBlockState state, IBlockAccess source, BlockPos pos) {
        return PLATE_AABB;
    }

    @Override
    public boolean isFullCube(IBlockState state) { return false; }

    @Override
    public boolean isOpaqueCube(IBlockState state) { return false; }
}
