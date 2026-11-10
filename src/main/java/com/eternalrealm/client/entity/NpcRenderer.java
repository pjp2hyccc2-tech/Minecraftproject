package com.eternalrealm.client.entity;

import com.eternalrealm.client.entity.layer.RigGlowLayer;
import com.eternalrealm.client.entity.model.NpcModel;
import com.eternalrealm.client.entity.model.RigTables;
import com.eternalrealm.npc.forge.NpcEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

/**
 * Renderer of the town NPCs: the appearance's texture ({@code textures/entity/npc/<id>.png}, unknown ids fall back to
 * the citizen), its model scale, the emissive layer of the appearances that glow, and the name tag as for any mob.
 */
public class NpcRenderer extends MobRenderer<NpcEntity, NpcModel> {
    private static final float[] WHITE = {1.0F, 1.0F, 1.0F};
    private static final float WALK_SPEED = 2.2F;
    private static final float SHADOW = 0.5F;

    private final RigTables tables = ERRigs.NPC;

    public NpcRenderer(EntityRendererProvider.Context context) {
        super(context, new NpcModel(context.bakeLayer(ERModelLayers.npc()), ERRigs.NPC, WALK_SPEED), SHADOW);
        addLayer(new RigGlowLayer<>(this, this::glowTexture, (npc, age) -> WHITE));
    }

    private int index(NpcEntity npc) {
        return this.tables.indexOf(npc.getAppearance());
    }

    @Override
    public ResourceLocation getTextureLocation(NpcEntity npc) {
        return EntityTextures.body("npc", this.tables.variants()[index(npc)]);
    }

    private ResourceLocation glowTexture(NpcEntity npc) {
        int index = index(npc);
        return this.tables.glows()[index] ? EntityTextures.glow("npc", this.tables.variants()[index]) : null;
    }

    @Override
    protected void scale(NpcEntity npc, PoseStack poseStack, float partialTick) {
        float scale = this.tables.scales()[index(npc)];
        poseStack.scale(scale, scale, scale);
    }

    @Override
    public void render(NpcEntity npc, float entityYaw, float partialTick, PoseStack poseStack, MultiBufferSource buffers,
                       int packedLight) {
        this.shadowRadius = SHADOW * this.tables.scales()[index(npc)];
        super.render(npc, entityYaw, partialTick, poseStack, buffers, packedLight);
    }

    @Override
    protected float getFlipDegrees(NpcEntity npc) {
        return 0.0F;
    }
}
