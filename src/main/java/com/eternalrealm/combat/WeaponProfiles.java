package com.eternalrealm.combat;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * The weapon profiles (docs/COMBAT_SYSTEM.md, section 5; staff and bow since Phase 3). Timings are in ticks before attack-speed
 * scaling. Defined in code so the client's prediction and the server's validation always agree (D-030).
 */
public final class WeaponProfiles {
    /** Id of the staff's weapon skill and of its bolt (the projectile kind). */
    public static final String STAR_SIGIL = "star_sigil";

    /** 片手剣: balanced three-step combo, good guard, whirlwind skill. */
    public static final WeaponProfile ONE_HANDED_SWORD = new WeaponProfile("one_handed_sword",
            WeaponType.ONE_HANDED_SWORD, 6.0, 0.0,
            List.of(
                    light("one_handed_sword_1", 10, 6, frame(3, HitShape.arc(3.2, 50), 1.0, 0.25)),
                    light("one_handed_sword_2", 10, 6, frame(3, HitShape.arc(3.2, 50), 1.1, 0.25)),
                    light("one_handed_sword_3", 16, 12, frame(5, HitShape.arc(3.4, 70), 1.6, 0.6))),
            12,
            new HeavyAttack(8, 30, 1.8, 3.0, 0.3,
                    heavy("one_handed_sword_heavy", 14, 10, 15, frame(3, HitShape.arc(3.6, 60), 1.0, 0.8))),
            skill("whirlwind", 22, 20, 25, 160,
                    frame(7, HitShape.circle(3.8), 1.4, 0.5),
                    frame(13, HitShape.circle(3.8), 1.4, 0.5)),
            new GuardProfile(0.60, 5, 75, 3.0));

    /** 両手剣: slow, wide and heavy hitting; strongest guard; ground slam skill. */
    public static final WeaponProfile TWO_HANDED_SWORD = new WeaponProfile("two_handed_sword",
            WeaponType.TWO_HANDED_SWORD, 9.0, 0.0,
            List.of(
                    light("two_handed_sword_1", 16, 11, frame(6, HitShape.arc(3.6, 80), 1.0, 0.4)),
                    light("two_handed_sword_2", 20, 15, frame(7, HitShape.arc(3.6, 80), 1.4, 0.8))),
            14,
            new HeavyAttack(12, 40, 2.0, 3.5, 0.3,
                    heavy("two_handed_sword_heavy", 18, 14, 20, frame(4, HitShape.arc(4.0, 70), 1.0, 1.2))),
            skill("ground_rend", 28, 26, 35, 240,
                    frame(12, HitShape.circle(4.5), 3.0, 1.0)),
            new GuardProfile(0.75, 4, 70, 2.5));

    /** 双剣: very fast four-step combo, critical bonus, weak guard with a long parry window; flurry skill. */
    public static final WeaponProfile DUAL_BLADES = new WeaponProfile("dual_blades",
            WeaponType.DUAL_BLADES, 4.0, 0.10,
            List.of(
                    light("dual_blades_1", 7, 4, frame(2, HitShape.arc(2.8, 45), 0.7, 0.1)),
                    light("dual_blades_2", 7, 4, frame(2, HitShape.arc(2.8, 45), 0.7, 0.1)),
                    light("dual_blades_3", 7, 4, frame(2, HitShape.arc(2.8, 45), 0.8, 0.1)),
                    light("dual_blades_4", 12, 9, frame(3, HitShape.arc(3.0, 60), 1.2, 0.4))),
            10,
            new HeavyAttack(6, 24, 1.0, 1.6, 0.2,
                    heavy("dual_blades_heavy", 12, 9, 15,
                            frame(2, HitShape.arc(3.0, 50), 1.0, 0.2),
                            frame(5, HitShape.arc(3.0, 50), 1.0, 0.2))),
            skill("blade_dance", 30, 28, 30, 200,
                    frame(4, HitShape.arc(3.2, 60), 0.6, 0.05),
                    frame(8, HitShape.arc(3.2, 60), 0.6, 0.05),
                    frame(12, HitShape.arc(3.2, 60), 0.6, 0.05),
                    frame(16, HitShape.arc(3.2, 60), 0.6, 0.05),
                    frame(20, HitShape.arc(3.2, 60), 0.6, 0.05),
                    frame(24, HitShape.arc(3.2, 60), 0.6, 0.05)),
            new GuardProfile(0.40, 7, 70, 3.5));

