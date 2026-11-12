package com.eternalrealm.job.fx;

import com.eternalrealm.job.SkillTheme;

/**
 * One layer of a skill's show: a shape of light ({@link Kind}) that appears at {@code start}, reaches its moment at
 * {@code peak} (the hit, the landing, the flash) and is gone at {@code end} (ticks of the skill's own timeline).
 * {@code a..d}, {@code n} and {@code variant} are the kind's sizes and choices (see each kind); {@code palette}
 * overrides the skill's theme colours (an elemental strike of another element), {@code seed} varies its details.
 */
public record Cue(Kind kind, double start, double peak, double end, Anchor anchor, int frame, double a, double b,
                  double c, double d, int n, int variant, SkillTheme palette, long seed) {

    /** Where a layer is placed. */
    public enum Anchor {
        /** Follows the caster. */
        CASTER,
        /** Where and how the caster stood when the skill began. */
        ORIGIN,
        /** Where the caster stood when hit frame {@code frame} came (the caster at that moment until then). */
        FRAME,
        /** The point the skill aims at (where the caster looked, or the locked-on enemy) when it began. */
        AIM
    }

    /** The shapes; the meaning of a, b, c, d, n and variant is given per kind. */
    public enum Kind {
        // ---- strikes (anchored on the hit frame's pose)
        /** Crescent cut. a reach, b half-angle (deg), c tilt (deg: 0 level, 90 upright), d width; variant: bit0 reversed, bit1 heavy, bit2 emphasis. */
        SLASH,
        /** Three parallel claw cuts. a reach, c tilt. */
        CLAW,
        /** A full turn around the caster. a radius, b height above the feet, c tilt, d width; variant bit0 reversed. */
        SPIN,
        /** A spear streak along the forward direction. a length, b width. */
        THRUST,
        /** A beam along the look direction from the hand. a length, b width. */
        BEAM,
        // ---- areas
        /** Expanding ground ring(s) with a low wall. a radius, b wall height, n rings, d distance ahead. */
        SHOCKWAVE,
        /** A burst of rings and rays. a radius, n rays, d distance ahead. */
        NOVA,
        /** Glowing cracks with shards. a radius, n cracks, b distance ahead. */
        QUAKE,
        /** Crystals erupting on a ring. a radius, b height, n count, d distance ahead. */
        SPIKES,
        /** Columns of light. a radius, b height, d distance ahead; n pillars on a ring of radius c (0: one at the anchor). */
        PILLAR,
        /** A magic circle on the ground (or upright with d=90, c ahead). a radius, n polygram corners, b distance ahead, c height, d tilt, variant style. */
        SIGIL,
        /** A magic circle high above, shining down at its peak. a radius, c height, d distance ahead, n corners, variant style. */
        SKY_SIGIL,
        /** Lightning from the sky onto points in a radius. a radius, n bolts, b distance ahead, c height. */
        BOLTS,
        /** Falling light within a radius. a radius, n drops per tick, b distance ahead, c height; variant: 0 streak, 1 arrow, 2 icicle, 3 ember, 4 needle, 5 feather, 6 petal, 7 star. */
        RAIN,
        /** A star burst. a radius, n rays, b distance ahead. */
        BURST,
        /** A small impact star at the strike point. a size; b distance ahead. */
        IMPACT,
        /** Inward spiral and funnel. a radius, b height, d distance ahead. */
        VORTEX,
        /** Drifting mist. a radius, n patches, b distance ahead. */
        FOG,
        /** Chains of light reaching out. a radius, n chains. */
        CHAINS,
        /** A cone of tongues. a length, b half-angle (deg), n tongues. */
        BREATH,
        /** Rings rolling forward. a length, n rings; variant 1: small rings from the hand along the look (a launch). */
        ROAR,
        /** A shield sphere. a radius. */
        DOME,
        /** Wall panels standing in a ring. a radius, b height, n panels. */
        WALLS,
        /**
         * A skill's ground area for its whole life (burning ground, a healing circle, a storm field): its rim, its
         * floor and the theme's weather inside. a radius, b ticks before the first pulse, c ticks between pulses, n
         * pulses; variant: bit0 heals, bit1 harms.
         */
        FIELD,
        // ---- on the caster
        /** Casting light at the hand: a flare and an upright casting circle an arm's length ahead. a size; variant: job style. */
        CAST,
        /** Light gathering into the hand. a size. */
        CHARGE,
        /** Ribbons spiralling up. a radius, b height, n strands. */
        HELIX,
        /** Rising rings of a blessing. a radius (allies area or 0), variant: the buff. */
        AURA,
        /** A halo over the head. a radius. */
        HALO,
        /** Wings of light. a span. */
        WINGS,
        /** Afterimages of a dash from where it began. a distance (when the caster has not moved). */
        STREAK,
        /** A spiral wake of a leap. a height. */
        LEAP,
        /** A floating sign over the head. variant: 0 cross, 1 sword, 2 shield, 3 clock, 4 eye, 5 star, 6 moon, 7/8 arrow up/down, 9 death mark, 10 heart, 11 lightning; a size, b distance ahead, c height. */
        GLYPH,
        /** The weapon wrapped in its element. a size. */
        WEAPON,
        /** Orbs circling. a radius, n orbs. */
        ORBIT,
        /** Healing light: crosses and motes rising. a radius. */
        HEAL,
        /** A cleansing white wave. a radius. */
        CLEANSE,
        /** Sparkles. a radius, n count; variant: 0 rise, 1 converge, 2 orbit, 3 fall. */
        MOTES,
        /** Streams of light flowing from the strike to the caster. a distance. */
        SIPHON,
        /** Red pulses of a challenge. a radius. */
        TAUNT,
        /** The mark of an affliction where the strike lands. variant: the affliction, a radius, b distance ahead. */
        AFFLICT,
        /** A short blinding flash. a size, b distance ahead. */
        FLASH,
        /** A clock face with turning hands. a radius. */
        CLOCK,
        /** A crosshair. a radius, b distance ahead. */
        RETICLE,
        // ---- the grand shapes of ultimates
        /** A giant sword of light. a length, b distance ahead; variant 0 falls from the sky, 1 raised and swung forward. */
        GIANT_SWORD,
        /** A great cross of light. a length, b distance ahead. */
        CROSS,
        /** Floating blades around the caster. a radius, n blades. */
        BLADES,
        /** A tear in space. a length, c tilt (deg). */
        RIFT,
        /** A dark sun's corona in the sky. a radius, c height. */
        ECLIPSE,
        /** Serpents of light rising behind the caster, lunging on each hit frame. n heads, a size. */
        HYDRA,
        /** A flaming rock falling from the sky onto the anchor at its peak. a size. */
        METEOR
    }

    public static Builder of(Kind kind, double start, double peak, double end) {
        return new Builder(kind, start, peak, end);
    }

    /** Short way to build cues. */
    public static final class Builder {
        private final Kind kind;
        private final double start;
        private final double peak;
        private final double end;
        private Anchor anchor = Anchor.CASTER;
        private int frame = -1;
        private double a;
        private double b;
        private double c;
        private double d;
        private int n;
        private int variant;
        private SkillTheme palette;
        private long seed;

        private Builder(Kind kind, double start, double peak, double end) {
            this.kind = kind;
            this.start = start;
            this.peak = Math.max(start, peak);
            this.end = Math.max(this.peak, end);
        }

        public Builder at(Anchor anchor) {
            this.anchor = anchor;
            return this;
        }

        public Builder frame(int frame) {
            this.anchor = Anchor.FRAME;
            this.frame = frame;
            return this;
        }

        public Builder a(double v) {
            this.a = v;
            return this;
        }

        public Builder b(double v) {
            this.b = v;
            return this;
        }

        public Builder c(double v) {
            this.c = v;
            return this;
        }

        public Builder d(double v) {
            this.d = v;
            return this;
        }

        public Builder n(int v) {
            this.n = v;
            return this;
        }

        public Builder variant(int v) {
            this.variant = v;
            return this;
        }

        public Builder palette(SkillTheme theme) {
            this.palette = theme;
            return this;
        }

        public Builder seed(long v) {
            this.seed = v;
            return this;
        }

        public Cue build() {
            return new Cue(kind, start, peak, end, anchor, frame, a, b, c, d, n, variant, palette, seed);
        }
    }
}
