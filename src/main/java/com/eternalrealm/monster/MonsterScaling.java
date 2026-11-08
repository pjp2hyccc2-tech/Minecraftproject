package com.eternalrealm.monster;

import com.eternalrealm.character.CharacterRules;

import java.util.Objects;

/**
 * Level scaling of monster stats (docs/modules/world.md, "モンスターの強さ").
 *
 * <pre>
 * health(L) = baseHealth + healthPerLevel × (L − 1)        (capped at 1024, the vanilla attribute limit)
 * damage(L) = baseDamage + damagePerLevel × (L − 1)
 * armor(L)  = baseArmor  + armorPerLevel  × (L − 1)        (capped at 30, the vanilla attribute limit)
 * speed     = speedMultiplier                               (no growth: high-level monsters stay dodgeable)
 * exp(L)    = round(baseExp × L^1.2)
 * gold(L)   = round(baseGold × (1 + 0.1 × (L − 1)))
 * </pre>
 *
 * <p>Tuning target: the "reference light hit" of a level-matched player grows linearly from 6.6 (level 1, one-handed
 * sword, all stats 5) to 42 (level 50, high STR and good gear; the expected range there is 30-60). The catalog's NORMAL
 * monsters are tuned to about 4 reference hits at every level, ELITEs to about 12, MINIONs to about 2 (tests check
 * 3-6, 9-18 and 1-4). EXP grows with {@code L^1.2} while the default curve needs {@code 100 × L^1.5} per level, so a
 * level takes about 10 standard kills at level 1 and about 30 at level 50: early levels are quick and later levels lean
 * on quests, dungeons and bosses.</p>
 */
public final class MonsterScaling {
    public static final int MAX_LEVEL = CharacterRules.MAX_LEVEL;
    /** Vanilla's MAX_HEALTH attribute limit. */
    public static final double MAX_HEALTH = 1024.0;
    /** Vanilla's ARMOR attribute limit. */
    public static final double MAX_ARMOR = 30.0;
    public static final double EXP_LEVEL_EXPONENT = 1.2;
    public static final double GOLD_GROWTH_PER_LEVEL = 0.1;
    /** One-handed sword, first combo step, all stats 5: {@code 6 × (1 + 5 × 0.02)}. */
    public static final double REFERENCE_HIT_LEVEL_1 = 6.6;
    /** Level 50 with high STR and good gear (expected range 30-60). */
    public static final double REFERENCE_HIT_LEVEL_50 = 42.0;

    private MonsterScaling() {
    }

    public static MonsterStats stats(MonsterTemplate template, int level) {
        Objects.requireNonNull(template, "template");
        Check.range("level", level, 1, MAX_LEVEL);
        int steps = level - 1;
        double health = Math.min(MAX_HEALTH, template.baseHealth() + template.healthPerLevel() * steps);
        double damage = template.baseDamage() + template.damagePerLevel() * steps;
        double armor = Math.min(MAX_ARMOR, template.baseArmor() + template.armorPerLevel() * steps);
        long exp = Math.round(template.baseExp() * StrictMath.pow(level, EXP_LEVEL_EXPONENT));
        double goldScale = 1.0 + GOLD_GROWTH_PER_LEVEL * steps;
        long goldMin = Math.round(template.baseGoldMin() * goldScale);
        long goldMax = Math.round(template.baseGoldMax() * goldScale);
        return new MonsterStats(health, damage, armor, template.speedMultiplier(), exp, goldMin, goldMax);
    }

    /** Light-attack damage of a level-matched player, the yardstick of the tuning (linear in level). */
    public static double referenceLightHit(int level) {
        Check.range("level", level, 1, MAX_LEVEL);
        return REFERENCE_HIT_LEVEL_1 + (REFERENCE_HIT_LEVEL_50 - REFERENCE_HIT_LEVEL_1) * (level - 1) / 49.0;
    }

    /**
     * Vanilla 1.20.1 armor absorption ({@code CombatRules.getDamageAfterAbsorb}, toughness 0):
     * {@code damage × (1 − clamp(armor − damage / 2, armor / 5, 20) / 25)}.
     */
    public static double damageAfterArmor(double damage, double armor) {
        Check.range("damage", damage, 0.0, 1_000_000.0);
        Check.range("armor", armor, 0.0, MAX_ARMOR);
        double absorbed = Math.max(armor * 0.2, Math.min(20.0, armor - damage / 2.0));
        return damage * (1.0 - absorbed / 25.0);
    }

    /** Hits of {@code hitDamage} (before armor) needed to kill a monster with these stats. */
    public static int hitsToKill(MonsterStats stats, double hitDamage) {
        Objects.requireNonNull(stats, "stats");
        Check.range("hitDamage", hitDamage, 0.01, 1_000_000.0);
        return (int) Math.ceil(stats.health() / damageAfterArmor(hitDamage, stats.armor()));
    }
}
