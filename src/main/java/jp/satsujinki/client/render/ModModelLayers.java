package jp.satsujinki.client.render;

import jp.satsujinki.Satsujinki;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.util.Mth;

/** Baked geometry for the animated trap parts and gore chunks (all in model pixels, +Y up for block entities). */
public final class ModModelLayers {
    public static final ModelLayerLocation MIXER_BLADES = new ModelLayerLocation(Satsujinki.id("mixer_blades"), "main");
    public static final ModelLayerLocation PRESS_PLATE = new ModelLayerLocation(Satsujinki.id("press_plate"), "main");
    public static final ModelLayerLocation PRESS_ROD = new ModelLayerLocation(Satsujinki.id("press_rod"), "main");
    public static final ModelLayerLocation GUILLOTINE = new ModelLayerLocation(Satsujinki.id("guillotine"), "main");
    public static final ModelLayerLocation GIB = new ModelLayerLocation(Satsujinki.id("gib"), "main");

    private ModModelLayers() {
    }

    /** Hub + four long, tilted, serrated blades covering the 3x3 mixer floor. Texture 64x32. */
    public static LayerDefinition mixerBlades() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        root.addOrReplaceChild("hub", CubeListBuilder.create().texOffs(0, 0).addBox(-3.0F, -1.0F, -3.0F, 6.0F, 5.0F, 6.0F), PartPose.ZERO);
        root.addOrReplaceChild("cap", CubeListBuilder.create().texOffs(24, 0).addBox(-2.0F, 4.0F, -2.0F, 4.0F, 2.0F, 4.0F), PartPose.ZERO);
        for (int i = 0; i < 4; i++) {
            float tilt = (i % 2 == 0) ? 0.22F : -0.22F;
            root.addOrReplaceChild("blade" + i,
                    CubeListBuilder.create().texOffs(0, 12).addBox(2.5F, 0.5F + (i % 2), -2.0F, 19.0F, 1.0F, 4.0F),
                    PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, tilt, i * Mth.HALF_PI, 0.0F));
        }
        return LayerDefinition.create(mesh, 64, 32);
    }

    /** 3x3 block steel plate, 4 px thick. Texture 256x64. */
    public static LayerDefinition pressPlate() {
        MeshDefinition mesh = new MeshDefinition();
        mesh.getRoot().addOrReplaceChild("plate", CubeListBuilder.create().texOffs(0, 0).addBox(-24.0F, 0.0F, -24.0F, 48.0F, 4.0F, 48.0F), PartPose.ZERO);
        return LayerDefinition.create(mesh, 256, 64);
    }

    /** One block of hydraulic rod (stretched to length). Texture 32x32. */
    public static LayerDefinition pressRod() {
        MeshDefinition mesh = new MeshDefinition();
        mesh.getRoot().addOrReplaceChild("rod", CubeListBuilder.create().texOffs(0, 0).addBox(-3.0F, 0.0F, -3.0F, 6.0F, 16.0F, 6.0F), PartPose.ZERO);
        return LayerDefinition.create(mesh, 32, 32);
    }

    /** Guillotine frame (posts, crossbar, lunette) and the blade. Texture 64x64. */
    public static LayerDefinition guillotine() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        root.addOrReplaceChild("frame", CubeListBuilder.create()
                .texOffs(0, 0).addBox(-8.0F, 3.0F, -1.0F, 2.0F, 42.0F, 2.0F)
                .texOffs(8, 0).addBox(6.0F, 3.0F, -1.0F, 2.0F, 42.0F, 2.0F)
                .texOffs(16, 0).addBox(-8.5F, 45.0F, -1.5F, 17.0F, 3.0F, 3.0F)
                .texOffs(16, 8).addBox(-6.0F, 22.0F, -1.0F, 4.0F, 4.0F, 2.0F)
                .texOffs(28, 8).addBox(2.0F, 22.0F, -1.0F, 4.0F, 4.0F, 2.0F)
                .texOffs(16, 26).addBox(-6.0F, 29.0F, -1.0F, 12.0F, 2.0F, 2.0F), PartPose.ZERO);
        root.addOrReplaceChild("blade", CubeListBuilder.create().texOffs(16, 16).addBox(-6.0F, 0.0F, -0.5F, 12.0F, 7.0F, 1.0F), PartPose.ZERO);
        root.addOrReplaceChild("weight", CubeListBuilder.create().texOffs(44, 16).addBox(-4.0F, 7.0F, -1.0F, 8.0F, 3.0F, 2.0F), PartPose.ZERO);
        return LayerDefinition.create(mesh, 64, 64);
    }

    /** Gore chunk cube and a bone. Texture 32x32. */
    public static LayerDefinition gib() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        root.addOrReplaceChild("chunk", CubeListBuilder.create().texOffs(0, 0).addBox(-2.0F, -2.0F, -2.0F, 4.0F, 4.0F, 4.0F), PartPose.ZERO);
        root.addOrReplaceChild("bone", CubeListBuilder.create()
                .texOffs(0, 8).addBox(-1.0F, -3.5F, -1.0F, 2.0F, 7.0F, 2.0F)
                .texOffs(8, 8).addBox(-1.5F, -4.5F, -1.5F, 3.0F, 1.0F, 3.0F)
                .texOffs(8, 12).addBox(-1.5F, 3.5F, -1.5F, 3.0F, 1.0F, 3.0F), PartPose.ZERO);
        return LayerDefinition.create(mesh, 32, 32);
    }
}
