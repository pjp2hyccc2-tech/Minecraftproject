package com.eternalrealm.item;

import com.eternalrealm.combat.WeaponProfile;
import com.eternalrealm.combat.WeaponProfiles;
import com.eternalrealm.combat.WeaponType;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * How the weapon skill of the Z key moves (owner, 2026-11-12: 「zボタンの技の視覚的効果と武器の動きが一緒すぎるので
 * もう少し各々派手にして」): every art form has its own choreography (a stance while it winds up, one strike per hit, a
 * stance it ends in) instead of five shared motions, and every strike draws its own trail (a horizontal crescent, a
 * falling arc, a thrust streak, a ring, a quake, a magic circle...). Arts that share a form are told apart by
 * mirroring (half of them cut the other way round) and by their own colours and shapes ({@link ArtLook}). The client
 * poses the arms and the weapon from this and draws the trails; the server draws the same trails in particles. Pure.
 */
public final class ArtChoreography {
    /** A held pose: before the first strike, between strikes, at the end. */
    public enum Stance {
        READY, GUARD_LOW, HIGH_RAISE, DRAW_BACK, SIDE_LOAD, BLADE_UP, LOW_FINISH, EXTENDED, CROUCH, CROSSED,
        TWIN_HIGH, SPREAD_OUT, COUCHED, OVERHAND, STAFF_UP, STAFF_FORWARD, STAFF_LOW, STAFF_PLANTED, BOW_DRAW,
        BOW_STEADY, BOW_SKY, BOW_LOW
    }

    /** What a strike draws. */
    public enum Trail {
        /** A crescent sweeping from the wielder's left to the right (and the other way). */
        ARC_RIGHT, ARC_LEFT,
        /** A wide, low crescent across the whole front. */
        SWEEP,
        /** A vertical arc rising from the ground in front. */
        RISE,
        /** A vertical arc falling from overhead, a flash where it lands. */
        FALL,
        /** An arc in a slanted plane, high left to low right (and mirrored). */
        DIAG_RIGHT, DIAG_LEFT,
        /** A long tapered streak along the look. */
        STREAK,
        /** A ring all around the wielder. */
        RING,
        /** A shockwave on the ground and a pillar of light where the weapon struck. */
        QUAKE,
        /** Two crossing slanted arcs, an X in front. */
        CROSS,
        /** Short streaks fanning out ahead. */
        FAN,
        /** A turning magic circle in front of the weapon. */
        SIGIL_AHEAD,
        /** A magic circle in the sky over the target ground. */
        SIGIL_SKY,
        /** A beam straight ahead from a magic circle. */
        BEAM,
        /** A ring at the bow, the arrow's streak. */
        MUZZLE,
        /** A bigger ring with a shockwave at the bow. */
        MUZZLE_HEAVY
    }

    /** One strike, played around a hit (or launch) of the art. */
    public enum Strike {
        SLASH_RIGHT(Trail.ARC_RIGHT), SLASH_LEFT(Trail.ARC_LEFT), WIDE_SWEEP(Trail.SWEEP), RISING(Trail.RISE),
        FALLING(Trail.FALL), DIAGONAL_RIGHT(Trail.DIAG_RIGHT), DIAGONAL_LEFT(Trail.DIAG_LEFT), THRUST(Trail.STREAK),
        LUNGE(Trail.STREAK), JAB(Trail.STREAK), SPIN(Trail.RING), SLAM(Trail.QUAKE), STAB_DOWN(Trail.QUAKE),
        TWIN_RIGHT(Trail.ARC_RIGHT), TWIN_LEFT(Trail.ARC_LEFT), TWIN_CROSS(Trail.CROSS), TWIN_SPREAD(Trail.FAN),
        TWIN_STAB(Trail.STREAK), TWIN_SPIN(Trail.RING), TWIN_SLAM(Trail.QUAKE), CHARGE(Trail.STREAK),
        VAULT(Trail.QUAKE), THROW(Trail.STREAK), CAST_POINT(Trail.SIGIL_AHEAD), CAST_CIRCLE(Trail.SIGIL_AHEAD),
        CAST_THRUST(Trail.STREAK), CAST_SWEEP(Trail.FAN), CAST_SKYFALL(Trail.SIGIL_SKY), CAST_BEAM(Trail.BEAM),
        LOOSE(Trail.MUZZLE), LOOSE_HEAVY(Trail.MUZZLE_HEAVY), LOOSE_SWEEP(Trail.FAN), LOOSE_SKY(Trail.SIGIL_SKY);

