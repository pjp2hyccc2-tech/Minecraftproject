package com.eternalrealm.boss;

import com.eternalrealm.core.ModInfo;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * What the platform must do for a boss this tick, in order. Returned by
 * {@link BossBrain#tick(long, double, BossPosition, List)}.
 *
 * <p>Typical handling: {@link AbilityStarted} shows a cast bar with the ability name, {@link Telegraph} draws the
 * warning shape until it executes or is {@link Cancelled}, {@link Execute} damages every player inside the shape,
 * {@link Volley} spawns projectiles, {@link Summon} spawns adds, {@link ShieldChanged} toggles the immunity visuals
 * (the immunity itself is {@link BossBrain#isInvulnerable(long)}), {@link PhaseChanged} applies the phase's movement
 * speed.</p>
 */
public sealed interface BossCommand permits BossCommand.PhaseChanged, BossCommand.AbilityStarted,
        BossCommand.Telegraph, BossCommand.Cancelled, BossCommand.Execute, BossCommand.Volley, BossCommand.Summon,
        BossCommand.Teleport, BossCommand.ShieldChanged, BossCommand.Enraged, BossCommand.Heal, BossCommand.Buffed {

    /**
     * The fight entered a phase (index 0 when the fight starts). From index 1 on, a short invulnerable transition
     * follows ({@link ShieldChanged} on, then off after {@link BossRules#PHASE_TRANSITION_TICKS}).
     */
    record PhaseChanged(int phaseIndex, BossPhase phase) implements BossCommand {
        /** Chat/title message, argument 1: boss name, 2: phase number (1-based). */
        public static final String MESSAGE_KEY = "message." + ModInfo.MOD_ID + ".boss.phase_changed";

        public PhaseChanged {
            Check.range("phaseIndex", phaseIndex, 0, 64);
            Objects.requireNonNull(phase, "phase");
        }
    }

    /** The boss began an ability; it executes in {@code executeInTicks} unless {@link Cancelled}. Keep the boss still. */
    record AbilityStarted(BossAbility ability, int executeInTicks) implements BossCommand {
        public AbilityStarted {
            Objects.requireNonNull(ability, "ability");
            Check.range("executeInTicks", executeInTicks, 0, BossAbility.MAX_TICKS);
        }
    }

    /**
     * Draw {@code shape} for {@code durationTicks}: a danger zone for damaging kinds, a harmless marker for summon,
     * shield and teleport positions ({@code ability.kind().damaging()} tells which). One command per shape.
     */
    record Telegraph(BossAbility ability, BossShape shape, int durationTicks) implements BossCommand {
        public Telegraph {
            Objects.requireNonNull(ability, "ability");
            Objects.requireNonNull(shape, "shape");
            Check.range("durationTicks", durationTicks, 0, BossAbility.MAX_TICKS);
        }

        public String abilityId() {
            return ability.id();
        }
    }

    /** An ability that had not executed yet was interrupted (phase change, death): remove its telegraphs. */
    record Cancelled(BossAbility ability) implements BossCommand {
        public Cancelled {
            Objects.requireNonNull(ability, "ability");
        }
    }

    /**
     * Damage every player inside {@code shape} (one command per shape; a player inside two shapes is hit twice).
     * Apply {@code damage} through the normal defense pipeline and respect the kind's flags:
     * {@code dodgeable} (dodge invulnerability avoids it), {@code guardable} (guard/parry work), {@code splitsDamage}
     * (divide {@code damage} by the number of players inside).
     *
     * @param damageMultiplier total multiplier on the boss's base damage (ability × difficulty × enrage × buff)
     * @param damage           base damage × {@code damageMultiplier}, before the player's defense
     */
    record Execute(BossAbility ability, BossShape shape, double damageMultiplier, double damage)
            implements BossCommand {
        public Execute {
            Objects.requireNonNull(ability, "ability");
            Objects.requireNonNull(shape, "shape");
            Check.range("damageMultiplier", damageMultiplier, 0.0, 10_000.0);
            Check.range("damage", damage, 0.0, 1_000_000.0);
        }

        public String abilityId() {
            return ability.id();
        }
    }

    /**
     * Spawn one projectile per yaw from {@code origin} (plus the boss's eye height) at {@code speed} blocks per tick;
     * each deals {@code damage} on impact and expires after {@link BossRules#VOLLEY_RANGE} blocks.
     */
    record Volley(BossAbility ability, Point origin, List<Double> yawDegrees, double speed, double damageMultiplier,
                  double damage) implements BossCommand {
        public Volley {
            Objects.requireNonNull(ability, "ability");
            Objects.requireNonNull(origin, "origin");
            yawDegrees = List.copyOf(yawDegrees);
            if (yawDegrees.isEmpty()) {
                throw new IllegalArgumentException("a volley needs at least one projectile");
            }
            Check.range("speed", speed, 0.0, 10.0);
            Check.range("damageMultiplier", damageMultiplier, 0.0, 10_000.0);
            Check.range("damage", damage, 0.0, 1_000_000.0);
        }
    }

    /**
     * Spawn {@code count} monsters of {@code templateId} at {@code positions} (move a position to the nearest free
     * spot if it is blocked). If {@code shieldAdds} is true, call {@link BossBrain#onAddKilled()} once for each of these
     * adds that dies or cannot be spawned.
     */
    record Summon(String templateId, int count, List<Point> positions, boolean shieldAdds) implements BossCommand {
        public Summon {
            Check.id("templateId", templateId);
            positions = List.copyOf(positions);
            Check.range("count", count, 1, 64);
            if (positions.size() != count) {
                throw new IllegalArgumentException("need one position per monster");
            }
        }
    }

    /** Move the boss to {@code destination}, where {@code targetId} stood when the telegraph started. */
    record Teleport(UUID targetId, Point destination) implements BossCommand {
        public Teleport {
            Objects.requireNonNull(targetId, "targetId");
            Objects.requireNonNull(destination, "destination");
        }
    }

    /** The boss became immune ({@code on}) or vulnerable again. Visual/message only. */
    record ShieldChanged(boolean on) implements BossCommand {
        /** Message when the shield goes up, argument 1: boss name. */
        public static final String SHIELD_UP_KEY = "message." + ModInfo.MOD_ID + ".boss.shield_up";
        /** Message when the shield breaks, argument 1: boss name. */
        public static final String SHIELD_DOWN_KEY = "message." + ModInfo.MOD_ID + ".boss.shield_down";
    }

    /** Soft enrage reached: every hit now deals {@link BossRules#ENRAGE_DAMAGE_MULTIPLIER} more. */
    record Enraged() implements BossCommand {
        /** Message, argument 1: boss name. */
        public static final String MESSAGE_KEY = "message." + ModInfo.MOD_ID + ".boss.enraged";
    }

    /** Restore {@code fraction} of the boss's maximum health. */
    record Heal(double fraction) implements BossCommand {
        public Heal {
            Check.range("fraction", fraction, 0.0, 1.0);
        }
    }

    /** All damage of the boss is multiplied by {@code damageMultiplier} for {@code durationTicks} (effects only). */
    record Buffed(double damageMultiplier, int durationTicks) implements BossCommand {
        public Buffed {
            Check.range("damageMultiplier", damageMultiplier, 1.0, 10.0);
            Check.range("durationTicks", durationTicks, 1, 20 * 600);
        }
    }
}
