package com.eternalrealm.item;

import com.eternalrealm.character.StatPercents;
import com.eternalrealm.character.StatType;
import com.eternalrealm.combat.CombatModifiers;
import com.eternalrealm.combat.DamageType;
import com.eternalrealm.combat.WeaponType;
import com.eternalrealm.core.ModInfo;
import com.eternalrealm.job.PassiveEffect;
import com.eternalrealm.job.SkillEffect.AfflictionKind;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.random.RandomGenerator;

/**
 * 金枠の遺宝 (relics, owner's request of 2026-11-11): every dungeon boss (b1-b8 and the final raid), calamity (10), lord
 * (ヌシ, 30) and wanderer (15) has a set of its own relics in its motif: a weapon, an armor set (four pieces), an
 * accessory and a charm. About one kill in a thousand ({@link #DROP_CHANCE} per rewarded player, server config
 * {@code relicDropChance}) leaves one of them; the whole server hears of it.
 *
 * <p>Relics are the strongest pieces of their level: they always come as {@link ItemRarity#LEGENDARY} with four
 * affixes, ask for their source's level + {@value #LEVEL_BONUS} (and nothing more for their rarity) and show a gold
 * frame wherever they appear.</p>
 *
 * <pre>
 * weapon  attack ×1.30..1.40 by feel (boss weapons ×1.25 at most), crit +3 %, speed +2 %, element chance 16 % (28 %
 *         for elemental feels), damage of its type +8 % × (0.5 + level / 50) and the feel's and element's traits,
 *         a perk by feel and skill power +6 %, and its own weapon art (burst 2.05..2.30 × the type skill)
 * armor   set quality ×1.40 (calamity sets ×1.08), the style's bonuses ×1.5, the theme's perk
 * acc.    main stat 3 + 0.30 × level, second stat 2 + 0.16 × level, one bonus by focus
 * charm   VIT 2 + 0.2 × level, LUK 1 + 0.12 × level, max health 0.6 × level, defense 0.5 × level, a perk by focus
 * </pre>
 *
 * Which relic a drop is: the weapon, the accessory, the charm or an armor piece, a quarter each (each piece 1/16).
 * The catalogue rows are generated ({@link RelicTable}, tools/art/relics.py); the numbers are made here.
 */
public final class Relics {
    /** Chance per rewarded player per kill (server config {@code relicDropChance} overrides it). */
    public static final double DROP_CHANCE = 0.001;
    /** Levels a relic asks for above its source's level. */
    public static final int LEVEL_BONUS = 5;

    /** What a relic comes from. */
    public enum SourceKind {
        BOSS, CALAMITY, LORD, WANDERER;

        public String translationKey() {
            return "relic_source." + ModInfo.MOD_ID + "." + name().toLowerCase(Locale.ROOT);
        }
    }

    /** The bonus of an accessory beyond its two stats. */
    enum AccessoryFocus {
        CRIT, CRIT_DAMAGE, DAMAGE, SPEED, MOVE, HEALING
    }

    /** The perk of a charm. */
    enum CharmFocus {
        WARD, RENEWAL, EVASION, THORNS, LIFESTEAL, HEALTH
    }

    /**
     * One relic source with its relics.
     *
     * @param key    stable id of the source ({@code b1}, {@code c_meadow}, {@code l_garm}, {@code w_gravebloom}): the
     *               origin of its relics
     * @param target the boss, calamity, lord or wanderer id
     * @param level  the source's level (the relics ask for {@code level + 5})
     */
    public record Source(String key, SourceKind kind, String target, int field, int level, WeaponDesign weapon,
                         ArmorSet set, TrinketDesign accessory, TrinketDesign charm) {
        public Source {
            Checks.id("key", key);
            Objects.requireNonNull(kind, "kind");
            Checks.id("target", target);
        }

        /** The source's own name ("地下都市の盗賊王", "ヌシ「街道荒らしのガルム」"...). */
        public String nameKey() {
            return switch (kind) {
                case BOSS -> "boss." + ModInfo.MOD_ID + "." + target;
                case CALAMITY -> "threat." + ModInfo.MOD_ID + ".calamity." + target;
                case LORD -> "threat." + ModInfo.MOD_ID + ".lord." + target;
                case WANDERER -> "boss." + ModInfo.MOD_ID + ".w_" + target;
            };
        }

        /** Level the relics ask for. */
        public int relicLevel() {
            return weapon.level();
        }

        /** The seven relics: weapon, the four armor pieces, accessory, charm. */
        public List<Design> designs() {
            List<Design> all = new ArrayList<>(7);
            all.add(weapon);
            all.addAll(set.pieces());
            all.add(accessory);
            all.add(charm);
            return Collections.unmodifiableList(all);
        }
    }

