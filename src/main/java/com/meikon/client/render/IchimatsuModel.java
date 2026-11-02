package com.meikon.client.render;

import com.meikon.Meikon;
import com.meikon.entity.Ichimatsu;
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
import net.minecraft.util.Mth;

/** An Ichimatsu doll: big porcelain head under a black bob, a small body in a red kimono. */
public class IchimatsuModel extends EntityModel<Ichimatsu> {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(Meikon.id("ichimatsu"), "main");
    private static final float DEG = (float) (Math.PI / 180.0);
    private final ModelPart root;
    private final ModelPart body;
    private final ModelPart head;
    private final ModelPart rightArm;
    private final ModelPart leftArm;

    public IchimatsuModel(ModelPart root) {
        this.root = root;
        this.body = root.getChild("body");
        this.head = root.getChild("head");
        this.rightArm = root.getChild("right_arm");
        this.leftArm = root.getChild("left_arm");
    }

    public static LayerDefinition createLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        root.addOrReplaceChild("body", CubeListBuilder.create().texOffs(0, 13).addBox(-3.0f, -8.0f, -2.0f, 6, 8, 4),
                PartPose.offset(0.0f, 24.0f, 0.0f));
        PartDefinition head = root.addOrReplaceChild("head", CubeListBuilder.create().texOffs(0, 0).addBox(-3.0f, -6.0f, -3.0f, 6, 6, 6),
                PartPose.offset(0.0f, 16.0f, 0.0f));
        head.addOrReplaceChild("hair", CubeListBuilder.create().texOffs(24, 0).addBox(-3.5f, -6.5f, -3.5f, 7, 6, 7), PartPose.ZERO);
        root.addOrReplaceChild("right_arm", CubeListBuilder.create().texOffs(20, 13).addBox(-2.0f, 0.0f, -1.0f, 2, 5, 2),
                PartPose.offset(-3.0f, 16.5f, 0.0f));
        root.addOrReplaceChild("left_arm", CubeListBuilder.create().texOffs(28, 13).addBox(0.0f, 0.0f, -1.0f, 2, 5, 2),
                PartPose.offset(3.0f, 16.5f, 0.0f));
        return LayerDefinition.create(mesh, 64, 32);
    }

    @Override
    public void setupAnim(Ichimatsu e, float limbSwing, float limbSwingAmount, float age, float netHeadYaw, float headPitch) {
        head.yRot = netHeadYaw * DEG;
        head.xRot = headPitch * DEG;
        head.zRot = 0.0f;
        float amt = Math.min(1.0f, limbSwingAmount * 2.0f);
        body.zRot = Mth.sin(limbSwing * 2.6f) * 0.12f * amt;
        head.zRot = body.zRot * 0.6f;
        rightArm.xRot = -0.35f + Mth.sin(limbSwing * 2.6f) * 0.2f * amt;
        leftArm.xRot = -0.35f - Mth.sin(limbSwing * 2.6f) * 0.2f * amt;
        rightArm.zRot = 0.12f;
        leftArm.zRot = -0.12f;
    }

    @Override
    public void renderToBuffer(PoseStack poseStack, VertexConsumer consumer, int light, int overlay, float r, float g, float b, float a) {
        root.render(poseStack, consumer, light, overlay, r, g, b, a);
    }
}
