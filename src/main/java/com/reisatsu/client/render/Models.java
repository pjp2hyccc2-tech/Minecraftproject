package com.reisatsu.client.render;

import com.reisatsu.Reisatsu;
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
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

/**
 * The entity models. The spirits worker makes {@code com.reisatsu.client.model.<Name>Model} (each with
 * {@code LAYER}, {@code createBodyLayer()} and a {@code (ModelPart)} constructor); they are picked up by reflection as
 * soon as they exist in the build, and until then a plain humanoid / box stands in.
 */
public final class Models {
    public static final ModelLayerLocation PLACEHOLDER = new ModelLayerLocation(Reisatsu.id("placeholder_humanoid"), "main");
    public static final ModelLayerLocation PLACEHOLDER_CAR = new ModelLayerLocation(Reisatsu.id("placeholder_car"), "main");

    private record Found(ModelLayerLocation layer, Method create, Constructor<?> ctor) {}

    private static final Map<String, Found> FOUND = new HashMap<>();
    private static boolean scanned;

    private Models() {}

    private static synchronized void scan() {
        if (scanned) return;
        scanned = true;
        for (String name : new String[]{"Visitor", "Waitress", "Chef", "Child", "MirrorWoman", "Manager", "Medium", "MediumCar"}) {
            try {
                Class<?> c = Class.forName("com.reisatsu.client.model." + name + "Model");
                ModelLayerLocation layer = (ModelLayerLocation) c.getField("LAYER").get(null);
                Method create = c.getMethod("createBodyLayer");
                Constructor<?> ctor = c.getConstructor(ModelPart.class);
                FOUND.put(name, new Found(layer, create, ctor));
                Reisatsu.LOGGER.info("[reisatsu] model {}Model found", name);
            } catch (ClassNotFoundException e) {
                Reisatsu.LOGGER.info("[reisatsu] model {}Model not in this build: a stand-in is drawn", name);
            } catch (Throwable t) {
                Reisatsu.LOGGER.warn("[reisatsu] model {}Model unusable: {}", name, t.toString());
            }
        }
    }

    public static boolean has(String name) {
        scan();
        return FOUND.containsKey(name);
    }

    public static void layers(EntityRenderersEvent.RegisterLayerDefinitions event) {
        scan();
        event.registerLayerDefinition(PLACEHOLDER, () -> LayerDefinition.create(HumanoidModel.createMesh(CubeDeformation.NONE, 0.0f), 64, 64));
        event.registerLayerDefinition(PLACEHOLDER_CAR, Models::carLayer);
        for (Map.Entry<String, Found> e : FOUND.entrySet()) {
            Found f = e.getValue();
            event.registerLayerDefinition(f.layer(), () -> {
                try {
                    return (LayerDefinition) f.create().invoke(null);
                } catch (Exception ex) {
                    throw new IllegalStateException("createBodyLayer of " + e.getKey() + "Model failed", ex);
                }
            });
        }
    }

    /** The real model of this name, or null. */
    @Nullable
    @SuppressWarnings("unchecked")
    public static <T extends Entity> EntityModel<T> create(String name, EntityRendererProvider.Context ctx) {
        scan();
        Found f = FOUND.get(name);
        if (f == null) return null;
        try {
            return (EntityModel<T>) f.ctor().newInstance(ctx.bakeLayer(f.layer()));
        } catch (Throwable t) {
            Reisatsu.LOGGER.error("[reisatsu] could not build {}Model", name, t);
            return null;
        }
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