    /** 槍: long piercing thrusts; drilling thrust skill. */
    public static final WeaponProfile SPEAR = new WeaponProfile("spear",
            WeaponType.SPEAR, 7.0, 0.0,
            List.of(
                    light("spear_1", 12, 8, frame(4, HitShape.line(4.5, 0.6), 1.0, 0.3)),
                    light("spear_2", 12, 8, frame(4, HitShape.line(4.5, 0.6), 1.0, 0.3)),
                    light("spear_3", 16, 12, frame(5, HitShape.line(5.0, 0.6), 1.4, 0.7))),
            12,
            new HeavyAttack(10, 36, 1.8, 3.2, 0.3,
                    heavy("spear_heavy", 16, 12, 15, frame(4, HitShape.line(5.5, 0.7), 1.0, 1.0))),
            skill("spiral_thrust", 22, 20, 25, 160,
                    frame(8, HitShape.line(6.5, 0.8), 1.0, 0.3),
                    frame(11, HitShape.line(6.5, 0.8), 1.0, 0.3),
                    frame(14, HitShape.line(6.5, 0.8), 1.0, 0.3)),
            new GuardProfile(0.50, 5, 70, 3.0));

    /**
     * 杖: magic bolts (INT); the third bolt and the charged orb explode; the weapon skill is a spell, the star sigil: a
     * magic circle charges in front of the caster and launches a bolt that bursts where it lands.
     */
    public static final WeaponProfile STAFF = new WeaponProfile("staff",
            WeaponType.STAFF, 6.0, 0.0,
            List.of(
                    light("staff_1", 14, 10, HitFrame.launch(4, ProjectileSpec.magic("magic_bolt", 1.6, 20), 1.0, 0.2)),
                    light("staff_2", 14, 10, HitFrame.launch(4, ProjectileSpec.magic("magic_bolt", 1.6, 20), 1.0, 0.2)),
                    light("staff_3", 20, 15, HitFrame.launch(6,
                            ProjectileSpec.magic("magic_orb", 1.4, 22).withExplosion(1.8), 1.5, 0.5))),
            12,
            new HeavyAttack(10, 40, 2.0, 3.5, 0.3,
                    heavy("staff_heavy", 16, 12, 20, HitFrame.launch(4,
                            ProjectileSpec.magic("magic_orb", 1.5, 24).withExplosion(2.5), 1.0, 0.8))),
            skill(STAR_SIGIL, 24, 22, 30, 200, HitFrame.launch(10,
                    ProjectileSpec.magic(STAR_SIGIL, 1.3, 30).withExplosion(3.5), 2.2, 1.0)),
            new GuardProfile(0.35, 5, 75, 3.5));

    /** 弓: arrows (DEX); charged shots pierce; piercing arrow skill. */
    public static final WeaponProfile BOW = new WeaponProfile("bow",
            WeaponType.BOW, 6.0, 0.05,
            List.of(
                    light("bow_1", 14, 10, HitFrame.launch(5, ProjectileSpec.arrow(3.0), 1.0, 0.3)),
                    light("bow_2", 16, 12, HitFrame.launch(6, ProjectileSpec.arrow(3.0), 1.2, 0.4))),
            12,
            new HeavyAttack(10, 40, 1.8, 3.5, 0.2,
                    heavy("bow_heavy", 14, 10, 15, HitFrame.launch(3, ProjectileSpec.arrow(3.5).withPierce(2), 1.0, 1.0))),
            skill("piercing_arrow", 20, 18, 25, 160,
                    HitFrame.launch(8, ProjectileSpec.arrow(4.0).withPierce(8), 2.5, 0.8)),
            new GuardProfile(0.25, 4, 70, 4.0));

    private static final List<WeaponProfile> ALL =
            List.of(ONE_HANDED_SWORD, TWO_HANDED_SWORD, DUAL_BLADES, SPEAR, STAFF, BOW);

    private WeaponProfiles() {
    }

    public static List<WeaponProfile> all() {
        return ALL;
    }

    /** The profile of a weapon type (one per type). */
    public static WeaponProfile of(WeaponType type) {
        for (WeaponProfile profile : ALL) {
            if (profile.type() == type) {
                return profile;
            }
        }
        throw new IllegalStateException("No profile for " + type);
    }

    public static Optional<WeaponProfile> byId(String id) {
        for (WeaponProfile profile : ALL) {
            if (profile.id().equals(id)) {
                return Optional.of(profile);
            }
        }
        return Optional.empty();
    }

    private static HitFrame frame(int offset, HitShape shape, double multiplier, double knockback) {
        return new HitFrame(offset, shape, multiplier, knockback);
    }

    private static ActionDefinition light(String id, int duration, int cancel, HitFrame frame) {
        return new ActionDefinition(id, ActionKind.LIGHT, duration, cancel, List.of(frame), 0.0, 0, 0, true);
    }

    private static ActionDefinition heavy(String id, int duration, int cancel, double stamina, HitFrame... frames) {
        return new ActionDefinition(id, ActionKind.HEAVY, duration, cancel, List.of(frames), stamina, 0, 0, true);
    }

    private static ActionDefinition skill(String id, int duration, int cancel, double stamina, int cooldown,
                                          HitFrame... frames) {
        return new ActionDefinition(id, ActionKind.SKILL, duration, cancel, new ArrayList<>(Arrays.asList(frames)),
                stamina, cooldown, 0, false);
    }
}
