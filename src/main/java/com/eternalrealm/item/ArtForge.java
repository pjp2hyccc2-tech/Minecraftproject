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

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Builds the generated weapon arts (the Z key of every named and tier weapon) from a few choices: the attack
 * {@link Form} (by weapon type and feel), the extras ({@link Extra}: ground areas, buffs, heals, dashes, pulls,
 * executes ...), the element's affliction and the damage budget. Damage is scaled so that one use deals exactly the
 * target burst ({@link ArtBudget}) and the cooldown so that the burst per second lands on its target.
 */
public final class ArtForge {
    /** The attack shapes: six per weapon type (one per feel). */
    public enum Form {
        SWORD_COMBO, SWORD_DASH, SWORD_CLEAVE, SWORD_PRECISE, SWORD_WAVE, SWORD_BURST,
        GREAT_REND, GREAT_SPIN, GREAT_SLAM, GREAT_GUILLOTINE, GREAT_SWEEP, GREAT_ERUPTION,
        TWIN_FLURRY, TWIN_ASSASSINATE, TWIN_CROSS, TWIN_FAN, TWIN_MAUL, TWIN_CHAIN,
        SPEAR_LANCE, SPEAR_THRUSTS, SPEAR_CHARGE, SPEAR_IMPALE, SPEAR_PIERCE, SPEAR_JAVELIN,
        STAFF_BURST, STAFF_ORBS, STAFF_LANCE, STAFF_VOLLEY, STAFF_METEOR, STAFF_BEAM,
        BOW_VOLLEY, BOW_SNIPE, BOW_FAN, BOW_PIERCE, BOW_RAIN, BOW_IMPACT;

        public WeaponType type() {
            String prefix = name().substring(0, name().indexOf('_'));
            return switch (prefix) {
                case "SWORD" -> WeaponType.ONE_HANDED_SWORD;
                case "GREAT" -> WeaponType.TWO_HANDED_SWORD;
                case "TWIN" -> WeaponType.DUAL_BLADES;
                case "SPEAR" -> WeaponType.SPEAR;
                case "STAFF" -> WeaponType.STAFF;
                default -> WeaponType.BOW;
            };
        }

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /** What an art does besides its hits. */
    public enum Extra {
        /** A damaging ground area where it lands (takes part of the damage). */
        ZONE,
        /** A slowing ground area (takes part of the damage). */
        SLOW_FIELD,
        /** The landing spot is marked first, then a delayed strike falls there (takes part of the damage). */
        MARK,
        /** A healing ground area under the wielder for allies. */
        FIELD_HEAL,
        /** Heals the wielder and allies nearby. */
        HEAL,
        /** A shield on the wielder. */
        SHIELD,
        ATTACK_UP,
        CRIT_UP,
        SPEED,
        EVASION,
        DEFENSE_UP,
        /** Raises the attack of allies nearby. */
        PARTY_ATTACK,
        REGEN,
        LIFESTEAL,
        THORNS,
        /** The weapon takes on its element for a while (fire, frost, venom or thunder). */
        IMBUE,
        DASH,
        LEAP,
        /** Springs back after the last hit. */
        BACKSTEP,
        PULL,
        LAUNCH,
        /** Hits knock foes far back. */
        KNOCKBACK,
        EXECUTE,
        SIPHON,
        TAUNT,
        CLEANSE,
        STAMINA,
        /** Vanishes: enemies lose track, invisible for a moment. */
        VANISH,
        STUN,
        BLIND;

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /**
     * One art to build.
     *
     * @param type       the weapon type that wields it (a form may be borrowed from another type: a spear that throws)
     * @param affliction the element's (or material's) affliction on the finishing hit, {@code null} for none
     * @param burst      target damage of one use (multipliers)
     * @param perSecond  target burst per second of cooldown
     */
    public record Recipe(String id, WeaponType type, Form form, SkillTheme theme, AfflictionKind affliction,
                         int afflictionTicks, double afflictionPower, List<Extra> extras, double burst,
                         double perSecond, ArtLook look) {
        public Recipe {
            extras = List.copyOf(extras);
        }
    }

