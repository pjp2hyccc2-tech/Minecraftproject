package com.eternalrealm.client.monster;

import com.eternalrealm.monster.MonsterMenace;
import com.eternalrealm.monster.MonsterTemplate;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.logging.LogUtils;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.client.event.EntityRenderersEvent;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;

/**
 * Lv35+ field monsters wear an energy shroud: their own model drawn once more, a little larger, with the scrolling
 * swirl of a charged creeper tinted in their field's element colour — brighter and faster at Lv45+, breathing in and
 * out. Added to the renderers of every body a field monster can have.
 */
public class MenaceSwirlLayer<T extends LivingEntity, M extends EntityModel<T>> extends RenderLayer<T, M> {
    private static final Logger LOG = LogUtils.getLogger();
    /** Vanilla's swirl (referenced by id, not copied). */
    private static final ResourceLocation SWIRL = new ResourceLocation("minecraft", "textures/entity/creeper/creeper_armor.png");
    /** Model-space height of the feet (y points down; the renderer moved the model up by 1.501). */
    private static final float FEET = 1.501F;

    public MenaceSwirlLayer(RenderLayerParent<T, M> parent) {
        super(parent);
    }

    @Override
    public void render(PoseStack pose, MultiBufferSource buffers, int packedLight, T entity, float limbSwing,
                       float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
        if (entity.isInvisible()) {
            return;
        }
        int tier = MenaceClient.tier(entity);
        if (!MonsterMenace.shrouded(tier)) {
            return;
        }
        int color = MenaceClient.element(entity).color();
        boolean fierce = tier >= 4;
        float speed = fierce ? 0.016F : 0.009F;
        float breath = 0.75F + 0.25F * Mth.sin(ageInTicks * (fierce ? 0.22F : 0.12F) + entity.getId());
        float strength = (fierce ? 0.85F : 0.5F) * breath;
        VertexConsumer buffer = buffers.getBuffer(RenderType.energySwirl(SWIRL, (ageInTicks * speed) % 1.0F,
                (ageInTicks * speed * 0.6F) % 1.0F));
        float scale = fierce ? 1.07F : 1.045F;
        pose.pushPose();
        pose.translate(0.0F, FEET, 0.0F);
        pose.scale(scale, scale, scale);
        pose.translate(0.0F, -FEET, 0.0F);
        getParentModel().renderToBuffer(pose, buffer, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY,
                ((color >> 16) & 0xFF) / 255.0F * strength, ((color >> 8) & 0xFF) / 255.0F * strength,
                (color & 0xFF) / 255.0F * strength, 1.0F);
        pose.popPose();
    }

    /** Adds the shroud to the renderer of every vanilla body of the monster templates and of the animated bodies. */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static void addLayers(EntityRenderersEvent.AddLayers event) {
        List<EntityType<?>> types = new ArrayList<>();
        for (String id : MonsterTemplate.BASE_ENTITY_TYPES) {
            EntityType.byString(id).ifPresent(types::add);
        }
        types.add(com.eternalrealm.entity.EREntityTypes.HUMANOID_ENEMY.get());
        types.add(com.eternalrealm.entity.EREntityTypes.BEAST_ENEMY.get());
        types.add(com.eternalrealm.entity.EREntityTypes.GOLEM_ENEMY.get());
        types.add(com.eternalrealm.entity.EREntityTypes.FLYER_ENEMY.get());
        int added = 0;
        for (EntityType<?> type : types) {
            try {
                EntityRenderer<?> renderer = event.getRenderer((EntityType) type);
                if (renderer instanceof LivingEntityRenderer living) {
                    living.addLayer(new MenaceSwirlLayer(living));
                    added++;
                }
            } catch (ClassCastException | IllegalArgumentException e) {
                LOG.debug("No living renderer for {}", type, e);
            }
        }
        LOG.debug("Menace shroud added to {} renderers", added);
    }
}
