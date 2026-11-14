package com.eternalrealm.boss;

import com.eternalrealm.boss.AbilityKind.CircleAroundBoss;
import com.eternalrealm.boss.AbilityKind.Part;
import com.eternalrealm.combat.Telegraph;

import java.util.ArrayList;
import java.util.List;

import static com.eternalrealm.boss.TargetRule.FARTHEST;
import static com.eternalrealm.boss.TargetRule.HIGHEST_THREAT;
import static com.eternalrealm.boss.TargetRule.NEAREST;
import static com.eternalrealm.boss.TargetRule.RANDOM;

/**
 * The bosses of the field (docs/modules/boss.md §8): the ten 災厄 (one per field, three forms, fought at their lair by
 * everyone nearby) and the fifteen 放浪する大物 (wandering bosses that turn up now and then, two forms). Open-world
 * fights: balanced for two or more fighters on {@link Difficulty#NORMAL}, every attack fits the lair (radius 20).
 *
 * <p>Each boss is a list of moves; its first form uses the first moves, every later form adds more and opens with
 * the newest; the move before the last is only used on EX, the last is the roar of its transformations. Ability ids
 * are {@code <boss>_<move>}.</p>
 *
 * <p>The calamities got four more moves each on 2026-11-14 (twelve, used 5 / 8 / 12 by their forms), blows half again
 * as strong and the party they are sized for raised to five ({@code world.field.ThreatPower}, 「ヌシと災厄も弱すぎる。
 * 攻撃の種類とHPなども増やして…5人くらい集まらないと倒せないレベルにして」).</p>
 */
public final class FieldBosses {
    private static final double PARTY_SCALING = 0.55;
    /**
     * Field bosses are crowd fights (everyone nearby joins), so the owner raised their health by half (2026-11-11,
     * 「全レイドボスのHPを今の1.5倍に」): the health numbers below are the earlier ones, this factor applies on top.
     */
    public static final double RAID_HEALTH = 1.5;
    /** Soft enrage after 9 minutes: grown with the health so the enrage still comes well after a normal fight. */
    private static final int ENRAGE_TICKS = 20 * 60 * 9;
    /**
     * The calamities' soft enrage: 12 minutes. They are sized for five fighters (2026-11-14), so one alone fights much
     * longer; the enrage makes that harder, never impossible.
     */
    private static final int CALAMITY_ENRAGE_TICKS = 20 * 60 * 12;

    /** The ten calamities, in field order (fields 1..10). */
    public static final List<BossDefinition> CALAMITIES = List.of(meadow(), moonForest(), ironMine(), sacredFalls(),
            ancientRuins(), soaringPeaks(), endForest(), farm(), lakeside(), harbour());
    /** The fifteen wanderers. */
    public static final List<BossDefinition> WANDERERS = List.of(brambleKnight(), moonmothWitch(), slagTitan(),
            cascadeWyvern(), mistHermit(), walkingGlyph(), ashHerald(), thunderRoc(), glacierBearer(), hollowPilgrim(),
            gravebloom(), scarecrowKing(), reedSerpent(), sunkenCaptain(), tidePriestess());

    private FieldBosses() {
    }

    /** What each field boss is made of: the colour of its markers and the block its debris shows. */
    private static final java.util.Map<String, String> ELEMENT = java.util.Map.ofEntries(
            java.util.Map.entry("c_meadow", "shadow"), java.util.Map.entry("c_moon_forest", "moon"),
            java.util.Map.entry("c_iron_mine", "forge"), java.util.Map.entry("c_sacred_falls", "water"),
            java.util.Map.entry("c_ancient_ruins", "fire"), java.util.Map.entry("c_summit", "stone"),
            java.util.Map.entry("c_end_forest", "void"), java.util.Map.entry("c_farm", "blight"),
            java.util.Map.entry("c_lakeside", "water"), java.util.Map.entry("c_harbour", "storm"),
            java.util.Map.entry("w_bramble_knight", "blight"), java.util.Map.entry("w_moonmoth_witch", "moon"),
            java.util.Map.entry("w_slag_titan", "forge"), java.util.Map.entry("w_cascade_wyvern", "water"),
            java.util.Map.entry("w_mist_hermit", "moon"), java.util.Map.entry("w_walking_glyph", "stone"),
            java.util.Map.entry("w_ash_herald", "fire"), java.util.Map.entry("w_thunder_roc", "storm"),
            java.util.Map.entry("w_glacier_bearer", "ice"), java.util.Map.entry("w_hollow_walker", "void"),
            java.util.Map.entry("w_gravebloom", "shadow"), java.util.Map.entry("w_scarecrow_king", "blight"),
            java.util.Map.entry("w_reed_serpent", "water"), java.util.Map.entry("w_sunken_captain", "water"),
            java.util.Map.entry("w_tide_singer", "water"));

    /** The element of a field boss ("shadow", "moon", "forge", "water", "fire", "stone", "void", "blight", ...). */
    public static String element(String bossId) {
        return ELEMENT.getOrDefault(bossId, "stone");
    }

    /** The marker colour of a field boss's attacks, by its element. */
    public static Telegraph.Style style(String bossId) {
        return switch (element(bossId)) {
            case "shadow", "void" -> Telegraph.Style.DARK;
            case "moon", "storm" -> Telegraph.Style.MAGIC;
            case "forge", "fire" -> Telegraph.Style.FIRE;
            case "water", "ice" -> Telegraph.Style.FROST;
            default -> Telegraph.Style.DANGER;
        };
    }

    /** The block of the debris and dust of a field boss's attacks, by its element. */
    public static String block(String bossId) {
        return switch (element(bossId)) {
            case "shadow" -> "minecraft:black_concrete_powder";
            case "moon" -> "minecraft:calcite";
            case "forge" -> "minecraft:magma_block";
            case "water" -> "minecraft:prismarine";
            case "fire" -> "minecraft:netherrack";
            case "void" -> "minecraft:obsidian";
            case "blight" -> "minecraft:rooted_dirt";
            case "storm" -> "minecraft:light_blue_concrete";
            case "ice" -> "minecraft:packed_ice";
            default -> "minecraft:cobblestone";
        };
    }

    public static List<BossDefinition> all() {
        List<BossDefinition> all = new ArrayList<>(CALAMITIES);
        all.addAll(WANDERERS);
        return List.copyOf(all);
    }

