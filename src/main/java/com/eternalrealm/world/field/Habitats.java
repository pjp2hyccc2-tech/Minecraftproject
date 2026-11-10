package com.eternalrealm.world.field;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * The fixed home grounds of the field monsters (生息地): every normal monster of a field lives in its own place (the
 * common ones in two), so players know where to level and where to farm a material. A habitat keeps its population
 * filled while a player is near (a killed monster comes back after a short while), its monsters stay close to it and
 * give up a chase far from it. Within a field the weaker kinds live near the field's waystone and the stronger ones
 * further in. Now and then a field's elite turns up in a habitat instead.
 *
 * <p>The table is generated once from the terrain ({@code FieldSitesGenerator} in the tests) and kept fixed here, so
 * the places never move between versions: on land, inside the field, clear of the capital, the lords' territories, the
 * dungeon entrances, the waystones, the set pieces and each other ({@code HabitatsTest}).</p>
 */
public final class Habitats {
    /** How close a player must be for a habitat to fill up. */
    public static final int ACTIVE_RADIUS = 72;
    /** A monster without a target farther than this beyond its habitat's edge walks back. */
    public static final int WANDER_MARGIN = 8;
    /** A monster chasing a player farther than this beyond its habitat's edge gives up and walks back. */
    public static final int LEASH_MARGIN = 40;

    /**
     * @param id         stable id ({@code f<field>_<monster>[_2]})
     * @param monster    the monster template id (monster.Monsters)
     * @param radius     blocks around (x, z) where it spawns
     * @param population how many live there at most
     */
    public record Habitat(String id, int field, String monster, int x, int z, int radius, int population) {
        public Habitat {
            if (id == null || id.isEmpty() || monster == null || monster.isEmpty()) {
                throw new IllegalArgumentException("habitat without id or monster");
            }
            if (field < 1 || field > FieldLayout.FIELD_COUNT || radius < 8 || radius > 64 || population < 1
                    || population > 12) {
                throw new IllegalArgumentException("bad habitat " + id);
            }
        }

        public double distanceTo(double px, double pz) {
            return Math.hypot(px - x, pz - z);
        }
    }