    private record Catalog(List<Source> sources, Map<String, Source> byKey, Map<String, Source> byTarget,
                           Map<String, Design> byId, Map<Integer, Design> byModel) {
    }

    private static final class Holder {
        static final Catalog CATALOG = build();
    }

    private Relics() {
    }

    // ------------------------------------------------------------------ lookups

    /** The 64 sources: dungeon bosses, calamities, lords, wanderers (in catalogue order). */
    public static List<Source> sources() {
        return Holder.CATALOG.sources();
    }

    public static Optional<Source> source(String key) {
        return Optional.ofNullable(key == null ? null : Holder.CATALOG.byKey().get(key));
    }

    /** The source of a boss, calamity, lord or wanderer by its own id. */
    public static Optional<Source> source(SourceKind kind, String target) {
        return Optional.ofNullable(kind == null || target == null ? null
                : Holder.CATALOG.byTarget().get(kind.name() + ":" + target));
    }

    /** The source a relic comes from (empty for every other design). */
    public static Optional<Source> sourceOf(Design design) {
        return design != null && design.isRelic() ? source(design.origin()) : Optional.empty();
    }

    public static Optional<Design> design(String id) {
        return Optional.ofNullable(id == null ? null : Holder.CATALOG.byId().get(id));
    }

    public static Optional<Design> byModel(int model) {
        return Optional.ofNullable(Holder.CATALOG.byModel().get(model));
    }

    /** Whether {@code id} names a relic (without building the catalogue for ids that cannot be one). */
    public static boolean isRelicId(String id) {
        return id != null && id.startsWith("relic_") && design(id).isPresent();
    }

    public static List<WeaponDesign> weapons() {
        List<WeaponDesign> out = new ArrayList<>();
        for (Source source : sources()) {
            out.add(source.weapon());
        }
        return Collections.unmodifiableList(out);
    }

    public static List<ArmorSet> sets() {
        List<ArmorSet> out = new ArrayList<>();
        for (Source source : sources()) {
            out.add(source.set());
        }
        return Collections.unmodifiableList(out);
    }

    public static List<TrinketDesign> trinkets() {
        List<TrinketDesign> out = new ArrayList<>();
        for (Source source : sources()) {
            out.add(source.accessory());
            out.add(source.charm());
        }
        return Collections.unmodifiableList(out);
    }

    /** Every relic (448: 64 weapons, 256 armor pieces, 128 trinkets). */
    public static List<Design> designs() {
        return List.copyOf(Holder.CATALOG.byId().values());
    }

    // ------------------------------------------------------------------ drops

    /** Whether a kill leaves a relic for one rewarded player ({@code chance}: {@link #DROP_CHANCE} or the config). */
    public static boolean rolls(double chance, RandomGenerator random) {
        return chance > 0.0 && random.nextDouble() < chance;
    }

    /**
     * Which relic a drop is: the weapon, the accessory, the charm or one of the armor pieces, a quarter each (each
     * armor piece 1/16). Draws one {@code nextInt}.
     */
    public static Design pick(Source source, RandomGenerator random) {
        int roll = random.nextInt(16);
        if (roll < 4) {
            return source.weapon();
        }
        if (roll < 8) {
            return source.set().piece(EquipmentSlot.armor().get(roll - 4));
        }
        return roll < 12 ? source.accessory() : source.charm();
    }

    // ------------------------------------------------------------------ the catalogue

