package com.reisatsu.client.render;

import com.reisatsu.Reisatsu;
import com.reisatsu.client.model.ChefModel;
import com.reisatsu.client.model.ChildModel;
import com.reisatsu.client.model.ManagerModel;
import com.reisatsu.client.model.MediumCarModel;
import com.reisatsu.client.model.MediumModel;
import com.reisatsu.client.model.MirrorWomanModel;
import com.reisatsu.client.model.VisitorModel;
import com.reisatsu.client.model.WaitressModel;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.client.event.EntityRenderersEvent;

import javax.annotation.Nullable;

/**
 * The entity models: the spirits worker's {@code com.reisatsu.client.model.*Model} (docs/DESIGN.md §5). The plain
 * humanoid / box stand-ins remain registered for anything unknown.
 */
public final class Models {
    public static final ModelLayerLocation PLACEHOLDER = new ModelLayerLocation(Reisatsu.id("placeholder_humanoid"), "main");
    public static final ModelLayerLocation PLACEHOLDER_CAR = new ModelLayerLocation(Reisatsu.id("placeholder_car"), "main");

    private Models() {}

    public static boolean has(String name) {
        return switch (name) {
            case "Visitor", "Waitress", "Chef", "Child", "MirrorWoman", "Manager", "Medium", "MediumCar" -> true;
            default -> false;
        };
    }

    public static void layers(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(PLACEHOLDER, () -> LayerDefinition.create(HumanoidModel.createMesh(CubeDeformation.NONE, 0.0f), 64, 64));
        event.registerLayerDefinition(PLACEHOLDER_CAR, Models::carLayer);
        event.registerLayerDefinition(VisitorModel.LAYER, VisitorModel::createBodyLayer);
        event.registerLayerDefinition(WaitressModel.LAYER, WaitressModel::createBodyLayer);
        event.registerLayerDefinition(ChefModel.LAYER, ChefModel::createBodyLayer);
        event.registerLayerDefinition(ChildModel.LAYER, ChildModel::createBodyLayer);
        event.registerLayerDefinition(MirrorWomanModel.LAYER, MirrorWomanModel::createBodyLayer);
        event.registerLayerDefinition(ManagerModel.LAYER, ManagerModel::createBodyLayer);
        event.registerLayerDefinition(MediumModel.LAYER, MediumModel::createBodyLayer);
        event.registerLayerDefinition(MediumCarModel.LAYER, MediumCarModel::createBodyLayer);
    }

    /** The real model of this name, or null for an unknown name. */
    @Nullable
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static <T extends Entity> EntityModel<T> create(String name, EntityRendererProvider.Context ctx) {
        EntityModel model = switch (name) {
            case "Visitor" -> new VisitorModel(ctx.bakeLayer(VisitorModel.LAYER));
            case "Waitress" -> new WaitressModel(ctx.bakeLayer(WaitressModel.LAYER));
            case "Chef" -> new ChefModel(ctx.bakeLayer(ChefModel.LAYER));
            case "Child" -> new ChildModel(ctx.bakeLayer(ChildModel.LAYER));
            case "MirrorWoman" -> new MirrorWomanModel(ctx.bakeLayer(MirrorWomanModel.LAYER));
            case "Manager" -> new ManagerModel(ctx.bakeLayer(ManagerModel.LAYER));
            case "Medium" -> new MediumModel(ctx.bakeLayer(MediumModel.LAYER));
            case "MediumCar" -> new MediumCarModel(ctx.bakeLayer(MediumCarModel.LAYER));
            default -> null;
        };
        return (EntityModel<T>) model;
    }

    private static LayerDefinition carLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        root.addOrReplaceChild("body", CubeListBuilder.create().texOffs(0, 0).addBox(-12.0f, -12.0f, -20.0f, 24.0f, 9.0f, 40.0f),
                PartPose.offset(0.0f, 24.0f, 0.0f));
        root.addOrReplaceChild("cabin", CubeListBuilder.create().texOffs(0, 49).addBox(-11.0f, -22.0f, -9.0f, 22.0f, 10.0f, 22.0f),
                PartPose.offset(0.0f, 24.0f, 0.0f));
        for (int i = 0; i < 4; i++) {
            float x = i % 2 == 0 ? -12.5f : 9.5f, z = i < 2 ? -14.0f : 10.0f;
            root.addOrReplaceChild("wheel" + i, CubeListBuilder.create().texOffs(0, 81).addBox(0.0f, -5.0f, -3.0f, 3.0f, 6.0f, 6.0f),
                    PartPose.offset(x, 24.0f, z));
        }
        return LayerDefinition.create(mesh, 128, 128);
    }
}
