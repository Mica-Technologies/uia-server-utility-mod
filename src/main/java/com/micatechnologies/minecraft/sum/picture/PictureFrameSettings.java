package com.micatechnologies.minecraft.sum.picture;

import io.netty.buffer.ByteBuf;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.MathHelper;
import net.minecraftforge.fml.common.network.ByteBufUtils;

/**
 * Everything a picture frame displays and how: the image URL, the size and position of the
 * picture relative to the frame block, and how the image is fitted, rotated and lit.
 *
 * <p>Immutable. The edit GUI builds a new instance and sends it; the server {@link #clamp}s it
 * against its own limits before storing it, so a modified client cannot exceed them.
 *
 * <p>Sizes and offsets are in blocks. The picture's bottom-left corner, as seen by someone
 * facing it, starts at the frame block's bottom-left corner and is moved by the offsets, so a
 * default 1x1 picture covers exactly the block it hangs on.
 */
public final class PictureFrameSettings {

    /** How the image is fitted into the {@link #width} x {@link #height} area. */
    public enum Fit {
        /** Whole image, aspect kept, centred with empty bands if the shapes differ. */
        FIT("Fit"),
        /** Area fully covered, aspect kept, image cropped to the area. */
        FILL("Fill"),
        /** Image squashed or stretched to exactly the area. */
        STRETCH("Stretch");

        private final String label;

        Fit(String label) { this.label = label; }

        public String getLabel() { return label; }

        public Fit next() { return values()[(ordinal() + 1) % values().length]; }

        static Fit byIndex(int index) {
            Fit[] all = values();
            return index >= 0 && index < all.length ? all[index] : FIT;
        }
    }

    public static final int MAX_URL_LENGTH = 1024;
    public static final float MIN_SIZE = 0.25F;
    public static final float MIN_OPACITY = 0.05F;
    public static final int MIN_RENDER_DISTANCE = 16;

    public static final PictureFrameSettings EMPTY =
        new PictureFrameSettings("", 1.0F, 1.0F, 0.0F, 0.0F, 0, Fit.FIT, false, 1.0F, 64);

    private final String url;
    private final float width;
    private final float height;
    private final float offsetX;
    private final float offsetY;
    /** Quarter turns clockwise, 0-3. */
    private final int rotation;
    private final Fit fit;
    private final boolean glow;
    private final float opacity;
    private final int renderDistance;

    public PictureFrameSettings(String url, float width, float height, float offsetX, float offsetY,
                                int rotation, Fit fit, boolean glow, float opacity, int renderDistance) {
        this.url = url == null ? "" : url.trim();
        this.width = width;
        this.height = height;
        this.offsetX = offsetX;
        this.offsetY = offsetY;
        this.rotation = Math.floorMod(rotation, 4);
        this.fit = fit == null ? Fit.FIT : fit;
        this.glow = glow;
        this.opacity = opacity;
        this.renderDistance = renderDistance;
    }

    public String getUrl() { return url; }
    public boolean hasUrl() { return !url.isEmpty(); }
    public float getWidth() { return width; }
    public float getHeight() { return height; }
    public float getOffsetX() { return offsetX; }
    public float getOffsetY() { return offsetY; }
    public int getRotation() { return rotation; }
    public Fit getFit() { return fit; }
    public boolean isGlow() { return glow; }
    public float getOpacity() { return opacity; }
    public int getRenderDistance() { return renderDistance; }

