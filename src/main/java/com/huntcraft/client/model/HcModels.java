package com.huntcraft.client.model;

import com.huntcraft.HuntCraft;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.EntityRenderersEvent;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Model layer registry for hcmodels. In a module's client setup:
 * <pre>
 * &#64;SubscribeEvent static void layers(EntityRenderersEvent.RegisterLayerDefinitions e) { HcModels.register(e, "ignivald"); }
 * &#64;SubscribeEvent static void renderers(EntityRenderersEvent.RegisterRenderers e) {
 *     e.registerEntityRenderer(MyEntities.IGNIVALD.get(), ctx -> new HcMonsterRenderer&lt;&gt;(ctx, "ignivald", 2.5F));
 * }
 * </pre>
 */
@OnlyIn(Dist.CLIENT)
public final class HcModels {
    private static final Map<String, ModelLayerLocation> LAYERS = new ConcurrentHashMap<>();

    private HcModels() {
    }

    public static ModelLayerLocation layer(String name) {
        return LAYERS.computeIfAbsent(name, n -> new ModelLayerLocation(HuntCraft.id(n), "main"));
    }

    public static void register(EntityRenderersEvent.RegisterLayerDefinitions event, String name) {
        event.registerLayerDefinition(layer(name), () -> HcModelLoader.get(name).layer);
    }
}
