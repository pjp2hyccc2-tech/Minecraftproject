package com.eternalrealm.client.guildhouse;

import com.eternalrealm.core.ModInfo;
import com.eternalrealm.guild.house.forge.TrainingDummy;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.resources.ResourceLocation;

/**
 * The training dummy's model (64 x 64 texture, tools/art/guild_house_blocks.py): a round wooden base, a post, a
 * stuffed sack body bound with rope, a sack head and a crossbar for arms. Everything sways together from the base when
 * the dummy is hit (the renderer turns the whole model).
 */
public class TrainingDummyModel extends EntityModel<TrainingDummy> {
    public static final ModelLayerLocation LAYER =
            new ModelLayerLocation(new ResourceLocation(ModInfo.MOD_ID, "training_dummy"), "main");

    private static final float PIVOT = 22.0F;

    private final ModelPart root;
    private final ModelPart top;

    public TrainingDummyModel(ModelPart root) {
        this.root = root;
        this.top = root.getChild("top");
    }

    /** Model space: y grows downwards, 24 = the ground; the upper part pivots on the post just above the base. */
    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition parts = mesh.getRoot();
        parts.addOrReplaceChild("base", CubeListBuilder.create().texOffs(0, 48).addBox(-5.0F, 22.0F, -5.0F, 10, 2, 10),
                PartPose.ZERO);
        PartDefinition top = parts.addOrReplaceChild("top", CubeListBuilder.create()
                        .texOffs(56, 0).addBox(-1.0F, -10.0F, -1.0F, 2, 10, 2),        // the post
                PartPose.offset(0.0F, PIVOT, 0.0F));
        top.addOrReplaceChild("body", CubeListBuilder.create().texOffs(0, 16).addBox(-4.5F, -22.0F, -2.5F, 9, 12, 5),
                PartPose.ZERO);
        top.addOrReplaceChild("head", CubeListBuilder.create().texOffs(0, 0).addBox(-4.0F, -30.0F, -4.0F, 8, 8, 8),
                PartPose.ZERO);
        top.addOrReplaceChild("arms", CubeListBuilder.create().texOffs(0, 36).addBox(-11.0F, -20.0F, -1.0F, 22, 2, 2),
                PartPose.ZERO);
        top.addOrReplaceChild("hands", CubeListBuilder.create()
                        .texOffs(48, 36).addBox(-12.0F, -21.0F, -1.5F, 3, 4, 3)
                        .texOffs(48, 43).addBox(9.0F, -21.0F, -1.5F, 3, 4, 3),
                PartPose.ZERO);
        return LayerDefinition.create(mesh, 64, 64);
    }

    @Override
    public void setupAnim(TrainingDummy dummy, float limbSwing, float limbSwingAmount, float ageInTicks,
                          float netHeadYaw, float headPitch) {
        float since = (dummy.level().getGameTime() - dummy.lastHit) + (ageInTicks - (int) ageInTicks);
        float sway = since < 10.0F ? (float) Math.sin(since / 1.6F * Math.PI) * (10.0F - since) / 10.0F : 0.0F;
        top.xRot = sway * 0.16F;
        top.zRot = sway * 0.05F;
        top.y = PIVOT;
    }

    @Override
    public void renderToBuffer(PoseStack poseStack, VertexConsumer buffer, int packedLight, int packedOverlay,
                               float red, float green, float blue, float alpha) {
        root.render(poseStack, buffer, packedLight, packedOverlay, red, green, blue, alpha);
    }
}
