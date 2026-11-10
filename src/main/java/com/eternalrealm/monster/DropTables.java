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
 *     <tr><th></th><th>signature</th><th>other</th><th>enhancement stone</th><th>reroll crystal</th><th>equipment</th>
 *     <th>health potion</th></tr>
 *     <tr><td>field</td><td>50% ×1-2</td><td>12%</td><td>4%</td><td>0.4%</td><td>2%, base weights</td><td>4%</td></tr>
 *     <tr><td>field elite</td><td>100% ×2-3</td><td>60% ×1-2</td><td>35% ×1-2</td><td>5%</td><td>20%, elite weights</td>
 *     <td>50% ×1-2</td></tr>
 *     <tr><td>dungeon</td><td>40% ×1-2</td><td>20%</td><td>8%</td><td>0.6%</td><td>4%, base weights</td><td>4%</td></tr>
 *     <tr><td>dungeon elite</td><td>100% ×2-3</td><td>80% ×1-2</td><td>50% ×1-2</td><td>8%</td><td>30%, elite weights</td>
 *     <td>50% ×1-2</td></tr>
 * </table>
 *
 * <p>Health potions are the way to heal (there is no natural regeneration), so monsters drop the size of their tier
 * ({@link #potionFor}) besides the shop's supply.</p>
 */
public final class DropTables {
    /** Table of boss summons: drops nothing. */
    public static final String NONE_ID = "none";
    /** Health potions monsters drop (item ids of {@code item.Consumable}): small, medium and large. */
    public static final List<String> POTIONS = List.of("eternalrealm:health_potion_small",
            "eternalrealm:health_potion_medium", "eternalrealm:health_potion_large");
    /** Chance of a health potion from a normal monster, and from an elite (which drops 1-2). */
    public static final double POTION_CHANCE = 0.04;
    public static final double ELITE_POTION_CHANCE = 0.5;

    /** Rarity weights of elite equipment rolls (instead of the base 60/25/10/4/1). */
    public static final RarityWeights ELITE_WEIGHTS = new RarityWeights(20, 40, 25, 12, 3);

    /** Tables of the bestiary monsters and lords (one per template, like every field monster). */
    // GENERATED BESTIARY DROPS BEGIN (tools/art_entities/bestiary_gen.py)
    private static final List<DropTable> BESTIARY = List.of(
            // field 1 (meadow), tier 1
            field("f1_horned_hare", 1, 1, WOLF_FANG, SLIME_GEL),
            field("f1_wild_boar", 1, 1, SLIME_GEL, WOLF_FANG),
            field("f1_bandit_scout", 1, 1, WOLF_FANG, SLIME_GEL),
            field("f1_goblin_pilferer", 1, 1, SLIME_GEL, WOLF_FANG),
            field("f1_meadow_wisp", 1, 1, WOLF_FANG, SLIME_GEL),
            field("f1_stray_sheepdog", 1, 1, SLIME_GEL, WOLF_FANG),
            field("f1_living_scarecrow", 1, 1, WOLF_FANG, SLIME_GEL),
            field("f1_bandit_crossbowman", 1, 1, SLIME_GEL, WOLF_FANG),
            field("f1_capling", 1, 1, WOLF_FANG, SLIME_GEL),
            field("f1_fallen_squire", 1, 1, SLIME_GEL, WOLF_FANG),
            fieldElite("lord_garm", 1, 1, WOLF_FANG, SLIME_GEL),
            fieldElite("lord_hook", 1, 1, SLIME_GEL, WOLF_FANG),
            fieldElite("lord_durn", 1, 1, WOLF_FANG, SLIME_GEL),
            // field 2 (moon_forest), tier 2
            field("f2_moonshade_wolf", 2, 2, TREANT_BARK, FOREST_SPORE),
            field("f2_twin_tailed_fox", 2, 2, FOREST_SPORE, TREANT_BARK),
            field("f2_thorn_dryad", 2, 2, TREANT_BARK, FOREST_SPORE),
            field("f2_moon_moth", 2, 2, FOREST_SPORE, TREANT_BARK),
            field("f2_fungal_brute", 2, 2, TREANT_BARK, FOREST_SPORE),
            field("f2_owl_warden", 2, 2, FOREST_SPORE, TREANT_BARK),
            field("f2_night_stalker", 2, 2, TREANT_BARK, FOREST_SPORE),
            field("f2_bark_lizard", 2, 2, FOREST_SPORE, TREANT_BARK),
            field("f2_moonbow_hunter", 2, 2, TREANT_BARK, FOREST_SPORE),
            field("f2_will_o_wisp", 2, 2, FOREST_SPORE, TREANT_BARK),
            fieldElite("lord_nebula", 2, 2, TREANT_BARK, FOREST_SPORE),
            fieldElite("lord_belladonna", 2, 2, FOREST_SPORE, TREANT_BARK),
            fieldElite("lord_lunar", 2, 2, TREANT_BARK, FOREST_SPORE),
            // field 3 (iron_mine), tier 3
            field("f3_kobold_miner", 3, 3, DESERT_CRYSTAL, SCARAB_SHELL),
            field("f3_rock_crab", 3, 3, SCARAB_SHELL, DESERT_CRYSTAL),
            field("f3_ore_golem", 3, 3, DESERT_CRYSTAL, SCARAB_SHELL),
            field("f3_dust_mummy", 3, 3, SCARAB_SHELL, DESERT_CRYSTAL),
            field("f3_giant_cave_bat", 3, 3, DESERT_CRYSTAL, SCARAB_SHELL),
            field("f3_sun_cultist", 3, 3, SCARAB_SHELL, DESERT_CRYSTAL),
            field("f3_golden_scarab", 3, 3, DESERT_CRYSTAL, SCARAB_SHELL),
            field("f3_tunnel_rat", 3, 3, SCARAB_SHELL, DESERT_CRYSTAL),
            field("f3_rust_guard", 3, 3, DESERT_CRYSTAL, SCARAB_SHELL),
            field("f3_gem_wraith", 3, 3, SCARAB_SHELL, DESERT_CRYSTAL),
            fieldElite("lord_dolga", 3, 3, DESERT_CRYSTAL, SCARAB_SHELL),
            fieldElite("lord_rustbone", 3, 3, SCARAB_SHELL, DESERT_CRYSTAL),
            fieldElite("lord_vein_eater", 3, 3, DESERT_CRYSTAL, SCARAB_SHELL),
            // field 4 (sacred_falls), tier 4
            field("f4_mist_spirit", 4, 4, FROST_SHARD, YETI_FUR),
            field("f4_falls_toad", 4, 4, YETI_FUR, FROST_SHARD),
            field("f4_kappa", 4, 4, FROST_SHARD, YETI_FUR),
            field("f4_lost_pilgrim", 4, 4, YETI_FUR, FROST_SHARD),
            field("f4_frost_heron", 4, 4, FROST_SHARD, YETI_FUR),
            field("f4_crystal_crab", 4, 4, YETI_FUR, FROST_SHARD),
            field("f4_frost_monk", 4, 4, FROST_SHARD, YETI_FUR),
            field("f4_moss_guardian", 4, 4, YETI_FUR, FROST_SHARD),
            field("f4_snow_ape", 4, 4, FROST_SHARD, YETI_FUR),
            field("f4_spray_wraith", 4, 4, YETI_FUR, FROST_SHARD),
            fieldElite("lord_goura", 4, 4, FROST_SHARD, YETI_FUR),
            fieldElite("lord_mistweaver", 4, 4, YETI_FUR, FROST_SHARD),
            fieldElite("lord_ord", 4, 4, FROST_SHARD, YETI_FUR),
            // field 5 (ancient_ruins), tier 5
            field("f5_ember_imp", 5, 5, MAGMA_CORE, DRAKE_SCALE),
            field("f5_ash_hound", 5, 5, DRAKE_SCALE, MAGMA_CORE),
            field("f5_magma_lizard", 5, 5, MAGMA_CORE, DRAKE_SCALE),
            field("f5_ruin_sentinel", 5, 5, DRAKE_SCALE, MAGMA_CORE),
            field("f5_cinder_wraith", 5, 5, MAGMA_CORE, DRAKE_SCALE),
            field("f5_relic_thief", 5, 5, DRAKE_SCALE, MAGMA_CORE),
            field("f5_flame_priest", 5, 5, MAGMA_CORE, DRAKE_SCALE),
            field("f5_obsidian_guard", 5, 5, DRAKE_SCALE, MAGMA_CORE),
            field("f5_scorch_vulture", 5, 5, MAGMA_CORE, DRAKE_SCALE),
            field("f5_lava_tortoise", 5, 5, DRAKE_SCALE, MAGMA_CORE),
            fieldElite("lord_valka", 5, 5, MAGMA_CORE, DRAKE_SCALE),
            fieldElite("lord_scorch_eye", 5, 5, DRAKE_SCALE, MAGMA_CORE),
            fieldElite("lord_agnis", 5, 5, MAGMA_CORE, DRAKE_SCALE),
            // field 6 (soaring_peaks), tier 6
            field("f6_thunder_hawk", 6, 6, SKY_FEATHER, STAR_FRAGMENT),
            field("f6_sky_knight", 6, 6, STAR_FRAGMENT, SKY_FEATHER),
            field("f6_cloud_giant", 6, 6, SKY_FEATHER, STAR_FRAGMENT),
            field("f6_gale_spirit", 6, 6, STAR_FRAGMENT, SKY_FEATHER),
            field("f6_peak_wolf", 6, 6, SKY_FEATHER, STAR_FRAGMENT),
            field("f6_storm_mage", 6, 6, STAR_FRAGMENT, SKY_FEATHER),
            field("f6_young_griffin", 6, 6, SKY_FEATHER, STAR_FRAGMENT),
            field("f6_sky_ram", 6, 6, STAR_FRAGMENT, SKY_FEATHER),
            field("f6_star_sentinel", 6, 6, SKY_FEATHER, STAR_FRAGMENT),
            field("f6_frost_titanling", 6, 6, STAR_FRAGMENT, SKY_FEATHER),
            fieldElite("lord_alvin", 6, 6, SKY_FEATHER, STAR_FRAGMENT),
            fieldElite("lord_peak_sniper", 6, 6, STAR_FRAGMENT, SKY_FEATHER),
            fieldElite("lord_zephyr", 6, 6, SKY_FEATHER, STAR_FRAGMENT),
            // field 7 (end_forest), tier 7
            field("f7_void_hound", 7, 7, VOID_ESSENCE, ABYSS_CRYSTAL),
            field("f7_blighted_treant", 7, 7, ABYSS_CRYSTAL, VOID_ESSENCE),
            field("f7_doom_knight", 7, 7, VOID_ESSENCE, ABYSS_CRYSTAL),
            field("f7_hollow_eye", 7, 7, ABYSS_CRYSTAL, VOID_ESSENCE),
            field("f7_amethyst_colossus", 7, 7, VOID_ESSENCE, ABYSS_CRYSTAL),
            field("f7_shadow_assassin", 7, 7, ABYSS_CRYSTAL, VOID_ESSENCE),
            field("f7_plague_rat", 7, 7, VOID_ESSENCE, ABYSS_CRYSTAL),
            field("f7_hex_witch", 7, 7, ABYSS_CRYSTAL, VOID_ESSENCE),
            field("f7_wandering_grudge", 7, 7, VOID_ESSENCE, ABYSS_CRYSTAL),
            field("f7_end_scorpion", 7, 7, ABYSS_CRYSTAL, VOID_ESSENCE),
            fieldElite("lord_xenos", 7, 7, VOID_ESSENCE, ABYSS_CRYSTAL),
            fieldElite("lord_black_knight", 7, 7, ABYSS_CRYSTAL, VOID_ESSENCE),
            fieldElite("lord_nihil", 7, 7, VOID_ESSENCE, ABYSS_CRYSTAL),
            // field 8 (farm), tier 2
            field("f8_field_crow", 2, 8, TREANT_BARK, FOREST_SPORE),
            field("f8_giant_mole", 2, 8, FOREST_SPORE, TREANT_BARK),
            field("f8_pumpkin_head", 2, 8, TREANT_BARK, FOREST_SPORE),
            field("f8_wild_rooster", 2, 8, FOREST_SPORE, TREANT_BARK),
            field("f8_rowdy_calf", 2, 8, TREANT_BARK, FOREST_SPORE),
            field("f8_hay_golem", 2, 8, FOREST_SPORE, TREANT_BARK),
            field("f8_lost_farmhand", 2, 8, TREANT_BARK, FOREST_SPORE),
            field("f8_giant_bee", 2, 8, FOREST_SPORE, TREANT_BARK),
            field("f8_vine_lurker", 2, 8, TREANT_BARK, FOREST_SPORE),
            field("f8_windmill_sprite", 2, 8, FOREST_SPORE, TREANT_BARK),
            fieldElite("lord_brutus", 2, 8, TREANT_BARK, FOREST_SPORE),
            fieldElite("lord_harvest_boss", 2, 8, FOREST_SPORE, TREANT_BARK),
            fieldElite("lord_strom", 2, 8, TREANT_BARK, FOREST_SPORE),
            // field 9 (lakeside), tier 3
            field("f9_marsh_frog", 3, 9, SCARAB_SHELL, DESERT_CRYSTAL),
            field("f9_lake_serpent", 3, 9, DESERT_CRYSTAL, SCARAB_SHELL),
            field("f9_egret_spirit", 3, 9, SCARAB_SHELL, DESERT_CRYSTAL),
            field("f9_bog_ghoul", 3, 9, DESERT_CRYSTAL, SCARAB_SHELL),
            field("f9_reed_witch", 3, 9, SCARAB_SHELL, DESERT_CRYSTAL),
            field("f9_great_crayfish", 3, 9, DESERT_CRYSTAL, SCARAB_SHELL),
            field("f9_mud_golem", 3, 9, SCARAB_SHELL, DESERT_CRYSTAL),
            field("f9_fisher_ghost", 3, 9, DESERT_CRYSTAL, SCARAB_SHELL),
            field("f9_giant_dragonfly", 3, 9, SCARAB_SHELL, DESERT_CRYSTAL),
            field("f9_water_imp", 3, 9, DESERT_CRYSTAL, SCARAB_SHELL),
            fieldElite("lord_melusine", 3, 9, SCARAB_SHELL, DESERT_CRYSTAL),
            fieldElite("lord_drowned_fisher", 3, 9, DESERT_CRYSTAL, SCARAB_SHELL),
            fieldElite("lord_numel", 3, 9, SCARAB_SHELL, DESERT_CRYSTAL),
            // field 10 (harbour), tier 4
            field("f10_deck_pirate", 4, 10, YETI_FUR, FROST_SHARD),
            field("f10_pirate_brute", 4, 10, FROST_SHARD, YETI_FUR),
            field("f10_beach_crab", 4, 10, YETI_FUR, FROST_SHARD),
            field("f10_sea_harpy", 4, 10, FROST_SHARD, YETI_FUR),
            field("f10_coral_golem", 4, 10, YETI_FUR, FROST_SHARD),
            field("f10_shore_monitor", 4, 10, FROST_SHARD, YETI_FUR),
            field("f10_ghost_sailor", 4, 10, YETI_FUR, FROST_SHARD),
            field("f10_smuggler", 4, 10, FROST_SHARD, YETI_FUR),
            field("f10_tide_spirit", 4, 10, YETI_FUR, FROST_SHARD),
            field("f10_sea_witch", 4, 10, FROST_SHARD, YETI_FUR),
            fieldElite("lord_barbas", 4, 10, YETI_FUR, FROST_SHARD),
            fieldElite("lord_dock_brawler", 4, 10, FROST_SHARD, YETI_FUR),
            fieldElite("lord_ankara", 4, 10, YETI_FUR, FROST_SHARD));
    // GENERATED BESTIARY DROPS END

    private static final List<DropTable> ALL = join(List.of(
            DropTable.empty(NONE_ID),
            // Area 1: Kingdom of Arcelia
            field("meadow_slime", 1, 1, SLIME_GEL, WOLF_FANG),
            field("wandering_dead", 1, 1, WOLF_FANG, SLIME_GEL),
            field("highway_bandit", 1, 1, WOLF_FANG, SLIME_GEL),
            fieldElite("bandit_chief", 1, 1, WOLF_FANG, SLIME_GEL),
            // Area 2: Elder Forest
            field("forest_spider", 2, 2, TREANT_BARK, FOREST_SPORE),
            field("vine_creeper", 2, 2, FOREST_SPORE, TREANT_BARK),
            field("forest_witch", 2, 2, FOREST_SPORE, TREANT_BARK),
            fieldElite("elder_druid", 2, 2, TREANT_BARK, FOREST_SPORE),
            // Area 3: Sandria Desert
            field("sand_husk", 3, 3, DESERT_CRYSTAL, SCARAB_SHELL),
            field("giant_scarab", 3, 3, SCARAB_SHELL, DESERT_CRYSTAL),
            field("desert_skeleton", 3, 3, DESERT_CRYSTAL, SCARAB_SHELL),
            fieldElite("tomb_warden", 3, 3, DESERT_CRYSTAL, SCARAB_SHELL),
            // Area 4: Nordia Snowfield
            field("frozen_dead", 4, 4, FROST_SHARD, YETI_FUR),
            field("snowfield_archer", 4, 4, FROST_SHARD, YETI_FUR),
            field("frost_spider", 4, 4, FROST_SHARD, YETI_FUR),
            fieldElite("snowfield_yeti", 4, 4, YETI_FUR, FROST_SHARD),
            // Area 5: Dragonia Volcano
            field("lava_slime", 5, 5, MAGMA_CORE, DRAKE_SCALE),
            field("crater_blaze", 5, 5, MAGMA_CORE, DRAKE_SCALE),
            field("eruption_creeper", 5, 5, MAGMA_CORE, DRAKE_SCALE),
            fieldElite("dragonkin_berserker", 5, 5, DRAKE_SCALE, MAGMA_CORE),
            // Area 6: Celestia Sky Continent
            field("cloud_phantom", 6, 6, SKY_FEATHER, STAR_FRAGMENT),
            field("storm_spirit", 6, 6, STAR_FRAGMENT, SKY_FEATHER),
            field("sky_archer", 6, 6, SKY_FEATHER, STAR_FRAGMENT),
            fieldElite("celestial_paladin", 6, 6, STAR_FRAGMENT, SKY_FEATHER),
            // Area 7: Tower of the End
            field("void_wanderer", 7, 7, VOID_ESSENCE, ABYSS_CRYSTAL),
            field("knight_of_the_end", 7, 7, ABYSS_CRYSTAL, VOID_ESSENCE),
            field("doom_cultist", 7, 7, VOID_ESSENCE, ABYSS_CRYSTAL),
            fieldElite("herald_of_ruin", 7, 7, ABYSS_CRYSTAL, VOID_ESSENCE),
            // Field 8: 豊穣の農場 (tier 2)
            field("crop_slime", 2, 8, FOREST_SPORE, TREANT_BARK),
            field("scarecrow_dead", 2, 8, TREANT_BARK, FOREST_SPORE),
            field("harvest_raider", 2, 8, TREANT_BARK, FOREST_SPORE),
            fieldElite("rampaging_bull", 2, 8, TREANT_BARK, FOREST_SPORE),
            // Field 9: 湖畔のほとり (tier 3)
            field("marsh_slime", 3, 9, SCARAB_SHELL, DESERT_CRYSTAL),
            field("lake_drowned", 3, 9, DESERT_CRYSTAL, SCARAB_SHELL),
            field("reed_spider", 3, 9, SCARAB_SHELL, DESERT_CRYSTAL),
            fieldElite("lake_witch", 3, 9, DESERT_CRYSTAL, SCARAB_SHELL),
            // Field 10: 風の港 (tier 4)
            field("dock_drowned", 4, 10, FROST_SHARD, YETI_FUR),
            field("pirate_cutthroat", 4, 10, YETI_FUR, FROST_SHARD),
            field("pirate_gunner", 4, 10, FROST_SHARD, YETI_FUR),
            fieldElite("pirate_captain", 4, 10, YETI_FUR, FROST_SHARD),
            // Dungeon waves (tier = area of the dungeon)
            wave("d1_bandit", 1, 1, WOLF_FANG, SLIME_GEL),
            wave("d1_crawler", 1, 1, SLIME_GEL, WOLF_FANG),
            waveElite("d1_canal_drowned", 1, 1, WOLF_FANG, SLIME_GEL),
            wave("d2_root_spider", 2, 2, TREANT_BARK, FOREST_SPORE),
            wave("d2_spore_creeper", 2, 2, FOREST_SPORE, TREANT_BARK),
            waveElite("d2_grove_priestess", 2, 2, TREANT_BARK, FOREST_SPORE),
            wave("d3_temple_mummy", 3, 3, DESERT_CRYSTAL, SCARAB_SHELL),
            wave("d3_scarab_swarm", 3, 3, SCARAB_SHELL, DESERT_CRYSTAL),
            waveElite("d3_sun_priest", 3, 3, DESERT_CRYSTAL, SCARAB_SHELL),
            wave("d4_frozen_prisoner", 4, 4, FROST_SHARD, YETI_FUR),
            wave("d4_ice_crawler", 4, 4, FROST_SHARD, YETI_FUR),
            waveElite("d4_jailer", 4, 4, YETI_FUR, FROST_SHARD),
            wave("d5_castle_archer", 4, 4, FROST_SHARD, YETI_FUR),
            wave("d5_ice_knight", 4, 4, FROST_SHARD, YETI_FUR),
            waveElite("d5_queens_handmaiden", 4, 4, FROST_SHARD, YETI_FUR),
            wave("d6_cinder_cube", 5, 5, MAGMA_CORE, DRAKE_SCALE),
            wave("d6_flame_spirit", 5, 5, MAGMA_CORE, DRAKE_SCALE),
            waveElite("d6_dragon_guard", 5, 5, DRAKE_SCALE, MAGMA_CORE),
            wave("d7_ruin_sentry", 6, 6, SKY_FEATHER, STAR_FRAGMENT),
            wave("d7_ancient_drone", 6, 6, STAR_FRAGMENT, SKY_FEATHER),
            waveElite("d7_ancient_guardian", 6, 6, STAR_FRAGMENT, SKY_FEATHER),
            wave("d8_void_acolyte", 7, 7, VOID_ESSENCE, ABYSS_CRYSTAL),
            wave("d8_doom_guard", 7, 7, ABYSS_CRYSTAL, VOID_ESSENCE),
            waveElite("d8_doom_sorcerer", 7, 7, VOID_ESSENCE, ABYSS_CRYSTAL),
            waveElite("d8_tower_behemoth", 7, 7, ABYSS_CRYSTAL, VOID_ESSENCE)), BESTIARY);

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

    private static DropTable field(String id, int tier, int field, String signature, String other) {
        return withField(table(id, tier, signature, 1, 2, 0.5, other, 1, 0.12, 1, 0.04, 0.004, 0.02, RarityWeights.BASE),
                field, 0.30, 0.12, 0.05, 0.0);
    }

    private static DropTable fieldElite(String id, int tier, int field, String signature, String other) {
        return withField(table(id, tier, signature, 2, 3, 1.0, other, 2, 0.6, 2, 0.35, 0.05, 0.2, ELITE_WEIGHTS),
                field, 0.70, 0.40, 0.30, 0.35);
    }

    private static DropTable wave(String id, int tier, int field, String signature, String other) {
        return withField(table(id, tier, signature, 1, 2, 0.4, other, 1, 0.2, 1, 0.08, 0.006, 0.04, RarityWeights.BASE),
                field, 0.20, 0.08, 0.04, 0.0);
    }

    private static DropTable waveElite(String id, int tier, int field, String signature, String other) {
        return withField(table(id, tier, signature, 2, 3, 1.0, other, 2, 0.8, 2, 0.5, 0.08, 0.3, ELITE_WEIGHTS),
                field, 0.60, 0.30, 0.25, 0.20);
    }

    /**
     * Adds the field-map materials of {@code field}: one of its two commons (which one depends on the monster, so
     * neighbours differ) at {@code primary}, the other at {@code secondary}, the uncommon one, and for elites the
     * elite material.
     */
    private static DropTable withField(DropTable table, int field, double primary, double secondary, double uncommon,
                                       double elite) {
        List<String> commons = Materials.fieldCommons(field);
        int pick = table.id().hashCode() & 1;
        List<DropEntry> entries = new ArrayList<>(table.entries());
        entries.add(new DropEntry(commons.get(pick), 1, elite > 0.0 ? 3 : 2, primary));
        entries.add(new DropEntry(commons.get(1 - pick), 1, elite > 0.0 ? 2 : 1, secondary));
        entries.add(new DropEntry(Materials.fieldUncommon(field), 1, 1, uncommon));
        if (elite > 0.0) {
            entries.add(new DropEntry(Materials.fieldElite(field), 1, 1, elite));
        }
        String potion = potionFor(table.equipment().tier());
        entries.add(elite > 0.0 ? new DropEntry(potion, 1, 2, ELITE_POTION_CHANCE) : new DropEntry(potion, 1, 1, POTION_CHANCE));
        return new DropTable(table.id(), entries, table.equipment());
    }

    /**
     * The health potion of a tier: small up to tier 2, medium for tiers 3-4 (level 15+ areas), large from tier 5 (level
     * 30+ areas), matching the potions' required levels.
     */
    public static String potionFor(int tier) {
        return POTIONS.get(tier <= 2 ? 0 : tier <= 4 ? 1 : 2);
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

    private static List<DropTable> join(List<DropTable> first, List<DropTable> second) {
        List<DropTable> all = new ArrayList<>(first);
        all.addAll(second);
        return List.copyOf(all);
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
