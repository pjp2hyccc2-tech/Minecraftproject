package com.eternalrealm.item;

import java.util.Objects;

/**
 * How a generated weapon art looks and sounds: the composer of the platform draws its {@link Shape}s with its
 * particles in its colours. Particles are spec strings: {@code dust:RRGGBB}, {@code v:<vanilla particle>},
 * {@code item:<item id>}, {@code block:<block id>} or {@code er:<particle of the mod>}.
 *
 * @param family         the motif (thorn, feather, ink, ore, ...): also names its ground areas ({@code fx_<family>})
 * @param primary        what every hit draws
 * @param accent         what the wind-up and the impacts draw
 * @param particle       main particle
 * @param accentParticle accent particle
 * @param rgb            main colour
 * @param accentRgb      accent colour
 * @param windup         how it charges
 */
public record ArtLook(String family, Shape primary, Shape accent, String particle, String accentParticle, int rgb,
                      int accentRgb, Windup windup) {
    /** The drawing primitives of the composer. */
    public enum Shape {
        RING, BURST, CRESCENT, ARC, LINE, HELIX, CHAIN, SPIKES, PILLAR, RAIN, WAVE, FAN, SPIRAL, VORTEX, STAR, SIGIL,
        CROSS, PETALS, ORBS, DOME, COMET, SCATTER, GEYSER, CAGE
    }

    /** How an art charges before its first hit. */
    public enum Windup {
        /** Motes spiral in onto the weapon. */
        GATHER,
        /** The weapon glows brighter and brighter. */
        GLOW,
        /** A circle is drawn on the ground under the wielder. */
        GROUND_SIGIL,
        /** Particles rise around the wielder. */
        RISING,
        /** A ring spins up around the wielder. */
        SPIN_UP,
        /** A circle is drawn in the air in front of the wielder. */
        AIR_SIGIL
    }

    public ArtLook {
        Objects.requireNonNull(family, "family");
        if (!family.matches("[a-z0-9_]+")) {
            throw new IllegalArgumentException("family must be [a-z0-9_]+: " + family);
        }
        Objects.requireNonNull(primary, "primary");
        Objects.requireNonNull(accent, "accent");
        checkParticle(particle);
        checkParticle(accentParticle);
        Objects.requireNonNull(windup, "windup");
    }

    /** The ground areas of the art draw this look ({@code SkillEffect.Zone#look}). */
    public String zoneLook() {
        return "fx_" + family;
    }

    /** What makes two arts look alike: the same motif, shapes, particles and colour. */
    public String signature() {
        return family + "|" + primary + "|" + accent + "|" + particle + "|" + accentParticle + "|"
                + String.format("%06x", rgb & 0xFFFFFF);
    }

    private static void checkParticle(String spec) {
        Objects.requireNonNull(spec, "particle");
        if (!spec.matches("(dust:[0-9a-f]{6}|v:[a-z_]+|item:[a-z0-9_]+:[a-z0-9_/]+|block:[a-z0-9_]+:[a-z0-9_/]+"
                + "|er:[a-z_]+)")) {
            throw new IllegalArgumentException("bad particle spec " + spec);
        }
    }
}
