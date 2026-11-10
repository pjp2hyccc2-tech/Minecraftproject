package com.eternalrealm.monster;

import java.util.List;
import java.util.OptionalInt;

/**
 * Item ids of the crafting materials monsters drop. The items themselves (registration, names, textures, recipes)
 * belong to the crafting module; these ids are the shared contract. Two materials per area, plus the two upgrade
 * materials that drop rarely anywhere.
 */
public final class Materials {
    private static final String NS = "eternalrealm:";
    public static final String SLIME_GEL = "eternalrealm:slime_gel";
    public static final String WOLF_FANG = "eternalrealm:wolf_fang";
    public static final String TREANT_BARK = "eternalrealm:treant_bark";
    public static final String FOREST_SPORE = "eternalrealm:forest_spore";
    public static final String SCARAB_SHELL = "eternalrealm:scarab_shell";
    public static final String DESERT_CRYSTAL = "eternalrealm:desert_crystal";
    public static final String FROST_SHARD = "eternalrealm:frost_shard";
    public static final String YETI_FUR = "eternalrealm:yeti_fur";
    public static final String MAGMA_CORE = "eternalrealm:magma_core";
    public static final String DRAKE_SCALE = "eternalrealm:drake_scale";
    public static final String SKY_FEATHER = "eternalrealm:sky_feather";
    public static final String STAR_FRAGMENT = "eternalrealm:star_fragment";
    public static final String VOID_ESSENCE = "eternalrealm:void_essence";
    public static final String ABYSS_CRYSTAL = "eternalrealm:abyss_crystal";
    /** Equipment upgrade material, fairly common from every monster. */
    public static final String ENHANCEMENT_STONE = "eternalrealm:enhancement_stone";
    /** Equipment re-roll material, rare from every monster. */
    public static final String REROLL_CRYSTAL = "eternalrealm:reroll_crystal";

    /** Index {@code areaId - 1}. */
    private static final List<List<String>> BY_AREA = List.of(
            List.of(SLIME_GEL, WOLF_FANG),
            List.of(TREANT_BARK, FOREST_SPORE),
            List.of(SCARAB_SHELL, DESERT_CRYSTAL),
            List.of(FROST_SHARD, YETI_FUR),
            List.of(MAGMA_CORE, DRAKE_SCALE),
            List.of(SKY_FEATHER, STAR_FRAGMENT),
            List.of(VOID_ESSENCE, ABYSS_CRYSTAL));
    private static final List<String> UPGRADES = List.of(ENHANCEMENT_STONE, REROLL_CRYSTAL);
    private static final List<String> ALL = BY_AREA.stream().flatMap(List::stream).toList();
    /** The field-map materials, index {@code field - 1}: common, common, uncommon, elite (mid-bosses). */
    private static final List<List<String>> FIELD = List.of(
            List.of(NS + "meadow_fiber", NS + "beast_hide", NS + "bandit_badge", NS + "alpha_mane"),
            List.of(NS + "moonleaf", NS + "spider_silk", NS + "glowcap", NS + "elder_heartwood"),
            List.of(NS + "iron_ore_chunk", NS + "coal_ember", NS + "golem_core_shard", NS + "veined_steel"),
            List.of(NS + "holy_water", NS + "mist_crystal", NS + "river_pearl", NS + "falls_spirit_tear"),
            List.of(NS + "ancient_gear", NS + "rune_tablet_shard", NS + "sun_amber", NS + "guardian_plate"),
            List.of(NS + "thunder_horn", NS + "glacier_ice", NS + "wyvern_talon", NS + "storm_heart"),
            List.of(NS + "corrupted_bark", NS + "shadow_silk", NS + "nightmare_eye", NS + "oblivion_shard"),
            List.of(NS + "golden_wheat", NS + "bull_horn", NS + "cursed_straw", NS + "harvest_gem"),
            List.of(NS + "lake_scale", NS + "reed_fiber", NS + "marsh_gel", NS + "water_spirit_pearl"),
            List.of(NS + "sea_salt_crystal", NS + "pirate_doubloon", NS + "kraken_ink", NS + "storm_sail_cloth"));
    /** The boss material of each field, index {@code field - 1}. */
    private static final List<String> BOSS = List.of(
            NS + "verdant_soul",
            NS + "moonlit_soul",
            NS + "iron_soul",
            NS + "holy_soul",
            NS + "ancient_soul",
            NS + "sky_soul",
            NS + "end_soul",
            NS + "harvest_soul",
            NS + "lake_soul",
            NS + "tide_soul");
    private static final List<String> FIELD_ALL;

    static {
        java.util.List<String> all = new java.util.ArrayList<>();
        FIELD.forEach(all::addAll);
        all.addAll(BOSS);
        FIELD_ALL = List.copyOf(all);
    }

    private Materials() {
    }

    /** The fourteen area materials (not the upgrades), area by area. */
    public static List<String> areaMaterials() {
        return ALL;
    }

    public static List<String> upgradeMaterials() {
        return UPGRADES;
    }

    /** The two materials of a field (those of its content tier); empty for unknown fields. */
    public static List<String> ofArea(int areaId) {
        int tier = com.eternalrealm.world.Areas.byId(areaId).map(com.eternalrealm.world.AreaDefinition::tier)
                .orElse(0);
        return tier >= 1 && tier <= BY_AREA.size() ? BY_AREA.get(tier - 1) : List.of();
    }

    /** The area an area material belongs to; empty for upgrades and unknown ids. */
    public static OptionalInt areaOf(String itemId) {
        for (int i = 0; i < BY_AREA.size(); i++) {
            if (BY_AREA.get(i).contains(itemId)) {
                return OptionalInt.of(i + 1);
            }
        }
        return OptionalInt.empty();
    }

    public static boolean isKnown(String itemId) {
        return ALL.contains(itemId) || UPGRADES.contains(itemId) || FIELD_ALL.contains(itemId);
    }

    /** The fifty field-map materials: field by field (common, common, uncommon, elite), then the boss materials. */
    public static List<String> fieldMaterials() {
        return FIELD_ALL;
    }

    /** The two common materials of a field (1..10); empty for unknown fields. */
    public static List<String> fieldCommons(int field) {
        return field >= 1 && field <= FIELD.size() ? FIELD.get(field - 1).subList(0, 2) : List.of();
    }

    /** The uncommon material of a field (1..10). */
    public static String fieldUncommon(int field) {
        return FIELD.get(checkField(field) - 1).get(2);
    }

    /** The elite material of a field (1..10), dropped by its elites and mid-bosses. */
    public static String fieldElite(int field) {
        return FIELD.get(checkField(field) - 1).get(3);
    }

    /** The boss material of a field (1..10). */
    public static String bossMaterial(int field) {
        return BOSS.get(checkField(field) - 1);
    }

    private static int checkField(int field) {
        if (field < 1 || field > FIELD.size()) {
            throw new IllegalArgumentException("field " + field);
        }
        return field;
    }
}
