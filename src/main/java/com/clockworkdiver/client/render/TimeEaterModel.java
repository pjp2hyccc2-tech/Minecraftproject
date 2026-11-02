package com.clockworkdiver.client.render;

import com.clockworkdiver.ClockworkDiver;
import com.clockworkdiver.entity.TimeEater;
import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.util.Mth;

/** A clockwork beetle: armoured shell (with a clock face drawn on top by the renderer), mandibles, six legs. */
public class TimeEaterModel<T extends TimeEater> extends HierarchicalModel<T> {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(ClockworkDiver.id("time_eater"), "main");
    private final ModelPart root;
    private final ModelPart body;
    private final ModelPart head;
    private final ModelPart leftMandible;
    private final ModelPart rightMandible;
    private final ModelPart tail;
    private final ModelPart[] legs = new ModelPart[6];

    public TimeEaterModel(ModelPart root) {
        this.root = root;
        this.body = root.getChild("body");
        this.head = root.getChild("head");
        this.leftMandible = head.getChild("left_mandible");
        this.rightMandible = head.getChild("right_mandible");
        this.tail = root.getChild("tail");
        for (int i = 0; i < 6; i++) legs[i] = root.getChild("leg" + i);
    }

    public static LayerDefinition createLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition r = mesh.getRoot();
        PartDefinition body = r.addOrReplaceChild("body", CubeListBuilder.create().texOffs(0, 0).addBox(-8.0f, -10.0f, -11.0f, 16.0f, 10.0f, 22.0f),
                PartPose.offset(0.0f, 17.0f, 0.0f));
        body.addOrReplaceChild("shell", CubeListBuilder.create().texOffs(0, 32).addBox(-9.0f, -13.0f, -10.0f, 18.0f, 3.0f, 20.0f), PartPose.ZERO);
        body.addOrReplaceChild("left_gear", CubeListBuilder.create().texOffs(80, 20).addBox(0.0f, -4.0f, -4.0f, 1.0f, 8.0f, 8.0f),
                PartPose.offset(8.0f, -5.0f, 2.0f));
        body.addOrReplaceChild("right_gear", CubeListBuilder.create().texOffs(80, 20).mirror().addBox(-1.0f, -4.0f, -4.0f, 1.0f, 8.0f, 8.0f),
                PartPose.offset(-8.0f, -5.0f, 2.0f));
        PartDefinition head = r.addOrReplaceChild("head", CubeListBuilder.create().texOffs(0, 56).addBox(-5.0f, -4.0f, -8.0f, 10.0f, 8.0f, 8.0f),
                PartPose.offset(0.0f, 12.0f, -11.0f));
        head.addOrReplaceChild("left_mandible", CubeListBuilder.create().texOffs(36, 56).addBox(-1.0f, -1.0f, -7.0f, 2.0f, 2.0f, 7.0f),
                PartPose.offsetAndRotation(3.5f, 2.5f, -7.0f, 0.0f, -0.35f, 0.0f));
        head.addOrReplaceChild("right_mandible", CubeListBuilder.create().texOffs(36, 56).mirror().addBox(-1.0f, -1.0f, -7.0f, 2.0f, 2.0f, 7.0f),
                PartPose.offsetAndRotation(-3.5f, 2.5f, -7.0f, 0.0f, 0.35f, 0.0f));
        r.addOrReplaceChild("tail", CubeListBuilder.create().texOffs(56, 56).addBox(-3.0f, -3.0f, 0.0f, 6.0f, 5.0f, 10.0f),
                PartPose.offsetAndRotation(0.0f, 12.0f, 11.0f, -0.3f, 0.0f, 0.0f));
        for (int i = 0; i < 6; i++) {
            boolean left = i < 3;
            int row = i % 3;
            float side = left ? 1.0f : -1.0f;
            r.addOrReplaceChild("leg" + i, CubeListBuilder.create().texOffs(80, 0).addBox(-1.5f, 0.0f, -1.5f, 3.0f, 12.0f, 3.0f),
                    PartPose.offsetAndRotation(7.5f * side, 13.0f, -7.0f + row * 7.0f, 0.0f, 0.0f, -side * 0.75f));
        }
        return LayerDefinition.create(mesh, 128, 128);
    }

    @Override
    public ModelPart root() {
        return root;
    }

    @Override
    public void setupAnim(T entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
        head.yRot = netHeadYaw * Mth.DEG_TO_RAD * 0.5f;
        head.xRot = headPitch * Mth.DEG_TO_RAD * 0.5f;
        float speed = entity.isEnraged() ? 0.6f : 0.35f;
        float chomp = Mth.sin(ageInTicks * speed) * 0.25f + 0.1f;
        leftMandible.yRot = -0.35f - chomp;
        rightMandible.yRot = 0.35f + chomp;
        tail.yRot = Mth.sin(ageInTicks * 0.08f) * 0.25f;
        body.y = 17.0f + Mth.sin(ageInTicks * 0.15f) * 0.3f;
        for (int i = 0; i < 6; i++) {
            float phase = (i % 2 == 0 ? 0.0f : Mth.PI) + (i < 3 ? 0.0f : Mth.PI);
            legs[i].xRot = Mth.cos(limbSwing * 0.9f + phase) * 0.7f * limbSwingAmount;
        }
        if (entity.pulseCharge() >= 0) {
            float shake = Mth.sin(ageInTicks * 2.5f) * 0.05f;
            body.zRot = shake;
            head.zRot = -shake;
        } else {
            body.zRot = 0.0f;
            head.zRot = 0.0f;
        }
    }
}
