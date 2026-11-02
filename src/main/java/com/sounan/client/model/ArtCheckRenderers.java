package com.sounan.client.model;

import com.sounan.Sounan;
import com.sounan.client.render.CameraVision;
import com.sounan.entity.Koyanushi;
import com.sounan.entity.Nozoki;
import com.sounan.entity.Oyamasama;
import com.sounan.entity.SounanMonster;
import com.sounan.entity.Ushiro;
import com.sounan.entity.Utsurumono;
import com.sounan.entity.Yobigoe;
import com.sounan.registry.ModEntities;
import java.util.function.Function;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.EyesLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * TEMPORARY (art branch only): puts the real monster models in game so the art branch's CI screenshots show them.
 * The main branch writes the real renderers; this class is removed again before the hand-off.
 */
@Mod.EventBusSubscriber(modid = Sounan.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ArtCheckRenderers {
    private ArtCheckRenderers() {
    }

    @SubscribeEvent
    public static void layers(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(NozokiModel.LAYER, NozokiModel::createBodyLayer);
        event.registerLayerDefinition(YobigoeModel.LAYER, YobigoeModel::createBodyLayer);
        event.registerLayerDefinition(UshiroModel.LAYER, UshiroModel::createBodyLayer);
        event.registerLayerDefinition(KoyanushiModel.LAYER, KoyanushiModel::createBodyLayer);
        event.registerLayerDefinition(OyamasamaModel.LAYER, OyamasamaModel::createBodyLayer);
        event.registerLayerDefinition(UtsurumonoModel.LAYER, UtsurumonoModel::createBodyLayer);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void renderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.NOZOKI.get(), ArtCheckRenderers.<Nozoki, NozokiModel<Nozoki>>provider(
                c -> new NozokiModel<Nozoki>(c.bakeLayer(NozokiModel.LAYER)), "nozoki", 0.5F));
        event.registerEntityRenderer(ModEntities.YOBIGOE.get(), ArtCheckRenderers.<Yobigoe, YobigoeModel<Yobigoe>>provider(
                c -> new YobigoeModel<Yobigoe>(c.bakeLayer(YobigoeModel.LAYER)), "yobigoe", 0.4F));
        event.registerEntityRenderer(ModEntities.USHIRO.get(), ArtCheckRenderers.<Ushiro, UshiroModel<Ushiro>>provider(
                c -> new UshiroModel<Ushiro>(c.bakeLayer(UshiroModel.LAYER)), "ushiro", 0.4F));
        event.registerEntityRenderer(ModEntities.KOYANUSHI.get(), ArtCheckRenderers.<Koyanushi, KoyanushiModel<Koyanushi>>provider(
                c -> new KoyanushiModel<Koyanushi>(c.bakeLayer(KoyanushiModel.LAYER)), "koyanushi", 0.4F));
        event.registerEntityRenderer(ModEntities.OYAMASAMA.get(), ArtCheckRenderers.<Oyamasama, OyamasamaModel<Oyamasama>>provider(
                c -> new OyamasamaModel<Oyamasama>(c.bakeLayer(OyamasamaModel.LAYER)), "oyamasama", 0.9F));
        event.registerEntityRenderer(ModEntities.UTSURUMONO.get(), ArtCheckRenderers.<Utsurumono, UtsurumonoModel<Utsurumono>>provider(
                c -> new UtsurumonoModel<Utsurumono>(c.bakeLayer(UtsurumonoModel.LAYER)), "utsurumono", 0.0F));
    }

    private static <T extends SounanMonster, M extends EntityModel<T>> EntityRendererProvider<T> provider(
            Function<EntityRendererProvider.Context, M> model, String id, float shadow) {
        return c -> new Renderer<T, M>(c, model.apply(c), id, shadow);
    }

    private static final class Renderer<T extends SounanMonster, M extends EntityModel<T>> extends MobRenderer<T, M> {
        private final ResourceLocation texture;

        Renderer(EntityRendererProvider.Context ctx, M model, String id, float shadow) {
            super(ctx, model, shadow);
            this.texture = new ResourceLocation(Sounan.MODID, "textures/entity/" + id + ".png");
            final RenderType eyes = RenderType.eyes(new ResourceLocation(Sounan.MODID, "textures/entity/" + id + "_eyes.png"));
            this.addLayer(new EyesLayer<T, M>(this) {
                @Override
                public RenderType renderType() {
                    return eyes;
                }
            });
        }

        @Override
        public ResourceLocation getTextureLocation(T entity) {
            return this.texture;
        }

        @Override
        public boolean shouldRender(T entity, Frustum frustum, double x, double y, double z) {
            if (entity instanceof Utsurumono && !CameraVision.active(Minecraft.getInstance())) {
                return false;
            }
            return super.shouldRender(entity, frustum, x, y, z);
        }
    }
}
