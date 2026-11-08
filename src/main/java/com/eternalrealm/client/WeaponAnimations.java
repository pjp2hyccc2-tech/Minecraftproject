package com.eternalrealm.client;

import com.eternalrealm.combat.WeaponType;
import com.eternalrealm.config.ERClientConfig;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

import java.util.Optional;

/**
 * Weapon animations: third-person arm poses for every player (from {@link CombatAnimations}) and first-person weapon
 * motion for the local player. Each motion has a wind-up, a fast strike and an eased recovery, so combos read as
 * distinct slashes, thrusts, overhead blows, spins and casts instead of vanilla's single swing.
 */
public final class WeaponAnimations {
    /**
     * The custom two-handed action pose (extensible enum). It must exist before {@link HumanoidModel} first runs its
     * arm-pose {@code switch}: the compiler's switch map is sized to {@code ArmPose.values()} at that moment, so a value
     * added later has no slot and every frame that poses it crashes (ArrayIndexOutOfBoundsException in poseRightArm).
     * {@link #init()} therefore creates it once during client setup, and {@link #armPose} never creates it late.
     */
    private static volatile HumanoidModel.ArmPose actionPose;

    private WeaponAnimations() {
    }

    /** Called from client setup (enqueued on the main thread), before any humanoid model is rendered. */
    public static void init() {
        if (actionPose == null) {
            actionPose = HumanoidModel.ArmPose.create("ETERNALREALM_ACTION", true, WeaponAnimations::poseArms);
        }
    }

    // ------------------------------------------------------------------ third person

    public static HumanoidModel.ArmPose armPose(LivingEntity entity, InteractionHand hand, ItemStack stack) {
        if (hand != InteractionHand.MAIN_HAND || !ERClientConfig.WEAPON_ANIMATIONS.get()) {
            return null;
        }
        Optional<CombatAnimations.Anim> anim = CombatAnimations.of(entity);
        if (anim.isEmpty()) {
            return null;
        }
        if (anim.get().weapon() == WeaponType.BOW
                && (anim.get().motion() == CombatAnimations.Motion.CHARGE || anim.get().motion() == CombatAnimations.Motion.SHOOT)) {
            return HumanoidModel.ArmPose.BOW_AND_ARROW;
        }
        // Never create the pose here (see actionPose); without it the vanilla pose is used rather than crashing.
        return actionPose;
    }

    private static void poseArms(HumanoidModel<?> model, LivingEntity entity, HumanoidArm arm) {
        Optional<CombatAnimations.Anim> current = CombatAnimations.of(entity);
        if (current.isEmpty()) {
            return;
        }
        CombatAnimations.Anim anim = current.get();
        float partial = net.minecraft.client.Minecraft.getInstance().getFrameTime();
        float p = anim.progress(ClientCombatState.tick(), partial);
        boolean right = arm == HumanoidArm.RIGHT;
        var main = right ? model.rightArm : model.leftArm;
        var other = right ? model.leftArm : model.rightArm;
        float side = right ? 1.0F : -1.0F;
        float strike = strikeCurve(p);
        switch (anim.motion()) {
            case SLASH_RIGHT, SLASH_LEFT -> {
                float dir = anim.motion() == CombatAnimations.Motion.SLASH_RIGHT ? 1.0F : -1.0F;
                main.xRot = -1.45F - 0.25F * windup(p);
                main.yRot = side * dir * Mth.lerp(strike, -0.9F, 0.8F);
                main.zRot = side * dir * Mth.lerp(strike, 0.5F, -0.3F);
                model.body.yRot = dir * Mth.lerp(strike, -0.35F, 0.35F) * (1.0F - recover(p));
            }
            case OVERHEAD -> {
                main.xRot = Mth.lerp(strike, -2.9F, -0.35F);
                main.yRot = side * -0.15F;
                other.xRot = main.xRot * 0.9F;
                other.yRot = side * 0.45F;
                model.body.xRot = 0.25F * strike * (1.0F - recover(p));
            }
            case THRUST -> {
                main.xRot = -1.55F;
                main.yRot = side * -0.1F;
                other.xRot = -1.3F;
                other.yRot = side * 0.5F;
                model.body.yRot = side * Mth.lerp(strike, 0.3F, -0.25F) * (1.0F - recover(p));
            }
            case SPIN -> {
                main.xRot = -1.57F;
                main.zRot = side * -0.35F;
                other.xRot = -1.57F;
                other.zRot = side * 0.35F;
                model.body.yRot = (float) (p * Math.PI * 4.0);
            }
            case CAST -> {
                float raise = Mth.sin(Math.min(1.0F, p * 1.6F) * Mth.PI);
                main.xRot = -1.6F - 0.9F * raise;
                main.zRot = side * 0.1F;
                other.xRot = -1.2F - 0.6F * raise;
                other.zRot = side * -0.4F;
            }
            case CHARGE -> {
                float tremble = Mth.sin((ClientCombatState.tick() + partial) * 2.2F) * 0.04F * p;
                main.xRot = -2.6F + tremble;
                main.yRot = side * 0.35F;
                main.zRot = side * 0.4F;
                other.xRot = -2.2F;
                other.yRot = side * -0.2F;
            }
            case DODGE -> {
                main.xRot = -0.4F;
                other.xRot = 0.4F;
                main.zRot = side * 0.6F;
                other.zRot = side * -0.6F;
            }
            case SHOOT -> {
                main.xRot = -1.55F;
                other.xRot = -1.55F;
                other.yRot = side * 0.6F;
            }
        }
    }

