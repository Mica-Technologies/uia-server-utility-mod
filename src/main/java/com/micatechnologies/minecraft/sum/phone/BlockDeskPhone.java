package com.micatechnologies.minecraft.sum.phone;

import com.micatechnologies.minecraft.sum.Sum;
import com.micatechnologies.minecraft.sum.SumConstants;
import com.micatechnologies.minecraft.sum.SumRegistry;
import com.micatechnologies.minecraft.sum.SumTab;
import com.micatechnologies.minecraft.sum.atm.SumGuiHandler;
import com.micatechnologies.minecraft.sum.phone.cloud.PhoneCloudSavedData;
import javax.annotation.Nullable;
import net.minecraft.block.Block;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.PropertyDirection;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.Mirror;
import net.minecraft.util.Rotation;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

/**
 * Desk phone block — a shared "public phone" that anyone can right-click. Opens the same
 * multi-app phone GUI as the phone item, but with the banking app hidden (since this isn't
 * bound to any one player's account). Each player who uses it sees their own cloud
 * (contacts, messages, notes), routed by their player UUID server-side.
 *
 * <p>Each placed block carries its own phone number via {@link TileEntityDeskPhone},
 * distinct from whichever player is using it at the moment. The exchange (middle 3
 * digits of the AAA-EEE-XXXX number) is derived from the block's chunk so phones placed
 * in one chunk share their middle 3 digits — mirroring how real-world exchange codes
 * cluster geographically. When the block is broken its number is released back to the
 * pool by {@link PhoneCloudSavedData#releaseNumber}.</p>
 */
public class BlockDeskPhone extends Block {

    public static final PropertyDirection FACING = PropertyDirection.create(
        "facing", EnumFacing.Plane.HORIZONTAL);

    // Matches the union of the three cuboids in models/block/desk_phone.json:
    // body 2..14×0..2×2..14, cradle 2..14×2..5×8..14, handset 3..13×5..7×9..13. The bounding
    // box must enclose the visible model so right-click raytraces hit the block — earlier
    // versions had a 5px-tall plate BB while the model rendered as a full cube, which let
    // clicks on the upper face pass through to whatever was behind it.
    private static final AxisAlignedBB BB = new AxisAlignedBB(
        2.0 / 16.0, 0.0, 2.0 / 16.0, 14.0 / 16.0, 7.0 / 16.0, 14.0 / 16.0);

    public BlockDeskPhone() {
        super(Material.IRON);
        setRegistryName(SumConstants.MOD_NAMESPACE, "desk_phone");
        setTranslationKey(SumConstants.MOD_NAMESPACE + ".desk_phone");
        setHardness(1.5F);
        setResistance(8.0F);
        setSoundType(SoundType.METAL);
        setCreativeTab(SumTab.TAB);
        setDefaultState(blockState.getBaseState().withProperty(FACING, EnumFacing.NORTH));

        SumRegistry.registerBlock(this);
        ItemBlock itemBlock = new ItemBlock(this);
        itemBlock.setRegistryName(getRegistryName());
        SumRegistry.registerItem(itemBlock);
    }

    @Override
    public IBlockState getStateForPlacement(World world, BlockPos pos, EnumFacing facing,
                                            float hitX, float hitY, float hitZ, int meta,
                                            EntityLivingBase placer) {
        return getDefaultState().withProperty(FACING, placer.getHorizontalFacing().getOpposite());
    }

    @Override
    public boolean hasTileEntity(IBlockState state) {
        return true;
    }

    @Override
    @Nullable
    public TileEntity createTileEntity(World world, IBlockState state) {
        return new TileEntityDeskPhone();
    }

    /**
     * Server-side: as soon as the block lands, allocate the phone's number so the
     * desk phone is "ringable" before anyone right-clicks. Doing it on placement (rather
     * than first activation) also guarantees the TE is synced to clients with a non-null
     * number by the time any player can open the GUI, so the home screen doesn't flash
     * a "Loading…" placeholder.
     */
    @Override
    public void onBlockPlacedBy(World world, BlockPos pos, IBlockState state,
                                EntityLivingBase placer, ItemStack stack) {
        super.onBlockPlacedBy(world, pos, state, placer, stack);
        if (world.isRemote) {
            return;
        }
        TileEntity te = world.getTileEntity(pos);
        if (te instanceof TileEntityDeskPhone) {
            PhoneCloudSavedData cloud = PhoneCloudSavedData.get(world);
            ((TileEntityDeskPhone) te).ensureNumberAllocated(cloud);
            // Push the updated NBT so the client sees the number on the very next
            // chunk-update tick (otherwise the TE-update packet only fires when the
            // chunk is re-streamed).
            te.markDirty();
            world.notifyBlockUpdate(pos, state, state, 3);
        }
    }

    /**
     * Release the desk phone's number back into the allocation pool when the block is
     * destroyed, so a placed-then-broken sequence doesn't permanently strand the number.
     */
    @Override
    public void breakBlock(World world, BlockPos pos, IBlockState state) {
        if (!world.isRemote) {
            TileEntity te = world.getTileEntity(pos);
            if (te instanceof TileEntityDeskPhone) {
                ((TileEntityDeskPhone) te).releaseNumber(PhoneCloudSavedData.get(world));
            }
        }
        super.breakBlock(world, pos, state);
    }

    @Override
    public boolean onBlockActivated(World world, BlockPos pos, IBlockState state,
                                    EntityPlayer player, EnumHand hand, EnumFacing facing,
                                    float hitX, float hitY, float hitZ) {
        // Screen-only GUIs (no Container) must be opened on BOTH sides in 1.12.2 Forge.
        // FMLNetworkHandler.openGui gates the OpenGui packet behind `remoteGuiContainer != null`
        // — if getServerGuiElement returns null (our case, since the phone has no inventory),
        // the server-side call is a no-op and the OPEN_WINDOW packet is never sent. The
        // client-side branch of that same method (line 109-113) calls showGuiScreen directly
        // when the player is EntityPlayerSP, which is the actual mechanism that opens the GUI.
        // Guarding with `!world.isRemote` skips that client branch, leaving nothing to fire.
        player.openGui(Sum.instance, SumGuiHandler.GUI_DESK_PHONE, world,
            pos.getX(), pos.getY(), pos.getZ());
        return true;
    }

    @Override
    public AxisAlignedBB getBoundingBox(IBlockState state, IBlockAccess source, BlockPos pos) {
        return BB;
    }

    @Override
    public boolean isFullCube(IBlockState state) { return false; }

    @Override
    public boolean isOpaqueCube(IBlockState state) { return false; }

    @Override
    public IBlockState getStateFromMeta(int meta) {
        return getDefaultState().withProperty(FACING, EnumFacing.byHorizontalIndex(meta));
    }

    @Override
    public int getMetaFromState(IBlockState state) {
        return state.getValue(FACING).getHorizontalIndex();
    }

    @Override
    public IBlockState withRotation(IBlockState state, Rotation rot) {
        return state.withProperty(FACING, rot.rotate(state.getValue(FACING)));
    }

    @Override
    public IBlockState withMirror(IBlockState state, Mirror mirror) {
        return state.withRotation(mirror.toRotation(state.getValue(FACING)));
    }

    @Override
    protected BlockStateContainer createBlockState() {
        return new BlockStateContainer(this, FACING);
    }
}
