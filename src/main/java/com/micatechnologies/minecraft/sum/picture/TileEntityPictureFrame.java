package com.micatechnologies.minecraft.sum.picture;

import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.SPacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * A picture frame's {@link PictureFrameSettings} and who may change them, synced to clients so
 * {@link TESRPictureFrame} can draw the picture.
 *
 * <p>The owner is whoever placed the frame. Only the owner and operators may edit it; see
 * {@link #canEdit}.
 */
public class TileEntityPictureFrame extends TileEntity {

    private PictureFrameSettings settings = PictureFrameSettings.EMPTY;
    @Nullable private UUID ownerId;
    private String ownerName = "";

    public PictureFrameSettings getSettings() {
        return settings;
    }

    public void setSettings(PictureFrameSettings settings) {
        this.settings = settings;
        markDirty();
        if (world != null && !world.isRemote) {
            world.notifyBlockUpdate(pos, world.getBlockState(pos), world.getBlockState(pos), 3);
        }
    }

    public void setOwner(EntityPlayer player) {
        this.ownerId = player.getUniqueID();
        this.ownerName = player.getName();
        markDirty();
    }

    public String getOwnerName() {
        return ownerName;
    }

    /** The placer, or an operator. A frame placed before owners were recorded is ops-only. */
    public boolean canEdit(EntityPlayer player) {
        return player.getUniqueID().equals(ownerId)
            || player.canUseCommand(2, "sum.pictureframe.admin");
    }

    // --- Rendering bounds ---

    /**
     * A box around everything the picture can cover, so it is still drawn when the frame block
     * itself is off-screen but a large picture is not. Conservative rather than exact: a cube of
     * the picture's reach in every direction.
     */
    @Override
    @SideOnly(Side.CLIENT)
    public AxisAlignedBB getRenderBoundingBox() {
        double r = settings.reach();
        return new AxisAlignedBB(pos).grow(r);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public double getMaxRenderDistanceSquared() {
        int distance = Math.min(settings.getRenderDistance(), PictureClientSettings.maxRenderDistance());
        return (double) distance * distance;
    }

    // --- NBT and sync ---

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound nbt) {
        super.writeToNBT(nbt);
        nbt.setTag("Picture", settings.writeToNBT(new NBTTagCompound()));
        if (ownerId != null) {
            nbt.setUniqueId("Owner", ownerId);
            nbt.setString("OwnerName", ownerName);
        }
        return nbt;
    }

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        super.readFromNBT(nbt);
        settings = PictureFrameSettings.readFromNBT(nbt.getCompoundTag("Picture"));
        ownerId = nbt.hasUniqueId("Owner") ? nbt.getUniqueId("Owner") : null;
        ownerName = nbt.getString("OwnerName");
    }

    @Override
    public NBTTagCompound getUpdateTag() {
        return writeToNBT(new NBTTagCompound());
    }

    @Override
    public SPacketUpdateTileEntity getUpdatePacket() {
        return new SPacketUpdateTileEntity(pos, 0, getUpdateTag());
    }

    @Override
    public void onDataPacket(NetworkManager net, SPacketUpdateTileEntity pkt) {
        readFromNBT(pkt.getNbtCompound());
    }
}