    private static final int START = SkillEffect.ON_START;
    private static final Set<Extra> ZONES = Set.of(Extra.ZONE, Extra.SLOW_FIELD, Extra.MARK);

    private ArtForge() {
    }

    /** A frame of a form before its damage is scaled: {@code weight} is its share of the hit damage. */
    private record Proto(int tick, HitShape shape, ProjectileSpec projectile, double weight, double knockback) {
        static Proto melee(int tick, HitShape shape, double weight, double knockback) {
            return new Proto(tick, shape, null, weight, knockback);
        }

        static Proto shot(int tick, ProjectileSpec projectile, double weight, double knockback) {
            return new Proto(tick, null, projectile, weight, knockback);
        }
    }

    private record Shape(int duration, WeaponArt.Motion motion, List<Proto> frames, Placement zonePlacement,
                         double zoneDistance, double startDash) {
    }

    public static WeaponArt build(Recipe recipe) {
        Shape shape = shape(recipe.form(), recipe.theme());
        boolean zoned = recipe.extras().stream().anyMatch(ZONES::contains);
        double zoneShare = zoned ? (recipe.extras().contains(Extra.MARK) ? 0.4 : 0.3) : 0.0;
        double frameBudget = recipe.burst() * (1.0 - zoneShare);
        double weights = 0.0;
        for (Proto proto : shape.frames()) {
            weights += proto.weight();
        }
        double knockScale = recipe.extras().contains(Extra.KNOCKBACK) ? 2.5 : 1.0;
        List<HitFrame> frames = new ArrayList<>();
        for (Proto proto : shape.frames()) {
            double damage = frameBudget * proto.weight() / weights;
            double knockback = Math.min(3.0, proto.knockback() * knockScale);
            if (proto.projectile() != null) {
                int count = Math.max(1, proto.projectile().count());
                frames.add(HitFrame.launch(proto.tick(), proto.projectile(), round(damage / count), knockback));
            } else {
                frames.add(new HitFrame(proto.tick(), proto.shape(), round(damage), knockback));
            }
        }
        int last = frames.size() - 1;
        int lastTick = frames.get(last).offsetTicks();
        double seconds = recipe.burst() / recipe.perSecond();
        int cooldown = (int) Math.max(100, Math.min(600, Math.round(seconds * 2.0) * 10));
        ActionDefinition action = new ActionDefinition(recipe.id(), ActionKind.SKILL, shape.duration(),
                shape.duration() - 2, frames, stamina(recipe.type()), cooldown, 0, false);
        List<SkillEffect> effects = new ArrayList<>();
        if (shape.startDash() > 0.0) {
            effects.add(new SkillEffect.Dash(START, shape.startDash(), false));
        }
        if (recipe.affliction() != null && !recipe.affliction().instant()) {
            effects.add(new SkillEffect.Afflict(last, recipe.affliction(), recipe.afflictionTicks(),
                    recipe.afflictionPower()));
        }
        double zoneBudget = recipe.burst() * zoneShare;
        String zoneLook = recipe.look().zoneLook();
        for (Extra extra : recipe.extras()) {
            switch (extra) {
                case ZONE -> effects.add(new SkillEffect.Zone(last, shape.zonePlacement(), shape.zoneDistance(), 3.2,
                        4, 3, 10, round(zoneBudget / 3.0), 0.0, recipe.affliction() != null
                        && !recipe.affliction().instant() ? recipe.affliction() : null,
                        Math.max(1, recipe.afflictionTicks()), recipe.afflictionPower(), zoneLook));
                case SLOW_FIELD -> effects.add(new SkillEffect.Zone(last, shape.zonePlacement(), shape.zoneDistance(),
                        3.5, 2, 4, 8, round(zoneBudget / 4.0), 0.0, AfflictionKind.SLOW, 40, 2.0, zoneLook));
                case MARK -> {
                    Placement placement = shape.zonePlacement() == Placement.CASTER ? Placement.AHEAD
                            : shape.zonePlacement();
                    double distance = shape.zonePlacement() == Placement.CASTER ? 3.5 : shape.zoneDistance();
                    effects.add(new SkillEffect.Mark(START, placement, distance, 3.0, Math.max(1, lastTick + 10)));
                    effects.add(new SkillEffect.Zone(last, placement, distance, 3.0, 10, 1, 1, round(zoneBudget),
                            0.0, recipe.affliction() != null && !recipe.affliction().instant()
                            ? recipe.affliction() : null, Math.max(1, recipe.afflictionTicks()),
                            recipe.afflictionPower(), zoneLook));
                }
                case FIELD_HEAL -> effects.add(new SkillEffect.Zone(last, Placement.CASTER, 0.0, 4.0, 0, 4, 10, 0.0,
                        0.03, null, 1, 0.0, zoneLook));
                case HEAL -> effects.add(new SkillEffect.Heal(last, 6.0, 0.08, 0.0));
                case SHIELD -> effects.add(new SkillEffect.Shield(START, 0.12, 100, 0.0));
                case ATTACK_UP -> effects.add(self(START, BuffKind.ATTACK_UP, 0.15, 100));
                case CRIT_UP -> effects.add(self(START, BuffKind.CRIT_UP, 0.2, 100));
                case SPEED -> effects.add(self(last, BuffKind.SPEED, 1.0, 80));
                case EVASION -> effects.add(self(START, BuffKind.EVASION, 0.3, 60));
                case DEFENSE_UP -> effects.add(self(START, BuffKind.DEFENSE_UP, 0.2, 100));
                case PARTY_ATTACK -> effects.add(new SkillEffect.Buff(last, BuffTarget.ALLIES, BuffKind.ATTACK_UP, 0.1,
                        160, 8.0));
                case REGEN -> effects.add(self(last, BuffKind.REGENERATION, 1.0, 100));
                case LIFESTEAL -> effects.add(self(START, BuffKind.LIFESTEAL, 0.12, 100));
                case THORNS -> effects.add(self(START, BuffKind.THORNS, 0.2, 100));
                case IMBUE -> effects.add(self(last, imbue(recipe.theme()), 0.0, 160));
                case DASH -> effects.add(new SkillEffect.Dash(START, 4.0, false));
                case LEAP -> effects.add(new SkillEffect.Leap(START, 4.0, 1.5));
                case BACKSTEP -> effects.add(new SkillEffect.Dash(last, 3.5, true));
                case PULL -> effects.add(new SkillEffect.Afflict(0, AfflictionKind.PULL, 1, 0.5));
                case LAUNCH -> effects.add(new SkillEffect.Afflict(last, AfflictionKind.LAUNCH, 1, 0.7));
                case KNOCKBACK -> {
                    // stronger knockback on every hit (above)
                }
                case EXECUTE -> effects.add(new SkillEffect.Execute(last, 0.3, 0.8));
                case SIPHON -> effects.add(new SkillEffect.Siphon(last, 0.15, 0.0));
                case TAUNT -> effects.add(new SkillEffect.Taunt(START, 6.0, 60));
                case CLEANSE -> effects.add(new SkillEffect.Cleanse(last, 6.0));
                case STAMINA -> effects.add(new SkillEffect.RestoreStamina(last, 20.0));
                case VANISH -> {
                    effects.add(new SkillEffect.DropAggro(START, 12.0));
                    effects.add(self(START, BuffKind.INVISIBILITY, 0.0, 40));
                }
                case STUN -> effects.add(new SkillEffect.Afflict(last, AfflictionKind.STUN, 20, 0.0));
                case BLIND -> effects.add(new SkillEffect.Afflict(last, AfflictionKind.BLIND, 60, 0.0));
            }
        }
        return new WeaponArt(recipe.id(), recipe.type(), action, recipe.theme(), motion(recipe, shape), effects);
    }

