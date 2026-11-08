package com.eternalrealm.item;

import com.eternalrealm.core.ModInfo;

import java.util.Objects;
import java.util.Optional;

/**
 * Equipment tiers, one per area (tier N drops and is crafted in area N). {@link #id()} is the stable persistence key:
 * never rename it.
 *
 * <p>Balance anchors (docs/modules/item.md): an {@link #IRON} weapon adds nothing to the weapon's base attack, so the
 * Phase 2 starter weapons are exactly tier 1; a full {@link #ABYSS} set of {@link ItemRarity#RARE} gear roughly doubles
 * a level-50 character's damage and adds about 215 defense and 90 max health. The weapon bonus is a fraction of the
 * weapon type's own base attack (instead of a flat number) so all six weapon families keep their relative balance.</p>
 *
 * @param number            1..7, also the area number
 * @param requiredLevel     character level needed to equip the tier
 * @param weaponAttackBonus weapon attack added on top of the weapon type's base attack, as a fraction of it
 * @param setDefense        defense of a full four-piece armor set (split by {@link EquipmentSlot#armorShare()})
 * @param setMaxHealth      max health of a full four-piece armor set (split the same way)
 * @param armorMaterial     vanilla armor material whose textures the platform layer uses for the pieces
 * @param glint             whether the pieces render with the enchantment glint
 */
public enum ItemTier {
    /** 鉄: アルセリア王国 (Lv1-8). */
    IRON(1, "iron", 1, 0.00, 12.0, 5.0, "leather", false),
    /** 鋼: エルダーフォレスト (Lv8-15). */
    STEEL(2, "steel", 8, 0.10, 30.0, 12.0, "chainmail", false),
    /** 砂岩: サンドリア砂漠 (Lv15-22). */
    SANDSTONE(3, "sandstone", 15, 0.20, 52.0, 21.0, "iron", false),
    /** 氷晶: ノルディア雪原 (Lv22-30). */
    FROST(4, "frost", 22, 0.30, 78.0, 30.0, "golden", false),
    /** 炎竜: ドラゴニア火山 (Lv30-37). */
    FLAME(5, "flame", 30, 0.40, 106.0, 40.0, "diamond", false),
    /** 天空: セレスティア天空大陸 (Lv37-44). */
    CELESTIAL(6, "celestial", 37, 0.50, 136.0, 52.0, "netherite", false),
    /** 終焉: 終焉の塔 (Lv44-50). */
    ABYSS(7, "abyss", 44, 0.60, 168.0, 64.0, "netherite", true);

    public static final int COUNT = 7;

    private final int number;
    private final String id;
    private final int requiredLevel;
    private final double weaponAttackBonus;
    private final double setDefense;
    private final double setMaxHealth;
    private final String armorMaterial;
    private final boolean glint;

    ItemTier(int number, String id, int requiredLevel, double weaponAttackBonus, double setDefense,
             double setMaxHealth, String armorMaterial, boolean glint) {
        this.number = number;
        this.id = id;
        this.requiredLevel = requiredLevel;
        this.weaponAttackBonus = weaponAttackBonus;
        this.setDefense = setDefense;
        this.setMaxHealth = setMaxHealth;
        this.armorMaterial = armorMaterial;
        this.glint = glint;
    }

    public int number() {
        return number;
    }

    /** The area this tier belongs to (equal to {@link #number()}). */
    public int area() {
        return number;
    }

    public String id() {
        return id;
    }

    /** Name prefix of the tier ("鉄", "終焉", ...), composed with the item noun by {@link EquipmentTooltip#name}. */
    public String translationKey() {
        return "item_tier." + ModInfo.MOD_ID + "." + id;
    }

    public int requiredLevel() {
        return requiredLevel;
    }

    public double weaponAttackBonus() {
        return weaponAttackBonus;
    }

    public double setDefense() {
        return setDefense;
    }

    public double setMaxHealth() {
        return setMaxHealth;
    }

    /** Base defense of one armor piece (before rarity and enhancement); 0 for {@link EquipmentSlot#WEAPON}. */
    public double armorDefense(EquipmentSlot slot) {
        return setDefense * Objects.requireNonNull(slot, "slot").armorShare();
    }

    /** Base max health of one armor piece (before rarity and enhancement); 0 for {@link EquipmentSlot#WEAPON}. */
    public double armorMaxHealth(EquipmentSlot slot) {
        return setMaxHealth * Objects.requireNonNull(slot, "slot").armorShare();
    }

    /** Vanilla armor material id ("leather", "chainmail", "iron", "golden", "diamond", "netherite"). */
    public String armorMaterial() {
        return armorMaterial;
    }

    public boolean glint() {
        return glint;
    }

    /** The tier after this one, if any. */
    public Optional<ItemTier> next() {
        return byNumber(number + 1);
    }

    public static Optional<ItemTier> byNumber(int number) {
        for (ItemTier tier : values()) {
            if (tier.number == number) {
                return Optional.of(tier);
            }
        }
        return Optional.empty();
    }

    public static Optional<ItemTier> byId(String id) {
        for (ItemTier tier : values()) {
            if (tier.id.equals(id)) {
                return Optional.of(tier);
            }
        }
        return Optional.empty();
    }

    /** The highest tier a character of {@code level} may equip (at least {@link #IRON}). */
    public static ItemTier highestFor(int level) {
        ItemTier best = IRON;
        for (ItemTier tier : values()) {
            if (tier.requiredLevel <= level) {
                best = tier;
            }
        }
        return best;
    }
}