    // ------------------------------------------------------------------ first person

    /** @return true when the transform was applied (vanilla's equip/swing transforms are then skipped) */
    public static boolean firstPerson(PoseStack pose, LocalPlayer player, HumanoidArm arm, ItemStack stack,
                                      float partialTick, float equipProcess, float swingProcess) {
        if (!ERClientConfig.WEAPON_ANIMATIONS.get() || arm != player.getMainArm()) {
            return false;
        }
        int side = arm == HumanoidArm.RIGHT ? 1 : -1;
        pose.translate(side * 0.56F, -0.52F + equipProcess * -0.6F, -0.72F);
        Optional<CombatAnimations.Anim> current = CombatAnimations.of(player);
        if (current.isEmpty()) {
            if (player.isUsingItem() && player.getUsedItemHand() == InteractionHand.MAIN_HAND) {
                // guard: weapon held across the body
                pose.translate(side * -0.28F, 0.12F, 0.1F);
                pose.mulPose(Axis.YP.rotationDegrees(side * 40.0F));
                pose.mulPose(Axis.ZP.rotationDegrees(side * 70.0F));
                return true;
            }
            vanillaSwing(pose, side, swingProcess);
            return true;
        }
        CombatAnimations.Anim anim = current.get();
        float p = anim.progress(ClientCombatState.tick(), partialTick);
        float w = windup(p);
        float s = strikeCurve(p);
        float r = recover(p);
        switch (anim.motion()) {
            case SLASH_RIGHT, SLASH_LEFT -> {
                float dir = anim.motion() == CombatAnimations.Motion.SLASH_RIGHT ? 1.0F : -1.0F;
                float amount = (1.0F - r);
                pose.translate(side * dir * Mth.lerp(s, 0.15F, -0.45F) * amount, 0.1F * w * (1 - s), -0.15F * s * amount);
                pose.mulPose(Axis.ZP.rotationDegrees(side * dir * Mth.lerp(s, 50.0F, -80.0F) * amount));
                pose.mulPose(Axis.YP.rotationDegrees(side * dir * Mth.lerp(s, 35.0F, -45.0F) * amount));
                pose.mulPose(Axis.XP.rotationDegrees(-25.0F * amount));
            }
            case OVERHEAD -> {
                float amount = 1.0F - r;
                pose.translate(0.0F, Mth.lerp(s, 0.35F, -0.25F) * amount, Mth.lerp(s, 0.1F, -0.3F) * amount);
                pose.mulPose(Axis.XP.rotationDegrees(Mth.lerp(s, -75.0F, 70.0F) * amount));
                pose.mulPose(Axis.ZP.rotationDegrees(side * 10.0F * amount));
            }
            case THRUST -> {
                float amount = 1.0F - r;
                pose.translate(side * -0.12F * amount, 0.02F, Mth.lerp(s, 0.18F, -0.55F) * amount);
                pose.mulPose(Axis.XP.rotationDegrees(-80.0F * Math.min(1.0F, p * 6.0F) * amount));
            }
            case SPIN -> {
                pose.translate(side * -0.2F, 0.05F, -0.1F);
                pose.mulPose(Axis.YP.rotationDegrees(side * p * 720.0F));
                pose.mulPose(Axis.ZP.rotationDegrees(side * 75.0F));
            }
            case CAST -> {
                float raise = Mth.sin(Math.min(1.0F, p * 1.6F) * Mth.PI);
                pose.translate(side * -0.15F * raise, 0.28F * raise, -0.22F * raise);
                pose.mulPose(Axis.XP.rotationDegrees(-35.0F * raise));
            }
            case CHARGE -> {
                float charge = CombatAnimations.localChargeRatio();
                float tremble = charge >= 1.0F ? Mth.sin((ClientCombatState.tick() + partialTick) * 3.0F) * 1.5F : 0.0F;
                if (anim.weapon() == WeaponType.BOW) {
                    pose.translate(side * -0.1F, 0.0F, 0.12F * charge);
                    pose.mulPose(Axis.ZP.rotationDegrees(side * (-20.0F + tremble)));
                } else {
                    pose.translate(side * 0.05F * charge, 0.25F * charge, 0.2F * charge);
                    pose.mulPose(Axis.XP.rotationDegrees(-60.0F * charge + tremble));
                    pose.mulPose(Axis.ZP.rotationDegrees(side * 25.0F * charge));
                }
            }
            case SHOOT -> {
                float snap = 1.0F - r;
                pose.translate(side * -0.1F, 0.0F, Mth.lerp(s, 0.12F, -0.05F) * snap);
                pose.mulPose(Axis.ZP.rotationDegrees(side * -20.0F * snap));
            }
            case DODGE -> {
                float lean = Mth.sin(p * Mth.PI);
                pose.translate(side * 0.1F * lean, -0.15F * lean, 0.1F * lean);
                pose.mulPose(Axis.ZP.rotationDegrees(side * 25.0F * lean));
            }
        }
        return true;
    }

