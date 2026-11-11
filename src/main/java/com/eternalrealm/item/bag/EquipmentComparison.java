package com.eternalrealm.item.bag;

import com.eternalrealm.character.StatType;
import com.eternalrealm.combat.CombatModifiers;
import com.eternalrealm.core.ModInfo;
import com.eternalrealm.item.EquipmentData;
import com.eternalrealm.item.EquipmentStats;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The comparison the equipment screen shows: a candidate from the bag against what is equipped in the same slot, row
 * by row (attack or defense and health, the six stats, critical chance and damage, attack and move speed). Rows that
 * are zero on both sides are left out.
 */
public final class EquipmentComparison {
    private static final double EPS = 1.0E-9;

    /** What a row measures. */
    public enum Stat {
        ATTACK(false),
        DEFENSE(false),
        MAX_HEALTH(false),
        STR(false),
        VIT(false),
        AGI(false),
        INT(false),
        DEX(false),
        LUK(false),
        CRIT_CHANCE(true),
        CRIT_DAMAGE(true),
        ATTACK_SPEED(true),
        MOVE_SPEED(true);

        private final boolean percent;

        Stat(boolean percent) {
            this.percent = percent;
        }

        /** Shown as a percentage (the values are fractions). */
        public boolean percent() {
            return percent;
        }

        public String translationKey() {
            return "screen." + ModInfo.MOD_ID + ".equipment.stat." + name().toLowerCase(Locale.ROOT);
        }
    }

    /** One row: the equipped value and the candidate's. */
    public record Delta(Stat stat, double before, double after) {
        public double change() {
            return after - before;
        }

        /** +1 better, -1 worse, 0 the same. */
        public int direction() {
            double change = change();
            return Math.abs(change) < EPS ? 0 : change > 0 ? 1 : -1;
        }
    }

    private EquipmentComparison() {
    }

    /** @param current what is equipped in the candidate's slot, {@code null} for nothing */
    public static List<Delta> compare(EquipmentData current, EquipmentData candidate) {
        double[] before = values(current);
        double[] after = values(candidate);
        List<Delta> rows = new ArrayList<>();
        for (Stat stat : Stat.values()) {
            double a = before[stat.ordinal()];
            double b = after[stat.ordinal()];
            if (Math.abs(a) > EPS || Math.abs(b) > EPS) {
                rows.add(new Delta(stat, a, b));
            }
        }
        return rows;
    }

    /** The values of one piece ({@code null}: all zero), indexed by {@link Stat#ordinal()}. */
    public static double[] values(EquipmentData data) {
        double[] out = new double[Stat.values().length];
        if (data == null) {
            return out;
        }
        EquipmentStats stats = EquipmentStats.of(data);
        CombatModifiers modifiers = stats.modifiers();
        out[Stat.ATTACK.ordinal()] = EquipmentStats.baseAttack(data) + stats.bonusAttack();
        // the modifiers already hold the piece's base defense and max health (EquipmentStats#of)
        out[Stat.DEFENSE.ordinal()] = modifiers.defenseFlat();
        out[Stat.MAX_HEALTH.ordinal()] = modifiers.maxHealthFlat();
        out[Stat.STR.ordinal()] = stats.stats().get(StatType.STR);
        out[Stat.VIT.ordinal()] = stats.stats().get(StatType.VIT);
        out[Stat.AGI.ordinal()] = stats.stats().get(StatType.AGI);
        out[Stat.INT.ordinal()] = stats.stats().get(StatType.INT);
        out[Stat.DEX.ordinal()] = stats.stats().get(StatType.DEX);
        out[Stat.LUK.ordinal()] = stats.stats().get(StatType.LUK);
        out[Stat.CRIT_CHANCE.ordinal()] = modifiers.critChance();
        out[Stat.CRIT_DAMAGE.ordinal()] = modifiers.critDamage();
        out[Stat.ATTACK_SPEED.ordinal()] = modifiers.attackSpeed();
        out[Stat.MOVE_SPEED.ordinal()] = modifiers.moveSpeed();
        return out;
    }

    /** The sum over several pieces (the equipment screen's totals). */
    public static double[] total(Iterable<EquipmentData> pieces) {
        double[] out = new double[Stat.values().length];
        for (EquipmentData piece : pieces) {
            double[] values = values(piece);
            for (int i = 0; i < out.length; i++) {
                out[i] += values[i];
            }
        }
        return out;
    }
}