    // ------------------------------------------------------------------ the calamities (three forms)

    /** 草原を呑む影: a veiled shade that drinks the light of the meadow and spreads its darkness in pools. */
    private static BossDefinition meadow() {
        return boss("c_meadow", 15, 9000, 7, 4, new int[]{5, 8, 12},
                m("veil_sweep", new AbilityKind.Cone(7, 60), 24, 90, 1.5, 30, NEAREST),
                m("shadow_pools", new AbilityKind.CircleAtTargets(3.5, 3), 30, 180, 1.4, 22, RANDOM),
                m("umbral_lance", new AbilityKind.Line(16, 3), 26, 140, 1.7, 22, FARTHEST),
                m("dusk_ring", new AbilityKind.Ring(4, 10), 30, 240, 1.8, 18, NEAREST),
                m("creeping_dark", new AbilityKind.Trail(2.5, 2, 6, 10, 80), 26, 360, 1.2, 16, RANDOM),
                m("shade_hands", new AbilityKind.CircleAtTargets(3, 5), 30, 220, 1.5, 18, RANDOM),
                m("dark_crescent", new AbilityKind.Cone(10, 100), 28, 240, 1.8, 16, NEAREST),
                m("swallow", seq(pull(13, -1.2), new CircleAroundBoss(5.5), 20), 30, 420, 1.8, 14, NEAREST),
                m("abyss_rows", new AbilityKind.Exaflare(3, 5, 3, 12, 7), 30, 400, 1.6, 12, NEAREST),
                m("umbral_cross", new AbilityKind.Crosses(15, 3, 3, 18), 30, 380, 1.6, 12, NEAREST),
                m("eclipse_scatter", new AbilityKind.Scatter(7, 3, 12, 6, 0), 30, 380, 1.5, 14, NEAREST),
                m("last_light", new AbilityKind.SafeZones(3, 3, 9), 60, 900, 2.4, 8, NEAREST),
                m("night_tide", new AbilityKind.Checkerboard(4, 7, 2, 24), 34, 520, 1.7, 10, NEAREST),
                roar(7.5));
    }

    /** 月蝕の巨樹: the moon-eaten tree; silver roots, eclipse beams and a crown of falling moon-shards. */
    private static BossDefinition moonForest() {
        return boss("c_moon_forest", 20, 11500, 8, 8, new int[]{5, 8, 12},
                m("root_slam", new CircleAroundBoss(7.5), 28, 140, 2.0, 26, NEAREST),
                m("branch_sweep", new AbilityKind.Cone(8.5, 70), 24, 100, 1.5, 26, NEAREST),
                m("silver_thorns", new AbilityKind.Line(17, 3), 26, 130, 1.7, 22, HIGHEST_THREAT),
                m("moonshards", new AbilityKind.Scatter(6, 3, 11, 8, 0), 30, 300, 1.4, 18, NEAREST),
                m("root_eruption", new AbilityKind.Exaflare(1, 6, 3, 12, 7), 28, 320, 1.5, 18, NEAREST),
                m("lunar_ring", new AbilityKind.Ring(5, 12), 32, 280, 1.9, 16, NEAREST),
                m("silver_rain", new AbilityKind.Scatter(9, 3, 13, 6, 0), 30, 360, 1.5, 14, NEAREST),
                m("eclipse_beams", new AbilityKind.RotatingBeam(2, 16, 3, 30, 5, 14), 28, 420, 1.4, 14, NEAREST),
                m("crescent_beams", new AbilityKind.RotatingBeam(3, 17, 3, 26, 6, 12), 30, 440, 1.5, 12, NEAREST),
                m("root_snare", seq(pull(14, -1.3), new AbilityKind.Ring(5.5, 12), 22), 32, 460, 1.9, 12, NEAREST),
                m("moon_pull", seq(pull(14, -1.3), new CircleAroundBoss(7), 20), 30, 420, 1.8, 14, NEAREST),
                m("night_of_no_moon", new AbilityKind.SafeZones(3, 3, 8), 60, 900, 2.4, 8, NEAREST),
                m("moonfall", new AbilityKind.Crosses(15, 3, 2, 20), 30, 340, 1.6, 12, NEAREST),
                roar(8.5));
    }

    /** 坑底の破壊獣: a beast of ore and slag from the bottom of the pit: drills, cave-ins and molten slag. */
    private static BossDefinition ironMine() {
        return boss("c_iron_mine", 25, 13500, 9, 10, new int[]{5, 8, 12},
                m("drill_slam", new CircleAroundBoss(7.5), 28, 140, 2.0, 26, NEAREST),
                m("pick_sweep", new AbilityKind.Cone(8.5, 65), 24, 100, 1.6, 26, NEAREST),
                m("cave_in", new AbilityKind.CircleAtTargets(4, 3), 30, 200, 1.5, 20, RANDOM),
                m("rail_charge", new AbilityKind.Charge(16, 4.5, 2, 40), 28, 300, 1.7, 18, FARTHEST),
                m("slag_spill", new AbilityKind.Trail(2.5, 2, 6, 10, 90), 26, 360, 1.3, 16, RANDOM),
                m("molten_rows", new AbilityKind.Exaflare(2, 6, 3, 13, 7), 30, 380, 1.6, 16, NEAREST),
                m("forge_ring", new AbilityKind.Ring(4.5, 11.5), 32, 300, 1.9, 14, NEAREST),
                m("ore_burst", new AbilityKind.Crosses(14, 3, 2, 20), 28, 340, 1.5, 16, NEAREST),
                m("piston_drop", new AbilityKind.CircleAtTargets(4, 4), 30, 240, 1.6, 14, RANDOM),
                m("slag_beams", new AbilityKind.RotatingBeam(2, 17, 3, 28, 6, 12), 30, 440, 1.5, 12, NEAREST),
                m("collapse", new AbilityKind.Checkerboard(4, 7, 2, 24), 34, 520, 1.8, 12, NEAREST),
                m("deep_tremor", seq(pull(14, -1.2), new CircleAroundBoss(7.5), 20), 32, 460, 2.0, 12, NEAREST),
                m("ore_storm", new AbilityKind.Scatter(8, 3, 12, 6, 0), 30, 380, 1.5, 12, NEAREST),
                roar(9));
    }

