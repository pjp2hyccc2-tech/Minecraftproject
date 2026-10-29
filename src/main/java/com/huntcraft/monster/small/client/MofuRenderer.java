package com.huntcraft.monster.small.client;

import com.huntcraft.HuntCraft;
import com.huntcraft.client.model.HcModel;
import com.huntcraft.client.model.HcModelLoader;
import com.huntcraft.client.model.HcModels;
import com.huntcraft.client.render.HcMonsterRenderer;
import com.huntcraft.monster.small.NussuttoMofuEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * Thief Mofu renderer: fur variants (textures nussutto_mofu[_ginger|_black].png), the {@link MofuModel} carry pose and
 * the stolen item, balanced on its head while it runs away (follows the "loot" bone of the model).
 */
@OnlyIn(Dist.CLIENT)
public class MofuRenderer extends HcMonsterRenderer<NussuttoMofuEntity> {
    private static final ResourceLocation[] TEXTURES = {
            HuntCraft.id("textures/entity/nussutto_mofu.png"),
            HuntCraft.id("textures/entity/nussutto_mofu_ginger.png"),
            HuntCraft.id("textures/entity/nussutto_mofu_black.png")
    };

    public MofuRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, "nussutto_mofu", 0.35F);
        this.model = new MofuModel(ctx.bakeLayer(HcModels.layer("nussutto_mofu")), HcModelLoader.get("nussutto_mofu"));
        this.addLayer(new LootLayer(this));
    }

    @Override
    public ResourceLocation getTextureLocation(NussuttoMofuEntity entity) {
        return TEXTURES[Math.floorMod(entity.getVariant(), TEXTURES.length)];
    }

    /** Draws the loot item at the model's "loot" bone (child of "body"). */
    static class LootLayer extends RenderLayer<NussuttoMofuEntity, HcModel<NussuttoMofuEntity>> {
        LootLayer(RenderLayerParent<NussuttoMofuEntity, HcModel<NussuttoMofuEntity>> parent) {
            super(parent);
        }

        @Override
        public void render(PoseStack pose, MultiBufferSource buffers, int light, NussuttoMofuEntity entity, float limbSwing, float limbSwingAmount,
                           float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
            ItemStack stack = entity.getLoot();
            if (stack.isEmpty() || entity.isBurrowing()) return;
            HcModel<NussuttoMofuEntity> model = this.getParentModel();
            ModelPart body = model.bone("body");
            ModelPart loot = model.bone("loot");
            if (body == null || loot == null) return;
            pose.pushPose();
            model.root().translateAndRotate(pose);
            body.translateAndRotate(pose);
            loot.translateAndRotate(pose);
            // model space is Y-down: turn the item upright, and let it wobble a little
            pose.mulPose(Axis.XP.rotationDegrees(180.0F));
            pose.mulPose(Axis.ZP.rotationDegrees((float) Math.sin(ageInTicks * 0.35F) * 8.0F));
            pose.scale(0.85F, 0.85F, 0.85F);
            Minecraft.getInstance().getItemRenderer().renderStatic(stack, ItemDisplayContext.GROUND, light, OverlayTexture.NO_OVERLAY,
                    pose, buffers, entity.level(), entity.getId());
            pose.popPose();
        }
    }
}