        private final Trail trail;

        Strike(Trail trail) {
            this.trail = trail;
        }

        public Trail trail() {
            return trail;
        }

        /** The same strike cut the other way round. */
        public Strike mirrored() {
            return switch (this) {
                case SLASH_RIGHT -> SLASH_LEFT;
                case SLASH_LEFT -> SLASH_RIGHT;
                case DIAGONAL_RIGHT -> DIAGONAL_LEFT;
                case DIAGONAL_LEFT -> DIAGONAL_RIGHT;
                case TWIN_RIGHT -> TWIN_LEFT;
                case TWIN_LEFT -> TWIN_RIGHT;
                default -> this;
            };
        }
    }

    /**
     * @param windup   held while it winds up (before the first strike)
     * @param strikes  one per hit or launch, in order (the last one repeats if the art has more hits)
     * @param finish   held after the last strike, before the weapon returns
     * @param heavy    a weighty art: its last strike shakes the wielder's view and lands with a bigger burst
     */
    public record Choreo(Stance windup, List<Strike> strikes, Stance finish, boolean heavy) {
        public Choreo {
            if (windup == null || finish == null || strikes == null || strikes.isEmpty()) {
                throw new IllegalArgumentException("a choreography needs a wind-up, strikes and a finish");
            }
            strikes = List.copyOf(strikes);
        }

        /** The strike of hit {@code index} (0-based). */
        public Strike strike(int index) {
            return strikes.get(Math.max(0, Math.min(index, strikes.size() - 1)));
        }

        /** Every strike cut the other way round. */
        public Choreo mirrored() {
            return new Choreo(windup, strikes.stream().map(Strike::mirrored).toList(), finish, heavy);
        }
    }

    private static final Map<ArtForge.Form, Choreo> FORMS = new EnumMap<>(ArtForge.Form.class);
    private static final Map<String, Choreo> BOSS = new HashMap<>();
    private static final Map<WeaponType, ArtForge.Form> BASE = new EnumMap<>(WeaponType.class);

