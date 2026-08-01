package com.micatechnologies.minecraft.sum.jobs;

import com.micatechnologies.minecraft.sum.SumConstants;
import com.micatechnologies.minecraft.sum.SumRegistry;
import com.micatechnologies.minecraft.sum.SumTab;
import com.micatechnologies.minecraft.sum.atm.SumNetwork;
import com.micatechnologies.minecraft.sum.economy.EconomyBridge;
import com.micatechnologies.minecraft.sum.omceapi.OmceParty;
import com.micatechnologies.minecraft.sum.omceapi.OmceProtocol;
import java.util.List;
import java.util.Locale;
import net.minecraft.block.Block;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.PropertyDirection;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemBlock;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.Mirror;
import net.minecraft.util.Rotation;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

/**
 * Job board: a wall-mounted bulletin block. Right-click to browse server-wide listings posted
 * via {@code /sum job post}. Storage is shared via {@link JobBoardSavedData}, so every job
 * board on the server shows the same set of listings.
 *
 * <p>The block itself is purely a portal to the GUI — it has no per-block state. Posting
 * happens through a chat command (simpler than embedding a form GUI), and the GUI's only
 * write path is "remove my listing", which is gated on the original poster's UUID.
 */
public class BlockJobBoard extends Block {

    public static final PropertyDirection FACING = PropertyDirection.create(
        "facing", EnumFacing.Plane.HORIZONTAL);

    // FACING = direction the board points outward. Body sits on the wall behind it
    // (opposite side of the cell from FACING), matching the rotated model.
    private static final AxisAlignedBB BB_NORTH = new AxisAlignedBB(0.0, 0.0, 14.0 / 16.0, 1.0, 1.0, 1.0);
    private static final AxisAlignedBB BB_SOUTH = new AxisAlignedBB(0.0, 0.0, 0.0, 1.0, 1.0, 2.0 / 16.0);
    private static final AxisAlignedBB BB_WEST = new AxisAlignedBB(14.0 / 16.0, 0.0, 0.0, 1.0, 1.0, 1.0);
    private static final AxisAlignedBB BB_EAST = new AxisAlignedBB(0.0, 0.0, 0.0, 2.0 / 16.0, 1.0, 1.0);

    public BlockJobBoard() {
        super(Material.WOOD);
        setRegistryName(SumConstants.MOD_NAMESPACE, "job_board");
        setTranslationKey(SumConstants.MOD_NAMESPACE + ".job_board");
        setHardness(1.0F);
        setResistance(5.0F);
        setSoundType(SoundType.WOOD);
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
    public boolean onBlockActivated(World world, BlockPos pos, IBlockState state,
                                    EntityPlayer player, EnumHand hand, EnumFacing facing,
                                    float hitX, float hitY, float hitZ) {
        // Server-authoritative: job listings live in JobBoardSavedData (server-only), so we push
        // a snapshot to the client which opens GuiJobBoard. (The client can't read the SavedData
        // on a dedicated server.) Before sending, reclaim escrow from this player's own expired
        // listings so timed-out bounties refund automatically the next time they visit a board.
        if (!world.isRemote && player instanceof EntityPlayerMP) {
            EntityPlayerMP mp = (EntityPlayerMP) player;
            JobBoardSavedData data = JobBoardSavedData.get(world);
            long now = System.currentTimeMillis();
            refundExpired(mp, data, now);
            SumNetwork.CHANNEL.sendTo(new PacketOpenJobBoard(data.getActive(now)), mp);
        }
        return true;
    }

    /** Refunds and removes this player's expired listings, crediting the held escrow back. */
    private static void refundExpired(EntityPlayerMP player, JobBoardSavedData data, long now) {
        List<JobListing> expired = data.takeExpiredFor(player.getUniqueID(), now);
        double refund = 0.0;
        for (JobListing l : expired) {
            refund += l.reward;
        }
        if (refund > 0.0) {
            // takeExpiredFor has already removed the listings, so a refused credit would destroy
            // the escrow outright. Put them back if that happens; the next board interaction will
            // retry the reclaim.
            final double reclaimed = refund;
            EconomyBridge.adjustBalance(player, refund,
                OmceProtocol.TX_JOB_REFUND, OmceParty.system("escrow.jobs"),
                "Reclaimed escrow from " + expired.size() + " expired job listing(s)",
                () -> restoreExpired(player, expired, reclaimed));
            TextComponentString msg = new TextComponentString(TextFormatting.GREEN
                + "Reclaimed $" + String.format(Locale.ROOT, "%.2f", refund)
                + " escrow from " + expired.size() + " expired listing(s).");
            player.sendMessage(msg);
        }
    }

    /** Puts expired listings back on the board after a refused escrow reclaim. */
    private static void restoreExpired(EntityPlayerMP player, List<JobListing> expired,
        double refund) {
        JobBoardSavedData data = JobBoardSavedData.get(player.world);
        for (JobListing listing : expired) {
            data.addListing(listing);
        }
        player.sendMessage(new TextComponentString(TextFormatting.RED
            + "The $" + String.format(Locale.ROOT, "%.2f", refund)
            + " escrow reclaim was declined — your expired listings were restored."));
    }

    @Override
    public AxisAlignedBB getBoundingBox(IBlockState state, IBlockAccess source, BlockPos pos) {
        switch (state.getValue(FACING)) {
            case SOUTH: return BB_SOUTH;
            case WEST:  return BB_WEST;
            case EAST:  return BB_EAST;
            case NORTH:
            default:    return BB_NORTH;
        }
    }

    @Override
    public boolean isFullCube(IBlockState state) { return false; }

    @Override
    public boolean isOpaqueCube(IBlockState state) { return false; }

    @Override
    public boolean canPlaceBlockOnSide(World world, BlockPos pos, EnumFacing side) {
        return side.getAxis().isHorizontal();
    }

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
