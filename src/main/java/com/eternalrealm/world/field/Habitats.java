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
            new Habitat("f1_horned_hare", 1, "f1_horned_hare", 52, 384, 40, 6),
            new Habitat("f1_goblin_pilferer", 1, "f1_goblin_pilferer", -272, 240, 40, 6),
            new Habitat("meadow_slime", 1, "meadow_slime", -128, 528, 40, 6),
            new Habitat("f1_wild_boar", 1, "f1_wild_boar", -356, 444, 40, 6),
            new Habitat("f1_bandit_scout", 1, "f1_bandit_scout", -212, 672, 40, 6),
            new Habitat("wandering_dead", 1, "wandering_dead", -344, 684, 40, 6),
            new Habitat("f1_capling", 1, "f1_capling", -200, 840, 40, 6),
            new Habitat("f1_stray_sheepdog", 1, "f1_stray_sheepdog", -584, 672, 40, 6),
            new Habitat("f1_meadow_wisp", 1, "f1_meadow_wisp", -464, 888, 42, 4),
            new Habitat("f1_bandit_crossbowman", 1, "f1_bandit_crossbowman", -764, 696, 40, 6),
            new Habitat("highway_bandit", 1, "highway_bandit", -452, 1080, 40, 6),
            new Habitat("f1_living_scarecrow", 1, "f1_living_scarecrow", -344, 1212, 40, 6),
            new Habitat("f1_fallen_squire", 1, "f1_fallen_squire", -644, 1152, 40, 6),
            new Habitat("meadow_slime_2", 1, "meadow_slime", -296, 360, 40, 6),
            new Habitat("wandering_dead_2", 1, "wandering_dead", -500, 480, 40, 6),
            new Habitat("highway_bandit_2", 1, "highway_bandit", -164, 1176, 40, 6),
            new Habitat("f2_moonshade_wolf", 2, "f2_moonshade_wolf", -680, -104, 40, 6),
            new Habitat("f2_bark_lizard", 2, "f2_bark_lizard", -800, 280, 40, 6),
            new Habitat("forest_spider", 2, "forest_spider", -908, -44, 40, 6),
            new Habitat("f2_twin_tailed_fox", 2, "f2_twin_tailed_fox", -428, 64, 40, 6),
            new Habitat("vine_creeper", 2, "vine_creeper", -452, 256, 40, 6),
            new Habitat("f2_moon_moth", 2, "f2_moon_moth", -1016, 172, 42, 4),
            new Habitat("f2_night_stalker", 2, "f2_night_stalker", -620, -260, 40, 6),
            new Habitat("forest_witch", 2, "forest_witch", -776, 460, 40, 6),
            new Habitat("f2_owl_warden", 2, "f2_owl_warden", -344, -116, 42, 4),
            new Habitat("f2_thorn_dryad", 2, "f2_thorn_dryad", -1040, -188, 40, 6),
            new Habitat("f2_fungal_brute", 2, "f2_fungal_brute", -1052, 388, 45, 3),
            new Habitat("f2_moonbow_hunter", 2, "f2_moonbow_hunter", -392, -296, 40, 6),
            new Habitat("f2_will_o_wisp", 2, "f2_will_o_wisp", -632, -428, 42, 4),
            new Habitat("forest_spider_2", 2, "forest_spider", -512, -68, 40, 6),
            new Habitat("vine_creeper_2", 2, "vine_creeper", -812, -188, 40, 6),
            new Habitat("forest_witch_2", 2, "forest_witch", -1076, 16, 40, 6),
            new Habitat("f3_kobold_miner", 3, "f3_kobold_miner", -1472, 612, 40, 6),
            new Habitat("f3_tunnel_rat", 3, "f3_tunnel_rat", -1424, 420, 40, 6),
            new Habitat("f3_giant_cave_bat", 3, "f3_giant_cave_bat", -1364, 900, 42, 4),
            new Habitat("f3_rock_crab", 3, "f3_rock_crab", -1556, 780, 40, 6),
            new Habitat("sand_husk", 3, "sand_husk", -1028, 936, 40, 6),
            new Habitat("f3_dust_mummy", 3, "f3_dust_mummy", -1364, 204, 40, 6),
            new Habitat("giant_scarab", 3, "giant_scarab", -1388, 1068, 40, 6),
            new Habitat("f3_golden_scarab", 3, "f3_golden_scarab", -1208, 1128, 40, 6),
            new Habitat("f3_ore_golem", 3, "f3_ore_golem", -1556, 156, 45, 3),
            new Habitat("desert_skeleton", 3, "desert_skeleton", -1592, 1104, 40, 6),
            new Habitat("f3_sun_cultist", 3, "f3_sun_cultist", -968, 1188, 40, 6),
            new Habitat("f3_rust_guard", 3, "f3_rust_guard", -1496, -12, 40, 6),
            new Habitat("f3_gem_wraith", 3, "f3_gem_wraith", -1328, -96, 42, 4),
            new Habitat("sand_husk_2", 3, "sand_husk", -1616, 492, 40, 6),
            new Habitat("giant_scarab_2", 3, "giant_scarab", -1616, 324, 40, 6),
            new Habitat("desert_skeleton_2", 3, "desert_skeleton", -1268, 24, 40, 6),
            new Habitat("f4_falls_toad", 4, "f4_falls_toad", 396, -220, 40, 6),
            new Habitat("f4_mist_spirit", 4, "f4_mist_spirit", 708, -256, 42, 4),
            new Habitat("frozen_dead", 4, "frozen_dead", 576, -496, 40, 6),
            new Habitat("f4_frost_heron", 4, "f4_frost_heron", 324, -412, 42, 4),
            new Habitat("f4_kappa", 4, "f4_kappa", 756, -436, 40, 6),
            new Habitat("snowfield_archer", 4, "snowfield_archer", 300, -100, 40, 6),
            new Habitat("f4_lost_pilgrim", 4, "f4_lost_pilgrim", 204, -304, 40, 6),
            new Habitat("f4_snow_ape", 4, "f4_snow_ape", 648, -628, 45, 3),
            new Habitat("frost_spider", 4, "frost_spider", 504, -676, 40, 6),
            new Habitat("f4_crystal_crab", 4, "f4_crystal_crab", 768, -628, 40, 6),
            new Habitat("f4_frost_monk", 4, "f4_frost_monk", 612, -724, 40, 6),
            new Habitat("f4_moss_guardian", 4, "f4_moss_guardian", 888, -604, 45, 3),
            new Habitat("f4_spray_wraith", 4, "f4_spray_wraith", 744, -748, 42, 4),
            new Habitat("frozen_dead_2", 4, "frozen_dead", 324, -304, 40, 6),
            new Habitat("snowfield_archer_2", 4, "snowfield_archer", 552, -592, 40, 6),
            new Habitat("frost_spider_2", 4, "frost_spider", 192, -400, 40, 6),
            new Habitat("f5_ember_imp", 5, "f5_ember_imp", 1120, -284, 40, 6),
            new Habitat("f5_relic_thief", 5, "f5_relic_thief", 1408, 4, 40, 6),
            new Habitat("lava_slime", 5, "lava_slime", 1252, -392, 40, 6),
            new Habitat("f5_ash_hound", 5, "f5_ash_hound", 1504, -248, 40, 6),
            new Habitat("f5_scorch_vulture", 5, "f5_scorch_vulture", 1336, 184, 42, 4),
            new Habitat("crater_blaze", 5, "crater_blaze", 1024, -440, 42, 4),
            new Habitat("f5_cinder_wraith", 5, "f5_cinder_wraith", 1180, -536, 42, 4),
            new Habitat("f5_magma_lizard", 5, "f5_magma_lizard", 1564, 160, 40, 6),
            new Habitat("eruption_creeper", 5, "eruption_creeper", 1588, -452, 40, 6),
            new Habitat("f5_lava_tortoise", 5, "f5_lava_tortoise", 1300, -644, 40, 6),
            new Habitat("f5_ruin_sentinel", 5, "f5_ruin_sentinel", 1420, 388, 45, 3),
            new Habitat("f5_flame_priest", 5, "f5_flame_priest", 1144, -716, 40, 6),
            new Habitat("f5_obsidian_guard", 5, "f5_obsidian_guard", 1624, -620, 45, 3),
            new Habitat("lava_slime_2", 5, "lava_slime", 1492, -104, 40, 6),
            new Habitat("crater_blaze_2", 5, "crater_blaze", 1396, -464, 42, 4),
            new Habitat("eruption_creeper_2", 5, "eruption_creeper", 1084, -596, 40, 6),
            new Habitat("f6_peak_wolf", 6, "f6_peak_wolf", 140, -756, 40, 6),
            new Habitat("f6_thunder_hawk", 6, "f6_thunder_hawk", 512, -852, 42, 4),
            new Habitat("cloud_phantom", 6, "cloud_phantom", 80, -648, 42, 4),
            new Habitat("f6_gale_spirit", 6, "f6_gale_spirit", -4, -912, 42, 4),
            new Habitat("f6_sky_ram", 6, "f6_sky_ram", -64, -744, 40, 6),
            new Habitat("storm_spirit", 6, "storm_spirit", 44, -480, 42, 4),
            new Habitat("f6_sky_knight", 6, "f6_sky_knight", -100, -564, 40, 6),
            new Habitat("f6_young_griffin", 6, "f6_young_griffin", -208, -696, 42, 4),
            new Habitat("sky_archer", 6, "sky_archer", -268, -888, 40, 6),
            new Habitat("f6_storm_mage", 6, "f6_storm_mage", -112, -348, 40, 6),
            new Habitat("f6_cloud_giant", 6, "f6_cloud_giant", -328, -576, 45, 3),
            new Habitat("f6_frost_titanling", 6, "f6_frost_titanling", -400, -984, 45, 3),
            new Habitat("f6_star_sentinel", 6, "f6_star_sentinel", -460, -684, 42, 4),
            new Habitat("cloud_phantom_2", 6, "cloud_phantom", 296, -1056, 42, 4),
            new Habitat("storm_spirit_2", 6, "storm_spirit", 704, -924, 42, 4),
            new Habitat("sky_archer_2", 6, "sky_archer", -148, -456, 40, 6),
            new Habitat("doom_cultist", 7, "doom_cultist", -1220, -516, 40, 6),
            new Habitat("f7_amethyst_colossus", 7, "f7_amethyst_colossus", -908, -876, 45, 3),
            new Habitat("f7_blighted_treant", 7, "f7_blighted_treant", -1316, -720, 45, 3),
            new Habitat("f7_doom_knight", 7, "f7_doom_knight", -656, -780, 40, 6),
            new Habitat("f7_end_scorpion", 7, "f7_end_scorpion", -1268, -948, 40, 6),
            new Habitat("f7_hex_witch", 7, "f7_hex_witch", -1100, -1080, 40, 6),
            new Habitat("f7_hollow_eye", 7, "f7_hollow_eye", -1532, -540, 42, 4),
            new Habitat("f7_plague_rat", 7, "f7_plague_rat", -1424, -1008, 40, 6),
            new Habitat("f7_shadow_assassin", 7, "f7_shadow_assassin", -1580, -864, 40, 6),
            new Habitat("f7_void_hound", 7, "f7_void_hound", -1292, -1224, 40, 6),
            new Habitat("f7_wandering_grudge", 7, "f7_wandering_grudge", -896, -1332, 42, 4),
            new Habitat("knight_of_the_end", 7, "knight_of_the_end", -1496, -1212, 40, 6),
            new Habitat("void_wanderer", 7, "void_wanderer", -1136, -1428, 40, 6),
            new Habitat("doom_cultist_2", 7, "doom_cultist", -764, -624, 40, 6),
            new Habitat("knight_of_the_end_2", 7, "knight_of_the_end", -1256, -1344, 40, 6),
            new Habitat("void_wanderer_2", 7, "void_wanderer", -1412, -1332, 40, 6),
            new Habitat("f8_field_crow", 8, "f8_field_crow", 784, 52, 42, 4),
            new Habitat("crop_slime", 8, "crop_slime", 964, 184, 40, 6),
            new Habitat("f8_giant_mole", 8, "f8_giant_mole", 604, 52, 40, 6),
            new Habitat("f8_wild_rooster", 8, "f8_wild_rooster", 484, 280, 42, 4),
            new Habitat("f8_rowdy_calf", 8, "f8_rowdy_calf", 832, 532, 40, 6),
            new Habitat("scarecrow_dead", 8, "scarecrow_dead", 1048, 364, 40, 6),
            new Habitat("f8_lost_farmhand", 8, "f8_lost_farmhand", 904, -56, 40, 6),
            new Habitat("f8_pumpkin_head", 8, "f8_pumpkin_head", 400, 148, 40, 6),
            new Habitat("f8_giant_bee", 8, "f8_giant_bee", 1132, 184, 42, 4),
            new Habitat("f8_hay_golem", 8, "f8_hay_golem", 1072, 508, 45, 3),
            new Habitat("harvest_raider", 8, "harvest_raider", 820, 676, 40, 6),
            new Habitat("f8_vine_lurker", 8, "f8_vine_lurker", 292, 268, 40, 6),
            new Habitat("f8_windmill_sprite", 8, "f8_windmill_sprite", 1216, 388, 42, 4),
            new Habitat("crop_slime_2", 8, "crop_slime", 688, 484, 40, 6),
            new Habitat("scarecrow_dead_2", 8, "scarecrow_dead", 736, -68, 40, 6),
            new Habitat("harvest_raider_2", 8, "harvest_raider", 1192, 280, 40, 6),
            new Habitat("f9_marsh_frog", 9, "f9_marsh_frog", 136, 600, 40, 6),
            new Habitat("marsh_slime", 9, "marsh_slime", 448, 564, 40, 6),
            new Habitat("f9_bog_ghoul", 9, "f9_bog_ghoul", 64, 768, 40, 6),
            new Habitat("f9_egret_spirit", 9, "f9_egret_spirit", 268, 408, 42, 4),
            new Habitat("lake_drowned", 9, "lake_drowned", 592, 672, 40, 6),
            new Habitat("f9_giant_dragonfly", 9, "f9_giant_dragonfly", 52, 936, 42, 4),
            new Habitat("f9_great_crayfish", 9, "f9_great_crayfish", 172, 1044, 40, 6),
            new Habitat("f9_lake_serpent", 9, "f9_lake_serpent", 388, 1080, 40, 6),
            new Habitat("reed_spider", 9, "reed_spider", 700, 852, 40, 6),
            new Habitat("f9_fisher_ghost", 9, "f9_fisher_ghost", 604, 1044, 40, 6),
            new Habitat("f9_reed_witch", 9, "f9_reed_witch", 256, 1200, 40, 6),
            new Habitat("f9_mud_golem", 9, "f9_mud_golem", 28, 1176, 45, 3),
            new Habitat("f9_water_imp", 9, "f9_water_imp", 568, 1200, 40, 6),
            new Habitat("marsh_slime_2", 9, "marsh_slime", 196, 492, 40, 6),
            new Habitat("lake_drowned_2", 9, "lake_drowned", -32, 684, 40, 6),
            new Habitat("reed_spider_2", 9, "reed_spider", 76, 1080, 40, 6),
            new Habitat("f10_beach_crab", 10, "f10_beach_crab", 1032, 744, 40, 6),
            new Habitat("f10_deck_pirate", 10, "f10_deck_pirate", 1008, 1020, 40, 6),
            new Habitat("dock_drowned", 10, "dock_drowned", 912, 912, 40, 9),
            new Habitat("f10_sea_harpy", 10, "f10_sea_harpy", 1188, 672, 42, 4),
            new Habitat("f10_shore_monitor", 10, "f10_shore_monitor", 900, 1020, 40, 6),
            new Habitat("pirate_cutthroat", 10, "pirate_cutthroat", 972, 1116, 40, 9),
            new Habitat("f10_pirate_brute", 10, "f10_pirate_brute", 1368, 684, 40, 6),
            new Habitat("f10_smuggler", 10, "f10_smuggler", 1284, 612, 40, 6),
            new Habitat("pirate_gunner", 10, "pirate_gunner", 852, 1116, 40, 9),
            new Habitat("f10_ghost_sailor", 10, "f10_ghost_sailor", 924, 1200, 40, 6),
            new Habitat("f10_coral_golem", 10, "f10_coral_golem", 1380, 576, 45, 3),
            new Habitat("f10_tide_spirit", 10, "f10_tide_spirit", 816, 1212, 42, 4),
            new Habitat("f10_sea_witch", 10, "f10_sea_witch", 1488, 564, 40, 6));
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