    /** The same art with every damage multiplier (hits and ground areas) times {@code factor}. */
    public static WeaponArt strengthened(WeaponArt art, double factor) {
        List<HitFrame> frames = new ArrayList<>();
        for (HitFrame frame : art.action().frames()) {
            frames.add(frame.damaging() ? new HitFrame(frame.offsetTicks(), frame.shape(),
                    round(frame.multiplier() * factor), frame.knockback(), frame.projectile(), true) : frame);
        }
        ActionDefinition a = art.action();
        ActionDefinition action = new ActionDefinition(a.id(), a.kind(), a.durationTicks(), a.cancelTicks(), frames,
                a.staminaCost(), a.cooldownTicks(), a.invulnerableTicks(), a.speedScaled());
        List<SkillEffect> effects = new ArrayList<>();
        for (SkillEffect effect : art.effects()) {
            if (effect instanceof SkillEffect.Zone zone && zone.damageMultiplier() > 0.0) {
                effects.add(new SkillEffect.Zone(zone.frame(), zone.placement(), zone.distance(), zone.radius(),
                        zone.delayTicks(), zone.pulses(), zone.intervalTicks(), round(zone.damageMultiplier() * factor),
                        zone.healFraction(), zone.affliction(), zone.afflictionTicks(), zone.afflictionPower(),
                        zone.look()));
            } else {
                effects.add(effect);
            }
        }
        return new WeaponArt(art.id(), art.type(), action, art.theme(), art.motion(), effects);
    }

