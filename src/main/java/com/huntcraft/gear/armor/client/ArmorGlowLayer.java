package com.huntcraft.gear.armor.client;

import com.huntcraft.gear.armor.HCArmorItem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/** Emissive pass for HuntCraft armor (glowing runes, electric organs, ice crystals, embers). */
@OnlyIn(Dist.CLIENT)
public class ArmorGlowLayer<T extends LivingEntity, M extends HumanoidModel<T>> extends RenderLayer<T, M> {
    private static final EquipmentSlot[] SLOTS = {EquipmentSlot.FEET, EquipmentSlot.LEGS, EquipmentSlot.CHEST, EquipmentSlot.HEAD};

    public ArmorGlowLayer(RenderLayerParent<T, M> parent) {
        super(parent);
    }

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public void render(PoseStack poseStack, MultiBufferSource buffers, int light, T entity, float limbSwing, float limbSwingAmount,
                       float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
        if (entity.isInvisible()) return;
        for (EquipmentSlot slot : SLOTS) {
            ItemStack st = entity.getItemBySlot(slot);
            if (!(st.getItem() instanceof HCArmorItem armor) || armor.getType().getSlot() != slot) continue;
            boolean inner = slot == EquipmentSlot.LEGS;
            ResourceLocation tex = ArmorModels.glowTexture(armor.set.id, inner);
            if (tex == null) continue;
            HumanoidModel<LivingEntity> model = ArmorModels.get(armor.set.id, inner);
            if (model == null) continue;
            ((HumanoidModel) this.getParentModel()).copyPropertiesTo(model);
            model.setAllVisible(false);
            switch (slot) {
                case HEAD -> {
                    model.head.visible = true;
                    model.hat.visible = true;
                }
                case CHEST -> {
                    model.body.visible = true;
                    model.rightArm.visible = true;
                    model.leftArm.visible = true;
                }
                case LEGS -> {
                    model.body.visible = true;
                    model.rightLeg.visible = true;
                    model.leftLeg.visible = true;
                }
                default -> {
                    model.rightLeg.visible = true;
                    model.leftLeg.visible = true;
                }
            }
            float pulse = 0.82F + 0.18F * (float) Math.sin(ageInTicks * 0.12F);
            model.renderToBuffer(poseStack, buffers.getBuffer(RenderType.eyes(tex)), 0xF000F0, OverlayTexture.NO_OVERLAY, pulse, pulse, pulse, 1.0F);
        }
    }
}
