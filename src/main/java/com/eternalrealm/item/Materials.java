package com.eternalrealm.item;

import com.eternalrealm.core.ModInfo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * Item ids of the crafting materials, in one place so they can be renamed once the monster module registers the
 * actual items. Two materials per area (dropped by that area's monsters) plus the enhancement stone and the reroll
 * crystal, and the fifty materials of the field map ({@link #fieldMaterials()}): per field two common drops, an
 * uncommon one and an elite material of its mid-bosses, plus one boss material per field.
 */
public final class Materials {
    private static final String NS = ModInfo.MOD_ID + ":";

    // Area 1: アルセリア王国
    public static final String SLIME_GEL = NS + "slime_gel";
    public static final String WOLF_FANG = NS + "wolf_fang";
    // Area 2: エルダーフォレスト
    public static final String TREANT_BARK = NS + "treant_bark";
    public static final String FOREST_SPORE = NS + "forest_spore";
    // Area 3: サンドリア砂漠
    public static final String SCARAB_SHELL = NS + "scarab_shell";
    public static final String DESERT_CRYSTAL = NS + "desert_crystal";
    // Area 4: ノルディア雪原
    public static final String FROST_SHARD = NS + "frost_shard";
    public static final String YETI_FUR = NS + "yeti_fur";
    // Area 5: ドラゴニア火山
    public static final String MAGMA_CORE = NS + "magma_core";
    public static final String DRAKE_SCALE = NS + "drake_scale";
    // Area 6: セレスティア天空大陸
    public static final String SKY_FEATHER = NS + "sky_feather";
    public static final String STAR_FRAGMENT = NS + "star_fragment";
    // Area 7: 終焉の塔
    public static final String VOID_ESSENCE = NS + "void_essence";
    public static final String ABYSS_CRYSTAL = NS + "abyss_crystal";

    /** Consumed by enhancement (強化). */
    public static final String ENHANCEMENT_STONE = NS + "enhancement_stone";
    /** Consumed by rerolling affixes (厳選). */
    public static final String REROLL_CRYSTAL = NS + "reroll_crystal";

    /** How rare a field material is: who drops it and how often. */
    public enum Grade {
        /** Any monster of the field, often. */
        COMMON,
        /** Any monster of the field, now and then. */
        UNCOMMON,
        /** The field's elites and mid-bosses. */
        ELITE,
        /** The field's bosses (dungeon bosses and bosses roaming the field). */
        BOSS
    }

    /** A material of the field map: its item id, the field (area id 1..10) that yields it, its grade. */
    public record FieldMaterial(String id, int field, Grade grade) {
        public FieldMaterial {
            Checks.itemId("id", id);
            Checks.range("field", field, 1, 10);
            Objects.requireNonNull(grade, "grade");
        }
    }

    /** Per field: common, common, uncommon, elite (fields 1..10), then the ten boss materials (field order). */
    private static final List<FieldMaterial> FIELD = List.of(
            new FieldMaterial(NS + "meadow_fiber", 1, Grade.COMMON),
            new FieldMaterial(NS + "beast_hide", 1, Grade.COMMON),
            new FieldMaterial(NS + "bandit_badge", 1, Grade.UNCOMMON),
            new FieldMaterial(NS + "alpha_mane", 1, Grade.ELITE),
            new FieldMaterial(NS + "moonleaf", 2, Grade.COMMON),
            new FieldMaterial(NS + "spider_silk", 2, Grade.COMMON),
            new FieldMaterial(NS + "glowcap", 2, Grade.UNCOMMON),
            new FieldMaterial(NS + "elder_heartwood", 2, Grade.ELITE),
            new FieldMaterial(NS + "iron_ore_chunk", 3, Grade.COMMON),
            new FieldMaterial(NS + "coal_ember", 3, Grade.COMMON),
            new FieldMaterial(NS + "golem_core_shard", 3, Grade.UNCOMMON),
            new FieldMaterial(NS + "veined_steel", 3, Grade.ELITE),
            new FieldMaterial(NS + "holy_water", 4, Grade.COMMON),
            new FieldMaterial(NS + "mist_crystal", 4, Grade.COMMON),
            new FieldMaterial(NS + "river_pearl", 4, Grade.UNCOMMON),
            new FieldMaterial(NS + "falls_spirit_tear", 4, Grade.ELITE),
            new FieldMaterial(NS + "ancient_gear", 5, Grade.COMMON),
            new FieldMaterial(NS + "rune_tablet_shard", 5, Grade.COMMON),
            new FieldMaterial(NS + "sun_amber", 5, Grade.UNCOMMON),
            new FieldMaterial(NS + "guardian_plate", 5, Grade.ELITE),
            new FieldMaterial(NS + "thunder_horn", 6, Grade.COMMON),
            new FieldMaterial(NS + "glacier_ice", 6, Grade.COMMON),
            new FieldMaterial(NS + "wyvern_talon", 6, Grade.UNCOMMON),
            new FieldMaterial(NS + "storm_heart", 6, Grade.ELITE),
            new FieldMaterial(NS + "corrupted_bark", 7, Grade.COMMON),
            new FieldMaterial(NS + "shadow_silk", 7, Grade.COMMON),
            new FieldMaterial(NS + "nightmare_eye", 7, Grade.UNCOMMON),
            new FieldMaterial(NS + "oblivion_shard", 7, Grade.ELITE),
            new FieldMaterial(NS + "golden_wheat", 8, Grade.COMMON),
            new FieldMaterial(NS + "bull_horn", 8, Grade.COMMON),
            new FieldMaterial(NS + "cursed_straw", 8, Grade.UNCOMMON),
            new FieldMaterial(NS + "harvest_gem", 8, Grade.ELITE),
            new FieldMaterial(NS + "lake_scale", 9, Grade.COMMON),
            new FieldMaterial(NS + "reed_fiber", 9, Grade.COMMON),
            new FieldMaterial(NS + "marsh_gel", 9, Grade.UNCOMMON),
            new FieldMaterial(NS + "water_spirit_pearl", 9, Grade.ELITE),
            new FieldMaterial(NS + "sea_salt_crystal", 10, Grade.COMMON),
            new FieldMaterial(NS + "pirate_doubloon", 10, Grade.COMMON),
            new FieldMaterial(NS + "kraken_ink", 10, Grade.UNCOMMON),
            new FieldMaterial(NS + "storm_sail_cloth", 10, Grade.ELITE),
            new FieldMaterial(NS + "verdant_soul", 1, Grade.BOSS),
            new FieldMaterial(NS + "moonlit_soul", 2, Grade.BOSS),
            new FieldMaterial(NS + "iron_soul", 3, Grade.BOSS),
            new FieldMaterial(NS + "holy_soul", 4, Grade.BOSS),
            new FieldMaterial(NS + "ancient_soul", 5, Grade.BOSS),
            new FieldMaterial(NS + "sky_soul", 6, Grade.BOSS),
            new FieldMaterial(NS + "end_soul", 7, Grade.BOSS),
            new FieldMaterial(NS + "harvest_soul", 8, Grade.BOSS),
            new FieldMaterial(NS + "lake_soul", 9, Grade.BOSS),
            new FieldMaterial(NS + "tide_soul", 10, Grade.BOSS));

    /** Index = area − 1; per area: the armor material first, the weapon material second. */
    private static final List<List<String>> BY_AREA = List.of(
            List.of(SLIME_GEL, WOLF_FANG),
            List.of(TREANT_BARK, FOREST_SPORE),
            List.of(SCARAB_SHELL, DESERT_CRYSTAL),
            List.of(FROST_SHARD, YETI_FUR),
            List.of(MAGMA_CORE, DRAKE_SCALE),
            List.of(SKY_FEATHER, STAR_FRAGMENT),
            List.of(VOID_ESSENCE, ABYSS_CRYSTAL));

    private static final List<String> ALL;

    static {
        List<String> all = new ArrayList<>();
        for (List<String> area : BY_AREA) {
            all.addAll(area);
        }
        all.add(ENHANCEMENT_STONE);
        all.add(REROLL_CRYSTAL);
        for (FieldMaterial material : FIELD) {
            all.add(material.id());
        }
        for (String id : all) {
            Checks.itemId("material", id);
        }
        ALL = Collections.unmodifiableList(all);
    }

    private Materials() {
    }

    /** The two materials of {@code area} (1..7). */
    public static List<String> forArea(int area) {
        return BY_AREA.get(Checks.range("area", area, 1, ItemTier.COUNT) - 1);
    }

    /** Material used most by the tier's armor recipes (first of the area). */
    public static String armorMaterial(ItemTier tier) {
        return forArea(Objects.requireNonNull(tier, "tier").area()).get(0);
    }

    /** Material used most by the tier's weapon recipes (second of the area). */
    public static String weaponMaterial(ItemTier tier) {
        return forArea(Objects.requireNonNull(tier, "tier").area()).get(1);
    }

    /** The area (field) that drops {@code itemId}, or empty for the stone, the crystal and unknown ids. */
    public static OptionalInt areaOf(String itemId) {
        for (int i = 0; i < BY_AREA.size(); i++) {
            if (BY_AREA.get(i).contains(itemId)) {
                return OptionalInt.of(i + 1);
            }
        }
        return fieldMaterial(itemId).map(m -> OptionalInt.of(m.field())).orElse(OptionalInt.empty());
    }

    /** The fifty field-map materials (see {@link #FIELD} for the order). */
    public static List<FieldMaterial> fieldMaterials() {
        return FIELD;
    }

    public static Optional<FieldMaterial> fieldMaterial(String itemId) {
        for (FieldMaterial material : FIELD) {
            if (material.id().equals(itemId)) {
                return Optional.of(material);
            }
        }
        return Optional.empty();
    }

    /** Content tier (1..7) of a field: the story fields are their own tier, the side fields share one. */
    public static int tierOfField(int field) {
        return switch (Checks.range("field", field, 1, 10)) {
            case 8 -> 2;
            case 9 -> 3;
            case 10 -> 4;
            default -> field;
        };
    }

    /** The field's materials of one grade, in catalogue order (two commons, one of every other grade). */
    public static List<String> ofField(int field, Grade grade) {
        List<String> ids = new ArrayList<>();
        for (FieldMaterial material : FIELD) {
            if (material.field() == field && material.grade() == grade) {
                ids.add(material.id());
            }
        }
        return Collections.unmodifiableList(ids);
    }

    /** Vanilla-style name key of a namespaced item id: {@code eternalrealm:reroll_crystal} → {@code item.eternalrealm.reroll_crystal}. */
    public static String translationKey(String itemId) {
        Checks.itemId("itemId", itemId);
        return "item." + itemId.replace(':', '.');
    }

    public static boolean isKnown(String itemId) {
        return ALL.contains(itemId);
    }

    /** All 66 material ids: the area materials in area order, the stone and the crystal, then the field materials. */
    public static List<String> all() {
        return ALL;
    }
}
