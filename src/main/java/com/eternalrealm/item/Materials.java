package com.eternalrealm.item;

import com.eternalrealm.core.ModInfo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.OptionalInt;

/**
 * Item ids of the crafting materials, in one place so they can be renamed once the monster module registers the
 * actual items. Two materials per area (dropped by that area's monsters) plus the enhancement stone and the reroll
 * crystal.
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

    /** The area that drops {@code itemId}, or empty for the stone, the crystal and unknown ids. */
    public static OptionalInt areaOf(String itemId) {
        for (int i = 0; i < BY_AREA.size(); i++) {
            if (BY_AREA.get(i).contains(itemId)) {
                return OptionalInt.of(i + 1);
            }
        }
        return OptionalInt.empty();
    }

    /** Vanilla-style name key of a namespaced item id: {@code eternalrealm:reroll_crystal} → {@code item.eternalrealm.reroll_crystal}. */
    public static String translationKey(String itemId) {
        Checks.itemId("itemId", itemId);
        return "item." + itemId.replace(':', '.');
    }

    public static boolean isKnown(String itemId) {
        return ALL.contains(itemId);
    }

    /** All 16 material ids: the area materials in area order, then the stone and the crystal. */
    public static List<String> all() {
        return ALL;
    }
}