    // GENERATED HABITATS BEGIN (FieldSitesGenerator)
    private static final List<Habitat> ALL = List.of(
            new Habitat("f1_f1_horned_hare", 1, "f1_horned_hare", 148, 336, 28, 6),
            new Habitat("f1_f1_goblin_pilferer", 1, "f1_goblin_pilferer", -200, 396, 28, 6),
            new Habitat("f1_meadow_slime", 1, "meadow_slime", -56, 552, 28, 6),
            new Habitat("f1_f1_wild_boar", 1, "f1_wild_boar", -392, 396, 28, 6),
            new Habitat("f1_f1_bandit_scout", 1, "f1_bandit_scout", -236, 660, 28, 6),
            new Habitat("f1_wandering_dead", 1, "wandering_dead", -524, 492, 28, 6),
            new Habitat("f1_f1_capling", 1, "f1_capling", -272, 816, 28, 6),
            new Habitat("f1_f1_stray_sheepdog", 1, "f1_stray_sheepdog", -632, 612, 28, 6),
            new Habitat("f1_f1_meadow_wisp", 1, "f1_meadow_wisp", -308, 972, 30, 4),
            new Habitat("f1_f1_bandit_crossbowman", 1, "f1_bandit_crossbowman", -740, 732, 28, 6),
            new Habitat("f1_highway_bandit", 1, "highway_bandit", -452, 1080, 28, 6),
            new Habitat("f1_f1_living_scarecrow", 1, "f1_living_scarecrow", -764, 948, 28, 6),
            new Habitat("f1_f1_fallen_squire", 1, "f1_fallen_squire", -584, 1188, 28, 6),
            new Habitat("f1_meadow_slime_2", 1, "meadow_slime", -344, 312, 28, 6),
            new Habitat("f1_wandering_dead_2", 1, "wandering_dead", -152, 768, 28, 6),
            new Habitat("f1_highway_bandit_2", 1, "highway_bandit", -224, 1164, 28, 6),
            new Habitat("f2_f2_moonshade_wolf", 2, "f2_moonshade_wolf", -824, -44, 28, 6),
            new Habitat("f2_f2_bark_lizard", 2, "f2_bark_lizard", -908, 100, 28, 6),
            new Habitat("f2_forest_spider", 2, "forest_spider", -464, 112, 28, 6),
            new Habitat("f2_f2_twin_tailed_fox", 2, "f2_twin_tailed_fox", -692, 352, 28, 6),
            new Habitat("f2_vine_creeper", 2, "vine_creeper", -932, 268, 28, 6),
            new Habitat("f2_f2_moon_moth", 2, "f2_moon_moth", -668, -236, 30, 4),
            new Habitat("f2_f2_night_stalker", 2, "f2_night_stalker", -440, -152, 28, 6),
            new Habitat("f2_forest_witch", 2, "forest_witch", -1076, 148, 28, 6),
            new Habitat("f2_f2_owl_warden", 2, "f2_owl_warden", -296, 16, 30, 4),
            new Habitat("f2_f2_thorn_dryad", 2, "f2_thorn_dryad", -1052, -176, 28, 6),
            new Habitat("f2_f2_fungal_brute", 2, "f2_fungal_brute", -1076, 364, 32, 3),
            new Habitat("f2_f2_moonbow_hunter", 2, "f2_moonbow_hunter", -452, -344, 28, 6),
            new Habitat("f2_f2_will_o_wisp", 2, "f2_will_o_wisp", -908, 568, 30, 4),
            new Habitat("f2_forest_spider_2", 2, "forest_spider", -584, -116, 28, 6),
            new Habitat("f2_vine_creeper_2", 2, "vine_creeper", -488, 292, 28, 6),
            new Habitat("f2_forest_witch_2", 2, "forest_witch", -824, -272, 28, 6),
            new Habitat("f3_f3_kobold_miner", 3, "f3_kobold_miner", -1448, 588, 28, 6),
            new Habitat("f3_f3_tunnel_rat", 3, "f3_tunnel_rat", -1352, 396, 28, 6),
            new Habitat("f3_f3_giant_cave_bat", 3, "f3_giant_cave_bat", -1496, 768, 30, 4),
            new Habitat("f3_f3_rock_crab", 3, "f3_rock_crab", -1532, 444, 28, 6),
            new Habitat("f3_sand_husk", 3, "sand_husk", -1376, 972, 28, 6),
            new Habitat("f3_f3_dust_mummy", 3, "f3_dust_mummy", -1184, 1032, 28, 6),
            new Habitat("f3_giant_scarab", 3, "giant_scarab", -1400, 180, 28, 6),
            new Habitat("f3_f3_golden_scarab", 3, "f3_golden_scarab", -1016, 1068, 28, 6),
            new Habitat("f3_f3_ore_golem", 3, "f3_ore_golem", -1316, 1164, 32, 3),
            new Habitat("f3_desert_skeleton", 3, "desert_skeleton", -1484, 1164, 28, 6),
            new Habitat("f3_f3_sun_cultist", 3, "f3_sun_cultist", -1616, 96, 28, 6),
            new Habitat("f3_f3_rust_guard", 3, "f3_rust_guard", -1316, -60, 28, 6),
            new Habitat("f3_f3_gem_wraith", 3, "f3_gem_wraith", -1472, -72, 30, 4),
            new Habitat("f3_sand_husk_2", 3, "sand_husk", -1004, 900, 28, 6),
            new Habitat("f3_giant_scarab_2", 3, "giant_scarab", -1616, 900, 28, 6),
            new Habitat("f3_desert_skeleton_2", 3, "desert_skeleton", -1136, 1200, 28, 6),
            new Habitat("f4_f4_falls_toad", 4, "f4_falls_toad", 408, -244, 28, 6),
            new Habitat("f4_f4_mist_spirit", 4, "f4_mist_spirit", 660, -196, 30, 4),
            new Habitat("f4_frozen_dead", 4, "frozen_dead", 384, -412, 28, 6),
            new Habitat("f4_f4_frost_heron", 4, "f4_frost_heron", 612, -496, 30, 4),
            new Habitat("f4_f4_kappa", 4, "f4_kappa", 768, -376, 28, 6),
            new Habitat("f4_snowfield_archer", 4, "snowfield_archer", 300, -124, 28, 6),
            new Habitat("f4_f4_lost_pilgrim", 4, "f4_lost_pilgrim", 216, -316, 28, 6),
            new Habitat("f4_f4_snow_ape", 4, "f4_snow_ape", 324, -568, 32, 3),
            new Habitat("f4_frost_spider", 4, "frost_spider", 576, -664, 28, 6),
            new Habitat("f4_f4_crystal_crab", 4, "f4_crystal_crab", 756, -628, 28, 6),
            new Habitat("f4_f4_frost_monk", 4, "f4_frost_monk", 84, -280, 28, 6),
            new Habitat("f4_f4_moss_guardian", 4, "f4_moss_guardian", 900, -580, 32, 3),
            new Habitat("f4_f4_spray_wraith", 4, "f4_spray_wraith", 732, -748, 30, 4),
            new Habitat("f4_frozen_dead_2", 4, "frozen_dead", 420, -136, 28, 6),
            new Habitat("f4_snowfield_archer_2", 4, "snowfield_archer", 468, -568, 28, 6),
            new Habitat("f4_frost_spider_2", 4, "frost_spider", 852, -460, 28, 6),
            new Habitat("f5_f5_ember_imp", 5, "f5_ember_imp", 1120, -272, 28, 6),
            new Habitat("f5_f5_relic_thief", 5, "f5_relic_thief", 1384, -284, 28, 6),
            new Habitat("f5_lava_slime", 5, "lava_slime", 976, -116, 28, 6),
            new Habitat("f5_f5_ash_hound", 5, "f5_ash_hound", 1252, 160, 28, 6),
            new Habitat("f5_f5_scorch_vulture", 5, "f5_scorch_vulture", 1108, -440, 30, 4),
            new Habitat("f5_crater_blaze", 5, "crater_blaze", 1264, -500, 30, 4),
            new Habitat("f5_f5_cinder_wraith", 5, "f5_cinder_wraith", 1624, -236, 30, 4),
            new Habitat("f5_f5_magma_lizard", 5, "f5_magma_lizard", 1324, 304, 28, 6),
            new Habitat("f5_eruption_creeper", 5, "eruption_creeper", 1432, -572, 28, 6),
            new Habitat("f5_f5_lava_tortoise", 5, "f5_lava_tortoise", 1612, -488, 28, 6),
            new Habitat("f5_f5_ruin_sentinel", 5, "f5_ruin_sentinel", 1204, -692, 32, 3),
            new Habitat("f5_f5_flame_priest", 5, "f5_flame_priest", 1420, 436, 28, 6),
            new Habitat("f5_f5_obsidian_guard", 5, "f5_obsidian_guard", 1420, -740, 32, 3),
            new Habitat("f5_lava_slime_2", 5, "lava_slime", 1480, -80, 28, 6),
            new Habitat("f5_crater_blaze_2", 5, "crater_blaze", 1456, 160, 30, 4),
            new Habitat("f5_eruption_creeper_2", 5, "eruption_creeper", 1624, 148, 28, 6),
            new Habitat("f6_f6_peak_wolf", 6, "f6_peak_wolf", 116, -792, 28, 6),
            new Habitat("f6_f6_thunder_hawk", 6, "f6_thunder_hawk", 224, -576, 30, 4),
            new Habitat("f6_cloud_phantom", 6, "cloud_phantom", 404, -1104, 30, 4),
            new Habitat("f6_f6_gale_spirit", 6, "f6_gale_spirit", -40, -660, 30, 4),
            new Habitat("f6_f6_sky_ram", 6, "f6_sky_ram", 728, -888, 28, 6),
            new Habitat("f6_storm_spirit", 6, "storm_spirit", -196, -780, 30, 4),
            new Habitat("f6_f6_sky_knight", 6, "f6_sky_knight", -28, -360, 28, 6),
            new Habitat("f6_f6_young_griffin", 6, "f6_young_griffin", -280, -588, 30, 4),
            new Habitat("f6_sky_archer", 6, "sky_archer", -376, -912, 28, 6),
            new Habitat("f6_f6_storm_mage", 6, "f6_storm_mage", -184, -1380, 28, 6),
            new Habitat("f6_f6_cloud_giant", 6, "f6_cloud_giant", -508, -792, 32, 3),
            new Habitat("f6_f6_frost_titanling", 6, "f6_frost_titanling", -520, -1104, 32, 3),
            new Habitat("f6_f6_star_sentinel", 6, "f6_star_sentinel", 1220, -972, 30, 4),
            new Habitat("f6_cloud_phantom_2", 6, "cloud_phantom", 572, -960, 30, 4),
            new Habitat("f6_storm_spirit_2", 6, "storm_spirit", -124, -1068, 30, 4),
            new Habitat("f6_sky_archer_2", 6, "sky_archer", 980, -888, 28, 6),
            new Habitat("f7_doom_cultist", 7, "doom_cultist", -1184, -720, 28, 6),
            new Habitat("f7_f7_amethyst_colossus", 7, "f7_amethyst_colossus", -932, -864, 32, 3),
            new Habitat("f7_f7_blighted_treant", 7, "f7_blighted_treant", -680, -588, 32, 3),
            new Habitat("f7_f7_doom_knight", 7, "f7_doom_knight", -1364, -528, 28, 6),
            new Habitat("f7_f7_end_scorpion", 7, "f7_end_scorpion", -1400, -744, 28, 6),
            new Habitat("f7_f7_hex_witch", 7, "f7_hex_witch", -1328, -948, 28, 6),
            new Habitat("f7_f7_hollow_eye", 7, "f7_hollow_eye", -944, -1128, 30, 4),
            new Habitat("f7_f7_plague_rat", 7, "f7_plague_rat", -1556, -444, 28, 6),
            new Habitat("f7_f7_shadow_assassin", 7, "f7_shadow_assassin", -1484, -1008, 28, 6),
            new Habitat("f7_f7_void_hound", 7, "f7_void_hound", -1640, -840, 28, 6),
            new Habitat("f7_f7_wandering_grudge", 7, "f7_wandering_grudge", -1244, -1296, 30, 4),
            new Habitat("f7_knight_of_the_end", 7, "knight_of_the_end", -1412, -1272, 28, 6),
            new Habitat("f7_void_wanderer", 7, "void_wanderer", -956, -1440, 28, 6),
            new Habitat("f7_doom_cultist_2", 7, "doom_cultist", -1064, -396, 28, 6),
            new Habitat("f7_knight_of_the_end_2", 7, "knight_of_the_end", -812, -1368, 28, 6),
            new Habitat("f7_void_wanderer_2", 7, "void_wanderer", -1640, -1140, 28, 6),
            new Habitat("f8_f8_field_crow", 8, "f8_field_crow", 892, 280, 30, 4),
            new Habitat("f8_crop_slime", 8, "crop_slime", 736, 76, 28, 6),
            new Habitat("f8_f8_giant_mole", 8, "f8_giant_mole", 904, 112, 28, 6),
            new Habitat("f8_f8_wild_rooster", 8, "f8_wild_rooster", 700, 472, 30, 4),
            new Habitat("f8_f8_rowdy_calf", 8, "f8_rowdy_calf", 496, 280, 28, 6),
            new Habitat("f8_scarecrow_dead", 8, "scarecrow_dead", 532, 64, 28, 6),
            new Habitat("f8_f8_lost_farmhand", 8, "f8_lost_farmhand", 1036, 388, 28, 6),
            new Habitat("f8_f8_pumpkin_head", 8, "f8_pumpkin_head", 808, -92, 28, 6),
            new Habitat("f8_f8_giant_bee", 8, "f8_giant_bee", 1120, 184, 30, 4),
            new Habitat("f8_f8_hay_golem", 8, "f8_hay_golem", 844, 640, 32, 3),
            new Habitat("f8_harvest_raider", 8, "harvest_raider", 1024, 580, 28, 6),
            new Habitat("f8_f8_vine_lurker", 8, "f8_vine_lurker", 304, 148, 28, 6),
            new Habitat("f8_f8_windmill_sprite", 8, "f8_windmill_sprite", 1192, 460, 30, 4),
            new Habitat("f8_crop_slime_2", 8, "crop_slime", 928, 196, 28, 6),
            new Habitat("f8_scarecrow_dead_2", 8, "scarecrow_dead", 568, 472, 28, 6),
            new Habitat("f8_harvest_raider_2", 8, "harvest_raider", 1180, 316, 28, 6),
            new Habitat("f9_f9_marsh_frog", 9, "f9_marsh_frog", 208, 552, 28, 6),
            new Habitat("f9_marsh_slime", 9, "marsh_slime", 100, 672, 28, 6),
            new Habitat("f9_f9_bog_ghoul", 9, "f9_bog_ghoul", 472, 564, 28, 6),
            new Habitat("f9_f9_egret_spirit", 9, "f9_egret_spirit", 364, 432, 30, 4),
            new Habitat("f9_lake_drowned", 9, "lake_drowned", 28, 828, 28, 6),
            new Habitat("f9_f9_giant_dragonfly", 9, "f9_giant_dragonfly", 616, 672, 30, 4),
            new Habitat("f9_f9_great_crayfish", 9, "f9_great_crayfish", 88, 996, 28, 6),
            new Habitat("f9_f9_lake_serpent", 9, "f9_lake_serpent", 220, 1080, 28, 6),
            new Habitat("f9_reed_spider", 9, "reed_spider", 412, 1104, 28, 6),
            new Habitat("f9_f9_fisher_ghost", 9, "f9_fisher_ghost", 568, 1068, 28, 6),
            new Habitat("f9_f9_reed_witch", 9, "f9_reed_witch", 712, 972, 28, 6),
            new Habitat("f9_f9_mud_golem", 9, "f9_mud_golem", 316, 1236, 32, 3),
            new Habitat("f9_f9_water_imp", 9, "f9_water_imp", -8, 1200, 28, 6),
            new Habitat("f9_marsh_slime_2", 9, "marsh_slime", 388, 528, 28, 6),
            new Habitat("f9_lake_drowned_2", 9, "lake_drowned", 268, 384, 28, 6),
            new Habitat("f9_reed_spider_2", 9, "reed_spider", 712, 816, 28, 6),
            new Habitat("f10_f10_beach_crab", 10, "f10_beach_crab", 1116, 756, 28, 6),
            new Habitat("f10_f10_deck_pirate", 10, "f10_deck_pirate", 960, 828, 28, 6),
            new Habitat("f10_dock_drowned", 10, "dock_drowned", 1056, 1044, 28, 6),
            new Habitat("f10_f10_sea_harpy", 10, "f10_sea_harpy", 936, 996, 30, 4),
            new Habitat("f10_f10_shore_monitor", 10, "f10_shore_monitor", 1272, 720, 28, 6),
            new Habitat("f10_pirate_cutthroat", 10, "pirate_cutthroat", 1212, 648, 28, 6),
            new Habitat("f10_f10_pirate_brute", 10, "f10_pirate_brute", 960, 1116, 28, 6),
            new Habitat("f10_f10_smuggler", 10, "f10_smuggler", 864, 1068, 28, 6),
            new Habitat("f10_pirate_gunner", 10, "pirate_gunner", 1320, 624, 28, 6),
            new Habitat("f10_f10_ghost_sailor", 10, "f10_ghost_sailor", 888, 1164, 28, 6),
            new Habitat("f10_f10_coral_golem", 10, "f10_coral_golem", 1404, 636, 32, 3),
            new Habitat("f10_f10_tide_spirit", 10, "f10_tide_spirit", 1368, 552, 30, 4),
            new Habitat("f10_f10_sea_witch", 10, "f10_sea_witch", 804, 1200, 28, 6),
            new Habitat("f10_dock_drowned_2", 10, "dock_drowned", 1020, 720, 28, 6),
            new Habitat("f10_pirate_cutthroat_2", 10, "pirate_cutthroat", 900, 912, 28, 6),
            new Habitat("f10_pirate_gunner_2", 10, "pirate_gunner", 1188, 804, 28, 6));
    // GENERATED HABITATS END

    private Habitats() {
    }

    public static List<Habitat> all() {
        return ALL;
    }

    public static List<Habitat> ofField(int field) {
        List<Habitat> out = new ArrayList<>();
        for (Habitat habitat : ALL) {
            if (habitat.field() == field) {
                out.add(habitat);
            }
        }
        return Collections.unmodifiableList(out);
    }

    public static Optional<Habitat> byId(String id) {
        for (Habitat habitat : ALL) {
            if (habitat.id().equals(id)) {
                return Optional.of(habitat);
            }
        }
        return Optional.empty();
    }

    /** Habitats whose edge is within {@code distance} of (x, z). */
    public static List<Habitat> near(double x, double z, double distance) {
        List<Habitat> out = new ArrayList<>();
        for (Habitat habitat : ALL) {
            if (habitat.distanceTo(x, z) - habitat.radius() <= distance) {
                out.add(habitat);
            }
        }
        return out;
    }

    /** The habitats of {@code monster} (a template id). */
    public static List<Habitat> of(String monster) {
        List<Habitat> out = new ArrayList<>();
        for (Habitat habitat : ALL) {
            if (habitat.monster().equals(monster)) {
                out.add(habitat);
            }
        }
        return out;
    }
}
