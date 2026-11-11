package com.eternalrealm.mount;

import com.eternalrealm.core.ModInfo;

import java.util.Locale;
import java.util.Optional;

/**
 * The three mounts (docs/modules/mounts.md): a black warhorse, an armoured griffin and an arcane motorcycle. Bought
 * from the capital's stablemaster from a level ({@link #requiredLevel()}) for gold ({@link #price()}), owned for
 * good. The horse is about twice as fast as sprinting, the bike about one and a half times the horse; the griffin
 * flies at about the horse's speed. {@link #id()} is stored with the player: never rename it.
 */
public enum MountKind {
    HORSE("horse", 10, 1_500L, false,
            new MountStats(0.57, 0.16, 0.62, 0.35, 0.5, 0.80, 0.0, 0.0, 0.0, 0.0)),
    GRIFFIN("griffin", 30, 12_000L, true,
            new MountStats(0.58, 0.12, 0.42, 0.35, 0.5, 0.80, 0.32, 0.30, 0.45, 0.035)),
    BIKE("bike", 50, 40_000L, false,
            new MountStats(0.86, 0.12, 0.52, 0.30, 0.35, 0.88, 0.0, 0.0, 0.0, 0.0));

    private final String id;
    private final int requiredLevel;
    private final long price;
    private final boolean flies;
    private final MountStats stats;

    MountKind(String id, int requiredLevel, long price, boolean flies, MountStats stats) {
        this.id = id;
        this.requiredLevel = requiredLevel;
        this.price = price;
        this.flies = flies;
        this.stats = stats;
    }

    public String id() {
        return id;
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
        return flies;
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