    /** 堕ちた滝守: the fallen keeper of the falls; torrents, mist and the weight of the water. */
    private static BossDefinition sacredFalls() {
        return boss("c_sacred_falls", 30, 15000, 10, 6, new int[]{5, 8, 12},
                m("torrent_lash", new AbilityKind.Cone(8, 55), 24, 90, 1.6, 28, NEAREST),
                m("cascade", new AbilityKind.Line(18, 3.5), 26, 140, 1.7, 22, FARTHEST),
                m("spray", new AbilityKind.ProjectileVolley(6, 1.1, 60), 26, 160, 1.0, 20, HIGHEST_THREAT),
                m("whirlpool", new AbilityKind.Ring(4, 11), 30, 240, 1.8, 18, NEAREST),
                m("falling_water", new AbilityKind.CircleAtTargets(4, 4), 30, 260, 1.5, 16, RANDOM),
                m("geyser", new AbilityKind.CircleAtTargets(3.5, 5), 30, 240, 1.5, 16, RANDOM),
                m("spear_rain", new AbilityKind.Scatter(9, 3, 12, 6, 0), 30, 360, 1.5, 14, NEAREST),
                m("tidal_rows", new AbilityKind.Exaflare(2, 5, 3, 12, 8), 30, 380, 1.5, 14, NEAREST),
                m("torrent_cross", new AbilityKind.Crosses(15, 3.5, 2, 20), 30, 360, 1.7, 12, NEAREST),
                m("maelstrom_beams", new AbilityKind.RotatingBeam(2, 17, 3.5, 28, 6, 12), 30, 440, 1.5, 12, NEAREST),
                m("undertow", seq(pull(15, -1.4), new CircleAroundBoss(7), 22), 32, 460, 2.0, 12, NEAREST),
                m("sanctuary_lost", new AbilityKind.SafeZones(3, 3.5, 9), 60, 900, 2.4, 8, NEAREST),
                m("deluge", new AbilityKind.Checkerboard(4, 7, 2, 24), 34, 520, 1.7, 10, NEAREST),
                roar(8.5));
    }

    /** 遺跡を焦がす焔: a flame-wyrm that bakes the old stones: breath cones, burning rows, a sky of embers. */
    private static BossDefinition ancientRuins() {
        return boss("c_ancient_ruins", 40, 19000, 12, 8, new int[]{5, 8, 12},
                m("flame_breath", new AbilityKind.Cone(10, 45), 26, 110, 1.8, 28, HIGHEST_THREAT),
                m("tail_sweep", new AbilityKind.Cone(8, 90), 24, 100, 1.5, 24, NEAREST),
                m("ember_rain", new AbilityKind.Scatter(8, 3, 12, 6, 0), 30, 260, 1.4, 20, NEAREST),
                m("scorch_dive", new AbilityKind.Charge(18, 6.5, 2, 40), 30, 320, 1.8, 18, FARTHEST),
                m("burning_rows", new AbilityKind.Exaflare(2, 6, 3, 14, 7), 30, 380, 1.5, 16, NEAREST),
                m("magma_pillars", new AbilityKind.CircleAtTargets(4, 4), 30, 240, 1.6, 16, RANDOM),
                m("wyrm_lance", new AbilityKind.Line(19, 4), 28, 180, 1.9, 16, FARTHEST),
                m("ring_of_fire", new AbilityKind.Ring(5, 12), 32, 300, 1.9, 14, NEAREST),
                m("inferno_burst", seq(new CircleAroundBoss(6), new AbilityKind.Ring(6, 13), 24), 32, 460, 2.0, 12, NEAREST),
                m("ember_trail", new AbilityKind.Trail(3, 2, 6, 10, 90), 28, 400, 1.4, 12, RANDOM),
                m("pyre_beams", new AbilityKind.RotatingBeam(3, 16, 3, 24, 6, 12), 30, 460, 1.5, 12, NEAREST),
                m("ruin_pyre", new AbilityKind.SafeZones(2, 4, 9), 60, 900, 2.5, 8, NEAREST),
                m("pyre_cross", new AbilityKind.Crosses(16, 3.5, 2, 20), 30, 340, 1.7, 12, NEAREST),
                roar(9.5));
    }

    /** 峰を砕く巨像: a mountain-colossus; avalanches, peak-splitting fists and shockwaves through the rock. */
    private static BossDefinition soaringPeaks() {
        return boss("c_summit", 50, 24000, 14, 14, new int[]{5, 8, 12},
                m("peak_fist", new CircleAroundBoss(8), 28, 140, 2.1, 26, NEAREST),
                m("ridge_sweep", new AbilityKind.Cone(9, 70), 26, 100, 1.6, 26, NEAREST),
                m("avalanche", new AbilityKind.Scatter(7, 3.5, 12, 8, 0), 32, 280, 1.5, 20, NEAREST),
                m("split_the_peak", new AbilityKind.Crosses(16, 3.5, 2, 22), 30, 320, 1.7, 18, NEAREST),
                m("boulder_toss", new AbilityKind.CircleAtTargets(4.5, 3), 32, 260, 1.6, 16, RANDOM),
                m("pillar_rise", new AbilityKind.CircleAtTargets(4, 5), 32, 240, 1.7, 16, RANDOM),
                m("colossus_ring", new AbilityKind.Ring(5, 12.5), 34, 300, 2.0, 14, NEAREST),
                m("shock_rows", new AbilityKind.Exaflare(1, 7, 3, 14, 7), 30, 380, 1.6, 14, NEAREST),
                m("landslide_rows", new AbilityKind.Exaflare(3, 5, 3, 13, 7), 30, 420, 1.6, 12, NEAREST),
                m("summit_beams", new AbilityKind.RotatingBeam(3, 17, 3.5, 24, 6, 12), 30, 460, 1.6, 12, NEAREST),
                m("summit_quake", new AbilityKind.Checkerboard(4, 8, 2, 24), 36, 520, 1.9, 12, NEAREST),
                m("mountain_falls", seq(pull(16, -1.3), new CircleAroundBoss(8.5), 22), 34, 480, 2.2, 10, NEAREST),
                m("rockslide", new AbilityKind.Trail(3, 2, 6, 10, 90), 28, 380, 1.4, 12, RANDOM),
                roar(10));
    }

