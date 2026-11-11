package com.eternalrealm.item;

import com.eternalrealm.combat.CombatModifiers;
import com.eternalrealm.core.ModInfo;
import com.eternalrealm.job.PassiveEffect;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * A named armor set (名品の防具): four pieces (head, chest, legs, feet) of one style, field and level, with bonuses for
 * wearing two and four of them. {@link #id()} and {@link #model()} are stable.
 *
 * <pre>
 * piece defense = {@link Armory#setDefenseAt}(level) × slot share × style defense × {@link #defense} × rarity × enhancement
 * piece health  = {@link Armory#setHealthAt}(level)  × slot share × style health  × {@link #health} × rarity × enhancement
 * piece bonus   = {@link #piece} on every piece; set bonuses once for 2 and once more for 4 pieces worn
 * </pre>
 *
 * @param model custom model data of the head piece; chest, legs and feet follow (+1, +2, +3)
 * @param theme the element of the set's look and of its proc perks
 */
public record ArmorSet(String id, int model, ArmorStyle style, int field, int level, Set<DesignSource> sources,
                       String origin, Element theme, double defense, double health, CombatModifiers piece,
                       SetBonus two, SetBonus four) {
    public static final int PIECES = 4;

    /** Bonus of a number of pieces worn: secondary modifiers plus server-resolved perks. */
    public record SetBonus(CombatModifiers modifiers, List<PassiveEffect> perks) {
        public static final SetBonus NONE = new SetBonus(CombatModifiers.NONE, List.of());

        public SetBonus {
            Objects.requireNonNull(modifiers, "modifiers");
            perks = List.copyOf(perks);
            for (PassiveEffect perk : perks) {
                if (perk instanceof PassiveEffect.Cooldown || perk instanceof PassiveEffect.StaminaSaver) {
                    throw new IllegalArgumentException("set perks never change cooldowns or stamina");
                }
            }
        }
    }

    public ArmorSet {
        Checks.id("id", id);
        Checks.range("model", model, Design.ARMOR_MODELS, 9999 - PIECES);
        Objects.requireNonNull(style, "style");
        Checks.range("field", field, 1, 10);
        Checks.range("level", level, 1, Design.MAX_LEVEL);
        sources = Set.copyOf(sources);
        if (sources.isEmpty()) {
            throw new IllegalArgumentException(id + ": a set needs a source");
        }
        Objects.requireNonNull(origin, "origin");
        if (!origin.isEmpty()) {
            Checks.id("origin", origin);
        }
        Objects.requireNonNull(theme, "theme");
        Checks.range("defense", defense, 0.8, 1.4);
        Checks.range("health", health, 0.8, 1.4);
        Objects.requireNonNull(piece, "piece");
        Objects.requireNonNull(two, "two");
        Objects.requireNonNull(four, "four");
    }

    public ItemTier tier() {
        return ItemTier.highestFor(level);
    }

    /** Name of the whole set ("草原駆けの装束"). */
    public String translationKey() {
        return "armor_set." + ModInfo.MOD_ID + "." + id;
    }

    public String loreKey() {
        return translationKey() + ".lore";
    }

    /** The four pieces, head to feet. */
    public List<ArmorPiece> pieces() {
        List<ArmorPiece> pieces = new ArrayList<>(PIECES);
        for (EquipmentSlot slot : EquipmentSlot.armor()) {
            pieces.add(new ArmorPiece(this, slot));
        }
        return Collections.unmodifiableList(pieces);
    }

    public ArmorPiece piece(EquipmentSlot slot) {
        return new ArmorPiece(this, slot);
    }

    public boolean has(DesignSource source) {
        return sources.contains(source);
    }

    /** The bonus of {@code worn} pieces of this set (nothing below two). */
    public List<SetBonus> active(int worn) {
        if (worn >= PIECES) {
            return List.of(two, four);
        }
        if (worn >= 2) {
            return List.of(two);
        }
        return List.of();
    }
}
