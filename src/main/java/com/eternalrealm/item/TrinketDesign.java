package com.eternalrealm.item;

import com.eternalrealm.character.StatPercents;
import com.eternalrealm.character.StatType;
import com.eternalrealm.combat.CombatModifiers;
import com.eternalrealm.job.PassiveEffect;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * A named trinket (装身具): an accessory (ring, amulet, earring, bracelet) or a charm (talisman, bell, ward stone,
 * pouch). Trinkets have no defense; they raise the primary stats by percentages (STR +12 %, applied after every flat
 * source, see {@link StatPercents}) and add secondary bonuses and perks, all multiplied by the piece's rarity
 * ({@link ItemRarity#power()}) and enhancement. The relics ({@link Relics}) and the smithy's catalogue
 * ({@link Trinkets}) are trinkets.
 *
 * <pre>
 * stat percents   = {@link #percents}  × rarity power × percent factor (1 + 0.10 × enhancement)
 * modifiers       = {@link #modifiers} × rarity power × enhancement factor (1 + 0.06 × enhancement)
 * perks           = {@link #perks}     × rarity power (resolved by the server when hits land)
 * </pre>
 *
 * @param percents  the primary stat percentages it adds
 * @param modifiers the secondary bonuses it adds
 * @param perks     passive effects while worn (never cooldowns or stamina)
 */
public record TrinketDesign(String id, int model, TrinketKind kind, int field, int level, Set<DesignSource> sources,
                            String origin, StatPercents percents, CombatModifiers modifiers, List<PassiveEffect> perks,
                            Map<AffixKind, Integer> bias) implements Design {
    public TrinketDesign {
        Checks.id("id", id);
        Checks.range("model", model, Design.TRINKET_MODELS, 9999);
        Objects.requireNonNull(kind, "kind");
        Checks.range("field", field, 1, 10);
        Checks.range("level", level, 1, Design.MAX_LEVEL);
        sources = Set.copyOf(sources);
        if (sources.isEmpty()) {
            throw new IllegalArgumentException(id + ": a design needs a source");
        }
        Objects.requireNonNull(origin, "origin");
        if (!origin.isEmpty()) {
            Checks.id("origin", origin);
        }
        Objects.requireNonNull(percents, "percents");
        Objects.requireNonNull(modifiers, "modifiers");
        perks = List.copyOf(perks);
        for (PassiveEffect perk : perks) {
            if (perk instanceof PassiveEffect.Cooldown || perk instanceof PassiveEffect.StaminaSaver) {
                throw new IllegalArgumentException(id + ": trinket perks never change cooldowns or stamina");
            }
        }
        bias = WeaponDesign.checkedBias(id, bias, AffixPool.of(kind.slot(), null));
    }

    public EquipmentSlot slot() {
        return kind.slot();
    }

    /** The tier of the design's level: the base item it is made on. */
    public ItemTier tier() {
        return ItemTier.highestFor(level);
    }

    @Override
    public EquipmentBase base() {
        return EquipmentBase.trinket(kind.slot(), tier());
    }

    /** The affix tendencies of a trinket: the percentages of its own primary stats. */
    static Map<AffixKind, Integer> statBias(StatPercents percents, EquipmentSlot slot) {
        AffixPool pool = AffixPool.of(slot, null);
        Map<AffixKind, Integer> bias = new EnumMap<>(AffixKind.class);
        for (StatType stat : StatType.values()) {
            AffixKind kind = AffixKind.percentOf(stat);
            if (percents.get(stat) > 0.0 && pool.rolls(kind)) {
                bias.put(kind, 2);
            }
        }
        return Map.copyOf(bias);
    }
}
