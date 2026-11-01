package com.frostbound.client;

import com.frostbound.FrostboundMod;
import com.frostbound.entity.CitizenEntity;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.resources.ResourceLocation;

/** Citizens in hooded winter coats (four looks). */
public final class CitizenRenderer extends HumanoidMobRenderer<CitizenEntity, PlayerModel<CitizenEntity>> {
    private static final ResourceLocation[] TEXTURES = {
            FrostboundMod.id("textures/entity/citizen_0.png"),
            FrostboundMod.id("textures/entity/citizen_1.png"),
            FrostboundMod.id("textures/entity/citizen_2.png"),
            FrostboundMod.id("textures/entity/citizen_3.png")};

    public CitizenRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new PlayerModel<>(ctx.bakeLayer(ModelLayers.PLAYER), false), 0.45f);
    }

    @Override
    public ResourceLocation getTextureLocation(CitizenEntity entity) {
        return TEXTURES[Math.floorMod(entity.look(), TEXTURES.length)];
    }
}
