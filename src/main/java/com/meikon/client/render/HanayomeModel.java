package com.meikon.client.render;

import com.meikon.Meikon;
import com.meikon.entity.Hanayome;
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
import net.minecraft.world.entity.Mob;

/**
 * The bride: ~2.5 blocks tall in a white 白無垢 that drags on the floor, a 綿帽子 hood, long black hair hanging
 * over her face, arms that reach past her knees. Her head is always tilted. When she runs she leans forward
 * with both arms reaching for you and her head jerks.
 */
public class HanayomeModel<T extends Mob> extends EntityModel<T> {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(Meikon.id("hanayome"), "main");
    private static final float DEG = (float) (Math.PI / 180.0);

    private final ModelPart root;
    private final ModelPart skirt;
    private final ModelPart body;
    private final ModelPart head;
    private final ModelPart rightArm;
    private final ModelPart leftArm;
    private final ModelPart rightForearm;
    private final ModelPart leftForearm;

    public Hanayome.State state = Hanayome.State.WANDER;
    public float duck;
    public boolean phantom;

    public HanayomeModel(ModelPart root) {
        this.root = root;
        this.skirt = root.getChild("skirt");
        this.body = root.getChild("body");
        this.head = body.getChild("head");
        this.rightArm = body.getChild("right_arm");
        this.leftArm = body.getChild("left_arm");
        this.rightForearm = rightArm.getChild("right_forearm");
        this.leftForearm = leftArm.getChild("left_forearm");
    }

