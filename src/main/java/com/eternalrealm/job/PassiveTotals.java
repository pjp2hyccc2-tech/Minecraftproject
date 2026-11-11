package com.eternalrealm.job;

import com.eternalrealm.combat.CombatModifiers;
import com.eternalrealm.job.SkillEffect.AfflictionKind;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Everything a character's learned passive skills add up to (values already multiplied by the skill levels and
 * capped). Computed by {@link SkillTree#passives} from the character data, so the server and the owning client agree.
 *
 * @param modifiers          secondary combat bonuses (added to the job passive and the equipment)
 * @param skillPower         job skills deal × (1 + skillPower)
 * @param cooldownReduction  job skill cooldowns × (1 − cooldownReduction), at most {@value #MAX_COOLDOWN_REDUCTION}
 * @param staminaReduction   job skill stamina × (1 − staminaReduction), at most {@value #MAX_STAMINA_REDUCTION}
 * @param lifesteal          fraction of dealt damage healed
 * @param procs              on-hit afflictions with their total chance
 * @param lastStandDamage    damage bonus below half health
 * @param lastStandReduction damage taken reduction below half health
 * @param thorns             fraction of damage taken reflected to the attacker
 * @param evasion            chance to evade an attack completely
 * @param healOnKill         fraction of max health healed per kill
 */
public record PassiveTotals(CombatModifiers modifiers, double skillPower, double cooldownReduction,
                            double staminaReduction, double lifesteal, List<Proc> procs, double lastStandDamage,
                            double lastStandReduction, double thorns, double evasion, double healOnKill) {
    public static final double MAX_COOLDOWN_REDUCTION = 0.4;
    public static final double MAX_STAMINA_REDUCTION = 0.5;
    public static final double MAX_LIFESTEAL = 0.3;
    public static final double MAX_EVASION = 0.35;
    public static final double MAX_LAST_STAND = 0.6;
    public static final double MAX_THORNS = 1.0;
    public static final double MAX_HEAL_ON_KILL = 0.3;
    public static final PassiveTotals NONE = new PassiveTotals(CombatModifiers.NONE, 0, 0, 0, 0, List.of(), 0, 0, 0,
            0, 0);

    /** An on-hit affliction with its chance per hit. */
    public record Proc(AfflictionKind kind, double chance, int durationTicks, double power, boolean meleeOnly) {
        public Proc {
            Objects.requireNonNull(kind, "kind");
            Checks.range("chance", chance, 0.0, 1.0);
        }
    }

    public PassiveTotals {
        Objects.requireNonNull(modifiers, "modifiers");
        procs = List.copyOf(procs);
        Checks.range("skillPower", skillPower, 0.0, 10.0);
        Checks.range("cooldownReduction", cooldownReduction, 0.0, MAX_COOLDOWN_REDUCTION);
        Checks.range("staminaReduction", staminaReduction, 0.0, MAX_STAMINA_REDUCTION);
        Checks.range("lifesteal", lifesteal, 0.0, MAX_LIFESTEAL);
        Checks.range("lastStandDamage", lastStandDamage, 0.0, MAX_LAST_STAND);
        Checks.range("lastStandReduction", lastStandReduction, 0.0, MAX_LAST_STAND);
        Checks.range("thorns", thorns, 0.0, MAX_THORNS);
        Checks.range("evasion", evasion, 0.0, MAX_EVASION);
        Checks.range("healOnKill", healOnKill, 0.0, MAX_HEAL_ON_KILL);
    }

    /**
     * Passive effects that are not skills (equipment perks, set bonuses), each counted once. Only the effects resolved
     * on the server belong here: equipment never changes cooldowns or stamina costs, which the client predicts.
     *
     * @throws IllegalArgumentException for {@link PassiveEffect.Cooldown} and {@link PassiveEffect.StaminaSaver}
     */
    public static PassiveTotals ofEffects(List<PassiveEffect> effects) {
        return ofEffects(effects, 1.0);
    }

    /**
     * Like {@link #ofEffects(List)}, every value multiplied by {@code scale} (a piece's rarity power: chances, shares
     * and bonuses grow, the durations and strengths of procs stay).
     */
    public static PassiveTotals ofEffects(List<PassiveEffect> effects, double scale) {
        Checks.range("scale", scale, 0.0, 10.0);
        for (PassiveEffect effect : effects) {
            if (effect instanceof PassiveEffect.Cooldown || effect instanceof PassiveEffect.StaminaSaver) {
                throw new IllegalArgumentException("equipment perks must not change cooldowns or stamina: " + effect);
            }
        }
        return sumEffects(effects, scale);
    }

    /** Both totals together (sums, capped like {@link #sum}); the procs of both apply. */
    public PassiveTotals plus(PassiveTotals other) {
        List<Proc> both = new ArrayList<>(procs);
        both.addAll(other.procs);
        return new PassiveTotals(modifiers.plus(other.modifiers), Math.min(10.0, skillPower + other.skillPower),
                Math.min(MAX_COOLDOWN_REDUCTION, cooldownReduction + other.cooldownReduction),
                Math.min(MAX_STAMINA_REDUCTION, staminaReduction + other.staminaReduction),
                Math.min(MAX_LIFESTEAL, lifesteal + other.lifesteal), both,
                Math.min(MAX_LAST_STAND, lastStandDamage + other.lastStandDamage),
                Math.min(MAX_LAST_STAND, lastStandReduction + other.lastStandReduction),
                Math.min(MAX_THORNS, thorns + other.thorns), Math.min(MAX_EVASION, evasion + other.evasion),
                Math.min(MAX_HEAL_ON_KILL, healOnKill + other.healOnKill));
    }

    private static PassiveTotals sumEffects(List<PassiveEffect> effects, double scale) {
        CombatModifiers modifiers = CombatModifiers.NONE;
        double power = 0;
        double lifesteal = 0;
        double lastDamage = 0;
        double lastReduction = 0;
        double thorns = 0;
        double evasion = 0;
        double healOnKill = 0;
        List<Proc> procs = new ArrayList<>();
        for (PassiveEffect effect : effects) {
            if (effect instanceof PassiveEffect.Stats stats) {
                modifiers = modifiers.plus(stats.perLevel().scaled(scale));
            } else if (effect instanceof PassiveEffect.SkillPower p) {
                power += p.perLevel() * scale;
            } else if (effect instanceof PassiveEffect.Lifesteal l) {
                lifesteal += l.perLevel() * scale;
            } else if (effect instanceof PassiveEffect.Proc proc) {
                procs.add(new Proc(proc.kind(), Math.min(1.0, proc.chancePerLevel() * scale), proc.durationTicks(),
                        proc.power(),
                        proc.meleeOnly()));
            } else if (effect instanceof PassiveEffect.LastStand last) {
                lastDamage += last.damagePerLevel() * scale;
                lastReduction += last.reductionPerLevel() * scale;
            } else if (effect instanceof PassiveEffect.Thorns t) {
                thorns += t.perLevel() * scale;
            } else if (effect instanceof PassiveEffect.Evasion e) {
                evasion += e.perLevel() * scale;
            } else if (effect instanceof PassiveEffect.HealOnKill h) {
                healOnKill += h.perLevel() * scale;
            }
        }
        return new PassiveTotals(modifiers, Math.min(10.0, power), 0.0, 0.0, Math.min(MAX_LIFESTEAL, lifesteal), procs,
                Math.min(MAX_LAST_STAND, lastDamage), Math.min(MAX_LAST_STAND, lastReduction),
                Math.min(MAX_THORNS, thorns), Math.min(MAX_EVASION, evasion), Math.min(MAX_HEAL_ON_KILL, healOnKill));
    }

    /** Sums the passives of {@code learned} skills at their levels (passive skill → level). */
    static PassiveTotals sum(List<SkillDefinition> skills, List<Integer> levels) {
        CombatModifiers modifiers = CombatModifiers.NONE;
        double power = 0;
        double cooldown = 0;
        double stamina = 0;
        double lifesteal = 0;
        double lastDamage = 0;
        double lastReduction = 0;
        double thorns = 0;
        double evasion = 0;
        double healOnKill = 0;
        List<Proc> procs = new ArrayList<>();
        for (int i = 0; i < skills.size(); i++) {
            int level = levels.get(i);
            if (level <= 0) {
                continue;
            }
            for (PassiveEffect effect : skills.get(i).passives()) {
                if (effect instanceof PassiveEffect.Stats stats) {
                    modifiers = modifiers.plus(stats.perLevel().scaled(level));
                } else if (effect instanceof PassiveEffect.SkillPower p) {
                    power += p.perLevel() * level;
                } else if (effect instanceof PassiveEffect.Cooldown c) {
                    cooldown += c.perLevel() * level;
                } else if (effect instanceof PassiveEffect.StaminaSaver s) {
                    stamina += s.perLevel() * level;
                } else if (effect instanceof PassiveEffect.Lifesteal l) {
                    lifesteal += l.perLevel() * level;
                } else if (effect instanceof PassiveEffect.Proc proc) {
                    procs.add(new Proc(proc.kind(), Math.min(1.0, proc.chancePerLevel() * level), proc.durationTicks(),
                            proc.power(), proc.meleeOnly()));
                } else if (effect instanceof PassiveEffect.LastStand last) {
                    lastDamage += last.damagePerLevel() * level;
                    lastReduction += last.reductionPerLevel() * level;
                } else if (effect instanceof PassiveEffect.Thorns t) {
                    thorns += t.perLevel() * level;
                } else if (effect instanceof PassiveEffect.Evasion e) {
                    evasion += e.perLevel() * level;
                } else if (effect instanceof PassiveEffect.HealOnKill h) {
                    healOnKill += h.perLevel() * level;
                }
            }
        }
        return new PassiveTotals(modifiers, Math.min(10.0, power), Math.min(MAX_COOLDOWN_REDUCTION, cooldown),
                Math.min(MAX_STAMINA_REDUCTION, stamina), Math.min(MAX_LIFESTEAL, lifesteal), procs,
                Math.min(MAX_LAST_STAND, lastDamage), Math.min(MAX_LAST_STAND, lastReduction),
                Math.min(MAX_THORNS, thorns), Math.min(MAX_EVASION, evasion), Math.min(MAX_HEAL_ON_KILL, healOnKill));
    }
}