    static {
        // ---------------------------------------------------------------- one-handed sword
        form(ArtForge.Form.SWORD_COMBO, Stance.GUARD_LOW, Stance.BLADE_UP, false,
                Strike.SLASH_RIGHT, Strike.SLASH_LEFT, Strike.SPIN);
        form(ArtForge.Form.SWORD_DASH, Stance.CROUCH, Stance.HIGH_RAISE, false,
                Strike.SLASH_LEFT, Strike.SLASH_RIGHT, Strike.SLASH_LEFT, Strike.RISING);
        form(ArtForge.Form.SWORD_CLEAVE, Stance.HIGH_RAISE, Stance.LOW_FINISH, true, Strike.FALLING);
        form(ArtForge.Form.SWORD_PRECISE, Stance.BLADE_UP, Stance.EXTENDED, false, Strike.THRUST);
        form(ArtForge.Form.SWORD_WAVE, Stance.SIDE_LOAD, Stance.LOW_FINISH, false, Strike.WIDE_SWEEP);
        form(ArtForge.Form.SWORD_BURST, Stance.BLADE_UP, Stance.GUARD_LOW, false, Strike.SPIN);
        // ---------------------------------------------------------------- greatsword
        form(ArtForge.Form.GREAT_REND, Stance.SIDE_LOAD, Stance.LOW_FINISH, true,
                Strike.DIAGONAL_RIGHT, Strike.FALLING);
        form(ArtForge.Form.GREAT_SPIN, Stance.SIDE_LOAD, Stance.SIDE_LOAD, true, Strike.SPIN, Strike.SPIN);
        form(ArtForge.Form.GREAT_SLAM, Stance.HIGH_RAISE, Stance.LOW_FINISH, true, Strike.SLAM);
        form(ArtForge.Form.GREAT_GUILLOTINE, Stance.HIGH_RAISE, Stance.STAFF_PLANTED, true, Strike.FALLING);
        form(ArtForge.Form.GREAT_SWEEP, Stance.SIDE_LOAD, Stance.EXTENDED, true, Strike.WIDE_SWEEP);
        form(ArtForge.Form.GREAT_ERUPTION, Stance.HIGH_RAISE, Stance.STAFF_PLANTED, true,
                Strike.FALLING, Strike.STAB_DOWN);
        // ---------------------------------------------------------------- twin blades
        form(ArtForge.Form.TWIN_FLURRY, Stance.CROSSED, Stance.SPREAD_OUT, false,
                Strike.TWIN_RIGHT, Strike.TWIN_LEFT, Strike.TWIN_RIGHT, Strike.TWIN_LEFT, Strike.TWIN_RIGHT,
                Strike.TWIN_SPIN);
        form(ArtForge.Form.TWIN_ASSASSINATE, Stance.CROUCH, Stance.CROUCH, false, Strike.TWIN_STAB);
        form(ArtForge.Form.TWIN_CROSS, Stance.CROSSED, Stance.SPREAD_OUT, false,
                Strike.TWIN_CROSS, Strike.TWIN_CROSS, Strike.TWIN_SPIN);
        form(ArtForge.Form.TWIN_FAN, Stance.CROSSED, Stance.SPREAD_OUT, false, Strike.TWIN_SPREAD);
        form(ArtForge.Form.TWIN_MAUL, Stance.TWIN_HIGH, Stance.CROUCH, true, Strike.TWIN_SLAM, Strike.TWIN_SLAM);
        form(ArtForge.Form.TWIN_CHAIN, Stance.CROUCH, Stance.SPREAD_OUT, false, Strike.TWIN_STAB, Strike.TWIN_STAB);
        // ---------------------------------------------------------------- spear
        form(ArtForge.Form.SPEAR_LANCE, Stance.DRAW_BACK, Stance.EXTENDED, false, Strike.LUNGE);
        form(ArtForge.Form.SPEAR_THRUSTS, Stance.COUCHED, Stance.EXTENDED, false, Strike.JAB, Strike.JAB,
                Strike.LUNGE);
        form(ArtForge.Form.SPEAR_CHARGE, Stance.COUCHED, Stance.EXTENDED, false, Strike.CHARGE);
        form(ArtForge.Form.SPEAR_IMPALE, Stance.COUCHED, Stance.STAFF_PLANTED, true, Strike.THRUST, Strike.VAULT);
        form(ArtForge.Form.SPEAR_PIERCE, Stance.DRAW_BACK, Stance.EXTENDED, false, Strike.THRUST);
        form(ArtForge.Form.SPEAR_JAVELIN, Stance.OVERHAND, Stance.LOW_FINISH, false, Strike.THROW);
        // ---------------------------------------------------------------- staff
        form(ArtForge.Form.STAFF_BURST, Stance.STAFF_UP, Stance.STAFF_FORWARD, false, Strike.CAST_POINT);
        form(ArtForge.Form.STAFF_ORBS, Stance.STAFF_FORWARD, Stance.STAFF_FORWARD, false,
                Strike.CAST_CIRCLE, Strike.CAST_CIRCLE, Strike.CAST_POINT);
        form(ArtForge.Form.STAFF_LANCE, Stance.STAFF_LOW, Stance.STAFF_FORWARD, false, Strike.CAST_THRUST);
        form(ArtForge.Form.STAFF_VOLLEY, Stance.STAFF_UP, Stance.STAFF_FORWARD, false, Strike.CAST_SWEEP);
        form(ArtForge.Form.STAFF_METEOR, Stance.STAFF_UP, Stance.STAFF_PLANTED, true, Strike.CAST_SKYFALL);
        form(ArtForge.Form.STAFF_BEAM, Stance.STAFF_FORWARD, Stance.STAFF_FORWARD, false, Strike.CAST_BEAM);
        // ---------------------------------------------------------------- bow
        form(ArtForge.Form.BOW_VOLLEY, Stance.BOW_DRAW, Stance.BOW_DRAW, false, Strike.LOOSE, Strike.LOOSE,
                Strike.LOOSE_HEAVY);
        form(ArtForge.Form.BOW_SNIPE, Stance.BOW_STEADY, Stance.BOW_STEADY, false, Strike.LOOSE_HEAVY);
        form(ArtForge.Form.BOW_FAN, Stance.BOW_DRAW, Stance.BOW_DRAW, false, Strike.LOOSE_SWEEP);
        form(ArtForge.Form.BOW_PIERCE, Stance.BOW_LOW, Stance.BOW_STEADY, false, Strike.LOOSE_HEAVY);
        form(ArtForge.Form.BOW_RAIN, Stance.BOW_SKY, Stance.BOW_SKY, false, Strike.LOOSE_SKY);
        form(ArtForge.Form.BOW_IMPACT, Stance.BOW_DRAW, Stance.BOW_LOW, true, Strike.LOOSE_HEAVY);

        // ---------------------------------------------------------------- the boss arts
        boss("art_shadow_legion", Stance.CROUCH, Stance.SPREAD_OUT, true, Strike.TWIN_SPIN, Strike.TWIN_RIGHT,
                Strike.TWIN_LEFT, Strike.TWIN_CROSS, Strike.TWIN_RIGHT, Strike.TWIN_LEFT, Strike.TWIN_SPIN);
        boss("art_thornroot_bloom", Stance.STAFF_UP, Stance.STAFF_PLANTED, true, Strike.CAST_SKYFALL);
        boss("art_sand_maelstrom", Stance.COUCHED, Stance.EXTENDED, true, Strike.JAB, Strike.JAB, Strike.LUNGE);
        boss("art_doom_guillotine", Stance.HIGH_RAISE, Stance.LOW_FINISH, true, Strike.FALLING);
        boss("art_frozen_prison", Stance.STAFF_FORWARD, Stance.STAFF_PLANTED, true, Strike.CAST_CIRCLE);
        boss("art_meteor_slash", Stance.SIDE_LOAD, Stance.HIGH_RAISE, true, Strike.WIDE_SWEEP);
        boss("art_heavens_chain", Stance.BOW_SKY, Stance.BOW_DRAW, true, Strike.LOOSE_SWEEP);
        boss("art_gate_of_end", Stance.DRAW_BACK, Stance.EXTENDED, true, Strike.LUNGE);
        boss("art_black_sword_rain", Stance.BLADE_UP, Stance.HIGH_RAISE, true, Strike.SPIN);

        // ---------------------------------------------------------------- the weapon types' own skills
        BASE.put(WeaponType.ONE_HANDED_SWORD, ArtForge.Form.SWORD_BURST);
        BASE.put(WeaponType.TWO_HANDED_SWORD, ArtForge.Form.GREAT_SLAM);
        BASE.put(WeaponType.DUAL_BLADES, ArtForge.Form.TWIN_FLURRY);
        BASE.put(WeaponType.SPEAR, ArtForge.Form.SPEAR_THRUSTS);
        BASE.put(WeaponType.STAFF, ArtForge.Form.STAFF_BURST);
        BASE.put(WeaponType.BOW, ArtForge.Form.BOW_PIERCE);
    }