    private static Catalog build() {
        List<Source> sources = new ArrayList<>();
        Map<String, Source> byKey = new LinkedHashMap<>();
        Map<String, Source> byTarget = new LinkedHashMap<>();
        Map<String, Design> byId = new LinkedHashMap<>();
        Map<Integer, Design> byModel = new LinkedHashMap<>();
        int index = 0;
        for (RelicTable.Row row : RelicTable.ROWS) {
            Source source = source(index++, row);
            sources.add(source);
            if (byKey.put(source.key(), source) != null
                    || byTarget.put(source.kind().name() + ":" + source.target(), source) != null) {
                throw new IllegalStateException("Duplicate relic source " + source.key());
            }
            for (Design design : source.designs()) {
                if (byId.put(design.id(), design) != null || byModel.put(design.model(), design) != null) {
                    throw new IllegalStateException("Duplicate relic " + design.id());
                }
            }
        }
        return new Catalog(Collections.unmodifiableList(sources), Collections.unmodifiableMap(byKey),
                Collections.unmodifiableMap(byTarget), Collections.unmodifiableMap(byId),
                Collections.unmodifiableMap(byModel));
    }

    private static Source source(int index, RelicTable.Row row) {
        SourceKind kind = SourceKind.valueOf(row.kind());
        int level = Math.min(Design.MAX_LEVEL, row.level() + LEVEL_BONUS);
        Set<DesignSource> sources = EnumSet.of(DesignSource.RELIC);
        WeaponType type = WeaponType.valueOf(row.weaponType());
        Armory.Feel feel = Armory.Feel.valueOf(row.feel());
        Element element = Element.valueOf(row.element());
        WeaponDesign weapon = weapon(row.key(), index, type, row.field(), level, feel, element, sources);
        ArmorSet set = set(row.key(), index, ArmorStyle.valueOf(row.style()), row.field(), level, element, sources);
        TrinketDesign accessory = accessory(row, index, level, sources);
        TrinketDesign charm = charm(row, index, level, sources);
        return new Source(row.key(), kind, row.target(), row.field(), row.level(), weapon, set, accessory, charm);
    }

    /** Relic weapon: attack 1.30..1.40 by feel, the feel's handling a little higher, a strong element and perks. */
    static WeaponDesign weapon(String key, int index, WeaponType type, int field, int level, Armory.Feel feel,
                               Element element, Set<DesignSource> sources) {
        boolean ranged = type == WeaponType.STAFF || type == WeaponType.BOW;
        double attack = round(1.30 + 0.5 * (feel.attack - 0.92));
        double crit = round(feel.crit + 0.03);
        double speed = round(Math.min(0.2, feel.speed + 0.02));
        double proc = element == Element.NONE ? 0.0 : feel == Armory.Feel.ELEMENTAL ? 0.28 : 0.16;
        List<PassiveEffect> perks = new ArrayList<>();
        perks.add(switch (feel) {
            case HEAVY -> new PassiveEffect.LastStand(0.15, 0.08);
            case SWIFT -> new PassiveEffect.Evasion(0.05);
            case KEEN -> new PassiveEffect.HealOnKill(0.04);
            case ELEMENTAL -> new PassiveEffect.Lifesteal(0.025);
            case BALANCED, LONG -> new PassiveEffect.Lifesteal(0.03);
        });
        perks.add(new PassiveEffect.SkillPower(0.06));
        return new WeaponDesign("relic_" + key + "_weapon", Design.RELIC_WEAPON_MODELS + index, type, field, level,
                sources, key, attack, crit, speed, ranged ? 1.0 : feel.reach, element, proc,
                weaponBonus(type, level, feel, element), Armory.bias(type, feel, element), null, perks);
    }

