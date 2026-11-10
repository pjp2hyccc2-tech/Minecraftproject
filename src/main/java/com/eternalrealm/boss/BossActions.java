package com.eternalrealm.boss;

/**
 * The animated action the boss's body plays for an ability, and when it starts: an action has a wind-up, an impact
 * frame (the blade hits the floor, the spell leaves the hand, the dragon lands) and a recovery, and it is started
 * {@code impactTicks} before the ability's hit so the impact is seen exactly when the damage lands. Until then the
 * body shows its telegraph loop (the roar). Timings come from the rigs ({@link BossRigData}); content tests check
 * that every telegraph is at least as long as its action's wind-up.
 */
public final class BossActions {
    public static final String SLAM = "melee_slam";
    public static final String SWEEP = "melee_sweep";
    public static final String CAST = "cast";
    public static final String ROAR = "roar";
    public static final String CHARGE = "charge";
    public static final String SUMMON = "summon";

    private BossActions() {
    }

    /** Action id (a {@link BossRigData#ACTIONS} entry) of {@code kind}; a sequence plays the action of its first part. */
    public static String actionFor(AbilityKind kind) {
        if (kind instanceof AbilityKind.Sequence sequence) {
            return actionFor(sequence.parts().get(0).kind());
        }
        if (kind instanceof AbilityKind.CircleAroundBoss || kind instanceof AbilityKind.Ring
                || kind instanceof AbilityKind.Knockback || kind instanceof AbilityKind.Exaflare) {
            return SLAM;
        }
        if (kind instanceof AbilityKind.Cone || kind instanceof AbilityKind.Crosses) {
            return SWEEP;
        }
        if (kind instanceof AbilityKind.Line || kind instanceof AbilityKind.Charge) {
            return CHARGE;
        }
        if (kind instanceof AbilityKind.ArenaWide || kind instanceof AbilityKind.Buff
                || kind instanceof AbilityKind.SafeZones) {
            return ROAR;
        }
        if (kind instanceof AbilityKind.Summon) {
            return SUMMON;
        }
        return CAST;
    }

    /** Whether {@code action} has an impact frame to put on the hit (the roar is a loop held through the telegraph). */
    public static boolean timed(String action) {
        return !ROAR.equals(action);
    }

    /**
     * Whether the body plays the action of {@code kind} as a channel from the start of the ability instead of putting
     * its impact on the hit: chasing puddles explode one after another under moving players, there is no single hit
     * for the body to land.
     */
    public static boolean channelled(AbilityKind kind) {
        return kind instanceof AbilityKind.Trail;
    }

    /** Ticks from the start of the action of {@code kind} to its impact frame for {@code bossId}; 0 if untimed. */
    public static int impactTicks(String bossId, AbilityKind kind) {
        String action = actionFor(kind);
        return timed(action) && !channelled(kind) ? BossRigData.impactTicks(bossId, action) : 0;
    }

    /**
     * Ticks to wait before starting the action so that its impact lands {@code ticksToHit} from now (0: start at once;
     * a telegraph shorter than the wind-up starts at once and lands late, which content tests forbid).
     */
    public static int startDelay(String bossId, AbilityKind kind, int ticksToHit) {
        return Math.max(0, ticksToHit - impactTicks(bossId, kind));
    }
}
