package com.huntcraft.gear.weapon.client;

import com.huntcraft.core.WeaponClass;
import com.huntcraft.gear.weapon.GreatSwordItem;
import com.huntcraft.gear.weapon.HunterWeaponItem;
import com.huntcraft.gear.weapon.WeaponState;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;

/**
 * Client item extensions of the weapons: custom third person arm poses (two-handed great sword / hammer / horn,
 * couched lance, guards, charges, demon mode) and first person charge / guard transforms.
 */
@OnlyIn(Dist.CLIENT)
public final class WeaponItemExtensions implements IClientItemExtensions {
    private final HunterWeaponItem item;

    private WeaponItemExtensions(HunterWeaponItem item) {
        this.item = item;
    }

    public static IClientItemExtensions create(HunterWeaponItem item) {
        return new WeaponItemExtensions(item);
    }

    // ================================================================== third person poses
    private interface Pose {
        void apply(ModelPart main, ModelPart other, ModelPart head, float side, LivingEntity entity);
    }

    private static HumanoidModel.ArmPose pose(String name, boolean twoHanded, Pose p) {
        return HumanoidModel.ArmPose.create(name, twoHanded, (model, entity, arm) -> {
            boolean right = arm == HumanoidArm.RIGHT;
            ModelPart main = right ? model.rightArm : model.leftArm;
            ModelPart other = right ? model.leftArm : model.rightArm;
            p.apply(main, other, model.head, right ? 1.0F : -1.0F, entity);
        });
    }

    private static float shake(LivingEntity e, float amount) {
        return Mth.sin(e.tickCount * 2.3F) * amount;
    }

    private static int chargeLevel(LivingEntity e) {
        if (!(e instanceof Player p) || !(p.getUseItem().getItem() instanceof HunterWeaponItem w)) return 0;
        return w.chargeLevel(p, p.getTicksUsingItem());
    }

    /** Great sword / hunting horn held with both hands, blade resting forward. */
    private static final HumanoidModel.ArmPose HEAVY = pose("HUNTCRAFT_HEAVY", true, (m, o, h, s, e) -> {
        m.xRot = -0.85F;
        m.yRot = -0.35F * s;
        o.xRot = -0.95F;
        o.yRot = 0.55F * s;
    });
    /** Great sword charge: raised high over the shoulder, trembling with the charge level. */
    private static final HumanoidModel.ArmPose GS_CHARGE = pose("HUNTCRAFT_GS_CHARGE", true, (m, o, h, s, e) -> {
        float sh = shake(e, 0.035F * chargeLevel(e));
        m.xRot = -2.55F + sh;
        m.yRot = -0.25F * s;
        o.xRot = -2.45F + sh;
        o.yRot = 0.4F * s;
    });
    /** Hammer carried low with both hands. */
    private static final HumanoidModel.ArmPose HAMMER = pose("HUNTCRAFT_HAMMER", true, (m, o, h, s, e) -> {
        m.xRot = -0.55F;
        m.yRot = -0.25F * s;
        o.xRot = -0.7F;
        o.yRot = 0.6F * s;
    });
    /** Hammer charge: dragged back, ready for the swing. */
    private static final HumanoidModel.ArmPose HAMMER_CHARGE = pose("HUNTCRAFT_HAMMER_CHARGE", true, (m, o, h, s, e) -> {
        float sh = shake(e, 0.04F * chargeLevel(e));
        m.xRot = 0.55F + sh;
        m.yRot = -0.15F * s;
        o.xRot = 0.35F + sh;
        o.yRot = 0.55F * s;
    });
    /** Blade / shield held across the body. */
    private static final HumanoidModel.ArmPose GUARD = pose("HUNTCRAFT_GUARD", true, (m, o, h, s, e) -> {
        m.xRot = -1.25F;
        m.yRot = -0.75F * s;
        o.xRot = -1.2F;
        o.yRot = 0.85F * s;
    });
    /** Lance couched forward following the view, shield arm ready. */
    private static final HumanoidModel.ArmPose LANCE = pose("HUNTCRAFT_LANCE", true, (m, o, h, s, e) -> {
        m.xRot = -1.45F + h.xRot * 0.6F;
        m.yRot = -0.1F * s + h.yRot * 0.5F;
        o.xRot = -0.9F;
        o.yRot = 0.45F * s;
    });
    /** Lance guard: shield raised in front, lance held low. */
    private static final HumanoidModel.ArmPose LANCE_GUARD = pose("HUNTCRAFT_LANCE_GUARD", true, (m, o, h, s, e) -> {
        m.xRot = -1.05F;
        m.yRot = 0.1F * s;
        o.xRot = -1.55F + h.xRot * 0.4F;
        o.yRot = 0.95F * s;
    });
    /** Demon mode: both blades held out low. */
    private static final HumanoidModel.ArmPose DEMON = pose("HUNTCRAFT_DEMON", true, (m, o, h, s, e) -> {
        m.xRot = -0.45F;
        m.zRot = 0.45F * s;
        o.xRot = -0.45F;
        o.zRot = -0.45F * s;
    });