    /** Damage of the weapon's type +8 % and the element's and the feel's traits, ×1.25 a boss weapon's, by level. */
    private static CombatModifiers weaponBonus(WeaponType type, int level, Armory.Feel feel, Element element) {
        double k = Armory.bonusScale(level);
        CombatModifiers.Builder b = CombatModifiers.builder();
        double percent = round(0.08 * k);
        DamageType damageType = type.damageType();
        switch (damageType) {
            case MAGIC -> b.magic(percent);
            case RANGED -> b.ranged(percent);
            default -> b.physical(percent);
        }
        switch (element) {
            case FIRE -> b.critDamage(round(0.05 * k));
            case FROST -> b.defense(r1(5 * k));
            case THUNDER -> b.attackSpeed(round(0.025 * k));
            case VENOM -> b.moveSpeed(round(0.025 * k));
            case HOLY -> b.healing(round(0.04 * k));
            case SHADOW -> b.crit(round(0.0125 * k));
            case BLOOD -> b.critDamage(round(0.075 * k));
            case EARTH -> b.healthFlat(r1(10 * k));
            case NONE -> {
            }
        }
        switch (feel) {
            case SWIFT -> b.moveSpeed(round(0.0125 * k));
            case HEAVY -> b.guard(0.04);
            case KEEN -> b.critDamage(round(0.06 * k));
            default -> {
            }
        }
        return b.build();
    }

    /** Relic set: quality ×1.40, the style's bonuses ×1.5 and the theme's perk. */
    static ArmorSet set(String key, int index, ArmorStyle style, int field, int level, Element theme,
                        Set<DesignSource> sources) {
        double k = 1.5 * (0.6 + 0.8 * Math.min(level, 60) / 50.0);
        CombatModifiers piece;
        ArmorSet.SetBonus two;
        List<PassiveEffect> fourPerks = new ArrayList<>();
        switch (style) {
            case HEAVY -> {
                piece = CombatModifiers.builder().guard(0.01).healthFlat(r1(2 * k)).build();
                two = new ArmorSet.SetBonus(CombatModifiers.builder().defense(r1(10 * k)).healthFlat(r1(12 * k))
                        .build(), List.of());
                fourPerks.add(new PassiveEffect.LastStand(0.0, 0.08));
            }
            case LIGHT -> {
                piece = CombatModifiers.builder().moveSpeed(0.01).crit(0.005).build();
                two = new ArmorSet.SetBonus(CombatModifiers.builder().crit(round(0.02 * k)).moveSpeed(0.03).build(),
                        List.of());
                fourPerks.add(new PassiveEffect.Evasion(0.04));
            }
            case ROBE -> {
                piece = CombatModifiers.builder().magic(0.01).healing(0.01).build();
                two = new ArmorSet.SetBonus(CombatModifiers.builder().magic(round(0.04 * k)).healing(round(0.04 * k))
                        .build(), List.of());
                fourPerks.add(new PassiveEffect.HealOnKill(0.04));
            }
            default -> throw new IllegalStateException("unreachable");
        }
        CombatModifiers fourMods = switch (style) {
            case HEAVY -> CombatModifiers.builder().guard(0.06).healthPercent(round(0.04 * k)).build();
            case LIGHT -> CombatModifiers.builder().attackSpeed(round(0.04 * k)).critDamage(round(0.08 * k)).build();
            case ROBE -> CombatModifiers.builder().magic(round(0.06 * k)).stamina(r1(15 * k)).build();
        };
        switch (theme) {
            case FIRE -> fourPerks.add(new PassiveEffect.Proc(AfflictionKind.BURN, 0.08, 60, 0.0, false));
            case FROST -> fourPerks.add(new PassiveEffect.Proc(AfflictionKind.SLOW, 0.09, 40, 1.0, false));
            case THUNDER -> fourPerks.add(new PassiveEffect.Proc(AfflictionKind.STUN, 0.045, 10, 0.0, false));
            case VENOM -> fourPerks.add(new PassiveEffect.Proc(AfflictionKind.POISON, 0.08, 80, 0.15, false));
            case HOLY -> fourMods = fourMods.plus(CombatModifiers.builder().healing(round(0.05 * k)).build());
            case SHADOW -> fourPerks.add(new PassiveEffect.Evasion(0.03));
            case BLOOD -> fourPerks.add(new PassiveEffect.Lifesteal(0.03));
            case EARTH -> fourPerks.add(new PassiveEffect.Thorns(0.15));
            case NONE -> fourMods = fourMods.plus(CombatModifiers.builder().healthFlat(r1(8 * k)).build());
        }
        return new ArmorSet("relic_" + key, Design.RELIC_ARMOR_MODELS + index * ArmorSet.PIECES, style, field, level,
                sources, key, theme, 1.40, 1.40, piece, two, new ArmorSet.SetBonus(fourMods, fourPerks));
    }

