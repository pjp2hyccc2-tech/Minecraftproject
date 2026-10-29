package com.huntcraft.client.player;

import com.huntcraft.client.ClientState;
import com.huntcraft.gear.weapon.HunterWeaponItem;
import com.huntcraft.gear.weapon.WeaponState;
import com.huntcraft.gear.weapon.action.ActionDef;
import com.huntcraft.gear.weapon.action.CombatActions;
import net.minecraft.client.Minecraft;
import net.minecraft.client.animation.AnimationDefinition;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * Builds the pose of a hunter for one frame by layering animations of hunter.json:
 * stance idle (per weapon / guard / charge / item use) + walk or run cycle + combat action (with hit-stop and
 * cross fades) + dodge roll + crouch delta + head look + vanilla arm swing for non hunter items.
 */
@OnlyIn(Dist.CLIENT)
public final class HunterAnimator {
    private static final float DEG = (float) (Math.PI / 180.0);
    private static final java.util.Set<String> LEGS = java.util.Set.of("root", "hips", "leg_r", "leg_r_lower", "leg_l", "leg_l_lower");
    private static final java.util.Set<String> ARMS = java.util.Set.of("arm_r", "arm_r_lower", "hand_r", "arm_l", "arm_l_lower", "hand_l");

    private HunterAnimator() {
    }

    /** Stance animation name for the player's current situation. */
    public static String stance(AbstractClientPlayer p, PlayerAnimState st) {
        ItemStack main = p.getMainHandItem();
        boolean local = p == Minecraft.getInstance().player;
        int mode;
        int flags;
        if (local) {
            WeaponState s = WeaponState.get(p);
            mode = p.isUsingItem() && p.getUsedItemHand() == InteractionHand.MAIN_HAND ? s.useMode : 0;
            flags = (s.demon ? CombatActions.FLAG_DEMON : 0) | (s.archdemon ? CombatActions.FLAG_ARCHDEMON : 0)
                    | (s.sharpenLeft > 0 ? CombatActions.FLAG_SHARPEN : 0) | (s.dashTicks > 0 ? CombatActions.FLAG_DASH : 0);
        } else {
            mode = st.useMode;
            flags = st.flags;
        }
        if ((flags & CombatActions.FLAG_SHARPEN) != 0) return "sharpen";
        if (p.isUsingItem()) {
            ItemStack use = p.getUseItem();
            boolean off = p.getUsedItemHand() == InteractionHand.OFF_HAND;
            UseAnim ua = use.getUseAnimation();
            if (ua == UseAnim.DRINK) return off ? "drink_off" : "drink";
            if (ua == UseAnim.EAT) return off ? "eat_off" : "eat";
        }
        if (!(main.getItem() instanceof HunterWeaponItem w)) return "idle";
        boolean using = p.isUsingItem() && p.getUsedItemHand() == InteractionHand.MAIN_HAND;
        return switch (w.weaponClass()) {
            case GREAT_SWORD -> using ? (mode == WeaponState.MODE_GUARD ? "guard_gs" : "charge_gs") : "idle_gs";
            case HAMMER -> using ? "charge_hm" : "idle_hm";
            case SWORD_AND_SHIELD -> using ? "guard_sns" : "idle_sns";
            case LANCE -> (flags & CombatActions.FLAG_DASH) != 0 ? "ln_dash" : using ? "guard_ln" : "idle_ln";
            case DUAL_BLADES -> (flags & CombatActions.FLAG_DEMON) != 0 ? "idle_db_demon" : "idle_db";
            case BOW -> using ? "charge_bow" : "idle_bow";
            case LONG_SWORD -> "idle_ls";
            case HUNTING_HORN -> "idle_hh";
        };
    }

    public static boolean holdsHunterWeapon(AbstractClientPlayer p) {
        return p.getMainHandItem().getItem() instanceof HunterWeaponItem;
    }

