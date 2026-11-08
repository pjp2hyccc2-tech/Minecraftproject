package com.eternalrealm.monster;

import java.util.List;
import java.util.OptionalInt;

/**
 * Item ids of the crafting materials monsters drop. The items themselves (registration, names, textures, recipes)
 * belong to the crafting module; these ids are the shared contract. Two materials per area, plus the two upgrade
 * materials that drop rarely anywhere.
 */
public final class Materials {
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

    private Materials() {
    }

    /** The fourteen area materials (not the upgrades), area by area. */
    public static List<String> areaMaterials() {
        return ALL;
    }

    public static List<String> upgradeMaterials() {
        return UPGRADES;
    }

    /** The two materials of an area; empty for unknown areas. */
    public static List<String> ofArea(int areaId) {
        return areaId >= 1 && areaId <= BY_AREA.size() ? BY_AREA.get(areaId - 1) : List.of();
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
        return ALL.contains(itemId) || UPGRADES.contains(itemId);
    }
}