    /** 終焉より来るもの: what comes from the end: void blades, doom marks and a sky that tears open. */
    private static BossDefinition endForest() {
        return boss("c_end_forest", 55, 27000, 15, 12, new int[]{5, 8, 12},
                m("void_cleave", new AbilityKind.Cone(8, 60), 24, 90, 1.8, 28, NEAREST),
                m("rift_lance", new AbilityKind.Line(18, 3), 26, 130, 1.8, 22, FARTHEST),
                m("doom_marks", new AbilityKind.CircleAtTargets(3.5, 4), 30, 220, 1.6, 20, RANDOM),
                m("end_ring", new AbilityKind.Ring(4.5, 11), 30, 260, 1.9, 16, NEAREST),
                m("void_step", seq(new AbilityKind.CircleAtTargets(3.5, 2), new CircleAroundBoss(6), 22), 34, 420, 1.9,
                        14, RANDOM),
                m("void_rain", new AbilityKind.Scatter(10, 3, 13, 5, 0), 30, 360, 1.6, 16, NEAREST),
                m("rift_cross", new AbilityKind.Crosses(16, 3, 3, 18), 30, 380, 1.7, 14, NEAREST),
                m("tear_the_sky", new AbilityKind.RotatingBeam(4, 16, 3, 22, 6, 12), 30, 460, 1.6, 12, NEAREST),
                m("end_chase", new AbilityKind.Trail(3, 2, 7, 10, 90), 28, 400, 1.5, 12, RANDOM),
                m("oblivion_snare", seq(pull(15, -1.4), new AbilityKind.Ring(4.5, 11.5), 22), 32, 460, 2.0, 12, NEAREST),
                m("null_field", new AbilityKind.Checkerboard(4, 8, 3, 22), 34, 520, 1.8, 12, NEAREST),
                m("the_end", new AbilityKind.SafeZones(2, 3.5, 9), 64, 900, 2.6, 8, NEAREST),
                m("unmaking", new AbilityKind.Exaflare(2, 6, 3, 14, 7), 30, 420, 1.7, 10, NEAREST),
                roar(9));
    }

    /** 実りを枯らす根: the root that withers the harvest; blight spreads from it, husks burst, the soil grabs. */
    private static BossDefinition farm() {
        return boss("c_farm", 20, 11000, 8, 8, new int[]{5, 8, 12},
                m("root_lash", new AbilityKind.Cone(8, 70), 24, 100, 1.5, 28, NEAREST),
                m("blight_patch", new AbilityKind.CircleAtTargets(3.5, 3), 30, 180, 1.4, 22, RANDOM),
                m("furrow", new AbilityKind.Line(16, 3), 26, 140, 1.6, 20, HIGHEST_THREAT),
                m("husk_burst", new AbilityKind.Scatter(6, 3, 11, 8, 0), 30, 300, 1.4, 18, NEAREST),
                m("withering_trail", new AbilityKind.Trail(2.5, 2, 6, 12, 90), 26, 380, 1.2, 16, RANDOM),
                m("spore_cloud", new AbilityKind.Scatter(8, 3.5, 12, 7, 0), 30, 320, 1.4, 16, NEAREST),
                m("thorn_ring", new AbilityKind.Ring(4, 11), 30, 280, 1.8, 14, NEAREST),
                m("grasping_soil", seq(pull(13, -1.2), new CircleAroundBoss(6.5), 20), 30, 420, 1.8, 14, NEAREST),
                m("harvest_scythe", new AbilityKind.Cone(10, 120), 30, 300, 1.8, 12, NEAREST),
                m("blight_beams", new AbilityKind.RotatingBeam(2, 16, 3, 28, 6, 12), 30, 440, 1.4, 12, NEAREST),
                m("rot_rows", new AbilityKind.Exaflare(2, 5, 3, 12, 8), 28, 380, 1.5, 14, NEAREST),
                m("famine", new AbilityKind.SafeZones(3, 3, 8), 60, 900, 2.3, 8, NEAREST),
                m("blight_cross", new AbilityKind.Crosses(14, 3, 2, 20), 30, 340, 1.5, 12, NEAREST),
                roar(8));
    }

    /** 湖底の破壊獣: a scaled beast from the lake bed; waves, a crushing bite, water spouts under the fighters. */
    private static BossDefinition lakeside() {
        return boss("c_lakeside", 25, 13000, 9, 8, new int[]{5, 8, 12},
                m("crushing_bite", new AbilityKind.Cone(8, 50), 24, 90, 1.8, 28, HIGHEST_THREAT),
                m("tail_wave", new AbilityKind.Cone(8, 100), 26, 110, 1.5, 24, NEAREST),
                m("water_spouts", new AbilityKind.CircleAtTargets(3.5, 4), 30, 200, 1.4, 20, RANDOM),
                m("lake_charge", new AbilityKind.Charge(16, 6, 2, 40), 28, 300, 1.7, 18, FARTHEST),
                m("whirl", new AbilityKind.Ring(4.5, 11), 30, 260, 1.8, 16, NEAREST),
                m("lake_geysers", new AbilityKind.CircleAtTargets(3.5, 5), 30, 240, 1.5, 16, RANDOM),
                m("fin_crescent", new AbilityKind.Cone(10, 120), 30, 280, 1.8, 14, NEAREST),
                m("wave_rows", new AbilityKind.Exaflare(2, 5, 3, 13, 8), 30, 380, 1.5, 14, NEAREST),
                m("lake_beams", new AbilityKind.RotatingBeam(2, 17, 3, 28, 6, 12), 30, 440, 1.5, 12, NEAREST),
                m("abyss_snare", seq(pull(15, -1.4), new AbilityKind.Ring(5, 11.5), 22), 32, 460, 2.0, 12, NEAREST),
                m("drag_under", seq(pull(14, -1.3), new CircleAroundBoss(7), 22), 32, 460, 2.0, 12, NEAREST),
                m("flood", new AbilityKind.SafeZones(3, 3.5, 9), 60, 900, 2.4, 8, NEAREST),
                m("lake_cross", new AbilityKind.Crosses(14, 3, 2, 20), 30, 340, 1.6, 12, NEAREST),
                roar(9));
    }

