package com.eternalrealm.monster;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

import static com.eternalrealm.monster.Materials.ABYSS_CRYSTAL;
import static com.eternalrealm.monster.Materials.DESERT_CRYSTAL;
import static com.eternalrealm.monster.Materials.DRAKE_SCALE;
import static com.eternalrealm.monster.Materials.ENHANCEMENT_STONE;
import static com.eternalrealm.monster.Materials.FOREST_SPORE;
import static com.eternalrealm.monster.Materials.FROST_SHARD;
import static com.eternalrealm.monster.Materials.MAGMA_CORE;
import static com.eternalrealm.monster.Materials.REROLL_CRYSTAL;
import static com.eternalrealm.monster.Materials.SCARAB_SHELL;
import static com.eternalrealm.monster.Materials.SKY_FEATHER;
import static com.eternalrealm.monster.Materials.SLIME_GEL;
import static com.eternalrealm.monster.Materials.STAR_FRAGMENT;
import static com.eternalrealm.monster.Materials.TREANT_BARK;
import static com.eternalrealm.monster.Materials.VOID_ESSENCE;
import static com.eternalrealm.monster.Materials.WOLF_FANG;
import static com.eternalrealm.monster.Materials.YETI_FUR;

/**
 * The drop tables of the initial season. Every field and dungeon monster has its own table (id = template id) with a
 * signature material of its area, the area's other material, the two upgrade materials and an equipment roll of the
 * area's tier. Boss summons use {@link #NONE_ID} so they cannot be farmed.
 *
 * <table>
 *     <caption>Rates per kind of monster</caption>
 *     <tr><th></th><th>signature</th><th>other</th><th>enhancement stone</th><th>reroll crystal</th><th>equipment</th></tr>
 *     <tr><td>field</td><td>50% ×1-2</td><td>12%</td><td>4%</td><td>0.4%</td><td>2%, base weights</td></tr>
 *     <tr><td>field elite</td><td>100% ×2-3</td><td>60% ×1-2</td><td>35% ×1-2</td><td>5%</td><td>20%, elite weights</td></tr>
 *     <tr><td>dungeon</td><td>40% ×1-2</td><td>20%</td><td>8%</td><td>0.6%</td><td>4%, base weights</td></tr>
 *     <tr><td>dungeon elite</td><td>100% ×2-3</td><td>80% ×1-2</td><td>50% ×1-2</td><td>8%</td><td>30%, elite weights</td></tr>
 * </table>
 */
public final class DropTables {
    /** Table of boss summons: drops nothing. */
    public static final String NONE_ID = "none";
    /** Rarity weights of elite equipment rolls (instead of the base 60/25/10/4/1). */
    public static final RarityWeights ELITE_WEIGHTS = new RarityWeights(20, 40, 25, 12, 3);

