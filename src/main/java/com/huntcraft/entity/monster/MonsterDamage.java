package com.huntcraft.entity.monster;

import com.huntcraft.core.Element;
import com.huntcraft.core.HunterSkills;
import com.huntcraft.core.SkillType;
import com.huntcraft.data.HunterData;
import com.huntcraft.registry.HCEffects;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/** Damage dealt BY monsters TO hunters / other entities (guard, i-frames, blights, knockback). */
public final class MonsterDamage {
    /** Knock the target off its feet with a short stun. */
    public static final int FLAG_TRIP = 1;
    /** Dizzy stun (like a heavy head slam). */
    public static final int FLAG_STUN = 2;
    /** Ignores guard (unblockable). */
    public static final int FLAG_UNBLOCKABLE = 4;
    /** Big launch upward. */
    public static final int FLAG_LAUNCH = 8;

    private MonsterDamage() {
    }

    /**
     * @param source    attacking entity (monster or projectile owner)
     * @param damage    already-multiplied final damage before guard/resistances
     * @return true if damage was applied
     */
    public static boolean deal(Entity source, LivingEntity attacker, LivingEntity target, float damage, Element element, float knockback, int flags) {
        if (target == attacker || !target.isAlive()) return false;
        if (target instanceof HuntMonster) return false; // monsters do not hurt each other
        boolean blocked = false;
        if (target instanceof Player player) {
            if (player.isCreative() || player.isSpectator()) return false;
            HunterData data = HunterData.get(player);
            if (data.isInvulnerable()) {
                return false; // perfect dodge
            }
            if (element.isElemental()) {
                int res = resLevel(player, element);
                damage *= 1.0F - 0.1F * res;
            }
            if (data.guardPower > 0 && (flags & FLAG_UNBLOCKABLE) == 0 && isFacing(player, source.position(), 110)) {
                float guardLv = HunterSkills.level(player, SkillType.GUARD);
                float power = Math.min(0.95F, data.guardPower + 0.05F * guardLv);
                damage *= (1.0F - power);
                knockback *= 0.25F * (1.0F - 0.25F * guardLv);
                data.consumeStamina(Math.max(4, 14 - guardLv * 3));
                player.level().playSound(null, player.blockPosition(), SoundEvents.SHIELD_BLOCK, SoundSource.PLAYERS, 1.0F, 0.8F + player.getRandom().nextFloat() * 0.2F);
                blocked = true;
                flags &= ~(FLAG_STUN | FLAG_TRIP | FLAG_LAUNCH);
            }
            if (player.hasEffect(HCEffects.THUNDERBLIGHT.get()) && !blocked && player.getRandom().nextFloat() < 0.3F) {
                flags |= FLAG_STUN;
            }
        }
        target.invulnerableTime = 0;
        boolean hurt = target.hurt(attacker.damageSources().mobAttack(attacker), Math.max(0.5F, damage));
        if (!hurt) return false;
        if (!blocked) applyElementEffect(target, element);
        if (knockback > 0) applyKnockback(source.position(), target, knockback, (flags & FLAG_LAUNCH) != 0);
        if (!blocked && target instanceof Player player) {
            if ((flags & FLAG_STUN) != 0 && HunterSkills.level(player, SkillType.STUN_RES) < 2) {
                player.addEffect(new MobEffectInstance(HCEffects.STUN.get(), HunterSkills.level(player, SkillType.STUN_RES) == 1 ? 25 : 50, 0));
            }
            if ((flags & FLAG_TRIP) != 0) {
                player.addEffect(new MobEffectInstance(HCEffects.STUN.get(), 12, 0, false, false));
            }
        }
        return true;
    }

    public static int resLevel(Player player, Element element) {
        SkillType t = switch (element) {
            case FIRE -> SkillType.FIRE_RES;
            case WATER -> SkillType.WATER_RES;
            case THUNDER -> SkillType.THUNDER_RES;
            case ICE -> SkillType.ICE_RES;
            case DRAGON -> SkillType.DRAGON_RES;
            case POISON -> SkillType.POISON_RES;
            default -> null;
        };
        return t == null ? 0 : HunterSkills.level(player, t);
    }

    public static void applyElementEffect(LivingEntity target, Element element) {
        if (element == Element.NONE) return;
        int res = target instanceof Player p ? resLevel(p, element) : 0;
        MobEffect effect = null;
        int duration = 0;
        switch (element) {
            case FIRE -> { effect = HCEffects.FIREBLIGHT.get(); duration = 160; if (res >= 3) effect = null; }
            case ICE -> { effect = HCEffects.ICEBLIGHT.get(); duration = 240; if (res >= 3) effect = null; }
            case THUNDER -> { effect = HCEffects.THUNDERBLIGHT.get(); duration = 240; if (res >= 3) effect = null; }
            case WATER -> { effect = HCEffects.WATERBLIGHT.get(); duration = 240; if (res >= 3) effect = null; }
            case DRAGON -> { effect = HCEffects.DRAGONBLIGHT.get(); duration = 600; if (res >= 3) effect = null; }
            case POISON -> { effect = HCEffects.POISON.get(); duration = res >= 2 ? 0 : (res == 1 ? 120 : 240); }
            case PARALYSIS -> { effect = HCEffects.PARALYSIS.get(); duration = 50; }
            case SLEEP -> { effect = HCEffects.SLEEP.get(); duration = 80; }
            case BLAST -> { }
            default -> { }
        }
        if (effect != null && duration > 0) target.addEffect(new MobEffectInstance(effect, duration, 0));
    }

    public static void applyKnockback(Vec3 from, LivingEntity target, float strength, boolean launch) {
        Vec3 dir = target.position().subtract(from);
        dir = new Vec3(dir.x, 0, dir.z);
        if (dir.lengthSqr() < 1.0E-4) dir = new Vec3(0, 0, 1);
        dir = dir.normalize();
        double horiz = 0.35 + 0.35 * strength;
        double up = launch ? 0.55 + 0.15 * strength : 0.2 + 0.08 * Math.min(strength, 3);
        double resist = 1.0 - Mth.clamp(target.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.KNOCKBACK_RESISTANCE), 0, 1) * 0.6;
        target.setDeltaMovement(target.getDeltaMovement().scale(0.3).add(dir.x * horiz * resist, up * resist, dir.z * horiz * resist));
        target.hurtMarked = true;
    }

    /** True if `entity` faces `point` within the given full cone angle (degrees). */
    public static boolean isFacing(LivingEntity entity, Vec3 point, float coneDeg) {
        Vec3 look = entity.getViewVector(1.0F);
        Vec3 to = point.subtract(entity.getEyePosition());
        Vec3 flatLook = new Vec3(look.x, 0, look.z);
        Vec3 flatTo = new Vec3(to.x, 0, to.z);
        if (flatLook.lengthSqr() < 1e-6 || flatTo.lengthSqr() < 1e-6) return true;
        double dot = flatLook.normalize().dot(flatTo.normalize());
        return dot >= Math.cos(Math.toRadians(coneDeg / 2.0));
    }
}