    public static LayerDefinition createLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        root.addOrReplaceChild("skirt", CubeListBuilder.create().texOffs(34, 21).addBox(-6.0f, 0.0f, -4.0f, 12, 14, 8),
                PartPose.offset(0.0f, 10.0f, 0.0f));
        PartDefinition body = root.addOrReplaceChild("body", CubeListBuilder.create().texOffs(0, 19).addBox(-5.0f, -16.0f, -3.5f, 10, 16, 7),
                PartPose.offset(0.0f, 10.0f, 0.0f));
        body.addOrReplaceChild("obi", CubeListBuilder.create().texOffs(36, 43).addBox(-3.0f, -2.5f, 0.0f, 6, 5, 3),
                PartPose.offset(0.0f, -6.0f, 3.5f));
        PartDefinition head = body.addOrReplaceChild("head", CubeListBuilder.create().texOffs(0, 0).addBox(-3.5f, -8.0f, -3.5f, 7, 8, 7),
                PartPose.offset(0.0f, -16.0f, 0.0f));
        head.addOrReplaceChild("hood", CubeListBuilder.create().texOffs(28, 0).addBox(-4.5f, -9.5f, -4.5f, 9, 10, 9), PartPose.ZERO);
        head.addOrReplaceChild("hair_back", CubeListBuilder.create().texOffs(64, 0).addBox(-4.0f, -2.0f, 3.6f, 8, 20, 1), PartPose.ZERO);
        head.addOrReplaceChild("hair_front", CubeListBuilder.create().texOffs(82, 0).addBox(-3.5f, -7.0f, -4.7f, 7, 14, 1), PartPose.ZERO);
        PartDefinition ra = body.addOrReplaceChild("right_arm", CubeListBuilder.create().texOffs(74, 21).addBox(-3.0f, -1.0f, -2.5f, 4, 12, 5),
                PartPose.offset(-5.5f, -15.0f, 0.0f));
        ra.addOrReplaceChild("right_forearm", CubeListBuilder.create()
                .texOffs(0, 42).addBox(-1.0f, 0.0f, -1.0f, 2, 10, 2)
                .texOffs(16, 42).addBox(-1.5f, 10.0f, -1.5f, 3, 5, 2), PartPose.offset(-1.0f, 10.0f, 0.0f));
        PartDefinition la = body.addOrReplaceChild("left_arm", CubeListBuilder.create().texOffs(92, 21).addBox(-1.0f, -1.0f, -2.5f, 4, 12, 5),
                PartPose.offset(5.5f, -15.0f, 0.0f));
        la.addOrReplaceChild("left_forearm", CubeListBuilder.create()
                .texOffs(8, 42).addBox(-1.0f, 0.0f, -1.0f, 2, 10, 2)
                .texOffs(26, 42).addBox(-1.5f, 10.0f, -1.5f, 3, 5, 2), PartPose.offset(1.0f, 10.0f, 0.0f));
        return LayerDefinition.create(mesh, 128, 64);
    }

    @Override
    public void setupAnim(T entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
        float t = ageInTicks;
        body.xRot = 0.0f;
        body.yRot = 0.0f;
        body.zRot = 0.0f;
        skirt.zRot = 0.0f;
        skirt.xRot = 0.0f;
        head.yRot = netHeadYaw * DEG;
        head.xRot = headPitch * DEG;
        head.zRot = 0.24f + Mth.sin(t * 0.05f) * 0.05f;
        rightArm.xRot = 0.0f;
        rightArm.yRot = 0.0f;
        rightArm.zRot = 0.04f;
        leftArm.xRot = 0.0f;
        leftArm.yRot = 0.0f;
        leftArm.zRot = -0.04f;
        rightForearm.xRot = -0.05f;
        leftForearm.xRot = -0.05f;
        float amt = Math.min(1.0f, limbSwingAmount);
        switch (state) {
            case CHASE, CAPTURE -> {
                body.xRot = state == Hanayome.State.CAPTURE ? 0.5f : 0.38f;
                rightArm.xRot = -1.45f + Mth.sin(t * 0.9f) * 0.16f;
                leftArm.xRot = -1.55f + Mth.cos(t * 0.85f) * 0.16f;
                rightArm.zRot = 0.18f;
                leftArm.zRot = -0.18f;
                rightForearm.xRot = -0.25f;
                leftForearm.xRot = -0.25f;
                float jerk = Mth.sin(t * 2.3f) * Mth.sin(t * 0.7f);
                head.zRot += jerk * 0.35f;
                head.xRot += Mth.sin(t * 3.1f) * 0.1f - body.xRot;
                body.zRot = Mth.sin(limbSwing * 0.66f) * 0.1f * amt;
                skirt.zRot = body.zRot * 0.5f;
            }
            case IDLE, CHECK_CLOSET -> {
                head.yRot += Mth.sin(t * 0.031f) * 0.5f;
                rightArm.xRot = Mth.sin(t * 0.07f) * 0.03f;
                leftArm.xRot = -Mth.sin(t * 0.07f) * 0.03f;
                int beat = (int) (t / 6.0f) % 27;
                if (beat == 0) head.zRot += 0.7f;
                if (state == Hanayome.State.CHECK_CLOSET) {
                    body.xRot = 0.25f;
                    head.xRot += 0.2f;
                    rightArm.xRot = -0.6f;
                }
            }
            case STUNNED -> {
                body.xRot = -0.25f;
                head.xRot = -0.6f;
                head.zRot = 0.9f;
                rightArm.xRot = -2.6f;
                leftArm.xRot = -2.4f;
            }
            default -> {
                body.zRot = Mth.sin(limbSwing * 0.6f) * 0.07f * amt;
                body.yRot = Mth.sin(limbSwing * 0.3f) * 0.08f * amt;
                skirt.zRot = body.zRot * 0.4f;
                rightArm.xRot = Mth.sin(limbSwing * 0.6f) * 0.22f * amt + 0.04f;
                leftArm.xRot = -Mth.sin(limbSwing * 0.6f) * 0.22f * amt + 0.04f;
            }
        }
        body.xRot += duck * 0.55f;
        head.xRot -= duck * 0.2f;
    }

    @Override
    public void renderToBuffer(PoseStack poseStack, VertexConsumer consumer, int light, int overlay, float r, float g, float b, float a) {
        root.render(poseStack, consumer, light, overlay, r, g, b, phantom ? a * 0.72f : a);
    }
}