    /**
     * This settings object held to the server's limits. Sizes become
     * {@code [MIN_SIZE, maxSize]}, offsets {@code [-maxSize, maxSize]}, opacity
     * {@code [MIN_OPACITY, 1]}, render distance {@code [MIN_RENDER_DISTANCE, maxRenderDistance]}.
     * NaN, which a hostile client can send, becomes the default for that field.
     */
    public PictureFrameSettings clamp(float maxSize, int maxRenderDistance) {
        float max = Math.max(MIN_SIZE, maxSize);
        int maxDistance = Math.max(MIN_RENDER_DISTANCE, maxRenderDistance);
        String clampedUrl = url.length() > MAX_URL_LENGTH ? url.substring(0, MAX_URL_LENGTH) : url;
        return new PictureFrameSettings(clampedUrl,
            clampFinite(width, MIN_SIZE, max, 1.0F),
            clampFinite(height, MIN_SIZE, max, 1.0F),
            clampFinite(offsetX, -max, max, 0.0F),
            clampFinite(offsetY, -max, max, 0.0F),
            rotation, fit, glow,
            clampFinite(opacity, MIN_OPACITY, 1.0F, 1.0F),
            MathHelper.clamp(renderDistance, MIN_RENDER_DISTANCE, maxDistance));
    }

    private static float clampFinite(float value, float min, float max, float fallback) {
        if (Float.isNaN(value) || Float.isInfinite(value)) {
            return MathHelper.clamp(fallback, min, max);
        }
        return MathHelper.clamp(value, min, max);
    }

    /** The farthest the picture reaches from the frame block's origin, for render culling. */
    public float reach() {
        return Math.max(Math.abs(offsetX), Math.abs(offsetY)) + Math.max(width, height) + 1.0F;
    }

    // --- Persistence and sync ---

    public NBTTagCompound writeToNBT(NBTTagCompound tag) {
        tag.setString("Url", url);
        tag.setFloat("Width", width);
        tag.setFloat("Height", height);
        tag.setFloat("OffsetX", offsetX);
        tag.setFloat("OffsetY", offsetY);
        tag.setByte("Rotation", (byte) rotation);
        tag.setByte("Fit", (byte) fit.ordinal());
        tag.setBoolean("Glow", glow);
        tag.setFloat("Opacity", opacity);
        tag.setInteger("RenderDistance", renderDistance);
        return tag;
    }

    public static PictureFrameSettings readFromNBT(NBTTagCompound tag) {
        if (!tag.hasKey("Url")) {
            return EMPTY;
        }
        return new PictureFrameSettings(tag.getString("Url"),
            tag.hasKey("Width") ? tag.getFloat("Width") : 1.0F,
            tag.hasKey("Height") ? tag.getFloat("Height") : 1.0F,
            tag.getFloat("OffsetX"), tag.getFloat("OffsetY"),
            tag.getByte("Rotation"), Fit.byIndex(tag.getByte("Fit")), tag.getBoolean("Glow"),
            tag.hasKey("Opacity") ? tag.getFloat("Opacity") : 1.0F,
            tag.hasKey("RenderDistance") ? tag.getInteger("RenderDistance") : EMPTY.renderDistance);
    }

    public void writeTo(ByteBuf buf) {
        ByteBufUtils.writeUTF8String(buf, url);
        buf.writeFloat(width);
        buf.writeFloat(height);
        buf.writeFloat(offsetX);
        buf.writeFloat(offsetY);
        buf.writeByte(rotation);
        buf.writeByte(fit.ordinal());
        buf.writeBoolean(glow);
        buf.writeFloat(opacity);
        buf.writeInt(renderDistance);
    }

    public static PictureFrameSettings readFrom(ByteBuf buf) {
        return new PictureFrameSettings(ByteBufUtils.readUTF8String(buf),
            buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat(),
            buf.readByte(), Fit.byIndex(buf.readByte()), buf.readBoolean(),
            buf.readFloat(), buf.readInt());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PictureFrameSettings)) return false;
        PictureFrameSettings s = (PictureFrameSettings) o;
        return url.equals(s.url) && width == s.width && height == s.height
            && offsetX == s.offsetX && offsetY == s.offsetY && rotation == s.rotation
            && fit == s.fit && glow == s.glow && opacity == s.opacity
            && renderDistance == s.renderDistance;
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(url, width, height, offsetX, offsetY, rotation, fit, glow,
            opacity, renderDistance);
    }
}
