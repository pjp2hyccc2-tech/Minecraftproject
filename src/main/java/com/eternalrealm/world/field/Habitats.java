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
            new Habitat("f1_horned_hare", 1, "f1_horned_hare", 28, 396, 28, 6),
            new Habitat("f1_goblin_pilferer", 1, "f1_goblin_pilferer", -272, 264, 28, 6),
            new Habitat("meadow_slime", 1, "meadow_slime", -140, 528, 28, 6),
            new Habitat("f1_wild_boar", 1, "f1_wild_boar", -392, 396, 28, 6),
            new Habitat("f1_bandit_scout", 1, "f1_bandit_scout", -236, 660, 28, 6),
            new Habitat("wandering_dead", 1, "wandering_dead", -524, 492, 28, 6),
            new Habitat("f1_capling", 1, "f1_capling", -272, 816, 28, 6),
            new Habitat("f1_stray_sheepdog", 1, "f1_stray_sheepdog", -632, 612, 28, 6),
            new Habitat("f1_meadow_wisp", 1, "f1_meadow_wisp", -308, 972, 30, 4),
            new Habitat("f1_bandit_crossbowman", 1, "f1_bandit_crossbowman", -788, 660, 28, 6),
            new Habitat("highway_bandit", 1, "highway_bandit", -272, 1152, 28, 6),
            new Habitat("f1_living_scarecrow", 1, "f1_living_scarecrow", -572, 1104, 28, 6),
            new Habitat("f1_fallen_squire", 1, "f1_fallen_squire", -740, 1080, 28, 6),
            new Habitat("meadow_slime_2", 1, "meadow_slime", -332, 336, 28, 6),
            new Habitat("wandering_dead_2", 1, "wandering_dead", -152, 768, 28, 6),
            new Habitat("highway_bandit_2", 1, "highway_bandit", -776, 816, 28, 6),
            new Habitat("f2_moonshade_wolf", 2, "f2_moonshade_wolf", -692, -92, 28, 6),
            new Habitat("f2_bark_lizard", 2, "f2_bark_lizard", -524, 196, 28, 6),
            new Habitat("forest_spider", 2, "forest_spider", -932, 40, 28, 6),
            new Habitat("f2_twin_tailed_fox", 2, "f2_twin_tailed_fox", -872, -116, 28, 6),
            new Habitat("vine_creeper", 2, "vine_creeper", -968, 208, 28, 6),
            new Habitat("f2_moon_moth", 2, "f2_moon_moth", -884, 352, 30, 4),
            new Habitat("f2_night_stalker", 2, "f2_night_stalker", -512, -212, 28, 6),
            new Habitat("forest_witch", 2, "forest_witch", -320, 52, 28, 6),
            new Habitat("f2_owl_warden", 2, "f2_owl_warden", -1100, -8, 30, 4),
            new Habitat("f2_thorn_dryad", 2, "f2_thorn_dryad", -608, -344, 28, 6),
            new Habitat("f2_fungal_brute", 2, "f2_fungal_brute", -320, -188, 32, 3),
            new Habitat("f2_moonbow_hunter", 2, "f2_moonbow_hunter", -1028, 460, 28, 6),
            new Habitat("f2_will_o_wisp", 2, "f2_will_o_wisp", -884, 580, 30, 4),
            new Habitat("forest_spider_2", 2, "forest_spider", -476, 16, 28, 6),
            new Habitat("vine_creeper_2", 2, "vine_creeper", -644, 376, 28, 6),
            new Habitat("forest_witch_2", 2, "forest_witch", -788, -284, 28, 6),
            new Habitat("f3_kobold_miner", 3, "f3_kobold_miner", -1448, 588, 28, 6),
            new Habitat("f3_tunnel_rat", 3, "f3_tunnel_rat", -1352, 396, 28, 6),
            new Habitat("f3_giant_cave_bat", 3, "f3_giant_cave_bat", -1004, 768, 30, 4),
            new Habitat("f3_rock_crab", 3, "f3_rock_crab", -1532, 444, 28, 6),
            new Habitat("sand_husk", 3, "sand_husk", -1604, 744, 28, 6),
            new Habitat("f3_dust_mummy", 3, "f3_dust_mummy", -1316, 1032, 28, 6),
            new Habitat("giant_scarab", 3, "giant_scarab", -1400, 180, 28, 6),
            new Habitat("f3_golden_scarab", 3, "f3_golden_scarab", -1016, 1068, 28, 6),
            new Habitat("f3_ore_golem", 3, "f3_ore_golem", -1568, 168, 32, 3),
            new Habitat("desert_skeleton", 3, "desert_skeleton", -1556, 1128, 28, 6),
            new Habitat("f3_sun_cultist", 3, "f3_sun_cultist", -1412, 0, 28, 6),
            new Habitat("f3_rust_guard", 3, "f3_rust_guard", -956, 1236, 28, 6),
            new Habitat("f3_gem_wraith", 3, "f3_gem_wraith", -1280, -108, 30, 4),
            new Habitat("sand_husk_2", 3, "sand_husk", -1448, 936, 28, 6),
            new Habitat("giant_scarab_2", 3, "giant_scarab", -1616, 900, 28, 6),
            new Habitat("desert_skeleton_2", 3, "desert_skeleton", -1280, 1212, 28, 6),
            new Habitat("f4_falls_toad", 4, "f4_falls_toad", 672, -244, 28, 6),
            new Habitat("f4_mist_spirit", 4, "f4_mist_spirit", 396, -172, 30, 4),
            new Habitat("frozen_dead", 4, "frozen_dead", 324, -316, 28, 6),
            new Habitat("f4_frost_heron", 4, "f4_frost_heron", 624, -508, 30, 4),
            new Habitat("f4_kappa", 4, "f4_kappa", 768, -412, 28, 6),
            new Habitat("snowfield_archer", 4, "snowfield_archer", 468, -580, 28, 6),
            new Habitat("f4_lost_pilgrim", 4, "f4_lost_pilgrim", 252, -460, 28, 6),
            new Habitat("f4_snow_ape", 4, "f4_snow_ape", 168, -280, 32, 3),
            new Habitat("frost_spider", 4, "frost_spider", 624, -664, 28, 6),
            new Habitat("f4_crystal_crab", 4, "f4_crystal_crab", 756, -640, 28, 6),
            new Habitat("f4_frost_monk", 4, "f4_frost_monk", 552, -736, 28, 6),
            new Habitat("f4_moss_guardian", 4, "f4_moss_guardian", 900, -580, 32, 3),
            new Habitat("f4_spray_wraith", 4, "f4_spray_wraith", 732, -748, 30, 4),
            new Habitat("frozen_dead_2", 4, "frozen_dead", 672, -136, 28, 6),
            new Habitat("snowfield_archer_2", 4, "snowfield_archer", 804, -184, 28, 6),
            new Habitat("frost_spider_2", 4, "frost_spider", 156, -376, 28, 6),
            new Habitat("f5_ember_imp", 5, "f5_ember_imp", 1360, -8, 28, 6),
            new Habitat("f5_relic_thief", 5, "f5_relic_thief", 1012, -128, 28, 6),
            new Habitat("lava_slime", 5, "lava_slime", 1240, 124, 28, 6),
            new Habitat("f5_ash_hound", 5, "f5_ash_hound", 1072, -380, 28, 6),
            new Habitat("f5_scorch_vulture", 5, "f5_scorch_vulture", 1252, -464, 30, 4),
            new Habitat("crater_blaze", 5, "crater_blaze", 1516, -368, 30, 4),
            new Habitat("f5_cinder_wraith", 5, "f5_cinder_wraith", 1528, 148, 30, 4),
            new Habitat("f5_magma_lizard", 5, "f5_magma_lizard", 1372, 292, 28, 6),
            new Habitat("eruption_creeper", 5, "eruption_creeper", 1480, -548, 28, 6),
            new Habitat("f5_lava_tortoise", 5, "f5_lava_tortoise", 1120, -644, 28, 6),
            new Habitat("f5_ruin_sentinel", 5, "f5_ruin_sentinel", 1276, -692, 32, 3),
            new Habitat("f5_flame_priest", 5, "f5_flame_priest", 1600, 340, 28, 6),
            new Habitat("f5_obsidian_guard", 5, "f5_obsidian_guard", 1612, -644, 32, 3),
            new Habitat("lava_slime_2", 5, "lava_slime", 1480, -188, 28, 6),
            new Habitat("crater_blaze_2", 5, "crater_blaze", 904, -308, 30, 4),
            new Habitat("eruption_creeper_2", 5, "eruption_creeper", 988, -548, 28, 6),
            new Habitat("f6_peak_wolf", 6, "f6_peak_wolf", 116, -792, 28, 6),
            new Habitat("f6_thunder_hawk", 6, "f6_thunder_hawk", 356, -1056, 30, 4),
            new Habitat("cloud_phantom", 6, "cloud_phantom", 56, -612, 30, 4),
            new Habitat("f6_gale_spirit", 6, "f6_gale_spirit", 8, -1056, 30, 4),
            new Habitat("f6_sky_ram", 6, "f6_sky_ram", -136, -720, 28, 6),
            new Habitat("storm_spirit", 6, "storm_spirit", -208, -864, 30, 4),
            new Habitat("f6_sky_knight", 6, "f6_sky_knight", -40, -348, 28, 6),
            new Habitat("f6_young_griffin", 6, "f6_young_griffin", -280, -540, 30, 4),
            new Habitat("sky_archer", 6, "sky_archer", -400, -900, 28, 6),
            new Habitat("f6_storm_mage", 6, "f6_storm_mage", -460, -684, 28, 6),
            new Habitat("f6_cloud_giant", 6, "f6_cloud_giant", 1040, -1200, 32, 3),
            new Habitat("f6_frost_titanling", 6, "f6_frost_titanling", 1160, -1080, 32, 3),
            new Habitat("f6_star_sentinel", 6, "f6_star_sentinel", 1256, -948, 30, 4),
            new Habitat("cloud_phantom_2", 6, "cloud_phantom", 584, -948, 30, 4),
            new Habitat("storm_spirit_2", 6, "storm_spirit", 728, -1092, 30, 4),
            new Habitat("sky_archer_2", 6, "sky_archer", 1004, -864, 28, 6),
            new Habitat("doom_cultist", 7, "doom_cultist", -956, -828, 28, 6),
            new Habitat("f7_amethyst_colossus", 7, "f7_amethyst_colossus", -752, -732, 32, 3),
            new Habitat("f7_blighted_treant", 7, "f7_blighted_treant", -1304, -732, 32, 3),
            new Habitat("f7_doom_knight", 7, "f7_doom_knight", -1364, -492, 28, 6),
            new Habitat("f7_end_scorpion", 7, "f7_end_scorpion", -884, -1020, 28, 6),
            new Habitat("f7_hex_witch", 7, "f7_hex_witch", -1244, -1020, 28, 6),
            new Habitat("f7_hollow_eye", 7, "f7_hollow_eye", -1532, -564, 30, 4),
            new Habitat("f7_plague_rat", 7, "f7_plague_rat", -1028, -1188, 28, 6),
            new Habitat("f7_shadow_assassin", 7, "f7_shadow_assassin", -1220, -1200, 28, 6),
            new Habitat("f7_void_hound", 7, "f7_void_hound", -1640, -852, 28, 6),
            new Habitat("f7_wandering_grudge", 7, "f7_wandering_grudge", -884, -1332, 30, 4),
            new Habitat("knight_of_the_end", 7, "knight_of_the_end", -1628, -1080, 28, 6),
            new Habitat("void_wanderer", 7, "void_wanderer", -1508, -1272, 28, 6),
            new Habitat("doom_cultist_2", 7, "doom_cultist", -1184, -468, 28, 6),
            new Habitat("knight_of_the_end_2", 7, "knight_of_the_end", -1136, -1380, 28, 6),
            new Habitat("void_wanderer_2", 7, "void_wanderer", -1316, -1380, 28, 6),
            new Habitat("f8_field_crow", 8, "f8_field_crow", 784, 64, 30, 4),
            new Habitat("crop_slime", 8, "crop_slime", 964, 244, 28, 6),
            new Habitat("f8_giant_mole", 8, "f8_giant_mole", 724, 484, 28, 6),
            new Habitat("f8_wild_rooster", 8, "f8_wild_rooster", 628, 16, 30, 4),
            new Habitat("f8_rowdy_calf", 8, "f8_rowdy_calf", 472, 316, 28, 6),
            new Habitat("scarecrow_dead", 8, "scarecrow_dead", 532, 472, 28, 6),
            new Habitat("f8_lost_farmhand", 8, "f8_lost_farmhand", 436, 124, 28, 6),
            new Habitat("f8_pumpkin_head", 8, "f8_pumpkin_head", 832, 604, 28, 6),
            new Habitat("f8_giant_bee", 8, "f8_giant_bee", 1132, 160, 30, 4),
            new Habitat("f8_hay_golem", 8, "f8_hay_golem", 1144, 388, 32, 3),
            new Habitat("harvest_raider", 8, "harvest_raider", 1024, 592, 28, 6),
            new Habitat("f8_vine_lurker", 8, "f8_vine_lurker", 292, 172, 28, 6),
            new Habitat("f8_windmill_sprite", 8, "f8_windmill_sprite", 1156, 532, 30, 4),
            new Habitat("crop_slime_2", 8, "crop_slime", 808, 460, 28, 6),
            new Habitat("scarecrow_dead_2", 8, "scarecrow_dead", 964, 16, 28, 6),
            new Habitat("harvest_raider_2", 8, "harvest_raider", 1192, 268, 28, 6),
            new Habitat("f9_marsh_frog", 9, "f9_marsh_frog", 112, 708, 28, 6),
            new Habitat("marsh_slime", 9, "marsh_slime", 124, 540, 28, 6),
            new Habitat("f9_bog_ghoul", 9, "f9_bog_ghoul", 280, 432, 28, 6),
            new Habitat("f9_egret_spirit", 9, "f9_egret_spirit", 544, 576, 30, 4),
            new Habitat("lake_drowned", 9, "lake_drowned", 592, 732, 28, 6),
            new Habitat("f9_giant_dragonfly", 9, "f9_giant_dragonfly", -44, 804, 30, 4),
            new Habitat("f9_great_crayfish", 9, "f9_great_crayfish", 64, 996, 28, 6),
            new Habitat("f9_lake_serpent", 9, "f9_lake_serpent", 208, 1092, 28, 6),
            new Habitat("reed_spider", 9, "reed_spider", 700, 864, 28, 6),
            new Habitat("f9_fisher_ghost", 9, "f9_fisher_ghost", 412, 1152, 28, 6),
            new Habitat("f9_reed_witch", 9, "f9_reed_witch", 592, 1104, 28, 6),
            new Habitat("f9_mud_golem", 9, "f9_mud_golem", -20, 1152, 32, 3),
            new Habitat("f9_water_imp", 9, "f9_water_imp", 748, 1056, 28, 6),
            new Habitat("marsh_slime_2", 9, "marsh_slime", 400, 504, 28, 6),
            new Habitat("lake_drowned_2", 9, "lake_drowned", -32, 660, 28, 6),
            new Habitat("reed_spider_2", 9, "reed_spider", -44, 996, 28, 6),
            new Habitat("f10_beach_crab", 10, "f10_beach_crab", 972, 804, 28, 6),
            new Habitat("f10_deck_pirate", 10, "f10_deck_pirate", 1008, 1020, 28, 6),
            new Habitat("dock_drowned", 10, "dock_drowned", 1236, 732, 28, 6),
            new Habitat("f10_sea_harpy", 10, "f10_sea_harpy", 912, 996, 30, 4),
            new Habitat("f10_shore_monitor", 10, "f10_shore_monitor", 1188, 648, 28, 6),
            new Habitat("pirate_cutthroat", 10, "pirate_cutthroat", 972, 1116, 28, 6),
            new Habitat("f10_pirate_brute", 10, "f10_pirate_brute", 1344, 708, 28, 6),
            new Habitat("f10_smuggler", 10, "f10_smuggler", 864, 1092, 28, 6),
            new Habitat("pirate_gunner", 10, "pirate_gunner", 1296, 588, 28, 6),
            new Habitat("f10_ghost_sailor", 10, "f10_ghost_sailor", 900, 1188, 28, 6),
            new Habitat("f10_coral_golem", 10, "f10_coral_golem", 1404, 624, 32, 3),
            new Habitat("f10_tide_spirit", 10, "f10_tide_spirit", 804, 1176, 30, 4),
            new Habitat("f10_sea_witch", 10, "f10_sea_witch", 1392, 540, 28, 6),
            new Habitat("dock_drowned_2", 10, "dock_drowned", 900, 900, 28, 6),
            new Habitat("pirate_cutthroat_2", 10, "pirate_cutthroat", 1080, 696, 28, 6),
            new Habitat("pirate_gunner_2", 10, "pirate_gunner", 996, 720, 28, 6));
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