    private static boolean isLocal(LivingEntity e) {
        return e == Minecraft.getInstance().player;
    }

    private static boolean guardMode(LivingEntity e) {
        if (e instanceof Player p && isLocal(e)) return WeaponState.get(p).useMode == WeaponState.MODE_GUARD;
        return e.isCrouching();
    }

    @Override
    public HumanoidModel.ArmPose getArmPose(LivingEntity entity, InteractionHand hand, ItemStack stack) {
        if (hand != InteractionHand.MAIN_HAND) return null;
        boolean using = entity.isUsingItem() && entity.getUseItem().getItem() == item;
        WeaponClass c = item.weaponClass();
        return switch (c) {
            case GREAT_SWORD -> using ? (guardMode(entity) ? GUARD : GS_CHARGE) : HEAVY;
            case HAMMER -> using ? HAMMER_CHARGE : HAMMER;
            case HUNTING_HORN -> HEAVY;
            case LANCE -> using ? LANCE_GUARD : LANCE;
            case SWORD_AND_SHIELD -> using ? GUARD : null;
            case DUAL_BLADES -> entity instanceof Player p && isLocal(entity) && WeaponState.get(p).demon ? DEMON : null;
            default -> null;
        };
    }

    // ================================================================== first person
    @Override
    public boolean applyForgeHandTransform(PoseStack poseStack, LocalPlayer player, HumanoidArm arm, ItemStack itemInHand,
                                           float partialTick, float equipProcess, float swingProcess) {
        if (!player.isUsingItem() || player.getUseItem().getItem() != item) return false;
        WeaponClass c = item.weaponClass();
        if (c == WeaponClass.BOW || c == WeaponClass.HUNTING_HORN || c == WeaponClass.LONG_SWORD || c == WeaponClass.DUAL_BLADES) return false;
        WeaponState s = WeaponState.get(player);
        float side = arm == HumanoidArm.RIGHT ? 1.0F : -1.0F;
        float used = player.getTicksUsingItem() + partialTick;
        // vanilla base arm transform
        poseStack.translate(side * 0.56F, -0.52F + equipProcess * -0.6F, -0.72F);
        boolean guard = s.useMode == WeaponState.MODE_GUARD || c == WeaponClass.LANCE || c == WeaponClass.SWORD_AND_SHIELD;
        float t = Math.min(1.0F, used / 6.0F);
        if (guard) {
            poseStack.translate(side * -0.32F * t, 0.10F * t, 0.05F * t);
            poseStack.mulPose(Axis.YP.rotationDegrees(side * 55.0F * t));
            poseStack.mulPose(Axis.ZP.rotationDegrees(side * 20.0F * t));
            return true;
        }
        int lv = item.chargeLevel(player, (int) used);
        boolean over = item instanceof GreatSwordItem gs && gs.isOvercharged(player, (int) used);
        if (c == WeaponClass.HAMMER) {
            poseStack.translate(side * 0.05F * t, -0.12F * t, 0.15F * t);
            poseStack.mulPose(Axis.XP.rotationDegrees(-40.0F * t));
            poseStack.mulPose(Axis.ZP.rotationDegrees(side * -10.0F * t));
        } else {
            poseStack.translate(side * -0.1F * t, 0.2F * t, 0.15F * t);
            poseStack.mulPose(Axis.XP.rotationDegrees(35.0F * t));
            poseStack.mulPose(Axis.ZP.rotationDegrees(side * 15.0F * t));
        }
        if (lv > 0) {
            float sh = (over ? 0.012F : 0.004F) * lv;
            poseStack.translate(Mth.sin(used * 2.3F) * sh, Mth.cos(used * 2.9F) * sh, 0.0F);
        }
        return true;
    }
}