    private ArtChoreography() {
    }

    private static void form(ArtForge.Form form, Stance windup, Stance finish, boolean heavy, Strike... strikes) {
        FORMS.put(form, new Choreo(windup, List.of(strikes), finish, heavy));
    }

    private static void boss(String artId, Stance windup, Stance finish, boolean heavy, Strike... strikes) {
        BOSS.put(artId, new Choreo(windup, List.of(strikes), finish, heavy));
    }

    /** The choreography of an art form. */
    public static Choreo of(ArtForge.Form form) {
        return FORMS.get(form);
    }

    /**
     * The choreography of a Z-key action: a weapon art (boss, named, relic or tier weapon) or a weapon type's own
     * skill. Named arts of one form are mirrored by their id, so half of them cut the other way round.
     */
    public static Optional<Choreo> forAction(String actionId) {
        if (actionId == null) {
            return Optional.empty();
        }
        Choreo boss = BOSS.get(actionId);
        if (boss != null) {
            return Optional.of(boss);
        }
        Optional<ArtForge.Form> form = GeneratedArts.form(actionId);
        if (form.isPresent()) {
            Choreo choreo = FORMS.get(form.get());
            return Optional.of(mirroredFor(actionId) ? choreo.mirrored() : choreo);
        }
        for (WeaponProfile profile : WeaponProfiles.all()) {
            if (profile.skill().id().equals(actionId)) {
                return Optional.ofNullable(FORMS.get(BASE.get(profile.type())));
            }
        }
        return Optional.empty();
    }

    /** Whether an art cuts the other way round from its form (by its id: stable between runs). */
    static boolean mirroredFor(String artId) {
        return (artId.hashCode() & 1) == 1;
    }
}