    /** 嵐を呼ぶ呪術師: a sorcerer of the harbour who calls the storm: lightning marks, gale walls, a maelstrom. */
    private static BossDefinition harbour() {
        return boss("c_harbour", 30, 14500, 10, 6, new int[]{5, 8, 12},
                m("staff_sweep", new AbilityKind.Cone(7.5, 60), 24, 90, 1.5, 26, NEAREST),
                m("lightning_marks", new AbilityKind.CircleAtTargets(3.5, 4), 30, 180, 1.5, 22, RANDOM),
                m("gale_wall", new AbilityKind.Line(18, 4), 26, 140, 1.6, 20, FARTHEST),
                m("hex_volley", new AbilityKind.ProjectileVolley(7, 1.2, 70), 26, 160, 1.0, 20, HIGHEST_THREAT),
                m("thunder_cross", new AbilityKind.Crosses(15, 3, 3, 18), 30, 340, 1.5, 16, NEAREST),
                m("chain_lightning", new AbilityKind.CircleAtTargets(3, 5), 30, 220, 1.5, 16, RANDOM),
                m("gale_ring", new AbilityKind.Ring(4.5, 12), 30, 280, 1.8, 14, NEAREST),
                m("maelstrom", new AbilityKind.RotatingBeam(2, 16, 3, 30, 6, 12), 30, 420, 1.5, 14, NEAREST),
                m("storm_rows", new AbilityKind.Exaflare(3, 5, 3, 13, 7), 30, 400, 1.6, 12, NEAREST),
                m("thunder_snare", seq(pull(14, -1.3), new CircleAroundBoss(7), 20), 32, 460, 2.0, 12, NEAREST),
                m("storm_scatter", new AbilityKind.Scatter(8, 3, 12, 6, 0), 30, 380, 1.5, 14, NEAREST),
                m("eye_of_the_storm", new AbilityKind.SafeZones(2, 4, 9), 60, 900, 2.4, 8, NEAREST),
                m("storm_tide", new AbilityKind.Exaflare(2, 6, 3, 14, 7), 30, 420, 1.6, 10, NEAREST),
                roar(8));
    }

    // ------------------------------------------------------------------ the wanderers (two forms)

    /** 野茨の遍歴騎士: a knight overgrown with briars, still riding his endless errand. */
    private static BossDefinition brambleKnight() {
        return boss("w_bramble_knight", 12, 5600, 6, 6, new int[]{4, 7},
                m("lance_thrust", new AbilityKind.Line(14, 3), 24, 100, 1.6, 28, FARTHEST),
                m("briar_sweep", new AbilityKind.Cone(7, 70), 24, 90, 1.4, 28, NEAREST),
                m("thorn_field", new AbilityKind.CircleAtTargets(3, 3), 30, 200, 1.3, 20, RANDOM),
                m("knightly_charge", new AbilityKind.Charge(14, 4, 1, 40), 28, 260, 1.7, 18, FARTHEST),
                m("briar_ring", new AbilityKind.Ring(4, 9), 30, 260, 1.7, 14, NEAREST),
                m("rose_scatter", new AbilityKind.Scatter(5, 3, 10, 8, 0), 30, 340, 1.4, 12, NEAREST),
                m("bramble_trail", new AbilityKind.Trail(2.5, 2, 5, 12, 80), 26, 380, 1.2, 12, RANDOM),
                m("errant_cross", new AbilityKind.Crosses(13, 3, 2, 20), 30, 340, 1.5, 10, NEAREST),
                roar(7));
    }

    /** 月光蛾の魔女: a witch with moth wings who scatters moonlit dust. */
    private static BossDefinition moonmothWitch() {
        return boss("w_moonmoth_witch", 18, 7200, 7, 4, new int[]{4, 7},
                m("wing_gust", new AbilityKind.Cone(7.5, 75), 24, 90, 1.4, 28, NEAREST),
                m("moon_dust", new AbilityKind.CircleAtTargets(3.5, 3), 30, 180, 1.4, 22, RANDOM),
                m("moth_swarm", new AbilityKind.ProjectileVolley(6, 1.0, 70), 26, 160, 1.0, 20, HIGHEST_THREAT),
                m("pale_ring", new AbilityKind.Ring(4, 10), 30, 240, 1.7, 16, NEAREST),
                m("dust_trail", new AbilityKind.Trail(2.5, 2, 5, 12, 80), 26, 360, 1.2, 14, RANDOM),
                m("lunar_glare", new AbilityKind.RotatingBeam(2, 14, 3, 30, 5, 14), 28, 420, 1.4, 12, NEAREST),
                m("moth_scatter", new AbilityKind.Scatter(6, 3, 11, 8, 0), 30, 320, 1.4, 12, NEAREST),
                m("moonlit_maze", new AbilityKind.Checkerboard(4, 7, 2, 24), 34, 520, 1.6, 10, NEAREST),
                roar(7));
    }

    /** 鉱滓の巨人: a giant of cooled slag who still drips molten metal. */
    private static BossDefinition slagTitan() {
        return boss("w_slag_titan", 23, 9200, 8.5, 10, new int[]{4, 7},
                m("slag_slam", new CircleAroundBoss(7), 28, 140, 1.9, 26, NEAREST),
                m("cinder_sweep", new AbilityKind.Cone(8, 65), 24, 100, 1.5, 26, NEAREST),
                m("molten_drip", new AbilityKind.Trail(2.5, 2, 6, 10, 90), 26, 300, 1.2, 18, RANDOM),
                m("slag_lumps", new AbilityKind.CircleAtTargets(4, 3), 30, 220, 1.5, 18, RANDOM),
                m("furnace_cross", new AbilityKind.Crosses(13, 3, 2, 20), 28, 340, 1.5, 14, NEAREST),
                m("cooling_quake", seq(pull(13, -1.1), new CircleAroundBoss(7), 20), 32, 460, 1.9, 12, NEAREST),
                m("slag_rows", new AbilityKind.Exaflare(1, 6, 3, 12, 7), 30, 380, 1.5, 12, NEAREST),
                m("meltdown", new AbilityKind.SafeZones(3, 3, 8), 60, 900, 2.2, 8, NEAREST),
                roar(8.5));
    }

