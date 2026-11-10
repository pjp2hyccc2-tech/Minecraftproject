package com.eternalrealm.item;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * One piece of an {@link ArmorSet}: the design of a head, chest, legs or feet piece. Its id is the set id with the slot
 * ({@code meadowrunner_head}).
 */
public record ArmorPiece(ArmorSet set, EquipmentSlot armorSlot) implements Design {
    public ArmorPiece {
        Objects.requireNonNull(set, "set");
        if (!Objects.requireNonNull(armorSlot, "armorSlot").isArmor()) {
            throw new IllegalArgumentException("Not an armor slot: " + armorSlot);
        }
    }

    @Override
    public String id() {
        return set.id() + "_" + armorSlot.id();
    }

    @Override
    public int model() {
        return set.model() + EquipmentSlot.armor().indexOf(armorSlot);
    }

    @Override
    public EquipmentBase base() {
        return EquipmentBase.armor(armorSlot, set.tier());
    }

    @Override
    public int field() {
        return set.field();
    }

    @Override
    public int level() {
        return set.level();
    }

    @Override
    public Set<DesignSource> sources() {
        return set.sources();
    }

    @Override
    public String origin() {
        return set.origin();
    }

    /** The lore of the whole set. */
    @Override
    public String loreKey() {
        return set.loreKey();
    }

    /** Affix tendencies of the style, as far as the slot's pool allows them. */
    @Override
    public Map<AffixKind, Integer> bias() {
        AffixPool pool = AffixPool.of(armorSlot, null);
        Map<AffixKind, Integer> bias = new EnumMap<>(AffixKind.class);
        List<AffixKind> wanted = switch (set.style()) {
            case HEAVY -> List.of(AffixKind.VIT, AffixKind.DEFENSE, AffixKind.MAX_HEALTH, AffixKind.STR);
            case LIGHT -> List.of(AffixKind.AGI, AffixKind.DEX, AffixKind.CRIT_CHANCE, AffixKind.MOVE_SPEED);
            case ROBE -> List.of(AffixKind.INT, AffixKind.HEALING_PERCENT, AffixKind.MAX_HEALTH);
        };
        for (AffixKind kind : wanted) {
            if (pool.allows(kind)) {
                bias.put(kind, 2);
            }
        }
        return Map.copyOf(bias);
    }
}
