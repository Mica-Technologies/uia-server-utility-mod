package com.micatechnologies.minecraft.sum.roamer;

import net.minecraft.client.model.ModelPlayer;
import net.minecraft.client.model.ModelRenderer;

/**
 * Roamer model variant that samples one slice of the shared roamer atlas.
 *
 * <p>The atlas stacks {@code variantCount} 64x64 modern-format skins vertically.
 * Each variant rebuilds the rendered biped parts after {@code super(...)} so
 * their UV V offsets are shifted by {@code variantIndex * 64}, and bumps the
 * model's logical {@code textureHeight} to {@code variantCount * 64} so the
 * fresh ModelRenderers normalize V against the full atlas height.
 *
 * <p>The original parts created by ModelPlayer's constructor are left in
 * {@link net.minecraft.client.model.ModelBase#boxList} but are no longer
 * referenced by the {@code bipedHead}/{@code bipedBody}/etc. fields, so
 * {@link net.minecraft.client.model.ModelBiped#render} ignores them. All wear
 * (overlay) layers stay disabled for the FPS win called out in the
 * NPC_OPTIMIZE_PLAN doc.
 */
public class ModelRoamer extends ModelPlayer {

    public ModelRoamer(int variantIndex, int variantCount) {
        super(0.0F, false);

        final int vShift = variantIndex * 64;
        this.textureHeight = variantCount * 64;

        this.bipedHead = new ModelRenderer(this, 0, 0 + vShift);
        this.bipedHead.addBox(-4.0F, -8.0F, -4.0F, 8, 8, 8, 0.0F);
        this.bipedHead.setRotationPoint(0.0F, 0.0F, 0.0F);

        this.bipedBody = new ModelRenderer(this, 16, 16 + vShift);
        this.bipedBody.addBox(-4.0F, 0.0F, -2.0F, 8, 12, 4, 0.0F);
        this.bipedBody.setRotationPoint(0.0F, 0.0F, 0.0F);

        this.bipedRightArm = new ModelRenderer(this, 40, 16 + vShift);
        this.bipedRightArm.addBox(-3.0F, -2.0F, -2.0F, 4, 12, 4, 0.0F);
        this.bipedRightArm.setRotationPoint(-5.0F, 2.0F, 0.0F);

        this.bipedLeftArm = new ModelRenderer(this, 32, 48 + vShift);
        this.bipedLeftArm.addBox(-1.0F, -2.0F, -2.0F, 4, 12, 4, 0.0F);
        this.bipedLeftArm.setRotationPoint(5.0F, 2.0F, 0.0F);

        this.bipedRightLeg = new ModelRenderer(this, 0, 16 + vShift);
        this.bipedRightLeg.addBox(-2.0F, 0.0F, -2.0F, 4, 12, 4, 0.0F);
        this.bipedRightLeg.setRotationPoint(-1.9F, 12.0F, 0.0F);

        this.bipedLeftLeg = new ModelRenderer(this, 16, 48 + vShift);
        this.bipedLeftLeg.addBox(-2.0F, 0.0F, -2.0F, 4, 12, 4, 0.0F);
        this.bipedLeftLeg.setRotationPoint(1.9F, 12.0F, 0.0F);

        this.bipedHeadwear.showModel = false;
        this.bipedBodyWear.showModel = false;
        this.bipedLeftArmwear.showModel = false;
        this.bipedRightArmwear.showModel = false;
        this.bipedLeftLegwear.showModel = false;
        this.bipedRightLegwear.showModel = false;
    }
}
