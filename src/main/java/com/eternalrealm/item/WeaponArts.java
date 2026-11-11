package com.eternalrealm.item;

import com.eternalrealm.combat.ActionDefinition;
import com.eternalrealm.combat.ActionKind;
import com.eternalrealm.combat.HitFrame;
import com.eternalrealm.combat.HitShape;
import com.eternalrealm.combat.ProjectileSpec;
import com.eternalrealm.combat.WeaponType;
import com.eternalrealm.job.SkillEffect;
import com.eternalrealm.job.SkillEffect.AfflictionKind;
import com.eternalrealm.job.SkillEffect.BuffKind;
import com.eternalrealm.job.SkillEffect.BuffTarget;
import com.eternalrealm.job.SkillEffect.Placement;
import com.eternalrealm.job.SkillTheme;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * The nine weapon arts (武技) of the boss weapons, one per boss. Each is a showpiece: a long wind-up or a ground marker
 * says where it lands (delayed ground areas are marked for the wielder's party), it has its own look and sound, and it
 * is stronger than the weapon type's own skill at a longer cooldown, and stronger in one use than any art of a named or
 * tier weapon ({@link GeneratedArts}; six of them are raised by {@link ArtForge#strengthened}).
 *
 * <pre>
 * art                    weapon        boss   what it does
 * art_shadow_legion      twin blades   b1     vanishes into smoke, six slashes from every side, a bleeding finisher
 * art_thornroot_bloom    staff         b2     a bed of thorn roots blooms where you look: binds and hurts foes, heals allies
 * art_sand_maelstrom     spear         b3     three drilling thrusts, then a sand vortex drags enemies into the spear's line
 * art_doom_guillotine    greatsword    b4     a marked blade of ice falls ahead: huge damage, executes the weak, freezes
 * art_frozen_prison      staff         b5     ice entombs a marked area, then the prison shatters
 * art_meteor_slash       longsword     b6     a burning slash, then meteors strike where it pointed
 * art_heavens_chain      bow           b7     a fan of piercing bolts, then lightning falls on the marked ground
 * art_gate_of_end        spear         b8     opens a void gate ahead that drags foes in and bursts
 * art_black_sword_rain   greatsword    final  black swords rain around the wielder; they leave foes exposed
 * </pre>
 */
public final class WeaponArts {
    private static final int START = SkillEffect.ON_START;

    public static final WeaponArt SHADOW_LEGION = ArtForge.strengthened(new WeaponArt("art_shadow_legion", WeaponType.DUAL_BLADES,
            skill("art_shadow_legion", 36, 34, 30, 320,
                    melee(3, HitShape.circle(3.5), 0.5, 0.05),
                    melee(7, HitShape.arc(3.6, 70), 0.45, 0.05),
                    melee(10, HitShape.arc(3.6, 70), 0.45, 0.05),
                    melee(13, HitShape.arc(3.6, 70), 0.45, 0.05),
                    melee(16, HitShape.arc(3.6, 70), 0.45, 0.05),
                    melee(19, HitShape.arc(3.6, 70), 0.45, 0.05),
                    melee(26, HitShape.circle(4.5), 1.6, 0.8)),
            SkillTheme.SHADOW, WeaponArt.Motion.SPIN, List.of(
            new SkillEffect.Buff(START, BuffTarget.SELF, BuffKind.EVASION, 0.5, 40, 0.0),
            new SkillEffect.Visual(START, "art_shadow_legion"),
            new SkillEffect.Visual(3, "art_shadow_legion_cut"),
            new SkillEffect.Afflict(6, AfflictionKind.BLEED, 80, 0.25),
            new SkillEffect.Visual(6, "art_shadow_legion_finish"))), 1.65);

    public static final WeaponArt THORNROOT_BLOOM = ArtForge.strengthened(new WeaponArt("art_thornroot_bloom", WeaponType.STAFF,
            skill("art_thornroot_bloom", 24, 22, 30, 360, HitFrame.effect(8)),
            SkillTheme.LIFE, WeaponArt.Motion.CAST, List.of(
            new SkillEffect.Visual(START, "art_thornroot_bloom"),
            new SkillEffect.Zone(0, Placement.LOOK, 14.0, 4.5, 16, 5, 10, 0.7, 0.03, AfflictionKind.SLOW, 40, 3.0,
                    "thornroot"))), 1.26);

    public static final WeaponArt SAND_MAELSTROM = ArtForge.strengthened(new WeaponArt("art_sand_maelstrom", WeaponType.SPEAR,
            skill("art_sand_maelstrom", 30, 28, 30, 320,
                    melee(6, HitShape.line(7.5, 0.9), 1.0, 0.2),
                    melee(9, HitShape.line(7.5, 0.9), 1.0, 0.2),
                    melee(12, HitShape.line(8.0, 1.0), 1.4, 1.0)),
            SkillTheme.EARTH, WeaponArt.Motion.THRUST, List.of(
            new SkillEffect.Visual(START, "art_sand_maelstrom"),
            new SkillEffect.Afflict(2, AfflictionKind.VULNERABLE, 100, 0.15),
            new SkillEffect.Zone(2, Placement.AHEAD, 6.0, 3.5, 0, 6, 5, 0.35, 0.0, AfflictionKind.PULL, 1, 0.3,
                    "sand_vortex"))), 1.1);

    public static final WeaponArt DOOM_GUILLOTINE = ArtForge.strengthened(new WeaponArt("art_doom_guillotine", WeaponType.TWO_HANDED_SWORD,
            skill("art_doom_guillotine", 34, 32, 40, 400,
                    melee(18, HitShape.circleAhead(3.6, 3.0), 4.0, 1.2)),
            SkillTheme.FROST, WeaponArt.Motion.OVERHEAD, List.of(
            new SkillEffect.Mark(START, Placement.AHEAD, 3.0, 3.6, 18),
            new SkillEffect.Visual(START, "art_doom_guillotine"),
            new SkillEffect.Visual(0, "art_doom_guillotine_fall"),
            new SkillEffect.Execute(0, 0.3, 1.0),
            new SkillEffect.Afflict(0, AfflictionKind.FREEZE, 60, 0.0))), 1.5);

    public static final WeaponArt FROZEN_PRISON = ArtForge.strengthened(new WeaponArt("art_frozen_prison", WeaponType.STAFF,
            skill("art_frozen_prison", 26, 24, 30, 380, HitFrame.effect(8)),
            SkillTheme.FROST, WeaponArt.Motion.CAST, List.of(
            new SkillEffect.Visual(START, "art_frozen_prison"),
            new SkillEffect.Zone(0, Placement.LOOK, 16.0, 4.0, 14, 1, 1, 2.0, 0.0, AfflictionKind.FREEZE, 80, 0.0,
                    "ice_prison"),
            new SkillEffect.Zone(0, Placement.LOOK, 16.0, 4.0, 44, 1, 1, 1.8, 0.0, AfflictionKind.VULNERABLE, 100,
                    0.2, "ice_shatter"))), 1.16);

    public static final WeaponArt METEOR_SLASH = ArtForge.strengthened(new WeaponArt("art_meteor_slash", WeaponType.ONE_HANDED_SWORD,
            skill("art_meteor_slash", 30, 28, 30, 360,
                    melee(6, HitShape.arc(4.2, 80), 1.8, 0.8)),
            SkillTheme.FIRE, WeaponArt.Motion.OVERHEAD, List.of(
            new SkillEffect.Visual(START, "art_meteor_slash"),
            new SkillEffect.Afflict(0, AfflictionKind.BURN, 80, 0.0),
            new SkillEffect.Visual(0, "art_meteor_slash_wave"),
            new SkillEffect.Zone(0, Placement.AHEAD, 6.0, 3.5, 14, 3, 6, 1.1, 0.0, AfflictionKind.BURN, 60, 0.0,
                    "meteor"))), 1.1);

    public static final WeaponArt HEAVENS_CHAIN = new WeaponArt("art_heavens_chain", WeaponType.BOW,
            skill("art_heavens_chain", 26, 24, 30, 360,
                    HitFrame.launch(6, ProjectileSpec.arrow(3.5).withPierce(3).withFan(5, 36.0), 0.9, 0.3)),
            SkillTheme.THUNDER, WeaponArt.Motion.SHOOT, List.of(
            new SkillEffect.Visual(START, "art_heavens_chain"),
            new SkillEffect.Afflict(0, AfflictionKind.STUN, 10, 0.0),
            new SkillEffect.Zone(0, Placement.LOOK, 20.0, 4.0, 12, 4, 5, 0.9, 0.0, AfflictionKind.STUN, 12, 0.0,
                    "sky_lightning")));

    public static final WeaponArt GATE_OF_END = new WeaponArt("art_gate_of_end", WeaponType.SPEAR,
            skill("art_gate_of_end", 32, 30, 35, 400,
                    melee(6, HitShape.line(7.0, 1.0), 1.4, 0.4)),
            SkillTheme.SHADOW, WeaponArt.Motion.THRUST, List.of(
            new SkillEffect.Visual(START, "art_gate_of_end"),
            new SkillEffect.Zone(0, Placement.AHEAD, 5.0, 4.5, 0, 6, 5, 0.3, 0.0, AfflictionKind.PULL, 1, 0.35,
                    "void_gate"),
            new SkillEffect.Zone(0, Placement.AHEAD, 5.0, 5.0, 32, 1, 1, 3.2, 0.0, AfflictionKind.WEAKEN, 120, 0.2,
                    "void_gate_burst")));

    public static final WeaponArt BLACK_SWORD_RAIN = new WeaponArt("art_black_sword_rain",
            WeaponType.TWO_HANDED_SWORD,
            skill("art_black_sword_rain", 30, 28, 40, 480,
                    melee(10, HitShape.circle(6.0), 1.6, 0.8)),
            SkillTheme.SHADOW, WeaponArt.Motion.SPIN, List.of(
            new SkillEffect.Visual(START, "art_black_sword_rain"),
            new SkillEffect.Buff(START, BuffTarget.SELF, BuffKind.ATTACK_UP, 0.15, 160, 0.0),
            new SkillEffect.Zone(0, Placement.CASTER, 0.0, 7.0, 16, 6, 4, 0.75, 0.0, AfflictionKind.VULNERABLE, 100,
                    0.15, "black_swords")));

    private static final List<WeaponArt> ALL = List.of(SHADOW_LEGION, THORNROOT_BLOOM, SAND_MAELSTROM,
            DOOM_GUILLOTINE, FROZEN_PRISON, METEOR_SLASH, HEAVENS_CHAIN, GATE_OF_END, BLACK_SWORD_RAIN);

    private WeaponArts() {
    }

    public static List<WeaponArt> all() {
        return ALL;
    }

    /** A boss art, or the art of a named or tier weapon ({@link GeneratedArts}). */
    public static Optional<WeaponArt> byId(String id) {
        for (WeaponArt art : ALL) {
            if (art.id().equals(id)) {
                return Optional.of(art);
            }
        }
        return id != null && id.startsWith("art_") ? GeneratedArts.byId(id) : Optional.empty();
    }

    private static HitFrame melee(int offset, HitShape shape, double multiplier, double knockback) {
        return new HitFrame(offset, shape, multiplier, knockback);
    }

    private static ActionDefinition skill(String id, int duration, int cancel, double stamina, int cooldown,
                                          HitFrame... frames) {
        return new ActionDefinition(id, ActionKind.SKILL, duration, cancel, Arrays.asList(frames), stamina, cooldown,
                0, false);
    }
}
