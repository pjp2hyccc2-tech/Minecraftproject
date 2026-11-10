package com.eternalrealm.job;

import com.eternalrealm.combat.CombatModifiers;
import com.eternalrealm.job.SkillEffect.AfflictionKind;

import java.util.Objects;

/**
 * What a passive skill gives per learned level (the values are multiplied by the skill level). Summed over every
 * learned passive into {@link PassiveTotals}: the combat modifiers go into the character's stats, the skill bonuses
 * into the skill bindings (both computed the same way on the server and the owning client), the rest is applied by
 * the server when hits land.
 */
public sealed interface PassiveEffect permits PassiveEffect.Stats, PassiveEffect.SkillPower, PassiveEffect.Cooldown,
        PassiveEffect.StaminaSaver, PassiveEffect.Lifesteal, PassiveEffect.Proc, PassiveEffect.LastStand,
        PassiveEffect.Thorns, PassiveEffect.Evasion, PassiveEffect.HealOnKill {

    /** Secondary combat bonuses (damage, health, defense, critical, speed...) per level. */
    record Stats(CombatModifiers perLevel) implements PassiveEffect {
        public Stats {
            Objects.requireNonNull(perLevel, "perLevel");
        }
    }

    /** Damage of every job skill × (1 + perLevel × level). */
    record SkillPower(double perLevel) implements PassiveEffect {
        public SkillPower {
            Checks.range("perLevel", perLevel, 0.001, 0.2);
        }
    }

    /** Cooldowns of the job skills × (1 − perLevel × level). */
    record Cooldown(double perLevel) implements PassiveEffect {
        public Cooldown {
            Checks.range("perLevel", perLevel, 0.001, 0.1);
        }
    }

    /** Stamina cost of the job skills × (1 − perLevel × level). */
    record StaminaSaver(double perLevel) implements PassiveEffect {
        public StaminaSaver {
            Checks.range("perLevel", perLevel, 0.001, 0.1);
        }
    }

    /** Heals the fighter by perLevel × level of the damage their hits deal. */
    record Lifesteal(double perLevel) implements PassiveEffect {
        public Lifesteal {
            Checks.range("perLevel", perLevel, 0.001, 0.05);
        }
    }

    /**
     * Every damaging hit has a {@code chancePerLevel × level} chance to afflict the target.
     *
     * @param meleeOnly only melee hits (not projectiles, explosions or ground areas)
     */
    record Proc(AfflictionKind kind, double chancePerLevel, int durationTicks, double power, boolean meleeOnly)
            implements PassiveEffect {
        public Proc {
            Objects.requireNonNull(kind, "kind");
            Checks.range("chancePerLevel", chancePerLevel, 0.001, 0.1);
            Checks.range("durationTicks", durationTicks, 1, 20 * 30);
            Checks.range("power", power, 0.0, 10.0);
        }
    }

    /** While below half health: damage dealt +damagePerLevel × level, damage taken −reductionPerLevel × level. */
    record LastStand(double damagePerLevel, double reductionPerLevel) implements PassiveEffect {
        public LastStand {
            Checks.range("damagePerLevel", damagePerLevel, 0.0, 0.2);
            Checks.range("reductionPerLevel", reductionPerLevel, 0.0, 0.1);
            if (damagePerLevel == 0.0 && reductionPerLevel == 0.0) {
                throw new IllegalArgumentException("a last stand needs a bonus");
            }
        }
    }

    /** Reflects perLevel × level of the damage taken from an attacker back to it. */
    record Thorns(double perLevel) implements PassiveEffect {
        public Thorns {
            Checks.range("perLevel", perLevel, 0.001, 0.2);
        }
    }

    /** Chance (perLevel × level) to evade an attack completely. */
    record Evasion(double perLevel) implements PassiveEffect {
        public Evasion {
            Checks.range("perLevel", perLevel, 0.001, 0.05);
        }
    }

    /** Heals perLevel × level of the maximum health whenever the fighter kills something. */
    record HealOnKill(double perLevel) implements PassiveEffect {
        public HealOnKill {
            Checks.range("perLevel", perLevel, 0.001, 0.1);
        }
    }
}
