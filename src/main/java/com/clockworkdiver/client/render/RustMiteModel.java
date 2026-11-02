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

/** A flat six-legged rust mite with a riveted back plate. */
public class RustMiteModel<T extends Entity> extends HierarchicalModel<T> {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(ClockworkDiver.id("rust_mite"), "main");
    private final ModelPart root;
    private final ModelPart body;
    private final ModelPart head;
    private final ModelPart[] legs = new ModelPart[6];
    private final float[] baseY = new float[6];
    private final float[] baseZ = new float[6];

    public RustMiteModel(ModelPart root) {
        this.root = root;
        this.body = root.getChild("body");
        this.head = root.getChild("head");
        for (int i = 0; i < 6; i++) {
            legs[i] = root.getChild("leg" + i);
            baseY[i] = legs[i].yRot;
            baseZ[i] = legs[i].zRot;
        }
    }

    public static LayerDefinition createLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition r = mesh.getRoot();
        r.addOrReplaceChild("body", CubeListBuilder.create()
                .texOffs(0, 0).addBox(-4.0f, -3.0f, -5.0f, 8.0f, 4.0f, 10.0f)
                .texOffs(0, 14).addBox(-3.0f, -5.0f, -3.0f, 6.0f, 2.0f, 6.0f), PartPose.offset(0.0f, 20.0f, 0.0f));
        r.addOrReplaceChild("head", CubeListBuilder.create()
                .texOffs(24, 14).addBox(-3.0f, -2.0f, -4.0f, 6.0f, 3.0f, 4.0f)
                .texOffs(44, 14).addBox(-3.5f, -1.0f, -6.0f, 1.0f, 1.0f, 3.0f)
                .texOffs(44, 14).addBox(2.5f, -1.0f, -6.0f, 1.0f, 1.0f, 3.0f), PartPose.offset(0.0f, 20.0f, -5.0f));
        for (int i = 0; i < 6; i++) {
            boolean left = i < 3;
            int row = i % 3;
            float side = left ? 1.0f : -1.0f;
            r.addOrReplaceChild("leg" + i, CubeListBuilder.create().texOffs(36, 0).addBox(left ? 0.0f : -7.0f, -0.5f, -0.5f, 7.0f, 1.0f, 1.0f),
                    PartPose.offsetAndRotation(3.5f * side, 20.5f, -3.0f + row * 3.0f, 0.0f, side * (row - 1) * 0.35f, side * 0.45f));
        }
        return LayerDefinition.create(mesh, 64, 32);
    }

    @Override
    public ModelPart root() {
        return root;
    }

    @Override
    public void setupAnim(T entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
        head.yRot = netHeadYaw * Mth.DEG_TO_RAD * 0.6f;
        head.xRot = headPitch * Mth.DEG_TO_RAD * 0.4f;
        body.y = 20.0f + Mth.sin(ageInTicks * 0.3f) * 0.15f;
        for (int i = 0; i < 6; i++) {
            float phase = (i % 2 == 0 ? 0.0f : Mth.PI) + (i < 3 ? 0.0f : Mth.PI * 0.5f);
            float swing = Mth.cos(limbSwing * 1.6f + phase) * 0.55f * limbSwingAmount;
            legs[i].yRot = baseY[i] + swing;
            float lift = Math.abs(Mth.sin(limbSwing * 1.6f + phase)) * 0.35f * limbSwingAmount;
            legs[i].zRot = baseZ[i] + (i < 3 ? -lift : lift);
        }
    }
}
