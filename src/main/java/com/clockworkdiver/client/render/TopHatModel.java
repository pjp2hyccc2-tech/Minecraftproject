package com.clockworkdiver.client.render;

import com.clockworkdiver.ClockworkDiver;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.renderer.RenderType;

/** A gentleman's top hat with a hat band and a little brass gear pin. */
public class TopHatModel extends Model {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(ClockworkDiver.id("top_hat"), "main");
    private final ModelPart root;

    public TopHatModel(ModelPart root) {
        super(RenderType::entityCutoutNoCull);
        this.root = root;
    }

    public static LayerDefinition createLayer() {
        MeshDefinition mesh = new MeshDefinition();
        mesh.getRoot().addOrReplaceChild("hat", CubeListBuilder.create()
                .texOffs(0, 0).addBox(-5.0f, -1.0f, -5.0f, 10.0f, 1.0f, 10.0f)
                .texOffs(0, 11).addBox(-3.5f, -8.0f, -3.5f, 7.0f, 7.0f, 7.0f)
                .texOffs(28, 11).addBox(-3.75f, -2.75f, -3.75f, 7.5f, 1.5f, 7.5f)
                .texOffs(44, 0).addBox(2.0f, -3.5f, -4.25f, 2.0f, 2.0f, 1.0f), PartPose.ZERO);
        return LayerDefinition.create(mesh, 64, 32);
    }

    @Override
    public void renderToBuffer(PoseStack pose, VertexConsumer vc, int light, int overlay, float r, float g, float b, float a) {
        root.render(pose, vc, light, overlay, r, g, b, a);
    }
}
