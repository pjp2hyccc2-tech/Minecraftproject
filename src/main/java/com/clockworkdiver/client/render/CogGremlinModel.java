package com.clockworkdiver.client.render;

import com.clockworkdiver.ClockworkDiver;
import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;

/** A big-eared gremlin with a gear strapped to its back. */
public class CogGremlinModel<T extends Entity> extends HierarchicalModel<T> {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(ClockworkDiver.id("cog_gremlin"), "main");
    private final ModelPart root;
    private final ModelPart head;
    private final ModelPart body;
    private final ModelPart backGear;
    private final ModelPart rightArm;
    private final ModelPart leftArm;
    private final ModelPart rightLeg;
    private final ModelPart leftLeg;

    public CogGremlinModel(ModelPart root) {
        this.root = root;
        this.head = root.getChild("head");
        this.body = root.getChild("body");
        this.backGear = body.getChild("gear");
        this.rightArm = root.getChild("right_arm");
        this.leftArm = root.getChild("left_arm");
        this.rightLeg = root.getChild("right_leg");
        this.leftLeg = root.getChild("left_leg");
    }

    public static LayerDefinition createLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition r = mesh.getRoot();
        PartDefinition head = r.addOrReplaceChild("head", CubeListBuilder.create()
                .texOffs(0, 0).addBox(-4.0f, -7.0f, -3.5f, 8.0f, 7.0f, 7.0f)
                .texOffs(30, 0).addBox(-1.0f, -3.0f, -5.5f, 2.0f, 2.0f, 2.0f), PartPose.offset(0.0f, 11.0f, 0.0f));
        head.addOrReplaceChild("left_ear", CubeListBuilder.create().texOffs(40, 0).addBox(0.0f, -1.5f, -0.5f, 4.0f, 3.0f, 1.0f),
                PartPose.offsetAndRotation(4.0f, -4.5f, 0.0f, 0.0f, -0.3f, -0.35f));
        head.addOrReplaceChild("right_ear", CubeListBuilder.create().texOffs(40, 0).mirror().addBox(-4.0f, -1.5f, -0.5f, 4.0f, 3.0f, 1.0f),
                PartPose.offsetAndRotation(-4.0f, -4.5f, 0.0f, 0.0f, 0.3f, 0.35f));
        PartDefinition body = r.addOrReplaceChild("body", CubeListBuilder.create().texOffs(0, 16).addBox(-3.0f, 0.0f, -2.0f, 6.0f, 7.0f, 4.0f),
                PartPose.offset(0.0f, 11.0f, 0.0f));
        body.addOrReplaceChild("gear", CubeListBuilder.create().texOffs(24, 16).addBox(-4.0f, -4.0f, 0.0f, 8.0f, 8.0f, 0.0f),
                PartPose.offset(0.0f, 3.0f, 2.2f));
        r.addOrReplaceChild("right_arm", CubeListBuilder.create().texOffs(40, 16).addBox(-1.0f, -1.0f, -1.0f, 2.0f, 7.0f, 2.0f),
                PartPose.offset(-4.0f, 12.0f, 0.0f));
        r.addOrReplaceChild("left_arm", CubeListBuilder.create().texOffs(40, 16).mirror().addBox(-1.0f, -1.0f, -1.0f, 2.0f, 7.0f, 2.0f),
                PartPose.offset(4.0f, 12.0f, 0.0f));
        r.addOrReplaceChild("right_leg", CubeListBuilder.create().texOffs(48, 16).addBox(-1.0f, 0.0f, -1.0f, 2.0f, 6.0f, 2.0f),
                PartPose.offset(-1.5f, 18.0f, 0.0f));
        r.addOrReplaceChild("left_leg", CubeListBuilder.create().texOffs(48, 16).mirror().addBox(-1.0f, 0.0f, -1.0f, 2.0f, 6.0f, 2.0f),
                PartPose.offset(1.5f, 18.0f, 0.0f));
        return LayerDefinition.create(mesh, 64, 64);
    }

    @Override
    public ModelPart root() {
        return root;
    }

    @Override
    public void setupAnim(T entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
        head.yRot = netHeadYaw * Mth.DEG_TO_RAD;
        head.xRot = headPitch * Mth.DEG_TO_RAD;
        head.zRot = Mth.sin(ageInTicks * 0.1f) * 0.08f;
        float swing = Mth.cos(limbSwing * 0.8f) * 1.3f * limbSwingAmount;
        rightLeg.xRot = swing;
        leftLeg.xRot = -swing;
        rightArm.xRot = -swing * 0.8f - 0.3f;
        leftArm.xRot = swing * 0.8f - 0.3f;
        rightArm.zRot = 0.25f + Mth.sin(ageInTicks * 0.15f) * 0.06f;
        leftArm.zRot = -0.25f - Mth.sin(ageInTicks * 0.15f) * 0.06f;
        if (attackTime > 0.0f) {
            float a = Mth.sin(attackTime * Mth.PI);
            rightArm.xRot = -1.8f * a - 0.3f;
        }
        backGear.zRot = ageInTicks * 0.15f;
        body.yRot = 0.0f;
    }
}
