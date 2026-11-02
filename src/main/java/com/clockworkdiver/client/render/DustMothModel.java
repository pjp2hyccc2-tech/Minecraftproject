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

/** A fuzzy moth with broad dusty wings. */
public class DustMothModel<T extends Entity> extends HierarchicalModel<T> {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(ClockworkDiver.id("dust_moth"), "main");
    private final ModelPart root;
    private final ModelPart body;
    private final ModelPart leftWing;
    private final ModelPart rightWing;

    public DustMothModel(ModelPart root) {
        this.root = root;
        this.body = root.getChild("body");
        this.leftWing = root.getChild("left_wing");
        this.rightWing = root.getChild("right_wing");
    }

    public static LayerDefinition createLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition r = mesh.getRoot();
        PartDefinition body = r.addOrReplaceChild("body", CubeListBuilder.create().texOffs(0, 0).addBox(-2.0f, -1.5f, -4.0f, 4.0f, 3.0f, 8.0f),
                PartPose.offset(0.0f, 18.0f, 0.0f));
        PartDefinition head = body.addOrReplaceChild("head", CubeListBuilder.create().texOffs(0, 11).addBox(-2.0f, -2.0f, -3.0f, 4.0f, 4.0f, 3.0f),
                PartPose.offset(0.0f, 0.0f, -4.0f));
        head.addOrReplaceChild("left_antenna", CubeListBuilder.create().texOffs(14, 11).addBox(-0.5f, -4.0f, -0.5f, 1.0f, 4.0f, 1.0f),
                PartPose.offsetAndRotation(1.0f, -2.0f, -2.0f, -0.5f, 0.0f, 0.4f));
        head.addOrReplaceChild("right_antenna", CubeListBuilder.create().texOffs(14, 11).addBox(-0.5f, -4.0f, -0.5f, 1.0f, 4.0f, 1.0f),
                PartPose.offsetAndRotation(-1.0f, -2.0f, -2.0f, -0.5f, 0.0f, -0.4f));
        r.addOrReplaceChild("left_wing", CubeListBuilder.create().texOffs(24, 0).addBox(0.0f, 0.0f, -4.0f, 10.0f, 0.0f, 8.0f),
                PartPose.offset(2.0f, 16.6f, 0.0f));
        r.addOrReplaceChild("right_wing", CubeListBuilder.create().texOffs(24, 0).mirror().addBox(-10.0f, 0.0f, -4.0f, 10.0f, 0.0f, 8.0f),
                PartPose.offset(-2.0f, 16.6f, 0.0f));
        return LayerDefinition.create(mesh, 64, 32);
    }

    @Override
    public ModelPart root() {
        return root;
    }

    @Override
    public void setupAnim(T entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
        float flap = Mth.cos(ageInTicks * 1.5f) * 0.75f + 0.15f;
        leftWing.zRot = -flap;
        rightWing.zRot = flap;
        body.y = 18.0f + Mth.sin(ageInTicks * 0.2f) * 0.6f;
        leftWing.y = body.y - 1.4f;
        rightWing.y = body.y - 1.4f;
        body.xRot = 0.1f + Mth.sin(ageInTicks * 0.1f) * 0.05f;
    }
}
