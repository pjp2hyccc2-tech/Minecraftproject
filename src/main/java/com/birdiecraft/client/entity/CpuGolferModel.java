package com.birdiecraft.client.entity;

import com.birdiecraft.BirdieCraft;
import com.birdiecraft.entity.CpuGolferEntity;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.util.Mth;

/**
 * Player-shaped golfer that holds a club (modelled as part of the right arm) and plays a full
 * golf swing: address, backswing, downswing, follow-through.
 */
public class CpuGolferModel extends PlayerModel<CpuGolferEntity> {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(BirdieCraft.id("cpu_golfer"), "main");

    // keyframes: {bodyY, rX, rY, rZ, lX, lY, lZ}
    private static final float[] ADDRESS = {0f, -0.55f, 0.0f, -0.42f, -0.55f, 0.0f, 0.42f};
    private static final float[] TOP = {0.55f, -2.55f, 0.15f, -0.05f, -2.30f, 0.10f, 0.65f};
    private static final float[] FINISH = {-0.70f, -2.30f, -0.15f, -0.65f, -2.55f, -0.10f, 0.05f};

    public CpuGolferModel(ModelPart root) {
        super(root, false);
    }

    public static LayerDefinition createLayer() {
        MeshDefinition mesh = PlayerModel.createMesh(CubeDeformation.NONE, false);
        PartDefinition root = mesh.getRoot();
        PartDefinition rightArm = root.getChild("right_arm");
        // shaft continues from the hand along the arm, head sits on the ground at address
        PartDefinition shaft = rightArm.addOrReplaceChild("club_shaft",
                CubeListBuilder.create().texOffs(56, 16).addBox(-1.5F, 9.0F, -0.5F, 1.0F, 15.0F, 1.0F),
                PartPose.ZERO);
        shaft.addOrReplaceChild("club_head",
                CubeListBuilder.create().texOffs(24, 0).addBox(-1.5F, 22.5F, -2.5F, 1.0F, 2.0F, 3.0F),
                PartPose.ZERO);
        return LayerDefinition.create(mesh, 64, 64);
    }

    @Override
    public void setupAnim(CpuGolferEntity entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
        super.setupAnim(entity, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);
        float partial = ageInTicks - (float) Math.floor(ageInTicks);
        float t = entity.swingProgress(partial);
        float[] pose;
        if (t < 0f) {
            pose = ADDRESS;
        } else if (t < 0.40f) {
            pose = lerp(ADDRESS, TOP, easeOut(t / 0.40f));
        } else if (t < 0.47f) {
            pose = TOP;
        } else if (t < 0.56f) {
            pose = lerp(TOP, ADDRESS, easeIn((t - 0.47f) / 0.09f));
        } else if (t < 0.82f) {
            pose = lerp(ADDRESS, FINISH, easeOut((t - 0.56f) / 0.26f));
        } else {
            pose = FINISH;
        }
        float twist = pose[0];
        this.body.yRot = twist;
        this.rightArm.z = Mth.sin(twist) * 5.0F;
        this.rightArm.x = -Mth.cos(twist) * 5.0F;
        this.leftArm.z = -Mth.sin(twist) * 5.0F;
        this.leftArm.x = Mth.cos(twist) * 5.0F;
        this.rightArm.xRot = pose[1];
        this.rightArm.yRot = pose[2] + twist;
        this.rightArm.zRot = pose[3];
        this.leftArm.xRot = pose[4];
        this.leftArm.yRot = pose[5] + twist;
        this.leftArm.zRot = pose[6];
        // golfers look down at the ball while swinging
        this.head.xRot = 0.45f;
        this.head.yRot = twist * 0.3f;
        this.hat.copyFrom(this.head);
        this.rightSleeve.copyFrom(this.rightArm);
        this.leftSleeve.copyFrom(this.leftArm);
        this.jacket.copyFrom(this.body);
        // slight knee flex
        this.rightLeg.xRot = -0.08f;
        this.leftLeg.xRot = -0.08f;
        this.rightPants.copyFrom(this.rightLeg);
        this.leftPants.copyFrom(this.leftLeg);
    }

    private static float easeOut(float x) {
        return 1f - (1f - x) * (1f - x);
    }

    private static float easeIn(float x) {
        return x * x;
    }

    private static float[] lerp(float[] a, float[] b, float t) {
        float[] r = new float[a.length];
        for (int i = 0; i < a.length; i++) r[i] = a[i] + (b[i] - a[i]) * t;
        return r;
    }
}
