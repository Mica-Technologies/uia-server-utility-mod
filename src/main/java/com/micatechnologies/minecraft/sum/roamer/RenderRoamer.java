package com.micatechnologies.minecraft.sum.roamer;

import com.micatechnologies.minecraft.sum.SumConstants;
import net.minecraft.client.model.ModelBiped;
import net.minecraft.client.renderer.entity.RenderBiped;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.util.ResourceLocation;

public class RenderRoamer extends RenderBiped<EntityRoamer> {

    private static final ResourceLocation[] TEXTURES = {
        new ResourceLocation(SumConstants.MOD_NAMESPACE, "textures/entity/roamer_1.png"),
        new ResourceLocation(SumConstants.MOD_NAMESPACE, "textures/entity/roamer_2.png"),
        new ResourceLocation(SumConstants.MOD_NAMESPACE, "textures/entity/roamer_3.png"),
        new ResourceLocation(SumConstants.MOD_NAMESPACE, "textures/entity/roamer_4.png"),
        new ResourceLocation(SumConstants.MOD_NAMESPACE, "textures/entity/roamer_5.png"),
        new ResourceLocation(SumConstants.MOD_NAMESPACE, "textures/entity/roamer_6.png"),
    };

    public RenderRoamer(RenderManager manager) {
        super(manager, new ModelBiped(), 0.5F);
    }

    @Override
    protected ResourceLocation getEntityTexture(EntityRoamer entity) {
        int index = Math.abs((int)(entity.getUniqueID().getLeastSignificantBits() % TEXTURES.length));
        return TEXTURES[index];
    }
}