    /** 瀑流の飛竜: a wyvern that rides the spray of the falls. */
    private static BossDefinition cascadeWyvern() {
        return boss("w_cascade_wyvern", 28, 10500, 9.5, 6, new int[]{4, 7},
                m("spray_breath", new AbilityKind.Cone(9, 45), 26, 110, 1.7, 28, HIGHEST_THREAT),
                m("wing_buffet", new AbilityKind.Cone(7.5, 100), 24, 100, 1.4, 24, NEAREST),
                m("dive", new AbilityKind.Charge(16, 5, 2, 40), 28, 280, 1.7, 20, FARTHEST),
                m("spray_rain", new AbilityKind.Scatter(6, 3, 11, 8, 0), 30, 300, 1.4, 18, NEAREST),
                m("mist_ring", new AbilityKind.Ring(4.5, 10), 30, 260, 1.7, 14, NEAREST),
                m("cascade_rows", new AbilityKind.Exaflare(2, 5, 3, 12, 8), 30, 380, 1.5, 12, NEAREST),
                m("spray_marks", new AbilityKind.CircleAtTargets(3.5, 3), 30, 200, 1.4, 14, RANDOM),
                m("torrent_web", new AbilityKind.RotatingBeam(2, 14, 3, 30, 5, 14), 28, 420, 1.4, 10, NEAREST),
                roar(8.5));
    }

    /** 霧籠りの仙人: a hermit who has lived in the mist so long that he is half mist himself. */
    private static BossDefinition mistHermit() {
        return boss("w_mist_hermit", 30, 10800, 9.5, 4, new int[]{4, 7},
                m("staff_strike", new AbilityKind.Cone(7, 55), 24, 90, 1.6, 28, NEAREST),
                m("mist_bloom", new AbilityKind.CircleAtTargets(3.5, 4), 30, 180, 1.4, 22, RANDOM),
                m("vanish", seq(new AbilityKind.CircleAtTargets(3, 2), new CircleAroundBoss(5.5), 20), 32, 360, 1.7,
                        18, RANDOM),
                m("cloud_palm", new AbilityKind.Line(15, 3), 26, 140, 1.6, 18, FARTHEST),
                m("crane_ring", new AbilityKind.Ring(4, 10), 30, 260, 1.7, 14, NEAREST),
                m("thousand_mists", new AbilityKind.Checkerboard(4, 7, 2, 24), 34, 520, 1.7, 10, NEAREST),
                m("mist_trail", new AbilityKind.Trail(2.5, 2, 5, 12, 80), 26, 380, 1.2, 12, RANDOM),
                m("hermit_steps", new AbilityKind.Exaflare(2, 5, 3, 12, 8), 30, 380, 1.5, 10, NEAREST),
                roar(7.5));
    }

    /** 歩く碑文: a monolith covered in letters no one can read any more; the words still burn. */
    private static BossDefinition walkingGlyph() {
        return boss("w_walking_glyph", 36, 13500, 11, 14, new int[]{4, 7},
                m("monolith_slam", new CircleAroundBoss(7.5), 28, 140, 2.0, 26, NEAREST),
                m("glyph_lines", new AbilityKind.Crosses(14, 3, 2, 20), 28, 260, 1.6, 22, NEAREST),
                m("burning_words", new AbilityKind.CircleAtTargets(3.5, 4), 30, 200, 1.5, 20, RANDOM),
                m("inscription", new AbilityKind.Line(17, 3.5), 26, 140, 1.6, 18, FARTHEST),
                m("sentence", new AbilityKind.Checkerboard(4, 7, 2, 24), 34, 460, 1.8, 12, NEAREST),
                m("chapter_end", new AbilityKind.Exaflare(1, 6, 3, 13, 7), 30, 380, 1.6, 12, NEAREST),
                m("glyph_sweep", new AbilityKind.Cone(8, 60), 24, 100, 1.5, 20, NEAREST),
                m("erasure", new AbilityKind.Scatter(7, 3, 12, 6, 0), 30, 380, 1.5, 10, NEAREST),
                roar(9));
    }

    /** 灰の先触れ: the herald of ash who walks ahead of every fire. */
    private static BossDefinition ashHerald() {
        return boss("w_ash_herald", 38, 14000, 11.5, 10, new int[]{4, 7},
                m("ash_blade", new AbilityKind.Cone(8, 60), 24, 90, 1.7, 28, NEAREST),
                m("cinder_marks", new AbilityKind.CircleAtTargets(3.5, 3), 30, 180, 1.5, 22, RANDOM),
                m("herald_dash", new AbilityKind.Charge(15, 4, 2, 40), 28, 280, 1.7, 18, FARTHEST),
                m("ash_storm", new AbilityKind.Scatter(7, 3, 12, 6, 0), 30, 300, 1.4, 16, NEAREST),
                m("smoulder_ring", new AbilityKind.Ring(4.5, 10), 30, 260, 1.8, 14, NEAREST),
                m("trail_of_cinders", new AbilityKind.Trail(2.5, 2, 6, 10, 90), 26, 380, 1.3, 12, RANDOM),
                m("ash_rows", new AbilityKind.Exaflare(1, 6, 3, 13, 7), 30, 380, 1.5, 12, NEAREST),
                m("herald_beams", new AbilityKind.RotatingBeam(2, 15, 3, 30, 5, 14), 28, 420, 1.4, 10, NEAREST),
                roar(8));
    }

    /** 雷鳴の巨鳥: a great storm bird whose wingbeats are thunder. */
    private static BossDefinition thunderRoc() {
        return boss("w_thunder_roc", 45, 17000, 13, 8, new int[]{4, 7},
                m("talon_rake", new AbilityKind.Cone(8.5, 60), 24, 90, 1.7, 28, NEAREST),
                m("thunderclap", new AbilityKind.CircleAtTargets(4, 4), 30, 200, 1.6, 22, RANDOM),
                m("storm_dive", new AbilityKind.Charge(17, 5.5, 2, 40), 30, 280, 1.8, 18, FARTHEST),
                m("gale", new AbilityKind.Knockback(9, 1.2), 30, 300, 1.2, 14, NEAREST),
                m("lightning_web", new AbilityKind.RotatingBeam(3, 15, 3, 24, 6, 12), 30, 420, 1.5, 12, NEAREST),
                m("thunderhead", new AbilityKind.Scatter(8, 3, 12, 6, 0), 30, 380, 1.5, 12, NEAREST),
                m("thunder_line", new AbilityKind.Line(16, 3), 26, 140, 1.6, 16, FARTHEST),
                m("storm_cross", new AbilityKind.Crosses(15, 3, 3, 18), 30, 340, 1.6, 10, NEAREST),
                roar(9));
    }