    private static final List<DropTable> ALL = List.of(
            DropTable.empty(NONE_ID),
            // Area 1: Kingdom of Arcelia
            field("meadow_slime", 1, SLIME_GEL, WOLF_FANG),
            field("wandering_dead", 1, WOLF_FANG, SLIME_GEL),
            field("highway_bandit", 1, WOLF_FANG, SLIME_GEL),
            fieldElite("bandit_chief", 1, WOLF_FANG, SLIME_GEL),
            // Area 2: Elder Forest
            field("forest_spider", 2, TREANT_BARK, FOREST_SPORE),
            field("vine_creeper", 2, FOREST_SPORE, TREANT_BARK),
            field("forest_witch", 2, FOREST_SPORE, TREANT_BARK),
            fieldElite("elder_druid", 2, TREANT_BARK, FOREST_SPORE),
            // Area 3: Sandria Desert
            field("sand_husk", 3, DESERT_CRYSTAL, SCARAB_SHELL),
            field("giant_scarab", 3, SCARAB_SHELL, DESERT_CRYSTAL),
            field("desert_skeleton", 3, DESERT_CRYSTAL, SCARAB_SHELL),
            fieldElite("tomb_warden", 3, DESERT_CRYSTAL, SCARAB_SHELL),
            // Area 4: Nordia Snowfield
            field("frozen_dead", 4, FROST_SHARD, YETI_FUR),
            field("snowfield_archer", 4, FROST_SHARD, YETI_FUR),
            field("frost_spider", 4, FROST_SHARD, YETI_FUR),
            fieldElite("snowfield_yeti", 4, YETI_FUR, FROST_SHARD),
            // Area 5: Dragonia Volcano
            field("lava_slime", 5, MAGMA_CORE, DRAKE_SCALE),
            field("crater_blaze", 5, MAGMA_CORE, DRAKE_SCALE),
            field("eruption_creeper", 5, MAGMA_CORE, DRAKE_SCALE),
            fieldElite("dragonkin_berserker", 5, DRAKE_SCALE, MAGMA_CORE),
            // Area 6: Celestia Sky Continent
            field("cloud_phantom", 6, SKY_FEATHER, STAR_FRAGMENT),
            field("storm_spirit", 6, STAR_FRAGMENT, SKY_FEATHER),
            field("sky_archer", 6, SKY_FEATHER, STAR_FRAGMENT),
            fieldElite("celestial_paladin", 6, STAR_FRAGMENT, SKY_FEATHER),
            // Area 7: Tower of the End
            field("void_wanderer", 7, VOID_ESSENCE, ABYSS_CRYSTAL),
            field("knight_of_the_end", 7, ABYSS_CRYSTAL, VOID_ESSENCE),
            field("doom_cultist", 7, VOID_ESSENCE, ABYSS_CRYSTAL),
            fieldElite("herald_of_ruin", 7, ABYSS_CRYSTAL, VOID_ESSENCE),
            // Field 8: 豊穣の農場 (tier 2)
            field("crop_slime", 2, FOREST_SPORE, TREANT_BARK),
            field("scarecrow_dead", 2, TREANT_BARK, FOREST_SPORE),
            field("harvest_raider", 2, TREANT_BARK, FOREST_SPORE),
            fieldElite("rampaging_bull", 2, TREANT_BARK, FOREST_SPORE),
            // Field 9: 湖畔のほとり (tier 3)
            field("marsh_slime", 3, SCARAB_SHELL, DESERT_CRYSTAL),
            field("lake_drowned", 3, DESERT_CRYSTAL, SCARAB_SHELL),
            field("reed_spider", 3, SCARAB_SHELL, DESERT_CRYSTAL),
            fieldElite("lake_witch", 3, DESERT_CRYSTAL, SCARAB_SHELL),
            // Field 10: 風の港 (tier 4)
            field("dock_drowned", 4, FROST_SHARD, YETI_FUR),
            field("pirate_cutthroat", 4, YETI_FUR, FROST_SHARD),
            field("pirate_gunner", 4, FROST_SHARD, YETI_FUR),
            fieldElite("pirate_captain", 4, YETI_FUR, FROST_SHARD),
            // Dungeon waves (tier = area of the dungeon)
            wave("d1_bandit", 1, WOLF_FANG, SLIME_GEL),
            wave("d1_crawler", 1, SLIME_GEL, WOLF_FANG),
            waveElite("d1_canal_drowned", 1, WOLF_FANG, SLIME_GEL),
            wave("d2_root_spider", 2, TREANT_BARK, FOREST_SPORE),
            wave("d2_spore_creeper", 2, FOREST_SPORE, TREANT_BARK),
            waveElite("d2_grove_priestess", 2, TREANT_BARK, FOREST_SPORE),
            wave("d3_temple_mummy", 3, DESERT_CRYSTAL, SCARAB_SHELL),
            wave("d3_scarab_swarm", 3, SCARAB_SHELL, DESERT_CRYSTAL),
            waveElite("d3_sun_priest", 3, DESERT_CRYSTAL, SCARAB_SHELL),
            wave("d4_frozen_prisoner", 4, FROST_SHARD, YETI_FUR),
            wave("d4_ice_crawler", 4, FROST_SHARD, YETI_FUR),
            waveElite("d4_jailer", 4, YETI_FUR, FROST_SHARD),
            wave("d5_castle_archer", 4, FROST_SHARD, YETI_FUR),
            wave("d5_ice_knight", 4, FROST_SHARD, YETI_FUR),
            waveElite("d5_queens_handmaiden", 4, FROST_SHARD, YETI_FUR),
            wave("d6_cinder_cube", 5, MAGMA_CORE, DRAKE_SCALE),
            wave("d6_flame_spirit", 5, MAGMA_CORE, DRAKE_SCALE),
            waveElite("d6_dragon_guard", 5, DRAKE_SCALE, MAGMA_CORE),
            wave("d7_ruin_sentry", 6, SKY_FEATHER, STAR_FRAGMENT),
            wave("d7_ancient_drone", 6, STAR_FRAGMENT, SKY_FEATHER),
            waveElite("d7_ancient_guardian", 6, STAR_FRAGMENT, SKY_FEATHER),
            wave("d8_void_acolyte", 7, VOID_ESSENCE, ABYSS_CRYSTAL),
            wave("d8_doom_guard", 7, ABYSS_CRYSTAL, VOID_ESSENCE),
            waveElite("d8_doom_sorcerer", 7, VOID_ESSENCE, ABYSS_CRYSTAL),
            waveElite("d8_tower_behemoth", 7, ABYSS_CRYSTAL, VOID_ESSENCE));

