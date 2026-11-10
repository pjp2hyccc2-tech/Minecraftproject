package com.eternalrealm.client.entity.layer;

import com.eternalrealm.client.entity.model.EnemyModel;
import com.eternalrealm.entity.ERMonster;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/**
 * Draws the item a humanoid enemy holds in its right hand (the named weapons of the armory that monsters carry) at the
 * grip of the rig's hand, oriented like a vanilla mob's held item. The model hides its built-in weapon meanwhile.
 */
public final class HeldWeaponLayer<T extends ERMonster> extends RenderLayer<T, EnemyModel<T>> {
    private final ItemInHandRenderer items;

    public HeldWeaponLayer(RenderLayerParent<T, EnemyModel<T>> parent, ItemInHandRenderer items) {
        super(parent);
        this.items = items;
    }

    @Override
    public void render(PoseStack pose, MultiBufferSource buffers, int light, T entity, float limbSwing,
                       float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
        ItemStack stack = entity.getMainHandItem();
        if (stack.isEmpty() || !getParentModel().hasHand()) {
            return;
        }
        pose.pushPose();
        if (getParentModel().translateToHand(pose)) {
            // the vanilla hand transform (ItemInHandLayer), from the grip instead of the bottom of a vanilla arm
            pose.mulPose(Axis.XP.rotationDegrees(-90.0F));
            pose.mulPose(Axis.YP.rotationDegrees(180.0F));
            pose.translate(1.0F / 16.0F, 2.0F / 16.0F, -1.0F / 16.0F);
            this.items.renderItem(entity, stack, ItemDisplayContext.THIRD_PERSON_RIGHT_HAND, false, pose, buffers,
                    light);
        }
        pose.popPose();
    }
}