    /** 氷河を背負う者: a giant who carries a glacier on his back and lets it calve onto the fighters. */
    private static BossDefinition glacierBearer() {
        return boss("w_glacier_bearer", 48, 19000, 13.5, 14, new int[]{4, 7},
                m("glacier_slam", new CircleAroundBoss(8), 28, 140, 2.1, 26, NEAREST),
                m("frost_sweep", new AbilityKind.Cone(9, 70), 26, 100, 1.6, 26, NEAREST),
                m("calving", new AbilityKind.Scatter(6, 3.5, 12, 8, 0), 32, 280, 1.5, 18, NEAREST),
                m("ice_spears", new AbilityKind.Line(17, 3.5), 26, 160, 1.7, 18, FARTHEST),
                m("permafrost", new AbilityKind.Checkerboard(4, 8, 2, 24), 36, 520, 1.8, 12, NEAREST),
                m("glacial_pull", seq(pull(15, -1.2), new CircleAroundBoss(8), 22), 34, 480, 2.1, 10, NEAREST),
                m("frost_marks", new AbilityKind.CircleAtTargets(4, 3), 30, 220, 1.5, 14, RANDOM),
                m("ice_rows", new AbilityKind.Exaflare(1, 7, 3, 14, 7), 30, 420, 1.6, 10, NEAREST),
                roar(10));
    }

    /** 虚ろなる巡礼者: a pilgrim of the end forest, hollow inside, whose prayers open little rifts. */
    private static BossDefinition hollowPilgrim() {
        return boss("w_hollow_walker", 52, 19500, 14, 10, new int[]{4, 7},
                m("pilgrim_staff", new AbilityKind.Cone(8, 60), 24, 90, 1.7, 28, NEAREST),
                m("hollow_prayer", new AbilityKind.CircleAtTargets(3.5, 4), 30, 200, 1.6, 22, RANDOM),
                m("rift_walk", seq(new AbilityKind.CircleAtTargets(3.5, 2), new CircleAroundBoss(6), 22), 34, 380, 1.8,
                        16, RANDOM),
                m("void_lines", new AbilityKind.Crosses(15, 3, 3, 18), 30, 340, 1.6, 16, NEAREST),
                m("emptiness", new AbilityKind.Ring(4.5, 11), 30, 280, 1.9, 12, NEAREST),
                m("last_pilgrimage", new AbilityKind.Exaflare(2, 6, 3, 14, 7), 30, 420, 1.6, 10, NEAREST),
                m("hollow_trail", new AbilityKind.Trail(2.5, 2, 6, 10, 90), 26, 380, 1.3, 12, RANDOM),
                m("void_maze", new AbilityKind.Checkerboard(4, 8, 2, 24), 34, 520, 1.7, 10, NEAREST),
                roar(8.5));
    }

    /** 墓花の大樹: a great tree flowering on the graves of the end forest; its petals are cold. */
    private static BossDefinition gravebloom() {
        return boss("w_gravebloom", 54, 21000, 14, 12, new int[]{4, 7},
                m("grave_roots", new CircleAroundBoss(8), 28, 140, 2.0, 26, NEAREST),
                m("bough_sweep", new AbilityKind.Cone(9, 75), 26, 100, 1.6, 26, NEAREST),
                m("cold_petals", new AbilityKind.Scatter(7, 3, 12, 6, 0), 30, 280, 1.5, 18, NEAREST),
                m("burial", new AbilityKind.CircleAtTargets(4, 3), 30, 220, 1.6, 18, RANDOM),
                m("mourning_rows", new AbilityKind.Exaflare(2, 6, 3, 14, 7), 30, 380, 1.6, 12, NEAREST),
                m("wreath", new AbilityKind.Ring(5, 12), 32, 300, 1.9, 12, NEAREST),
                m("root_cross", new AbilityKind.Crosses(14, 3, 2, 20), 30, 340, 1.6, 12, NEAREST),
                m("funeral_trail", new AbilityKind.Trail(2.5, 2, 6, 12, 90), 26, 400, 1.3, 10, RANDOM),
                roar(9.5));
    }

    /** 案山子の王: a scarecrow that crowned itself king of the fields and drives the crows before it. */
    private static BossDefinition scarecrowKing() {
        return boss("w_scarecrow_king", 17, 6900, 7, 6, new int[]{4, 7},
                m("scythe_sweep", new AbilityKind.Cone(7.5, 80), 24, 90, 1.5, 28, NEAREST),
                m("crow_flock", new AbilityKind.ProjectileVolley(6, 1.1, 60), 26, 160, 1.0, 22, HIGHEST_THREAT),
                m("straw_fire", new AbilityKind.CircleAtTargets(3, 3), 30, 200, 1.4, 20, RANDOM),
                m("harvest_reap", new AbilityKind.Ring(4, 9), 30, 260, 1.7, 16, NEAREST),
                m("crow_dive", new AbilityKind.Scatter(5, 3, 10, 8, 0), 30, 320, 1.4, 14, NEAREST),
                m("field_of_crows", new AbilityKind.Trail(2.5, 2, 6, 12, 80), 26, 380, 1.2, 12, RANDOM),
                m("scythe_cross", new AbilityKind.Crosses(13, 3, 2, 20), 30, 340, 1.5, 12, NEAREST),
                m("harvest_moon", new AbilityKind.SafeZones(3, 3, 8), 60, 900, 2.2, 8, NEAREST),
                roar(7));
    }

    /** 葦原の大蛇: a huge serpent of the reed beds that strikes from the water's edge. */
    private static BossDefinition reedSerpent() {
        return boss("w_reed_serpent", 22, 8600, 8, 6, new int[]{4, 7},
                m("serpent_strike", new AbilityKind.Line(15, 3), 24, 100, 1.7, 28, FARTHEST),
                m("coil_sweep", new AbilityKind.Cone(8, 110), 26, 100, 1.5, 24, NEAREST),
                m("venom_spit", new AbilityKind.CircleAtTargets(3.5, 3), 30, 180, 1.4, 20, RANDOM),
                m("reed_lunge", new AbilityKind.Charge(15, 5, 2, 40), 28, 280, 1.7, 18, FARTHEST),
                m("constrict", seq(pull(13, -1.3), new CircleAroundBoss(6.5), 20), 30, 420, 1.8, 14, NEAREST),
                m("marsh_trail", new AbilityKind.Trail(2.5, 2, 6, 12, 90), 26, 380, 1.2, 12, RANDOM),
                m("reed_ring", new AbilityKind.Ring(4, 10), 30, 260, 1.7, 12, NEAREST),
                m("marsh_rows", new AbilityKind.Exaflare(2, 5, 3, 12, 8), 30, 380, 1.5, 10, NEAREST),
                roar(8));
    }

