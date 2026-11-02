package com.birdiecraft.client.entity;

import com.birdiecraft.BirdieCraft;
import com.birdiecraft.entity.CpuGolferEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

public class CpuGolferRenderer extends MobRenderer<CpuGolferEntity, CpuGolferModel> {
    private static final ResourceLocation[] SKINS = {
            BirdieCraft.id("textures/entity/golfer_riku.png"),
            BirdieCraft.id("textures/entity/golfer_hana.png"),
            BirdieCraft.id("textures/entity/golfer_tetsu.png"),
            BirdieCraft.id("textures/entity/golfer_mia.png")
    };

    public CpuGolferRenderer(EntityRendererProvider.Context context) {
        super(context, new CpuGolferModel(context.bakeLayer(CpuGolferModel.LAYER)), 0.5F);
    }

    @Override
    public ResourceLocation getTextureLocation(CpuGolferEntity entity) {
        int s = entity.getSkin();
        return SKINS[Math.floorMod(s, SKINS.length)];
    }
}
