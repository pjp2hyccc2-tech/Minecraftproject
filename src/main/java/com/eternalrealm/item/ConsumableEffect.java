package com.eternalrealm.item;

/** What using a {@link Consumable} does; the platform layer executes it. */
public sealed interface ConsumableEffect permits ConsumableEffect.Heal, ConsumableEffect.RestoreStamina,
        ConsumableEffect.ReturnHome, ConsumableEffect.ResetPoints, ConsumableEffect.Revive {

    /**
     * Instant healing: {@code fraction × max health + flat}. Not scaled by the user's healing multiplier (that is for
     * healing spells), so a potion heals everyone the same.
     */
    record Heal(double fraction, double flat) implements ConsumableEffect {
        public Heal {
            Checks.range("fraction", fraction, 0.0, 1.0);
            Checks.range("flat", flat, 0.0, 1000.0);
        }

        public double amount(double maxHealth) {
            return Checks.range("maxHealth", maxHealth, 0.0, 1_000_000.0) * fraction + flat;
        }
    }

    /** Instant stamina: {@code fraction × max stamina + flat} (capped by the pool by the combat system). */
    record RestoreStamina(double fraction, double flat) implements ConsumableEffect {
        public RestoreStamina {
            Checks.range("fraction", fraction, 0.0, 1.0);
            Checks.range("flat", flat, 0.0, 10_000.0);
        }

        public double amount(double maxStamina) {
            return Checks.range("maxStamina", maxStamina, 0.0, 1_000_000.0) * fraction + flat;
        }
    }

    /**
     * Teleport to the player's home point after channelling for {@code channelTicks}. Taking damage or moving cancels
     * the channel; the scroll is consumed only when the teleport happens.
     */
    record ReturnHome(int channelTicks) implements ConsumableEffect {
        public ReturnHome {
            Checks.range("channelTicks", channelTicks, 1, 20 * 60);
        }
    }

    /** Refunds all allocated stat points and/or invested skill points so they can be spent again. */
    record ResetPoints(boolean stats, boolean skills) implements ConsumableEffect {
        public ResetPoints {
            if (!stats && !skills) {
                throw new IllegalArgumentException("A reset must reset something");
            }
        }
    }

    /**
     * Brings a fallen player back where they lie with {@code healthFraction} of their health (the revival system,
     * docs/modules/revival.md). Used on oneself from the revival screen or on a fallen ally.
     */
    record Revive(double healthFraction) implements ConsumableEffect {
        public Revive {
            Checks.range("healthFraction", healthFraction, 0.05, 1.0);
        }
    }
}