    /** 沈まぬ船長: a captain who refused to go down with his ship and still commands the waves. */
    private static BossDefinition sunkenCaptain() {
        return boss("w_sunken_captain", 27, 10000, 9, 8, new int[]{4, 7},
                m("cutlass_sweep", new AbilityKind.Cone(7.5, 70), 24, 90, 1.6, 28, NEAREST),
                m("cannon_volley", new AbilityKind.CircleAtTargets(3.5, 4), 30, 200, 1.5, 22, RANDOM),
                m("boarding_rush", new AbilityKind.Charge(15, 4, 2, 40), 28, 280, 1.7, 18, FARTHEST),
                m("anchor_drop", seq(pull(13, -1.2), new CircleAroundBoss(6.5), 20), 30, 420, 1.8, 14, NEAREST),
                m("broadside", new AbilityKind.Exaflare(1, 6, 3, 13, 7), 30, 380, 1.5, 12, NEAREST),
                m("grapeshot", new AbilityKind.ProjectileVolley(7, 1.2, 60), 26, 220, 1.0, 12, HIGHEST_THREAT),
                m("powder_kegs", new AbilityKind.Scatter(6, 3, 11, 8, 0), 30, 320, 1.4, 12, NEAREST),
                m("helm_of_the_maelstrom", new AbilityKind.RotatingBeam(2, 15, 3, 30, 5, 14), 28, 420, 1.4, 10, NEAREST),
                roar(8));
    }

    /** 潮呼びの巫女: a priestess who sings the tide in; the sea answers in rings and spouts. */
    private static BossDefinition tidePriestess() {
        return boss("w_tide_singer", 29, 10400, 9.5, 4, new int[]{4, 7},
                m("tide_lash", new AbilityKind.Cone(7.5, 60), 24, 90, 1.5, 28, NEAREST),
                m("spout_song", new AbilityKind.CircleAtTargets(3.5, 4), 30, 180, 1.4, 22, RANDOM),
                m("high_tide", new AbilityKind.Ring(4, 10), 30, 240, 1.7, 18, NEAREST),
                m("pearl_volley", new AbilityKind.ProjectileVolley(6, 1.1, 60), 26, 160, 1.0, 18, HIGHEST_THREAT),
                m("riptide", new AbilityKind.Trail(2.5, 2, 6, 12, 80), 26, 360, 1.3, 14, RANDOM),
                m("sea_hymn", new AbilityKind.SafeZones(2, 3.5, 8), 60, 900, 2.2, 8, NEAREST),
                m("tide_rows", new AbilityKind.Exaflare(2, 5, 3, 12, 8), 30, 380, 1.5, 12, NEAREST),
                m("tidal_scatter", new AbilityKind.Scatter(7, 3, 12, 6, 0), 30, 380, 1.5, 10, NEAREST),
                roar(7.5));
    }

    // ------------------------------------------------------------------ builders

    /** One move of a field boss (the ability id is {@code <boss>_<name>}; 0 multiplier = no damage). */
    private record Move(String name, AbilityKind kind, int telegraph, int cooldown, double multiplier, int weight,
                        TargetRule rule) {
    }

    private static Move m(String name, AbilityKind kind, int telegraph, int cooldown, double multiplier, int weight,
                          TargetRule rule) {
        return new Move(name, kind, telegraph, cooldown, multiplier, weight, rule);
    }

    /** The roar of a transformation: knocks everyone back from the body (the boss's last move, never in a rotation). */
    private static Move roar(double radius) {
        return m("roar", new AbilityKind.Knockback(radius, 1.3), 40, 0, 1.0, 1, NEAREST);
    }

    private static AbilityKind pull(double radius, double strength) {
        return new AbilityKind.Knockback(radius, strength);
    }

    /** A two-step combo: {@code first} at once, then {@code second} after its own telegraph of {@code delay} ticks. */
    private static AbilityKind seq(AbilityKind first, AbilityKind second, int delay) {
        return new AbilityKind.Sequence(List.of(new Part(first, 0, 0.3), Part.of(second, delay)));
    }

    /**
     * A field boss. {@code forms[i]} is how many of the moves (in order) form {@code i} uses; the last move is the
     * roar of the transformations, the move before it the boss's extra move on EX.
     */
    private static BossDefinition boss(String id, int level, double health, double damage, double armor, int[] forms,
                                       Move... moves) {
        List<BossAbility> abilities = new ArrayList<>();
        for (Move move : moves) {
            abilities.add(new BossAbility(id + "_" + move.name(), move.kind(), move.telegraph(), 10, move.cooldown(),
                    move.multiplier(), move.weight(), move.rule()));
        }
        String roar = abilities.get(abilities.size() - 1).id();
        List<BossPhase> phases = new ArrayList<>();
        double[] starts = forms.length == 3 ? new double[]{1.0, 0.65, 0.3} : new double[]{1.0, 0.5};
        for (int form = 0; form < forms.length; form++) {
            List<String> rotation = new ArrayList<>();
            for (int k = 0; k < forms[form]; k++) {
                rotation.add(abilities.get(k).id());
            }
            int gcd = 36 - 5 * form;
            double speed = 1.0 + 0.08 * form;
            if (form == 0) {
                phases.add(new BossPhase(starts[0], rotation, gcd, speed, List.of()));
            } else {
                List<String> openers = List.of(abilities.get(forms[form] - 1).id());
                phases.add(new BossPhase(starts[form], rotation, gcd, speed, openers, roar,
                        form == forms.length - 1 && forms.length == 3));
            }
        }
        String ex = abilities.get(abilities.size() - 2).id();
        boolean calamity = forms.length == 3;
        int players = calamity ? com.eternalrealm.world.field.ThreatPower.SIZED_FOR : 2;
        double blows = calamity ? damage * com.eternalrealm.world.field.ThreatPower.CALAMITY_DAMAGE : damage;
        return new BossDefinition(id, "minecraft:iron_golem", level, health * RAID_HEALTH, blows, armor, PARTY_SCALING,
                players, 8, phases, abilities, calamity ? CALAMITY_ENRAGE_TICKS : ENRAGE_TICKS, List.of(),
                DifficultyTuning.NORMAL, DifficultyTuning.hard(), DifficultyTuning.ex(ex));
    }
}