    /** Vanilla's attack swing (used for clicks without our weapon actions). */
    private static void vanillaSwing(PoseStack pose, int side, float swingProcess) {
        if (swingProcess <= 0.0F) {
            return;
        }
        float f = Mth.sin(swingProcess * swingProcess * Mth.PI);
        float f1 = Mth.sin(Mth.sqrt(swingProcess) * Mth.PI);
        pose.mulPose(Axis.YP.rotationDegrees(side * (45.0F + f * -20.0F)));
        pose.mulPose(Axis.ZP.rotationDegrees(side * f1 * -20.0F));
        pose.mulPose(Axis.XP.rotationDegrees(f1 * -80.0F));
        pose.mulPose(Axis.YP.rotationDegrees(side * -45.0F));
    }

    // ------------------------------------------------------------------ curves

    /** 0 -> 1 over the first 30 % (wind-up). */
    private static float windup(float p) {
        return Math.min(1.0F, p / 0.3F);
    }

    /** 0 until 25 %, then a fast eased strike reaching 1 at 55 %. */
    private static float strikeCurve(float p) {
        float t = Mth.clamp((p - 0.25F) / 0.3F, 0.0F, 1.0F);
        return 1.0F - (1.0F - t) * (1.0F - t) * (1.0F - t);
    }

    /** 0 until 65 %, then eases back to rest. */
    private static float recover(float p) {
        float t = Mth.clamp((p - 0.65F) / 0.35F, 0.0F, 1.0F);
        return t * t * (3.0F - 2.0F * t);
    }
}
