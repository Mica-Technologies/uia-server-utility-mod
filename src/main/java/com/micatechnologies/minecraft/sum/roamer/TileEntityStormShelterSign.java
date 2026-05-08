package com.micatechnologies.minecraft.sum.roamer;

import net.minecraft.tileentity.TileEntity;

/**
 * Minimal tile entity that re-registers its position into {@link RoamerShelterCache} on
 * load. The cache is in-memory only; without an onLoad hook, signs placed in a previous
 * session would only be re-discovered organically by roamers running their full hazard scan.
 *
 * <p>The cache deduplicates positions within ~3 blocks of each other, so calling
 * {@code recordShelter} on every chunk load is harmless even if the entry is already
 * present.
 */
public class TileEntityStormShelterSign extends TileEntity {

    @Override
    public void onLoad() {
        super.onLoad();
        if (world != null && !world.isRemote) {
            RoamerShelterCache.recordShelter(pos);
        }
    }
}
