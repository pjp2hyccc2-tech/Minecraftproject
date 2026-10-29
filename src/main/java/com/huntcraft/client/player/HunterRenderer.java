package com.huntcraft.client.player;

import com.huntcraft.HuntCraft;
import com.huntcraft.client.ClientState;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

/**
 * Replaces the vanilla player renderer with the animated hunter rig whenever the player carries a hunter weapon
 * (in hand or sheathed), is in a combat action or rolls. Vanilla rendering is kept for states the rig does not
 * cover (sleeping, swimming, elytra, riding, spectator, dying, invisible).
 */
@Mod.EventBusSubscriber(modid = HuntCraft.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class HunterRenderer {
    private static boolean warned;

    private HunterRenderer() {
    }

    public static boolean shouldRig(AbstractClientPlayer p) {
        if (p.isSleeping() || p.isFallFlying() || p.isVisuallySwimming() || p.isPassenger() || p.isSpectator() || p.deathTime > 0
                || p.isAutoSpinAttack() || p.isInvisible()) return false;
        PlayerAnimState st = PlayerAnimState.get(p);
        return HunterAnimator.holdsHunterWeapon(p) || st.action != null || !st.sheathed.isEmpty() || ClientState.DODGES.containsKey(p.getId())
                || st.prevAction != null && p.level().getGameTime() - st.prevEnd < 4;
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onRenderPlayerPre(RenderPlayerEvent.Pre event) {
        if (!(event.getEntity() instanceof AbstractClientPlayer p) || !shouldRig(p)) return;
        event.setCanceled(true);
        try {
            render(p, event.getPartialTick(), event.getPoseStack(), event.getMultiBufferSource(), event.getPackedLight());
        } catch (Exception e) {
            if (!warned) {
                warned = true;
                HuntCraft.LOGGER.error("Hunter rig rendering failed", e);
            }
        }
    }

    public static void render(AbstractClientPlayer p, float pt, PoseStack ps, MultiBufferSource buffers, int light) {
        PlayerAnimState st = PlayerAnimState.get(p);
        HunterModel model = HunterModels.get("slim".equals(p.getModelName()));
        float bodyYaw = Mth.rotLerp(pt, p.yBodyRotO, p.yBodyRot);
        float headYaw = Mth.rotLerp(pt, p.yHeadRotO, p.yHeadRot);
        float netHeadYaw = Mth.wrapDegrees(headYaw - bodyYaw);
        float headPitch = Mth.lerp(pt, p.xRotO, p.getXRot());
        float limbSwing = 0, limbAmt = 0;
        if (p.isAlive()) {
            limbAmt = Math.min(1.0F, p.walkAnimation.speed(pt));
            limbSwing = p.walkAnimation.position(pt);
        }
        float age = p.tickCount + pt;
        ps.pushPose();
        ps.mulPose(Axis.YP.rotationDegrees(180.0F - bodyYaw));
        ps.scale(-1.0F, -1.0F, 1.0F);
        ps.scale(0.9375F, 0.9375F, 0.9375F);
        ps.translate(0.0F, -1.501F, 0.0F);
        HunterAnimator.pose(model, p, st, pt, limbSwing, limbAmt, age, netHeadYaw, headPitch);
        int overlay = LivingEntityRenderer.getOverlayCoords(p, 0.0F);
        VertexConsumer vc = buffers.getBuffer(RenderType.entityTranslucent(p.getSkinTextureLocation()));
        model.root().render(ps, vc, light, overlay);
        ArmorRig.render(p, model, ps, buffers, light, age);
        HeldItems.render(p, st, model, ps, buffers, light, pt);
        ps.popPose();
        renderNameTag(p, ps, buffers, light);
    }

    private static void renderNameTag(AbstractClientPlayer p, PoseStack ps, MultiBufferSource buffers, int light) {
        Minecraft mc = Minecraft.getInstance();
        if (p == mc.player || mc.player == null || !Minecraft.renderNames() || p.isInvisibleTo(mc.player)) return;
        EntityRenderDispatcher d = mc.getEntityRenderDispatcher();
        if (d.distanceToSqr(p) > 4096.0) return;
        Component name = p.getDisplayName();
        boolean discrete = p.isDiscrete();
        ps.pushPose();
        ps.translate(0.0F, p.getBbHeight() + 0.5F, 0.0F);
        ps.mulPose(d.cameraOrientation());
        ps.scale(-0.025F, -0.025F, 0.025F);
        Matrix4f m = ps.last().pose();
        float bg = mc.options.getBackgroundOpacity(0.25F);
        int bgColor = (int) (bg * 255.0F) << 24;
        Font font = mc.font;
        float x = -font.width(name) / 2.0F;
        font.drawInBatch(name, x, 0, discrete ? 0x20FFFFFF : -1, false, m, buffers, discrete ? Font.DisplayMode.NORMAL : Font.DisplayMode.SEE_THROUGH, bgColor, light);
        if (!discrete) font.drawInBatch(name, x, 0, -1, false, m, buffers, Font.DisplayMode.NORMAL, 0, light);
        ps.popPose();
    }

    /** Sheathed weapon on vanilla-rendered players is not needed: the rig takes over whenever one exists. */
    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        WeaponTrail.renderAll(event.getPoseStack(), event.getCamera().getPosition(), event.getPartialTick());
    }

    /** True for players rendered by the rig (used by the first person renderer). */
    public static boolean isRigged(Player p) {
        return p instanceof AbstractClientPlayer acp && shouldRig(acp);
    }
}
