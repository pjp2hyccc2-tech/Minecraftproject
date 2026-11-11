package com.eternalrealm.item;

import com.eternalrealm.combat.CombatModifiers;
import com.eternalrealm.combat.WeaponType;
import com.eternalrealm.job.PassiveEffect;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * A named weapon (名品の武器): the field and level it belongs to, its stat profile and where it comes from. Pieces are
 * rolled like every weapon (rarity, affixes, enhancement); the profile is fixed.
 *
 * <pre>
 * attack      = weapon type base attack × (1 + {@link Armory#weaponBonusAt}(level)) × {@link #attack} × rarity × enhancement
 * crit        = added to the critical chance while held
 * speed       = attack speed bonus while held (light and heavy attacks; {@code CombatModifiers.attackSpeed})
 * reach       = multiplies the reach of the weapon's melee hit shapes (1 for staves and bows)
 * element     = what a hit may inflict, with chance {@link #proc}
 * bonus       = fixed secondary bonuses while held
 * art / perks = boss weapons: their own weapon skill and special effects; lord and calamity weapons have perks
 * </pre>
 *
 * @param art   id of the boss weapon's {@link WeaponArt} (its weapon skill), or {@code null}
 * @param perks passive effects while held (resolved by the server; never cooldowns or stamina)
 */
public record WeaponDesign(String id, int model, WeaponType type, int field, int level, Set<DesignSource> sources,
                           String origin, double attack, double crit, double speed, double reach, Element element,
                           double proc, CombatModifiers bonus, Map<AffixKind, Integer> bias, String art,
                           List<PassiveEffect> perks) implements Design {
    public WeaponDesign {
        Checks.id("id", id);
        Checks.range("model", model, Design.WEAPON_MODELS, Design.ARMOR_MODELS - 1);
        Objects.requireNonNull(type, "type");
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
        Checks.range("attack", attack, 0.8, 1.5);
        Checks.range("crit", crit, 0.0, 0.25);
        Checks.range("speed", speed, 0.0, 0.2);
        Checks.range("reach", reach, 0.8, 1.3);
        if ((type == WeaponType.STAFF || type == WeaponType.BOW) && reach != 1.0) {
            throw new IllegalArgumentException(id + ": staves and bows have no melee reach");
        }
        Objects.requireNonNull(element, "element");
        Checks.range("proc", proc, 0.0, 0.4);
        if ((element == Element.NONE) != (proc == 0.0)) {
            throw new IllegalArgumentException(id + ": an element needs a proc chance and vice versa");
        }
        Objects.requireNonNull(bonus, "bonus");
        bias = checkedBias(id, bias, AffixPool.of(EquipmentSlot.WEAPON, type));
        if (art != null) {
            Checks.id("art", art);
        }
        perks = List.copyOf(perks);
        for (PassiveEffect perk : perks) {
            if (perk instanceof PassiveEffect.Cooldown || perk instanceof PassiveEffect.StaminaSaver) {
                throw new IllegalArgumentException(id + ": weapon perks never change cooldowns or stamina");
            }
        }
    }

    static Map<AffixKind, Integer> checkedBias(String id, Map<AffixKind, Integer> bias, AffixPool pool) {
        Map<AffixKind, Integer> copy = new EnumMap<>(AffixKind.class);
        for (Map.Entry<AffixKind, Integer> entry : Objects.requireNonNull(bias, "bias").entrySet()) {
            if (!pool.allows(entry.getKey())) {
                throw new IllegalArgumentException(id + ": " + entry.getKey() + " cannot appear on this piece");
            }
            copy.put(entry.getKey(), Checks.range("bias of " + entry.getKey(), entry.getValue(), 1, 20));
        }
        return Map.copyOf(copy);
    }

    /** The tier of the design's level: the base item it is made on. */
    public ItemTier tier() {
        return ItemTier.highestFor(level);
    }

    @Override
    public EquipmentBase base() {
        return EquipmentBase.weapon(type, tier());
    }

    /** A boss weapon: it brings its own weapon skill. */
    public boolean isBossWeapon() {
        return art != null;
    }

    public boolean has(DesignSource source) {
        return sources.contains(source);
    }

    /** Copy of the sources as an enum set (iteration in declaration order). */
    public Set<DesignSource> sourceSet() {
        return sources.isEmpty() ? EnumSet.noneOf(DesignSource.class) : EnumSet.copyOf(sources);
    }
}
