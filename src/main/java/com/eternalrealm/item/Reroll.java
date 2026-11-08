package com.eternalrealm.item;

import com.eternalrealm.core.ModInfo;

import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.random.RandomGenerator;

/**
 * 厳選: rolls all affixes of a piece again (kinds and values, from the same pool and tier ranges as a new drop). Slot,
 * weapon type, tier, rarity, enhancement, unique id and binding stay, so rerolling never changes what the piece is or
 * who owns it, only how good its affixes are.
 *
 * <pre>
 * cost = 1 reroll crystal per affix + gold per affix of the tier (20, 50, 100, 180, 280, 400, 560)
 * </pre>
 *
 * Pieces without affixes (COMMON) cannot be rerolled.
 */
public final class Reroll {
    /** Message after a successful reroll. */
    public static final String DONE_KEY = "message." + ModInfo.MOD_ID + ".reroll.done";
    /** Index = tier number − 1. */
    private static final long[] GOLD_PER_AFFIX = {20, 50, 100, 180, 280, 400, 560};

    public enum Check {
        OK,
        NO_AFFIXES,
        MISSING_CRYSTALS,
        NOT_ENOUGH_GOLD;

        public String translationKey() {
            return "message." + ModInfo.MOD_ID + ".reroll." + name().toLowerCase(Locale.ROOT);
        }
    }

    private Reroll() {
    }

    public static boolean canReroll(EquipmentData data) {
        return !data.affixes().isEmpty();
    }

    public static long goldPerAffix(ItemTier tier) {
        return GOLD_PER_AFFIX[Objects.requireNonNull(tier, "tier").number() - 1];
    }

    /**
     * Cost of one reroll.
     *
     * @throws IllegalArgumentException if the piece has no affixes
     */
    public static Cost cost(EquipmentData data) {
        Objects.requireNonNull(data, "data");
        int affixes = data.affixes().size();
        if (affixes == 0) {
            throw new IllegalArgumentException("A " + data.rarity().id() + " piece has no affixes to reroll");
        }
        return Cost.of(goldPerAffix(data.tier()) * affixes, Materials.REROLL_CRYSTAL, affixes);
    }

    /** Whether a reroll may start; checked in the order affixes, crystals, gold. */
    public static Check check(EquipmentData data, Map<String, Integer> inventory, long gold) {
        Objects.requireNonNull(data, "data");
        Objects.requireNonNull(inventory, "inventory");
        Cost.checkGold(gold);
        if (!canReroll(data)) {
            return Check.NO_AFFIXES;
        }
        Cost cost = cost(data);
        if (!cost.missing(inventory).isEmpty()) {
            return Check.MISSING_CRYSTALS;
        }
        return gold < cost.gold() ? Check.NOT_ENOUGH_GOLD : Check.OK;
    }

    /**
     * The piece with freshly rolled affixes (the caller has already removed the cost). Draws like a new drop:
     * {@code 2 × affix count} values.
     *
     * @throws IllegalArgumentException if the piece has no affixes
     */
    public static EquipmentData reroll(EquipmentData data, RandomGenerator random) {
        Objects.requireNonNull(data, "data");
        Objects.requireNonNull(random, "random");
        if (!canReroll(data)) {
            throw new IllegalArgumentException("A " + data.rarity().id() + " piece has no affixes to reroll");
        }
        AffixPool pool = AffixPool.of(data.slot(), data.weaponType());
        return data.withAffixes(pool.roll(data.tier(), data.rarity().affixCount(), random));
    }
}
