package com.eternalrealm.job;

import com.eternalrealm.combat.CombatModifiers;
import com.eternalrealm.combat.HitFrame;
import com.eternalrealm.combat.HitShape;
import com.eternalrealm.combat.ProjectileSpec;
import com.eternalrealm.job.SkillEffect.AfflictionKind;
import com.eternalrealm.job.SkillEffect.BuffKind;
import com.eternalrealm.job.SkillEffect.BuffTarget;
import com.eternalrealm.job.SkillEffect.Placement;

import java.util.ArrayList;
import java.util.List;

/**
 * Short helpers for the skill catalogues ({@code KnightSkills} ...): hit shapes, hit frames, projectiles, effects and
 * passive bonuses. Effect frames are hit frame indices of the skill's action ({@link #START} = when it starts).
 */
final class Dsl {
    static final int START = SkillEffect.ON_START;

    private Dsl() {
    }

    // ------------------------------------------------------------------ shapes

    static HitShape arc(double reach, double halfAngle) {
        return HitShape.arc(reach, halfAngle);
    }

    static HitShape circle(double radius) {
        return HitShape.circle(radius);
    }

    /** A disc {@code forward} blocks ahead (area attacks at a distance). */
    static HitShape ahead(double radius, double forward) {
        return HitShape.circleAhead(radius, forward);
    }

    static HitShape line(double length, double width) {
        return HitShape.line(length, width);
    }

    /** A circle that hits at most {@code targets} enemies (single-target magic). */
    static HitShape circle(double radius, int targets) {
        return new HitShape(HitShape.Kind.CIRCLE, radius, 180.0, 0.0, 4.0, 6.0, targets);
    }

    /** A line hitting more targets than a thrust (beams). */
    static HitShape beam(double length, double width) {
        return new HitShape(HitShape.Kind.LINE, length, 0.0, width, 1.0, 1.5, 12);
    }

    // ------------------------------------------------------------------ frames

    static HitFrame hit(int offset, HitShape shape, double multiplier, double knockback) {
        return new HitFrame(offset, shape, multiplier, knockback);
    }

    static HitFrame shot(int offset, ProjectileSpec projectile, double multiplier, double knockback) {
        return HitFrame.launch(offset, projectile, multiplier, knockback);
    }

    /** A frame that hits nothing (heals, buffs, ground areas happen here). */
    static HitFrame pause(int offset) {
        return HitFrame.effect(offset);
    }

    /** {@code count} melee hits every {@code step} ticks from {@code first}. */
    static HitFrame[] hits(int first, int step, int count, HitShape shape, double multiplier, double knockback) {
        HitFrame[] frames = new HitFrame[count];
        for (int i = 0; i < count; i++) {
            frames[i] = hit(first + i * step, shape, multiplier, knockback);
        }
        return frames;
    }

    /** {@code count} projectile frames every {@code step} ticks from {@code first}. */
    static HitFrame[] shots(int first, int step, int count, ProjectileSpec projectile, double multiplier,
                            double knockback) {
        HitFrame[] frames = new HitFrame[count];
        for (int i = 0; i < count; i++) {
            frames[i] = shot(first + i * step, projectile, multiplier, knockback);
        }
        return frames;
    }

    /** Frames of several groups, in order (offsets must not decrease). */
    static HitFrame[] seq(Object... parts) {
        List<HitFrame> frames = new ArrayList<>();
        for (Object part : parts) {
            if (part instanceof HitFrame frame) {
                frames.add(frame);
            } else if (part instanceof HitFrame[] array) {
                frames.addAll(List.of(array));
            } else {
                throw new IllegalArgumentException("not a frame: " + part);
            }
        }
        return frames.toArray(new HitFrame[0]);
    }

    // ------------------------------------------------------------------ projectiles

    static ProjectileSpec orb(String kind, double speed, int lifetime) {
        return ProjectileSpec.magic(kind, speed, lifetime);
    }

    static ProjectileSpec arrow(double speed) {
        return ProjectileSpec.arrow(speed);
    }

    /** A thrown object that falls (flasks, bombs). */
    static ProjectileSpec lob(String kind, double speed, int lifetime) {
        return new ProjectileSpec(kind, speed, true, lifetime, 0, 0.0, 1, 0.0, false);
    }

    // ------------------------------------------------------------------ effects

    static SkillEffect heal(int frame, double radius, double fraction, double flat) {
        return new SkillEffect.Heal(frame, radius, fraction, flat);
    }

    static SkillEffect self(int frame, BuffKind kind, double magnitude, int ticks) {
        return new SkillEffect.Buff(frame, BuffTarget.SELF, kind, magnitude, ticks, 0.0);
    }