    /** A borrowed form moves like the wielder's weapon: a bow shoots, a staff casts, a spear thrusts. */
    private static WeaponArt.Motion motion(Recipe recipe, Shape shape) {
        if (recipe.form().type() == recipe.type()) {
            return shape.motion();
        }
        return switch (recipe.type()) {
            case BOW -> WeaponArt.Motion.SHOOT;
            case STAFF -> WeaponArt.Motion.CAST;
            case SPEAR -> WeaponArt.Motion.THRUST;
            default -> shape.motion();
        };
    }

    /** The element buff of {@link Extra#IMBUE}: fire, frost, venom or thunder (else fire). */
    public static BuffKind imbue(SkillTheme theme) {
        return switch (theme) {
            case FROST -> BuffKind.FROST_WEAPON;
            case VENOM -> BuffKind.VENOM_WEAPON;
            case THUNDER -> BuffKind.THUNDER_WEAPON;
            default -> BuffKind.FLAME_WEAPON;
        };
    }

    private static SkillEffect.Buff self(int frame, BuffKind kind, double magnitude, int ticks) {
        return new SkillEffect.Buff(frame, BuffTarget.SELF, kind, magnitude, ticks, 0.0);
    }

    private static double stamina(WeaponType type) {
        return switch (type) {
            case TWO_HANDED_SWORD -> 35.0;
            case STAFF -> 30.0;
            default -> 28.0;
        };
    }

    private static double round(double value) {
        return Math.max(0.05, Math.round(value * 100.0) / 100.0);
    }

    /** The magic bolt of an element (what it looks like in flight). */
    private static String bolt(SkillTheme theme) {
        return switch (theme) {
            case FIRE -> "fireball";
            case FROST -> "ice_lance";
            case THUNDER -> "thunder_orb";
            case HOLY, LIFE -> "holy_light";
            case SHADOW, BLOOD -> "shadow_bolt";
            case VENOM -> "poison_dart";
            default -> "magic_orb";
        };
    }

