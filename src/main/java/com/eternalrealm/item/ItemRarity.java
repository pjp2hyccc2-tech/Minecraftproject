package com.eternalrealm.item;

import com.eternalrealm.core.ModInfo;

import java.util.Locale;
import java.util.Optional;

/**
 * Rarity of an equipment piece. {@link #id()} is the stable persistence key: never rename it, and keep the constant
 * names in sync with the loot module's rarity enum ({@link #fromName(String)} maps between them by name).
 *
 * <p>Rarity is mostly about the number of random affixes; the small {@link #baseMultiplier()} makes a rarer piece of
 * the same tier slightly stronger even before its affixes, so a LEGENDARY is always an upgrade over a COMMON of its
 * tier. The drop weights are the luck-free distribution of {@link EquipmentGenerator#rollRarity}.</p>
 *
 * <p>Rarity also raises the piece's special performance ({@link #power()}, owner's request of 2026-11-11): the values
 * of its affixes, a named weapon's element chance, fixed bonuses and perks, an armor set's or a relic's fixed bonuses,
 * and the damage and effect strength of the weapon's art (the Z key). A rarer piece also asks for a few more levels
 * ({@link #levelOffset()}).</p>
 *
 * <pre>
 * rarity      affixes  base   power  level  drop weight
 * COMMON      0        ×1.00  ×1.00  +0     60
 * UNCOMMON    1        ×1.05  ×1.07  +0     25
 * RARE        2        ×1.10  ×1.14  +1     10
 * EPIC        3        ×1.15  ×1.22  +3      4
 * LEGENDARY   4        ×1.20  ×1.30  +5      1
 * </pre>
 *
 * @param affixCount      number of random affixes (0..4)
 * @param baseMultiplier  multiplies the piece's base attack / defense / max health
 * @param power           multiplies the special performance (affix values, element chance, fixed bonuses, perks)
 *                        and the weapon art's damage and effect strength
 * @param levelOffset     levels added to the piece's required level
 * @param priceMultiplier multiplies the NPC sell value ({@link ItemPrices})
 * @param color           RGB colour of the name and rarity line (white, green, blue, purple, gold)
 * @param dropWeight      relative weight without luck (60/25/10/4/1)
 */
public enum ItemRarity {
    COMMON("common", 0, 1.00, 1.00, 0, 1.0, 0xFFFFFF, 60),
    UNCOMMON("uncommon", 1, 1.05, 1.07, 0, 1.5, 0x55FF55, 25),
    RARE("rare", 2, 1.10, 1.14, 1, 2.5, 0x5599FF, 10),
    EPIC("epic", 3, 1.15, 1.22, 3, 4.0, 0xBB55FF, 4),
    LEGENDARY("legendary", 4, 1.20, 1.30, 5, 7.0, 0xFFAA00, 1);

    /** The highest {@link #affixCount()} of any rarity. */
    public static final int MAX_AFFIXES = 4;

    private final String id;
    private final int affixCount;
    private final double baseMultiplier;
    private final double power;
    private final int levelOffset;
    private final double priceMultiplier;
    private final int color;
    private final int dropWeight;

    ItemRarity(String id, int affixCount, double baseMultiplier, double power, int levelOffset, double priceMultiplier,
               int color, int dropWeight) {
        this.id = id;
        this.affixCount = affixCount;
        this.baseMultiplier = baseMultiplier;
        this.power = power;
        this.levelOffset = levelOffset;
        this.priceMultiplier = priceMultiplier;
        this.color = color;
        this.dropWeight = dropWeight;
    }

    public String id() {
        return id;
    }

    public String translationKey() {
        return "rarity." + ModInfo.MOD_ID + "." + id;
    }

    public int affixCount() {
        return affixCount;
    }

    public double baseMultiplier() {
        return baseMultiplier;
    }

    /**
     * Multiplies the piece's special performance: affix values, a named weapon's element chance, fixed bonuses and
     * perks, a set's and a relic's bonuses, and the damage and effect strength of the weapon's art.
     */
    public double power() {
        return power;
    }

    /** Levels added to the piece's required level (relics have their own level and ignore it). */
    public int levelOffset() {
        return levelOffset;
    }

    public double priceMultiplier() {
        return priceMultiplier;
    }

    /** RGB without alpha ({@code 0xRRGGBB}), usable with Minecraft's {@code TextColor.fromRgb}. */
    public int color() {
        return color;
    }

    public int dropWeight() {
        return dropWeight;
    }

    /** Position in the rarity order: 0 for COMMON up to 4 for LEGENDARY. */
    public int rank() {
        return ordinal();
    }

    public static Optional<ItemRarity> byId(String id) {
        for (ItemRarity rarity : values()) {
            if (rarity.id.equals(id)) {
                return Optional.of(rarity);
            }
        }
        return Optional.empty();
    }

    /**
     * Maps another module's rarity by its constant name ({@code ItemRarity.fromName(lootRarity.name())}). The match
     * ignores case, so the stored ids work too; anything else (e.g. a rarity this module does not know) is empty.
     */
    public static Optional<ItemRarity> fromName(String name) {
        if (name == null) {
            return Optional.empty();
        }
        String upper = name.toUpperCase(Locale.ROOT);
        for (ItemRarity rarity : values()) {
            if (rarity.name().equals(upper)) {
                return Optional.of(rarity);
            }
        }
        return Optional.empty();
    }
}
