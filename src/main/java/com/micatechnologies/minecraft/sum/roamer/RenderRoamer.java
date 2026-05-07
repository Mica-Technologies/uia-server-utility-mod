package com.micatechnologies.minecraft.sum.roamer;

import com.micatechnologies.minecraft.sum.SumConstants;
import net.minecraft.client.model.ModelBase;
import net.minecraft.client.renderer.entity.RenderLivingBase;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.util.ResourceLocation;

public class RenderRoamer extends RenderLivingBase<EntityRoamer> {

    /**
     * Number of variants stacked vertically in roamer_atlas.png. Must match
     * the persona count produced by tools/roamer_atlas/generate.py — the atlas
     * height is {@code 64 * VARIANT_COUNT}.
     */
    public static final int VARIANT_COUNT = 16;

    private static final ResourceLocation ATLAS = new ResourceLocation(
        SumConstants.MOD_NAMESPACE, "textures/entity/roamer_atlas.png"
    );

    private static final ModelBase[] MODELS = buildModels();

    private static ModelBase[] buildModels() {
        ModelBase[] models = new ModelBase[VARIANT_COUNT];
        for (int i = 0; i < VARIANT_COUNT; i++) {
            models[i] = new ModelRoamer(i, VARIANT_COUNT);
        }
        return models;
    }

    public RenderRoamer(RenderManager manager) {
        super(manager, MODELS[0], 0.5F);
    }

    @Override
    public void doRender(EntityRoamer entity, double x, double y, double z, float entityYaw, float partialTicks) {
        this.mainModel = MODELS[pickVariant(entity)];
        super.doRender(entity, x, y, z, entityYaw, partialTicks);
    }

    private static int pickVariant(EntityRoamer entity) {
        long bits = entity.getUniqueID().getLeastSignificantBits();
        int idx = (int) (bits % VARIANT_COUNT);
        return idx < 0 ? idx + VARIANT_COUNT : idx;
    }

    @Override
    protected ResourceLocation getEntityTexture(EntityRoamer entity) {
        return ATLAS;
    }
}