    private static Shape shape(Form form, SkillTheme theme) {
        String bolt = bolt(theme);
        return switch (form) {
            // ---------------------------------------------------------------- one-handed sword
            case SWORD_COMBO -> new Shape(24, WeaponArt.Motion.SPIN, List.of(
                    Proto.melee(5, HitShape.arc(3.4, 60), 0.3, 0.2),
                    Proto.melee(10, HitShape.arc(3.4, 60), 0.3, 0.2),
                    Proto.melee(16, HitShape.circle(3.6), 0.4, 0.6)), Placement.CASTER, 0.0, 0.0);
            case SWORD_DASH -> new Shape(22, WeaponArt.Motion.THRUST, List.of(
                    Proto.melee(4, HitShape.arc(3.2, 70), 0.25, 0.1),
                    Proto.melee(7, HitShape.arc(3.2, 70), 0.25, 0.1),
                    Proto.melee(10, HitShape.arc(3.2, 70), 0.25, 0.1),
                    Proto.melee(14, HitShape.arc(3.6, 80), 0.25, 0.5)), Placement.AHEAD, 3.0, 4.0);
            case SWORD_CLEAVE -> new Shape(24, WeaponArt.Motion.OVERHEAD, List.of(
                    Proto.melee(12, HitShape.arc(4.0, 90), 1.0, 1.0)), Placement.AHEAD, 3.0, 0.0);
            case SWORD_PRECISE -> new Shape(20, WeaponArt.Motion.THRUST, List.of(
                    Proto.melee(9, HitShape.line(4.8, 0.6), 1.0, 0.4)), Placement.AHEAD, 4.0, 0.0);
            case SWORD_WAVE -> new Shape(20, WeaponArt.Motion.OVERHEAD, List.of(
                    Proto.shot(8, ProjectileSpec.magic("crescent", 1.4, 16).withPierce(4), 1.0, 0.4)),
                    Placement.AHEAD, 8.0, 0.0);
            case SWORD_BURST -> new Shape(22, WeaponArt.Motion.SPIN, List.of(
                    Proto.melee(10, HitShape.circle(3.8), 1.0, 0.6)), Placement.CASTER, 0.0, 0.0);
            // ---------------------------------------------------------------- greatsword
            case GREAT_REND -> new Shape(28, WeaponArt.Motion.OVERHEAD, List.of(
                    Proto.melee(8, HitShape.arc(3.8, 80), 0.4, 0.4),
                    Proto.melee(16, HitShape.circleAhead(2.6, 2.5), 0.6, 1.0)), Placement.AHEAD, 3.0, 0.0);
            case GREAT_SPIN -> new Shape(26, WeaponArt.Motion.SPIN, List.of(
                    Proto.melee(8, HitShape.circle(4.0), 0.5, 0.5),
                    Proto.melee(16, HitShape.circle(4.0), 0.5, 0.8)), Placement.CASTER, 0.0, 0.0);
            case GREAT_SLAM -> new Shape(30, WeaponArt.Motion.OVERHEAD, List.of(
                    Proto.melee(14, HitShape.circle(4.8), 1.0, 1.2)), Placement.CASTER, 0.0, 0.0);
            case GREAT_GUILLOTINE -> new Shape(30, WeaponArt.Motion.OVERHEAD, List.of(
                    Proto.melee(16, HitShape.circleAhead(2.4, 2.6), 1.0, 1.2)), Placement.AHEAD, 2.6, 0.0);
            case GREAT_SWEEP -> new Shape(28, WeaponArt.Motion.SPIN, List.of(
                    Proto.melee(12, HitShape.arc(5.5, 110), 1.0, 1.0)), Placement.AHEAD, 3.5, 0.0);
            case GREAT_ERUPTION -> new Shape(28, WeaponArt.Motion.OVERHEAD, List.of(
                    Proto.melee(8, HitShape.arc(3.6, 70), 0.4, 0.4),
                    Proto.melee(16, HitShape.circleAhead(3.0, 4.0), 0.6, 0.8)), Placement.AHEAD, 4.0, 0.0);
            // ---------------------------------------------------------------- twin blades
            case TWIN_FLURRY -> new Shape(24, WeaponArt.Motion.SPIN, List.of(
                    Proto.melee(3, HitShape.arc(3.0, 60), 1.0, 0.05),
                    Proto.melee(6, HitShape.arc(3.0, 60), 1.0, 0.05),
                    Proto.melee(9, HitShape.arc(3.0, 60), 1.0, 0.05),
                    Proto.melee(12, HitShape.arc(3.0, 60), 1.0, 0.05),
                    Proto.melee(15, HitShape.arc(3.0, 60), 1.0, 0.05),
                    Proto.melee(18, HitShape.circle(3.4), 1.5, 0.5)), Placement.CASTER, 0.0, 3.5);
            case TWIN_ASSASSINATE -> new Shape(20, WeaponArt.Motion.THRUST, List.of(
                    Proto.melee(6, HitShape.line(3.6, 0.8), 1.0, 0.3)), Placement.AHEAD, 2.5, 5.0);
            case TWIN_CROSS -> new Shape(22, WeaponArt.Motion.SPIN, List.of(
                    Proto.melee(5, HitShape.arc(3.2, 70), 0.35, 0.1),
                    Proto.melee(9, HitShape.arc(3.2, 70), 0.35, 0.1),
                    Proto.melee(15, HitShape.circle(3.4), 0.3, 0.5)), Placement.CASTER, 0.0, 0.0);
            case TWIN_FAN -> new Shape(18, WeaponArt.Motion.CAST, List.of(
                    Proto.shot(6, ProjectileSpec.magic("knife", 1.6, 14).withFan(5, 50.0), 1.0, 0.2)),
                    Placement.AHEAD, 5.0, 0.0);
            case TWIN_MAUL -> new Shape(22, WeaponArt.Motion.OVERHEAD, List.of(
                    Proto.melee(10, HitShape.circle(3.6), 0.5, 0.6),
                    Proto.melee(14, HitShape.circle(3.6), 0.5, 0.8)), Placement.CASTER, 0.0, 0.0);
            case TWIN_CHAIN -> new Shape(22, WeaponArt.Motion.THRUST, List.of(
                    Proto.melee(6, HitShape.line(6.0, 0.8), 0.5, 0.2),
                    Proto.melee(12, HitShape.line(6.0, 0.8), 0.5, 0.5)), Placement.AHEAD, 4.0, 0.0);
            // ---------------------------------------------------------------- spear
            case SPEAR_LANCE -> new Shape(22, WeaponArt.Motion.THRUST, List.of(
                    Proto.melee(10, HitShape.line(8.5, 0.8), 1.0, 0.8)), Placement.AHEAD, 5.0, 0.0);
            case SPEAR_THRUSTS -> new Shape(22, WeaponArt.Motion.THRUST, List.of(
                    Proto.melee(6, HitShape.line(6.0, 0.8), 0.3, 0.2),
                    Proto.melee(9, HitShape.line(6.0, 0.8), 0.3, 0.2),
                    Proto.melee(13, HitShape.line(6.5, 0.9), 0.4, 0.8)), Placement.AHEAD, 4.0, 0.0);
            case SPEAR_CHARGE -> new Shape(20, WeaponArt.Motion.THRUST, List.of(
                    Proto.melee(6, HitShape.line(6.0, 1.0), 1.0, 1.0)), Placement.AHEAD, 3.0, 5.0);
            case SPEAR_IMPALE -> new Shape(26, WeaponArt.Motion.OVERHEAD, List.of(
                    Proto.melee(8, HitShape.line(6.0, 0.9), 0.5, 0.3),
                    Proto.melee(14, HitShape.circleAhead(2.5, 5.0), 0.5, 1.0)), Placement.AHEAD, 5.0, 0.0);
            case SPEAR_PIERCE -> new Shape(22, WeaponArt.Motion.THRUST, List.of(
                    Proto.melee(10, HitShape.line(7.5, 0.5), 1.0, 0.5)), Placement.AHEAD, 5.0, 0.0);
            case SPEAR_JAVELIN -> new Shape(20, WeaponArt.Motion.THRUST, List.of(
                    Proto.shot(8, ProjectileSpec.magic(bolt, 1.5, 20).withExplosion(2.5), 1.0, 0.6)),
                    Placement.LOOK, 10.0, 0.0);
            // ---------------------------------------------------------------- staff
            case STAFF_BURST -> new Shape(24, WeaponArt.Motion.CAST, List.of(
                    Proto.shot(10, ProjectileSpec.magic(bolt, 1.3, 30).withExplosion(3.0), 1.0, 0.8)),
                    Placement.LOOK, 12.0, 0.0);
            case STAFF_ORBS -> new Shape(22, WeaponArt.Motion.CAST, List.of(
                    Proto.shot(6, ProjectileSpec.magic(bolt, 1.5, 24), 1.0, 0.2),
                    Proto.shot(10, ProjectileSpec.magic(bolt, 1.5, 24), 1.0, 0.2),
                    Proto.shot(14, ProjectileSpec.magic(bolt, 1.5, 24).withExplosion(2.0), 1.2, 0.6)),
                    Placement.LOOK, 12.0, 0.0);
            case STAFF_LANCE -> new Shape(20, WeaponArt.Motion.CAST, List.of(
                    Proto.shot(8, ProjectileSpec.magic(bolt, 2.2, 20).withPierce(5), 1.0, 0.4)),
                    Placement.LOOK, 12.0, 0.0);
            case STAFF_VOLLEY -> new Shape(18, WeaponArt.Motion.CAST, List.of(
                    Proto.shot(6, ProjectileSpec.magic(bolt, 1.6, 20).withFan(5, 40.0), 1.0, 0.2)),
                    Placement.LOOK, 10.0, 0.0);
            case STAFF_METEOR -> new Shape(26, WeaponArt.Motion.CAST, List.of(
                    Proto.shot(10, ProjectileSpec.magic(bolt, 1.0, 30).fallingFromAbove().withExplosion(3.5), 1.0,
                            1.0)), Placement.LOOK, 12.0, 0.0);
            case STAFF_BEAM -> new Shape(22, WeaponArt.Motion.CAST, List.of(
                    Proto.melee(10, HitShape.line(10.0, 0.8), 1.0, 0.4)), Placement.AHEAD, 6.0, 0.0);
            // ---------------------------------------------------------------- bow
            case BOW_VOLLEY -> new Shape(18, WeaponArt.Motion.SHOOT, List.of(
                    Proto.shot(4, ProjectileSpec.arrow(3.2), 1.0, 0.2),
                    Proto.shot(7, ProjectileSpec.arrow(3.2), 1.0, 0.2),
                    Proto.shot(10, ProjectileSpec.arrow(3.4), 1.2, 0.4)), Placement.LOOK, 12.0, 0.0);
            case BOW_SNIPE -> new Shape(22, WeaponArt.Motion.SHOOT, List.of(
                    Proto.shot(12, ProjectileSpec.arrow(4.5).withPierce(6), 1.0, 0.6)), Placement.LOOK, 14.0, 0.0);
            case BOW_FAN -> new Shape(18, WeaponArt.Motion.SHOOT, List.of(
                    Proto.shot(8, ProjectileSpec.arrow(3.0).withFan(5, 40.0), 1.0, 0.3)), Placement.LOOK, 10.0, 0.0);
            case BOW_PIERCE -> new Shape(20, WeaponArt.Motion.SHOOT, List.of(
                    Proto.shot(9, ProjectileSpec.arrow(4.0).withPierce(10), 1.0, 0.5)), Placement.LOOK, 12.0, 0.0);
            case BOW_RAIN -> new Shape(22, WeaponArt.Motion.SHOOT, List.of(
                    Proto.shot(10, ProjectileSpec.arrow(2.0).fallingFromAbove().withFan(5, 30.0), 1.0, 0.2)),
                    Placement.LOOK, 12.0, 0.0);
            case BOW_IMPACT -> new Shape(22, WeaponArt.Motion.SHOOT, List.of(
                    Proto.shot(10, ProjectileSpec.arrow(3.5).withExplosion(2.5), 1.0, 0.8)), Placement.LOOK, 12.0,
                    0.0);
        };
    }
}
