package com.eternalrealm.mount;

import com.eternalrealm.core.ModInfo;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Every mount the stablemaster sells (docs/modules/mounts.md): a breed of a {@link MountFamily}. The first three are
 * the black warhorse, the armoured griffin and the arcane motorcycle; each of the two animal families has five more
 * breeds, sold from higher levels for more gold, each with its own build, numbers and {@link MountTalent}. Bought
 * from a level ({@link #requiredLevel()}) for gold ({@link #price()}), owned for good. The horse is about twice as fast
 * as sprinting, the bike about one and a half times the horse; the griffin flies at about the horse's speed; no breed
 * outruns the bike on the ground. {@link #id()} is stored with the player and in deeds: never rename it (the mounts
 * bought before the breeds are {@code horse} and {@code griffin}, the first breed of their family).
 */
public enum MountKind {
    HORSE("horse", MountFamily.HORSE, 10, 1_500L, MountTalent.NONE,
            new MountStats(0.57, 0.16, 0.62, 0.35, 0.5, 0.80, 0.0, 0.0, 0.0, 0.0)),
    GRIFFIN("griffin", MountFamily.GRIFFIN, 30, 12_000L, MountTalent.NONE,
            new MountStats(0.58, 0.12, 0.42, 0.35, 0.5, 0.80, 0.32, 0.30, 0.45, 0.035)),
    BIKE("bike", MountFamily.BIKE, 50, 40_000L, MountTalent.NONE,
            new MountStats(0.86, 0.12, 0.52, 0.30, 0.35, 0.88, 0.0, 0.0, 0.0, 0.0)),
    // ---------------------------------------------------------------- the horse breeds (Lv15-45)
    /** Light and deer-like: a little slower, quick to answer, leaps about four blocks. */
    HORSE_BLOOM("horse_bloom", MountFamily.HORSE, 15, 3_000L, MountTalent.GREAT_LEAP,
            new MountStats(0.55, 0.18, 0.80, 0.40, 0.6, 0.78, 0.0, 0.0, 0.0, 0.0)),
    /** Sea-born: gallops over still water; slides on a little longer when let go. */
    HORSE_TIDE("horse_tide", MountFamily.HORSE, 20, 5_000L, MountTalent.WAVE_RUNNER,
            new MountStats(0.58, 0.16, 0.60, 0.35, 0.5, 0.84, 0.0, 0.0, 0.0, 0.0)),
    /** Winged: glides down from heights. */
    HORSE_GALE("horse_gale", MountFamily.HORSE, 25, 8_000L, MountTalent.GLIDE,
            new MountStats(0.59, 0.15, 0.66, 0.35, 0.55, 0.82, 0.0, 0.0, 0.0, 0.0)),
    /** Fire-maned: a long gallop makes it flare up and run faster still. */
    HORSE_BLAZE("horse_blaze", MountFamily.HORSE, 35, 16_000L, MountTalent.SURGE,
            new MountStats(0.61, 0.17, 0.58, 0.30, 0.45, 0.80, 0.0, 0.0, 0.0, 0.0)),
    /** Horned and haloed: the fastest horse, steps on the air once per jump. */
    HORSE_HALO("horse_halo", MountFamily.HORSE, 45, 28_000L, MountTalent.SKY_STEP,
            new MountStats(0.64, 0.19, 0.64, 0.40, 0.55, 0.82, 0.0, 0.0, 0.0, 0.0)),
    // ---------------------------------------------------------------- the griffin breeds (Lv35-65)
    /** Lion-bodied: runs on the ground nearly as fast as it flies. */
    GRIFFIN_GOLD("griffin_gold", MountFamily.GRIFFIN, 35, 18_000L, MountTalent.LAND_STRIDER,
            new MountStats(0.57, 0.13, 0.45, 0.35, 0.5, 0.84, 0.52, 0.28, 0.45, 0.04)),
    /** Owl-faced: hangs in the air without sinking, nimble backwards and sideways, silent wings. */
    GRIFFIN_OWL("griffin_owl", MountFamily.GRIFFIN, 40, 25_000L, MountTalent.HOVER,
            new MountStats(0.58, 0.14, 0.42, 0.45, 0.6, 0.80, 0.30, 0.30, 0.40, 0.0)),
    /** Storm-feathered: dives steeply and gains speed in the dive. */
    GRIFFIN_STORM("griffin_storm", MountFamily.GRIFFIN, 45, 34_000L, MountTalent.DIVE,
            new MountStats(0.62, 0.13, 0.44, 0.35, 0.5, 0.80, 0.32, 0.30, 0.90, 0.04)),
    /** Ice-crested: flies higher than the others and glides long. */
    GRIFFIN_FROST("griffin_frost", MountFamily.GRIFFIN, 55, 52_000L, MountTalent.HIGH_FLIGHT,
            new MountStats(0.64, 0.13, 0.44, 0.35, 0.5, 0.80, 0.33, 0.33, 0.48, 0.025)),
    /** Star-strewn: the fastest flier, shoots up from the ground and climbs fast. */
    GRIFFIN_VOID("griffin_void", MountFamily.GRIFFIN, 65, 80_000L, MountTalent.UPDRAFT,
            new MountStats(0.68, 0.15, 0.62, 0.40, 0.55, 0.82, 0.34, 0.46, 0.50, 0.03));

    private final String id;
    private final MountFamily family;
    private final int requiredLevel;
    private final long price;
    private final MountTalent talent;
    private final MountStats stats;

    MountKind(String id, MountFamily family, int requiredLevel, long price, MountTalent talent, MountStats stats) {
        this.id = id;
        this.family = family;
        this.requiredLevel = requiredLevel;
        this.price = price;
        this.talent = talent;
        this.stats = stats;
    }

    public String id() {
        return id;
    }

    /** What it is built like: horse, griffin or arcbike. */
    public MountFamily family() {
        return family;
    }

    /** The level from which the mount can be bought (and ridden). */
    public int requiredLevel() {
        return requiredLevel;
    }

    /** Gold the stablemaster asks for it. */
    public long price() {
        return price;
    }

    /** Whether it flies (jump climbs, sneak descends, it glides down slowly otherwise). */
    public boolean flies() {
        return family.flies();
    }

    /** What the breed does that its family otherwise does not. */
    public MountTalent talent() {
        return talent;
    }

    public MountStats stats() {
        return stats;
    }

    public String translationKey() {
        return "mount." + ModInfo.MOD_ID + "." + id;
    }

    public String descriptionKey() {
        return translationKey() + ".desc";
    }

    /** The registry path of its entity type ({@code eternalrealm:mount_<id>}). */
    public String entityPath() {
        return "mount_" + id;
    }

    public static Optional<MountKind> byId(String id) {
        if (id == null) {
            return Optional.empty();
        }
        for (MountKind kind : values()) {
            if (kind.id.equals(id.toLowerCase(Locale.ROOT))) {
                return Optional.of(kind);
            }
        }
        return Optional.empty();
    }

    /** The breeds of {@code family}, the first one first, then by level (the stable's order). */
    public static List<MountKind> breeds(MountFamily family) {
        List<MountKind> breeds = new ArrayList<>();
        for (MountKind kind : values()) {
            if (kind.family == family) {
                breeds.add(kind);
            }
        }
        breeds.sort(Comparator.comparingInt(MountKind::requiredLevel).thenComparingInt(MountKind::ordinal));
        return List.copyOf(breeds);
    }

    /**
     * How a mount moves (all speeds in blocks per tick).
     *
     * @param topSpeed        forward speed at full gallop or throttle (in the air for a flier)
     * @param acceleration    share of the gap to the wanted speed closed per tick
     * @param jump            upward speed of a jump (ground mounts) or of the take-off (fliers)
     * @param backFactor      backward speed as a share of the top speed
     * @param strafeFactor    sideways speed as a share of the top speed
     * @param coast           horizontal speed kept per tick without input on the ground
     * @param groundSpeed     a flier's speed walking on the ground (0: not a flier)
     * @param climb           a flier's climb speed while jump is held
     * @param descend         a flier's descent speed while sneak is held
     * @param glide           a flier's slow sink without input
     */
    public record MountStats(double topSpeed, double acceleration, double jump, double backFactor, double strafeFactor,
                             double coast, double groundSpeed, double climb, double descend, double glide) {
    }
}
