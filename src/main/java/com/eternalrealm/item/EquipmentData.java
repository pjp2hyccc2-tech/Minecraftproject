package com.eternalrealm.item;

import com.eternalrealm.combat.WeaponType;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * One concrete equipment piece, as stored in the item stack ({@link EquipmentCodec}). Immutable: enhancing, rerolling
 * or binding returns a new instance, which the platform layer writes back to the stack.
 *
 * <p>The constructor enforces every invariant, so an instance can always be equipped, priced and shown: the weapon
 * type matches the slot, the affix count equals the rarity's, affix kinds are distinct and allowed on the piece
 * ({@link AffixPool}), and no value exceeds what the tier can roll (with {@link AffixKind#STORED_VALUE_TOLERANCE}).
 * Affixes are kept sorted by kind, so two pieces with the same affixes are equal whatever the roll order was.</p>
 *
 * @param weaponType  required for {@link EquipmentSlot#WEAPON}, {@code null} for armor and trinkets
 * @param enhancement 強化 level, 0..{@link #MAX_ENHANCEMENT}
 * @param uniqueId    identity of this physical piece for duplicate detection in trades and the market, or
 *                    {@code null} until the platform layer stamps one ({@link #ensureUniqueId})
 * @param boundTo     the only player who may use and own it (quest and boss rewards), or {@code null} if tradeable
 * @param design      the named design of the piece ({@link Armory}), or {@code null} for a plain piece of its tier; a
 *                    design is always made on its own base (slot, weapon type and tier)
 */
public record EquipmentData(EquipmentSlot slot, WeaponType weaponType, ItemTier tier, ItemRarity rarity,
                            int enhancement, List<Affix> affixes, UUID uniqueId, UUID boundTo, Design design) {
    public static final int MAX_ENHANCEMENT = 10;

    /** A plain piece (no named design): the shape of every piece before the armory existed. */
    public EquipmentData(EquipmentSlot slot, WeaponType weaponType, ItemTier tier, ItemRarity rarity, int enhancement,
                         List<Affix> affixes, UUID uniqueId, UUID boundTo) {
        this(slot, weaponType, tier, rarity, enhancement, affixes, uniqueId, boundTo, null);
    }

    public EquipmentData {
        Objects.requireNonNull(slot, "slot");
        Objects.requireNonNull(tier, "tier");
        Objects.requireNonNull(rarity, "rarity");
        if ((slot == EquipmentSlot.WEAPON) != (weaponType != null)) {
            throw new IllegalArgumentException("A weapon type is required for weapons and forbidden for armor: "
                    + slot + " / " + weaponType);
        }
        Checks.range("enhancement", enhancement, 0, MAX_ENHANCEMENT);
        List<Affix> sorted = new ArrayList<>(Objects.requireNonNull(affixes, "affixes"));
        for (Affix affix : sorted) {
            Objects.requireNonNull(affix, "affix");
        }
        sorted.sort(Comparator.comparing(Affix::kind));
        if (sorted.size() != rarity.affixCount()) {
            throw new IllegalArgumentException(rarity + " needs " + rarity.affixCount() + " affixes, got "
                    + sorted.size());
        }
        AffixPool pool = AffixPool.of(slot, weaponType);
        Set<AffixKind> seen = EnumSet.noneOf(AffixKind.class);
        for (Affix affix : sorted) {
            if (!seen.add(affix.kind())) {
                throw new IllegalArgumentException("Duplicate affix kind " + affix.kind());
            }
            if (!pool.allows(affix.kind())) {
                throw new IllegalArgumentException(affix.kind() + " cannot appear on " + describe(slot, weaponType));
            }
            if (affix.value() > affix.kind().maxStored(tier)) {
                throw new IllegalArgumentException(affix.kind() + " value " + affix.value() + " exceeds what tier "
                        + tier.id() + " can have (" + affix.kind().maxStored(tier) + ")");
            }
        }
        affixes = List.copyOf(sorted);
        if (design != null && !design.base().equals(new EquipmentBase(slot, weaponType, tier))) {
            throw new IllegalArgumentException("Design " + design.id() + " is made on " + design.base().itemPath()
                    + ", not on " + tier.id() + " " + describe(slot, weaponType));
        }
    }

    /** A fresh piece: not enhanced, no unique id yet, not bound. */
    public static EquipmentData of(EquipmentBase base, ItemRarity rarity, List<Affix> affixes) {
        Objects.requireNonNull(base, "base");
        return new EquipmentData(base.slot(), base.weaponType(), base.tier(), rarity, 0, affixes, null, null);
    }

    /** A fresh piece of a named design (on the design's base). */
    public static EquipmentData of(Design design, ItemRarity rarity, List<Affix> affixes) {
        EquipmentBase base = Objects.requireNonNull(design, "design").base();
        return new EquipmentData(base.slot(), base.weaponType(), base.tier(), rarity, 0, affixes, null, null, design);
    }

    public EquipmentBase base() {
        return new EquipmentBase(slot, weaponType, tier);
    }

    public boolean isWeapon() {
        return slot == EquipmentSlot.WEAPON;
    }

    /**
     * Character level needed to equip the piece: the design's own level for a named piece, otherwise the tier's, plus
     * the rarity's {@link ItemRarity#levelOffset()} (at most {@link Design#MAX_LEVEL}). A relic asks for exactly its
     * own level.
     */
    public int requiredLevel() {
        if (design != null && design.isRelic()) {
            return design.level();
        }
        int base = design != null ? Math.max(design.level(), tier.requiredLevel()) : tier.requiredLevel();
        return Math.min(Design.MAX_LEVEL, base + rarity.levelOffset());
    }

    public boolean hasDesign() {
        return design != null;
    }

    /** A relic (金枠): its frame is gold everywhere it is shown. */
    public boolean isRelic() {
        return design != null && design.isRelic();
    }

    public boolean isTrinket() {
        return slot.isTrinket();
    }

    /** The named trinket, if this is one. */
    public Optional<TrinketDesign> trinketDesign() {
        return design instanceof TrinketDesign trinket ? Optional.of(trinket) : Optional.empty();
    }

    /** The named weapon design, if this is one. */
    public Optional<WeaponDesign> weaponDesign() {
        return design instanceof WeaponDesign weapon ? Optional.of(weapon) : Optional.empty();
    }

    /** The armor piece design, if this is one. */
    public Optional<ArmorPiece> armorPiece() {
        return design instanceof ArmorPiece piece ? Optional.of(piece) : Optional.empty();
    }

    public Optional<Affix> affix(AffixKind kind) {
        for (Affix affix : affixes) {
            if (affix.kind() == kind) {
                return Optional.of(affix);
            }
        }
        return Optional.empty();
    }

    public boolean isBound() {
        return boundTo != null;
    }

    /** Whether the piece may change owners (trade, market, guild storage). Bound pieces never may. */
    public boolean isTradeable() {
        return boundTo == null;
    }

    /**
     * Whether {@code player} at {@code level} may equip the piece (and have it count in {@link Gear#sum}): the level
     * requirement is met and the piece is not bound to someone else.
     */
    public boolean canBeUsedBy(int level, UUID player) {
        return level >= requiredLevel() && (boundTo == null || boundTo.equals(player));
    }

    public EquipmentData withEnhancement(int level) {
        return new EquipmentData(slot, weaponType, tier, rarity, level, affixes, uniqueId, boundTo, design);
    }

    /** Same piece with other affixes (a reroll); everything else, the unique id included, stays. */
    public EquipmentData withAffixes(List<Affix> newAffixes) {
        return new EquipmentData(slot, weaponType, tier, rarity, enhancement, newAffixes, uniqueId, boundTo, design);
    }

    public EquipmentData withUniqueId(UUID id) {
        return new EquipmentData(slot, weaponType, tier, rarity, enhancement, affixes, id, boundTo, design);
    }

    /**
     * This piece if it already has a unique id, otherwise a copy with {@code idSource.get()}. The platform layer calls
     * this with {@code UUID::randomUUID} whenever a piece enters the world (drop, craft, reward). The id must not come
     * from the loot generator: seeded loot would hand out equal ids and look like duplicates.
     */
    public EquipmentData ensureUniqueId(Supplier<UUID> idSource) {
        if (uniqueId != null) {
            return this;
        }
        return withUniqueId(Objects.requireNonNull(idSource.get(), "generated unique id"));
    }

    /** Binds the piece to {@code player} (quest and boss rewards); {@code null} unbinds it (admin tools only). */
    public EquipmentData withBoundTo(UUID player) {
        return new EquipmentData(slot, weaponType, tier, rarity, enhancement, affixes, uniqueId, player, design);
    }

    private static String describe(EquipmentSlot slot, WeaponType weaponType) {
        return weaponType == null ? slot.id() : slot.id() + "/" + weaponType.id();
    }
}
