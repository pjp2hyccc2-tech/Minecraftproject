package com.huntcraft.client.player;

import com.huntcraft.HuntCraft;
import com.huntcraft.gear.weapon.DualBladesItem;
import com.huntcraft.gear.weapon.HunterWeaponItem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * First person: while a hunter weapon is held the vanilla hand/item rendering is replaced by the rig's arms
 * (posed by the same animator, so attacks, stances and charges play in first person too), seen from the eye.
 * The rig is attached to the camera (it pitches with the view); it is pushed a little up/forward so the hands
 * stay in frame like the vanilla hands.
 */
@Mod.EventBusSubscriber(modid = HuntCraft.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class FirstPersonHands {
    private static final float RAISE = 0.42F;
    private static final float FORWARD = -0.12F;
    private static boolean warned;

    private FirstPersonHands() {
    }

    @SubscribeEvent
    public static void onRenderHand(RenderHandEvent event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null || !(p.getMainHandItem().getItem() instanceof HunterWeaponItem)) return;
        if (!HunterRenderer.shouldRig(p)) return;
        event.setCanceled(true);
        if (event.getHand() != InteractionHand.MAIN_HAND) return;   // both arms are drawn in the main hand pass
        try {
            render(p, event.getPoseStack(), event.getMultiBufferSource(), event.getPackedLight(), event.getPartialTick());
        } catch (Exception e) {
            if (!warned) {
                warned = true;
                HuntCraft.LOGGER.error("First person hunter rendering failed", e);
            }
        }
    }

    private static void render(LocalPlayer p, PoseStack ps, MultiBufferSource buffers, int light, float pt) {
        PlayerAnimState st = PlayerAnimState.get(p);
        HunterModel model = HunterModels.get("slim".equals(p.getModelName()));
        float limbAmt = Math.min(1.0F, p.walkAnimation.speed(pt));
        float limbSwing = p.walkAnimation.position(pt);
        HunterAnimator.pose(model, p, st, pt, limbSwing, limbAmt * 0.5F, p.tickCount + pt, 0, 0);
        ps.pushPose();
        ps.translate(0.0F, -p.getEyeHeight() + RAISE, FORWARD);
        ps.scale(-1.0F, -1.0F, 1.0F);
        ps.translate(0.0F, -1.501F, 0.0F);
        VertexConsumer skin = buffers.getBuffer(RenderType.entityTranslucent(p.getSkinTextureLocation()));
        // arms (with their lower halves and wrists as children)
        ps.pushPose();
        model.translateTo("chest", ps);
        model.armR.render(ps, skin, light, OverlayTexture.NO_OVERLAY);
        model.armL.render(ps, skin, light, OverlayTexture.NO_OVERLAY);
        ps.popPose();
        ArmorRig.renderArms(p, model, ps, buffers, light, p.tickCount + pt);
        // items
        ItemStack main = p.getMainHandItem();
        ItemStack off = p.getOffhandItem();
        if (!main.isEmpty()) {
            ps.pushPose();
            model.translateTo("hand_r", ps);
            HeldItems.handChain(ps, false);
            Minecraft.getInstance().getItemRenderer().renderStatic(p, main, ItemDisplayContext.THIRD_PERSON_RIGHT_HAND, false, ps, buffers, p.level(), light, OverlayTexture.NO_OVERLAY, p.getId());
            ps.popPose();
        }
        ItemStack left = off;
        if (main.getItem() instanceof DualBladesItem && off.isEmpty()) left = main;
        if (!left.isEmpty()) {
            ps.pushPose();
            model.translateTo("hand_l", ps);
            HeldItems.handChain(ps, true);
            Minecraft.getInstance().getItemRenderer().renderStatic(p, left, ItemDisplayContext.THIRD_PERSON_LEFT_HAND, true, ps, buffers, p.level(), light, OverlayTexture.NO_OVERLAY, p.getId());
            ps.popPose();
        }
        ps.popPose();
    }
}