    static SkillEffect allies(int frame, BuffKind kind, double magnitude, int ticks, double radius) {
        return new SkillEffect.Buff(frame, BuffTarget.ALLIES, kind, magnitude, ticks, radius);
    }

    static SkillEffect shield(int frame, double fraction, int ticks) {
        return new SkillEffect.Shield(frame, fraction, ticks);
    }

    static SkillEffect shieldAllies(int frame, double fraction, int ticks, double radius) {
        return new SkillEffect.Shield(frame, fraction, ticks, radius);
    }

    static SkillEffect dash(int frame, double distance) {
        return new SkillEffect.Dash(frame, distance, false);
    }

    static SkillEffect retreat(int frame, double distance) {
        return new SkillEffect.Dash(frame, distance, true);
    }

    static SkillEffect leap(int frame, double distance, double height) {
        return new SkillEffect.Leap(frame, distance, height);
    }

    static SkillEffect taunt(int frame, double radius, int ticks) {
        return new SkillEffect.Taunt(frame, radius, ticks);
    }

    static SkillEffect vanish(int frame, double radius) {
        return new SkillEffect.DropAggro(frame, radius);
    }

    static SkillEffect afflict(int frame, AfflictionKind kind, int ticks, double power) {
        return new SkillEffect.Afflict(frame, kind, ticks, power);
    }

    static SkillEffect vis(int frame, String kind) {
        return new SkillEffect.Visual(frame, kind);
    }

    static SkillEffect cleanse(int frame, double radius) {
        return new SkillEffect.Cleanse(frame, radius);
    }

    static SkillEffect siphon(int frame, double fraction, double radius) {
        return new SkillEffect.Siphon(frame, fraction, radius);
    }

    static SkillEffect stamina(int frame, double amount) {
        return new SkillEffect.RestoreStamina(frame, amount);
    }

    static SkillEffect cdcut(int frame, int ticks) {
        return new SkillEffect.CooldownCut(frame, ticks);
    }

    static SkillEffect execute(int frame, double threshold, double bonus) {
        return new SkillEffect.Execute(frame, threshold, bonus);
    }

    /** A damaging ground area. */
    static SkillEffect zone(int frame, Placement placement, double distance, double radius, int delay, int pulses,
                            int interval, double damage) {
        return new SkillEffect.Zone(frame, placement, distance, radius, delay, pulses, interval, damage, 0.0, null, 0,
                0.0);
    }

    /** A damaging ground area that afflicts what it damages. */
    static SkillEffect zone(int frame, Placement placement, double distance, double radius, int delay, int pulses,
                            int interval, double damage, AfflictionKind kind, int ticks, double power) {
        return new SkillEffect.Zone(frame, placement, distance, radius, delay, pulses, interval, damage, 0.0, kind,
                ticks, power);
    }

    /** A healing ground area (optionally also damaging enemies). */
    static SkillEffect healZone(int frame, Placement placement, double distance, double radius, int pulses,
                                int interval, double healFraction, double damage) {
        return new SkillEffect.Zone(frame, placement, distance, radius, 0, pulses, interval, damage, healFraction, null,
                0, 0.0);
    }

    // ------------------------------------------------------------------ passives

    static CombatModifiers.Builder mods() {
        return CombatModifiers.builder();
    }

    static PassiveEffect stats(CombatModifiers.Builder perLevel) {
        return new PassiveEffect.Stats(perLevel.build());
    }

    static PassiveEffect power(double perLevel) {
        return new PassiveEffect.SkillPower(perLevel);
    }

    static PassiveEffect cooldown(double perLevel) {
        return new PassiveEffect.Cooldown(perLevel);
    }

    static PassiveEffect saver(double perLevel) {
        return new PassiveEffect.StaminaSaver(perLevel);
    }

    static PassiveEffect lifesteal(double perLevel) {
        return new PassiveEffect.Lifesteal(perLevel);
    }

    static PassiveEffect proc(AfflictionKind kind, double chancePerLevel, int ticks, double power, boolean meleeOnly) {
        return new PassiveEffect.Proc(kind, chancePerLevel, ticks, power, meleeOnly);
    }

    static PassiveEffect lastStand(double damagePerLevel, double reductionPerLevel) {
        return new PassiveEffect.LastStand(damagePerLevel, reductionPerLevel);
    }

    static PassiveEffect thorns(double perLevel) {
        return new PassiveEffect.Thorns(perLevel);
    }

    static PassiveEffect evasion(double perLevel) {
        return new PassiveEffect.Evasion(perLevel);
    }

    static PassiveEffect healOnKill(double perLevel) {
        return new PassiveEffect.HealOnKill(perLevel);
    }
}