    /**
     * Accessory: two primary stats as percentages (the main ×1.25 and the second ×0.5 of a catalogue accessory of the
     * same level, {@link Trinkets#accessoryPercent}) and one bonus by focus, all growing with the level.
     */
    static TrinketDesign accessory(RelicTable.Row row, int index, int level, Set<DesignSource> sources) {
        TrinketKind kind = TrinketKind.valueOf(row.accessory());
        StatType main = StatType.valueOf(row.accessoryMain());
        StatType second = StatType.valueOf(row.accessorySecond());
        double base = Trinkets.accessoryPercent(level);
        Map<StatType, Double> percents = new EnumMap<>(StatType.class);
        percents.put(main, round(1.25 * base));
        percents.merge(second, round(0.5 * base), Double::sum);
        CombatModifiers.Builder b = CombatModifiers.builder();
        switch (AccessoryFocus.valueOf(row.accessoryFocus())) {
            case CRIT -> b.crit(round(0.01 + 0.0006 * level));
            case CRIT_DAMAGE -> b.critDamage(round(0.04 + 0.002 * level));
            case DAMAGE -> {
                double percent = round(0.02 + 0.001 * level);
                switch (main) {
                    case INT -> b.magic(percent);
                    case DEX -> b.ranged(percent);
                    default -> b.physical(percent);
                }
            }
            case SPEED -> b.attackSpeed(round(0.01 + 0.0005 * level));
            case MOVE -> b.moveSpeed(round(0.01 + 0.0004 * level));
            case HEALING -> b.healing(round(0.02 + 0.001 * level));
        }
        StatPercents block = StatPercents.of(percents);
        return new TrinketDesign("relic_" + row.key() + "_" + kind.id(), Design.TRINKET_MODELS + index * 2, kind,
                row.field(), level, sources, row.key(), block, b.build(), List.of(),
                TrinketDesign.statBias(block, kind.slot()));
    }

    /**
     * Charm: VIT and LUK as percentages (×1.25 and ×0.6 of a catalogue charm of the same level,
     * {@link Trinkets#charmPercent}), max health and defense, and a perk by focus.
     */
    static TrinketDesign charm(RelicTable.Row row, int index, int level, Set<DesignSource> sources) {
        TrinketKind kind = TrinketKind.valueOf(row.charm());
        double base = Trinkets.charmPercent(level);
        Map<StatType, Double> percents = new EnumMap<>(StatType.class);
        percents.put(StatType.VIT, round(1.25 * base));
        percents.put(StatType.LUK, round(0.6 * base));
        CombatModifiers.Builder b = CombatModifiers.builder().healthFlat(r1(0.6 * level)).defense(r1(0.5 * level));
        List<PassiveEffect> perks = new ArrayList<>();
        switch (CharmFocus.valueOf(row.charmFocus())) {
            case WARD -> perks.add(new PassiveEffect.LastStand(0.0, round(0.04 + 0.0008 * level)));
            case RENEWAL -> perks.add(new PassiveEffect.HealOnKill(round(0.02 + 0.0005 * level)));
            case EVASION -> perks.add(new PassiveEffect.Evasion(round(0.02 + 0.0004 * level)));
            case THORNS -> perks.add(new PassiveEffect.Thorns(round(0.08 + 0.002 * level)));
            case LIFESTEAL -> perks.add(new PassiveEffect.Lifesteal(round(0.01 + 0.0002 * level)));
            case HEALTH -> b.healthPercent(round(0.03 + 0.001 * level));
        }
        StatPercents block = StatPercents.of(percents);
        return new TrinketDesign("relic_" + row.key() + "_" + kind.id(), Design.TRINKET_MODELS + index * 2 + 1, kind,
                row.field(), level, sources, row.key(), block, b.build(), perks,
                TrinketDesign.statBias(block, kind.slot()));
    }

    private static double round(double value) {
        return Math.round(value * 1000.0) / 1000.0;
    }

    private static double r1(double value) {
        return Math.round(value * 10.0) / 10.0;
    }
}
