package com.hitori.client.render;

import com.hitori.Hitori;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
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
 * The entity models of the models worker ({@code com.hitori.client.model.OniDollModel / WomanModel / CameraModel}:
 * {@code LAYER}, {@code createBodyLayer()}, a {@code (ModelPart)} constructor), looked up by reflection so this branch
 * builds before they are merged. Box stand-ins remain registered for anything missing.
 */
public final class Models {
    public static final ModelLayerLocation PLACEHOLDER = new ModelLayerLocation(Hitori.id("placeholder_humanoid"), "main");
    public static final ModelLayerLocation PLACEHOLDER_CAMERA = new ModelLayerLocation(Hitori.id("placeholder_camera"), "main");
    private static final String PKG = "com.hitori.client.model.";

    private Models() {}

    @Nullable
    private static Class<?> cls(String name) {
        try {
            return Class.forName(PKG + name, true, Models.class.getClassLoader());
        } catch (Throwable t) {
            return null;
        }
    }

    public static boolean has(String name) {
        return cls(name) != null;
    }

    @Nullable
    private static ModelLayerLocation layer(Class<?> c) {
        try {
            return (ModelLayerLocation) c.getField("LAYER").get(null);
        } catch (Throwable t) {
            return null;
        }
    }

    public static void layers(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(PLACEHOLDER, () -> LayerDefinition.create(HumanoidModel.createMesh(CubeDeformation.NONE, 0.0f), 64, 64));
        event.registerLayerDefinition(PLACEHOLDER_CAMERA, Models::cameraLayer);
        for (String name : new String[]{"OniDollModel", "WomanModel", "CameraModel"}) {
            Class<?> c = cls(name);
            if (c == null) continue;
            ModelLayerLocation l = layer(c);
            if (l == null) continue;
            try {
                LayerDefinition def = (LayerDefinition) c.getMethod("createBodyLayer").invoke(null);
                event.registerLayerDefinition(l, () -> def);
                Hitori.LOGGER.info("[hitori] model {} registered", name);
            } catch (Throwable t) {
                Hitori.LOGGER.error("[hitori] model {} could not be registered", name, t);
            }
        }
    }

    /** The real model of this name, or null. */
    @Nullable
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static <T extends Entity> EntityModel<T> create(String name, EntityRendererProvider.Context ctx) {
        Class<?> c = cls(name);
        if (c == null) return null;
        ModelLayerLocation l = layer(c);
        if (l == null) return null;
        try {
            ModelPart root = ctx.bakeLayer(l);
            Object m = c.getConstructor(ModelPart.class).newInstance(root);
            return (EntityModel<T>) (EntityModel) m;
        } catch (Throwable t) {
            Hitori.LOGGER.error("[hitori] model {} could not be created", name, t);
            return null;
        }
    }

    /** A small black action cam on a tripod: the stand-in. */
    private static LayerDefinition cameraLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        root.addOrReplaceChild("body", CubeListBuilder.create().texOffs(0, 0).addBox(-2.0f, -6.0f, -1.5f, 4.0f, 3.0f, 3.0f),
                PartPose.offset(0.0f, 24.0f, 0.0f));
        root.addOrReplaceChild("lens", CubeListBuilder.create().texOffs(0, 6).addBox(-1.0f, -5.5f, -2.5f, 2.0f, 2.0f, 1.0f),
                PartPose.offset(0.0f, 24.0f, 0.0f));
        root.addOrReplaceChild("leg", CubeListBuilder.create().texOffs(8, 6).addBox(-0.5f, -3.0f, -0.5f, 1.0f, 3.0f, 1.0f),
                PartPose.offset(0.0f, 24.0f, 0.0f));
        return LayerDefinition.create(mesh, 32, 16);
    }
}