    private static final Set<String> DROPPABLE = collectItems(ALL);

    private DropTables() {
    }

    public static List<DropTable> all() {
        return ALL;
    }

    public static Optional<DropTable> byId(String id) {
        for (DropTable table : ALL) {
            if (table.id().equals(id)) {
                return Optional.of(table);
            }
        }
        return Optional.empty();
    }

    /** @throws IllegalArgumentException if there is no such table */
    public static DropTable require(String id) {
        return byId(id).orElseThrow(() -> new IllegalArgumentException("Unknown drop table " + id));
    }

    /** Every item id some table can drop (sorted, read-only); quests may only ask for these. */
    public static Set<String> droppableItemIds() {
        return DROPPABLE;
    }

    private static DropTable field(String id, int tier, String signature, String other) {
        return table(id, tier, signature, 1, 2, 0.5, other, 1, 0.12, 1, 0.04, 0.004, 0.02, RarityWeights.BASE);
    }

    private static DropTable fieldElite(String id, int tier, String signature, String other) {
        return table(id, tier, signature, 2, 3, 1.0, other, 2, 0.6, 2, 0.35, 0.05, 0.2, ELITE_WEIGHTS);
    }

    private static DropTable wave(String id, int tier, String signature, String other) {
        return table(id, tier, signature, 1, 2, 0.4, other, 1, 0.2, 1, 0.08, 0.006, 0.04, RarityWeights.BASE);
    }

    private static DropTable waveElite(String id, int tier, String signature, String other) {
        return table(id, tier, signature, 2, 3, 1.0, other, 2, 0.8, 2, 0.5, 0.08, 0.3, ELITE_WEIGHTS);
    }

    private static DropTable table(String id, int tier, String signature, int signatureMin, int signatureMax,
                                   double signatureChance, String other, int otherMax, double otherChance,
                                   int stoneMax, double stoneChance, double crystalChance, double equipmentChance,
                                   RarityWeights weights) {
        return new DropTable(id, List.of(
                new DropEntry(signature, signatureMin, signatureMax, signatureChance),
                new DropEntry(other, 1, otherMax, otherChance),
                new DropEntry(ENHANCEMENT_STONE, 1, stoneMax, stoneChance),
                new DropEntry(REROLL_CRYSTAL, 1, 1, crystalChance)),
                new EquipmentRoll(tier, equipmentChance, weights));
    }

    private static Set<String> collectItems(List<DropTable> tables) {
        List<String> ids = new ArrayList<>();
        for (DropTable table : tables) {
            for (DropEntry entry : table.entries()) {
                ids.add(entry.itemId());
            }
        }
        return Collections.unmodifiableSet(new TreeSet<>(ids));
    }
}
