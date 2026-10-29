package com.huntcraft.client;

import com.huntcraft.HuntCraft;
import com.huntcraft.core.CoreEvents;
import com.huntcraft.data.HunterData;
import com.huntcraft.network.HCNetwork;
import com.huntcraft.registry.HCEffects;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Quaternionf;

/** Core client gameplay events (forge bus): dodge input, damage numbers, camera shake, roll animation, HUD swap. */
@Mod.EventBusSubscriber(modid = HuntCraft.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class ClientForgeEvents {
    private ClientForgeEvents() {
    }

    @SubscribeEvent
    public static void clientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        ClientState.tick(mc.level.getGameTime());
        LocalPlayer p = mc.player;
        HunterData d = HunterData.get(p);
        if ((d.stamina <= 0.5F || d.exhausted) && !p.isCreative()) p.setSprinting(false);
        while (HCClientSetup.DODGE.consumeClick()) tryDodge(mc, p, d);
    }

    private static void tryDodge(Minecraft mc, LocalPlayer p, HunterData d) {
        if (mc.screen != null || ClientState.localDodgeCooldown > 0 || !p.onGround() || p.isPassenger()) return;
        if (p.hasEffect(HCEffects.PARALYSIS.get()) || p.hasEffect(HCEffects.SLEEP.get()) || p.hasEffect(HCEffects.STUN.get()) || p.hasEffect(HCEffects.ROAR.get())) return;
        if (!p.isCreative() && d.stamina < CoreEvents.DODGE_COST * 0.5F) return;
        float fwd = p.input.forwardImpulse, left = p.input.leftImpulse;
        int dir;
        if (Math.abs(fwd) < 0.1F && Math.abs(left) < 0.1F) dir = 1;
        else if (Math.abs(fwd) >= Math.abs(left)) dir = fwd > 0 ? 0 : 1;
        else dir = left > 0 ? 2 : 3;
        float yaw = p.getYRot() * Mth.DEG_TO_RAD;
        Vec3 forward = new Vec3(-Mth.sin(yaw), 0, Mth.cos(yaw));
        Vec3 leftV = new Vec3(Mth.cos(yaw), 0, Mth.sin(yaw));
        Vec3 v = switch (dir) {
            case 0 -> forward;
            case 1 -> forward.scale(-1);
            case 2 -> leftV;
            default -> leftV.scale(-1);
        };
        double speed = 0.95;
        p.setDeltaMovement(v.x * speed, 0.2, v.z * speed);
        ClientState.localDodgeCooldown = 11;
        ClientState.DODGES.put(p.getId(), new ClientState.Dodge(mc.level.getGameTime(), dir));
        CompoundTag t = new CompoundTag();
        t.putInt("dir", dir);
        HCNetwork.sendToServer("core/dodge", t);
    }

    // ------------------------------------------------------------------ HUD swap
    @SubscribeEvent
    public static void hideVanillaBars(RenderGuiOverlayEvent.Pre event) {
        if (event.getOverlay().id().equals(VanillaGuiOverlay.PLAYER_HEALTH.id()) || event.getOverlay().id().equals(VanillaGuiOverlay.FOOD_LEVEL.id())) {
            event.setCanceled(true);
        }
    }

    // ------------------------------------------------------------------ camera shake
    @SubscribeEvent
    public static void cameraAngles(ViewportEvent.ComputeCameraAngles event) {
        if (ClientState.shakeTicks <= 0) return;
        float f = ClientState.shakeIntensity * ClientState.shakeTicks / (float) Math.max(1, ClientState.shakeTotal);
        float t = (float) (ClientState.shakeTicks + event.getPartialTick());
        event.setYaw(event.getYaw() + Mth.sin(t * 2.7F) * f * 1.5F);
        event.setPitch(event.getPitch() + Mth.cos(t * 3.1F) * f * 1.5F);
        event.setRoll(event.getRoll() + Mth.sin(t * 1.9F) * f * 2.0F);
    }

    // ------------------------------------------------------------------ dodge roll
    @SubscribeEvent
    public static void renderPlayerPre(RenderPlayerEvent.Pre event) {
        Player p = event.getEntity();
        ClientState.Dodge dodge = ClientState.DODGES.get(p.getId());
        PoseStack ps = event.getPoseStack();
        ps.pushPose();
        if (dodge == null || p.level() == null) return;
        float prog = Mth.clamp((p.level().getGameTime() - dodge.start() + event.getPartialTick()) / 10.0F, 0, 1);
        if (prog >= 1) return;
        float angle = prog * 360.0F;
        float yaw = Mth.lerp(event.getPartialTick(), p.yBodyRotO, p.yBodyRot) * Mth.DEG_TO_RAD;
        ps.translate(0, 0.55, 0);
        Quaternionf q;
        switch (dodge.dir()) {
            case 0 -> q = new Quaternionf().rotationAxis(angle * Mth.DEG_TO_RAD, Mth.cos(yaw), 0, Mth.sin(yaw));
            case 1 -> q = new Quaternionf().rotationAxis(-angle * Mth.DEG_TO_RAD, Mth.cos(yaw), 0, Mth.sin(yaw));
            case 2 -> q = new Quaternionf().rotationAxis(-angle * Mth.DEG_TO_RAD, -Mth.sin(yaw), 0, Mth.cos(yaw));
            default -> q = new Quaternionf().rotationAxis(angle * Mth.DEG_TO_RAD, -Mth.sin(yaw), 0, Mth.cos(yaw));
        }
        ps.mulPose(q);
        ps.translate(0, -0.55, 0);
        float squash = 1.0F - 0.35F * Mth.sin(prog * Mth.PI);
        ps.scale(1.0F, squash, 1.0F);
    }

    @SubscribeEvent
    public static void renderPlayerPost(RenderPlayerEvent.Post event) {
        event.getPoseStack().popPose();
    }

    // ------------------------------------------------------------------ damage numbers
    @SubscribeEvent
    public static void renderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || ClientState.DAMAGE_NUMBERS.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        Camera cam = event.getCamera();
        Vec3 cp = cam.getPosition();
        PoseStack ps = event.getPoseStack();
        MultiBufferSource.BufferSource buf = mc.renderBuffers().bufferSource();
        Font font = mc.font;
        float pt = event.getPartialTick();
        for (ClientState.DamageNumber dn : ClientState.DAMAGE_NUMBERS) {
            float age = dn.age + pt;
            double y = dn.pos.y + age * 0.025;
            int alpha = age < 22 ? 255 : (int) Mth.clamp(255 * (1 - (age - 22) / 10F), 0, 255);
            if (alpha <= 8) continue;
            float scale = (dn.crit ? 0.034F : 0.026F) * (age < 4 ? 1.0F + (4 - age) * 0.12F : 1.0F);
            ps.pushPose();
            ps.translate(dn.pos.x - cp.x, y - cp.y, dn.pos.z - cp.z);
            ps.mulPose(cam.rotation());
            ps.mulPose(Axis.YP.rotationDegrees(0));
            ps.scale(-scale, -scale, scale);
            float w = font.width(dn.text) / 2.0F;
            int color = (alpha << 24) | (dn.color & 0xFFFFFF);
            font.drawInBatch(dn.text, -w, 0, color, true, ps.last().pose(), buf, Font.DisplayMode.SEE_THROUGH, 0, 0xF000F0);
            ps.popPose();
        }
        buf.endBatch();
    }
}