    /**
     * Poses the model. limbSwing / limbSwingAmount / ageInTicks / head angles as in LivingEntityRenderer.
     */
    public static void pose(HunterModel model, AbstractClientPlayer p, PlayerAnimState st, float pt, float limbSwing, float limbSwingAmount,
                            float ageInTicks, float netHeadYaw, float headPitch) {
        model.resetAll();
        long gt = p.level().getGameTime();
        String stanceName = stance(p, st);
        boolean armed = !"idle".equals(stanceName);

        // ---- weights
        float actionW = 0;
        float actionT = 0;
        ActionDef act = st.action;
        if (act != null && st.actionActive(gt)) {
            float raw = st.rawElapsed(gt, pt);
            actionT = st.actionTime(gt, pt);
            actionW = Mth.clamp(raw / 2.0F, 0, 1);
        } else {
            act = null;
        }
        float prevW = 0;
        if (st.prevAction != null) {
            prevW = Mth.clamp(1.0F - (gt - st.prevEnd + pt) / 3.0F, 0, 1) * (1.0F - actionW);
        }
        float dodgeW = 0;
        float dodgeT = 0;
        String dodgeName = null;
        ClientState.Dodge dodge = ClientState.DODGES.get(p.getId());
        if (dodge != null) {
            dodgeT = gt - dodge.start() + pt;
            if (dodgeT < 10.0F) {
                dodgeW = Mth.clamp(Math.min(dodgeT / 1.5F, (10.0F - dodgeT) / 1.5F), 0, 1);
                dodgeName = switch (dodge.dir()) {
                    case 0 -> "dodge_f";
                    case 1 -> "dodge_b";
                    case 2 -> "dodge_l";
                    default -> "dodge_r";
                };
            }
        }
        float baseW = (1.0F - actionW) * (1.0F - prevW) * (1.0F - dodgeW);
        float walkAmt = Mth.clamp(limbSwingAmount * 1.4F, 0, 1);
        if (p.isPassenger()) walkAmt = 0;

        // ---- stance (legs give way to the walk cycle)
        AnimationDefinition sd = HunterModels.anim(stanceName);
        if (sd != null) {
            final float legW = 1.0F - walkAmt * 0.85F;
            AnimUtil.animate(model, sd, ageInTicks / 20.0F, baseW, b -> LEGS.contains(b) ? legW : 1.0);
            if (stanceName.startsWith("charge_")) chargeShake(model, p, ageInTicks, baseW);
        }
        // ---- walk / run
        if (walkAmt > 0.02F) {
            boolean run = p.isSprinting();
            AnimationDefinition wd = HunterModels.anim((run ? "run" : "walk") + (armed ? "_armed" : ""));
            if (wd != null) AnimUtil.animate(model, wd, limbSwing * 0.106F, baseW * walkAmt, null);
        }
        // ---- previous action fading out from its last frame
        if (prevW > 0) {
            AnimationDefinition pd = HunterModels.anim(st.prevAction.anim);
            if (pd != null) AnimUtil.animate(model, pd, scaled(pd, st.prevAction, st.prevTime), prevW * (1.0F - dodgeW), null);
        }
        // ---- action
        if (act != null) {
            AnimationDefinition ad = HunterModels.anim(act.anim);
            if (ad != null) AnimUtil.animate(model, ad, scaled(ad, act, actionT), actionW * (1.0F - dodgeW), null);
        }
        // ---- dodge roll
        if (dodgeW > 0) {
            AnimationDefinition dd = HunterModels.anim(dodgeName);
            if (dd != null) AnimUtil.animate(model, dd, dodgeT / 20.0F, dodgeW, null);
        }
        // ---- crouch
        if (p.isCrouching() && dodgeW < 0.5F) {
            AnimationDefinition cd = HunterModels.anim("crouch");
            if (cd != null) AnimUtil.animate(model, cd, 0, (1.0F - dodgeW) * (1.0F - 0.6F * actionW), null);
        }
        // ---- head look
        float look = (1.0F - 0.6F * actionW) * (1.0F - dodgeW);
        model.head.yRot += Mth.clamp(netHeadYaw, -75, 75) * DEG * look;
        model.head.xRot += headPitch * DEG * look;
        // ---- vanilla style arm swing when hitting with a non hunter item
        if (!holdsHunterWeapon(p) && p.attackAnim > 0 && act == null) {
            float a = p.getAttackAnim(pt);
            float s1 = Mth.sin(Mth.sqrt(a) * Mth.PI * 2.0F);
            float s2 = Mth.sin(a * Mth.PI);
            model.armR.xRot += -(s1 * 1.2F + s2 * 0.5F) * (1.0F - dodgeW);
            model.armR.yRot += -s2 * 0.35F;
            model.armRLower.xRot += -s2 * 0.6F;
            model.chest.yRot += -s2 * 0.25F;
        }
    }

    /** Animation seconds for action tick t (the animation is stretched to the action length). */
    private static float scaled(AnimationDefinition def, ActionDef act, float ticks) {
        float len = def.lengthInSeconds();
        if (act.ticks <= 0 || len <= 0) return ticks / 20.0F;
        return Mth.clamp(ticks / act.ticks, 0, 1) * len;
    }

    private static void chargeShake(HunterModel model, AbstractClientPlayer p, float age, float w) {
        int lv = 0;
        if (p.isUsingItem() && p.getUseItem().getItem() instanceof HunterWeaponItem hw) lv = hw.chargeLevel(p, p.getTicksUsingItem());
        if (lv <= 0) return;
        float amp = 0.012F * lv * w;
        float s = Mth.sin(age * 2.3F) * amp;
        float c = Mth.cos(age * 2.9F) * amp;
        model.armR.xRot += s;
        model.armR.zRot += c;
        model.armL.xRot += c;
        model.armL.zRot += s;
        model.chest.zRot += s * 0.3F;
    }
}
