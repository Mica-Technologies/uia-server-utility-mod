package com.micatechnologies.minecraft.sum.roamer;

import com.micatechnologies.minecraft.sum.SumConstants;
import net.minecraft.client.model.ModelPlayer;
import net.minecraft.client.renderer.entity.RenderLivingBase;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.util.ResourceLocation;

public class RenderRoamer extends RenderLivingBase<EntityRoamer> {

    private static final ResourceLocation[] TEXTURES = {
        new ResourceLocation(SumConstants.MOD_NAMESPACE, "textures/entity/roamer_1.png"),
        new ResourceLocation(SumConstants.MOD_NAMESPACE, "textures/entity/roamer_2.png"),
        new ResourceLocation(SumConstants.MOD_NAMESPACE, "textures/entity/roamer_3.png"),
        new ResourceLocation(SumConstants.MOD_NAMESPACE, "textures/entity/roamer_4.png"),
        new ResourceLocation(SumConstants.MOD_NAMESPACE, "textures/entity/roamer_5.png"),
        new ResourceLocation(SumConstants.MOD_NAMESPACE, "textures/entity/roamer_6.png"),
    };

    public RenderRoamer(RenderManager manager) {
        super(manager, buildModel(), 0.5F);
    }

    /**
     * Builds the player model with all decorative overlay layers disabled. The default
     * {@link ModelPlayer} renders five "wear" parts (jacket, sleeves, pant legs) plus a hat
     * overlay on top of the base biped, which roughly doubles the cube count per draw call.
     * Roamer textures don't use the overlay UV regions, so disabling them is a free FPS win.
     */
    private static ModelPlayer buildModel() {
        ModelPlayer model = new ModelPlayer(0.0F, false);
        model.bipedBodyWear.showModel = false;
        model.bipedLeftArmwear.showModel = false;
        model.bipedRightArmwear.showModel = false;
        model.bipedLeftLegwear.showModel = false;
        model.bipedRightLegwear.showModel = false;
        model.bipedHeadwear.showModel = false;
        return model;
    }

    @Override
    protected ResourceLocation getEntityTexture(EntityRoamer entity) {
        int index = Math.abs((int)(entity.getUniqueID().getLeastSignificantBits() % TEXTURES.length));
        return TEXTURES[index];
    }
}
