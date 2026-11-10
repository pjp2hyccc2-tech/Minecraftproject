package com.eternalrealm.boss;

import java.util.List;
import java.util.Optional;

import static com.eternalrealm.boss.TargetRule.FARTHEST;
import static com.eternalrealm.boss.TargetRule.HIGHEST_THREAT;
import static com.eternalrealm.boss.TargetRule.NEAREST;
import static com.eternalrealm.boss.TargetRule.RANDOM;

/**
 * The bosses of the initial release: b1..b8 (boss of dungeon dN) and the final boss of the tower.
 * Names and mechanics are listed in docs/modules/boss.md.
 *
 * <p>Balance model (checked by BossesContentTest): a level-matched player deals about 8 (level 1) to 45 (level 50)
 * per light hit and lands 1.5 hits per second on average (dodging and moving included), reduced by the boss's armor
 * with vanilla's formula. Base health is chosen so one player needs about 3 minutes on NORMAL; with +70% health per
 * extra player a party of 5 needs about 2.3 minutes. The final boss is sized for 10-20 players (about 3.6 minutes).</p>
 */
public final class Bosses {
    private static final double PARTY_SCALING = 0.7;
    private static final int ENRAGE_TICKS = 20 * 60 * 5;
    private static final int FINAL_ENRAGE_TICKS = 20 * 60 * 15 / 2;

    /** 地下都市の盗賊王 (Lost Underground City, d1, area 1). */
    public static final BossDefinition B1 = b1();
    /** 古代樹の守護者 (Ancient Tree Labyrinth, d2, area 2). */
    public static final BossDefinition B2 = b2();
    /** 砂塵の巨神 (Sand Temple, d3, area 3). */
    public static final BossDefinition B3 = b3();
    /** 凍獄の処刑人 (Frozen Prison, d4, area 4). */
    public static final BossDefinition B4 = b4();
    /** 氷獄の女王 (Frozen Castle, d5, area 4). */
    public static final BossDefinition B5 = b5();
    /** 灼炎の竜王 (Volcanic Cave, d6, area 5). */
    public static final BossDefinition B6 = b6();
    /** 天空の機械神 (Ancient Sky Ruins, d7, area 6). */
    public static final BossDefinition B7 = b7();
    /** 終焉の門番 (Tower of the End, d8, area 7). */
    public static final BossDefinition B8 = b8();
    /** 終焉の王: raid boss at the top of the tower for 10-20 players, 4 phases. */
    public static final BossDefinition FINAL = finalBoss();

    private static final List<BossDefinition> ALL = List.of(B1, B2, B3, B4, B5, B6, B7, B8, FINAL);

    private Bosses() {
    }

    public static List<BossDefinition> all() {
        return ALL;
    }

    /** The dungeon and raid bosses, then the field bosses ({@link FieldBosses}): every boss that can be fought. */
    public static List<BossDefinition> everyBoss() {
        List<BossDefinition> every = new java.util.ArrayList<>(ALL);
        every.addAll(FieldBosses.all());
        return List.copyOf(every);
    }

    /** A boss of the dungeons, the raid or the field. */
    public static Optional<BossDefinition> byId(String id) {
        for (BossDefinition definition : everyBoss()) {
            if (definition.id().equals(id)) {
                return Optional.of(definition);
            }
        }
        return Optional.empty();
    }

    /** Template id of a boss's adds; the monster module defines one per boss. */
    public static String minionTemplateId(String bossId) {
        return bossId + "_minion";
    }

    // ------------------------------------------------------------------ b1 地下都市の盗賊王

    /** Laszlo: daggers, bombs and dirty tricks; a tantrum and his gang in the second form. */
    private static BossDefinition b1() {
        String minion = minionTemplateId("b1");
        List<BossAbility> abilities = List.of(
                hit("b1_cleave", new AbilityKind.Cone(5.5, 50), 16, 10, 80, 1.6, 30, NEAREST),
                hit("b1_dash", new AbilityKind.Line(12.5, 3), 20, 12, 140, 1.8, 20, FARTHEST),
                hit("b1_smoke_bombs", new AbilityKind.CircleAtTargets(3, 3), 30, 10, 200, 1.4, 20, RANDOM),
                hit("b1_whirlwind", new AbilityKind.CircleAroundBoss(5.5), 24, 20, 160, 2.0, 25, NEAREST),
                act("b1_call_gang", new AbilityKind.Summon(minion, 3), 30, 10, 700, 10, NEAREST),
                hit("b1_knife_fan", new AbilityKind.ProjectileVolley(5, 1.2, 50), 16, 8, 160, 1.0, 20,
                        HIGHEST_THREAT),
                hit("b1_dagger_cross", new AbilityKind.Crosses(12.5, 3, 2, 20), 24, 10, 320, 1.5, 20, NEAREST),
                hit("b1_bomb_barrage", new AbilityKind.Scatter(6, 2.5, 9, 6, 0), 26, 10, 360, 1.4, 18, NEAREST),
                hit("b1_greedy_grab", new AbilityKind.Sequence(List.of(
                        new AbilityKind.Part(new AbilityKind.Knockback(12.5, -1.2), 0, 0.3),
                        AbilityKind.Part.of(new AbilityKind.CircleAroundBoss(5), 18))), 22, 10, 420, 1.6, 15,
                        NEAREST),
                hit("b1_caltrops", new AbilityKind.Trail(2, 1, 5, 10, 60), 14, 8, 360, 1.2, 15, RANDOM),
                hit("b1_shadow_dash", new AbilityKind.Charge(14.5, 3, 3, 22), 22, 10, 480, 1.5, 15, RANDOM),
                hit("b1_tantrum", new AbilityKind.Knockback(6.5, 1.2), 36, 12, 0, 1.0, 1, NEAREST));
        List<BossPhase> phases = List.of(
                phase(1.0, 36, 1.0, List.of(), "b1_cleave", "b1_dash", "b1_smoke_bombs", "b1_dagger_cross",
                        "b1_bomb_barrage"),
                form(0.5, 28, 1.15, "b1_tantrum", false, List.of("b1_call_gang", "b1_shadow_dash"),
                        "b1_cleave", "b1_dash", "b1_smoke_bombs", "b1_whirlwind", "b1_call_gang", "b1_dagger_cross",
                        "b1_bomb_barrage", "b1_greedy_grab", "b1_caltrops", "b1_shadow_dash"));
        return boss("b1", "minecraft:vindicator", 6, 3100, 6, 4, 1, 5, phases, abilities, ENRAGE_TICKS,
                List.of(minion), "b1_knife_fan");
    }

    // ------------------------------------------------------------------ b2 古代樹の守護者

    /** The guardian: roots and thorns, then its saplings, then the crystal's pain and the light through the leaves. */
    private static BossDefinition b2() {
        String minion = minionTemplateId("b2");
        List<BossAbility> abilities = List.of(
                hit("b2_root_slam", new AbilityKind.CircleAroundBoss(7), 24, 16, 140, 2.0, 25, NEAREST),
                hit("b2_thorn_line", new AbilityKind.Line(17, 3), 20, 10, 120, 1.7, 25, HIGHEST_THREAT),
                hit("b2_branch_sweep", new AbilityKind.Cone(8, 70), 18, 10, 100, 1.5, 25, NEAREST),
                hit("b2_spore_burst", new AbilityKind.CircleAtTargets(3.5, 4), 30, 10, 180, 1.4, 20, RANDOM),
                act("b2_sapling_call", new AbilityKind.Summon(minion, 3), 30, 10, 700, 10, NEAREST),
                act("b2_bark_shield", new AbilityKind.Shield(minion, 3, 400), 30, 10, 1600, 5, NEAREST),
                act("b2_regrowth", new AbilityKind.Heal(0.05), 40, 10, 900, 8, NEAREST),
                hit("b2_vine_ring", new AbilityKind.Ring(4, 10), 26, 12, 220, 2.0, 20, NEAREST),
                hit("b2_root_eruption", new AbilityKind.Exaflare(1, 6, 3, 12, 7), 26, 10, 320, 1.5, 20, NEAREST),
                hit("b2_seed_pods", new AbilityKind.Scatter(5, 3, 11, 8, 0), 30, 10, 360, 1.4, 18, NEAREST),
                hit("b2_bramble_trail", new AbilityKind.Trail(2.5, 1, 5, 12, 80), 16, 8, 400, 1.2, 15, RANDOM),
                hit("b2_vine_lash", new AbilityKind.Sequence(List.of(
                        new AbilityKind.Part(new AbilityKind.Knockback(14, -1.3), 0, 0.3),
                        AbilityKind.Part.of(new AbilityKind.CircleAroundBoss(6.5), 18))), 24, 10, 420, 1.7, 15,
                        NEAREST),
                hit("b2_crystal_throb", new AbilityKind.RotatingBeam(2, 15, 3, 30, 5, 14), 26, 10, 420, 1.4, 15,
                        NEAREST),
                hit("b2_sunbeam_refuge", new AbilityKind.SafeZones(3, 3, 8), 60, 12, 900, 2.4, 8, NEAREST),
                hit("b2_awakening", new AbilityKind.Knockback(8, 1.3), 36, 12, 0, 1.0, 1, NEAREST));
        List<BossPhase> phases = List.of(
                phase(1.0, 36, 1.0, List.of(), "b2_root_slam", "b2_thorn_line", "b2_branch_sweep",
                        "b2_root_eruption", "b2_seed_pods"),
                form(0.6, 30, 1.1, "b2_awakening", false, List.of("b2_sapling_call", "b2_bramble_trail"),
                        "b2_root_slam", "b2_thorn_line", "b2_branch_sweep", "b2_spore_burst", "b2_sapling_call",
                        "b2_root_eruption", "b2_seed_pods", "b2_bramble_trail", "b2_vine_lash"),
                form(0.3, 26, 1.2, "b2_awakening", false, List.of("b2_bark_shield", "b2_sunbeam_refuge"),
                        "b2_root_slam", "b2_thorn_line", "b2_branch_sweep", "b2_spore_burst", "b2_bark_shield",
                        "b2_regrowth", "b2_bramble_trail", "b2_vine_lash", "b2_crystal_throb", "b2_sunbeam_refuge"));
        return boss("b2", "minecraft:iron_golem", 13, 4300, 7.5, 8, 1, 5, phases, abilities, ENRAGE_TICKS,
                List.of(minion), "b2_vine_ring");
    }

    // ------------------------------------------------------------------ b3 砂塵の巨神

    /** The sand titan: pillars and falling rocks, the sand sea, and a rampage in the storm. */
    private static BossDefinition b3() {
        String minion = minionTemplateId("b3");
        List<BossAbility> abilities = List.of(
                hit("b3_stomp", new AbilityKind.CircleAroundBoss(6.5), 22, 14, 120, 1.8, 25, NEAREST),
                hit("b3_charge", new AbilityKind.Line(21.5, 4), 24, 14, 160, 2.0, 20, FARTHEST),
                hit("b3_sand_breath", new AbilityKind.Cone(11.5, 35), 20, 16, 120, 1.6, 25, NEAREST),
                hit("b3_quicksand", new AbilityKind.CircleAtTargets(4, 3), 30, 10, 200, 1.4, 20, RANDOM),
                hit("b3_dust_ring", new AbilityKind.Ring(5.5, 13.5), 28, 14, 220, 2.0, 20, NEAREST),
                act("b3_sand_servants", new AbilityKind.Summon(minion, 3), 30, 10, 800, 8, NEAREST),
                hit("b3_sandstorm", new AbilityKind.ArenaWide(), 50, 20, 800, 1.8, 6, NEAREST),
                hit("b3_boulder_volley", new AbilityKind.ProjectileVolley(3, 1.0, 30), 18, 10, 180, 1.2, 20,
                        HIGHEST_THREAT),
                hit("b3_sandstone_pillars", new AbilityKind.Checkerboard(4, 7, 2, 24), 30, 10, 380, 1.6, 18,
                        NEAREST),
                hit("b3_falling_rocks", new AbilityKind.Scatter(7, 3, 12, 5, 0), 30, 10, 340, 1.5, 18, NEAREST),
                hit("b3_quicksand_trail", new AbilityKind.Trail(2.5, 1, 6, 10, 100), 16, 8, 420, 1.2, 15,
                        FARTHEST),
                hit("b3_sand_tsunami", new AbilityKind.Exaflare(2, 7, 3, 18, 6), 28, 10, 420, 1.6, 15, NEAREST),
                hit("b3_titan_rampage", new AbilityKind.Charge(21.5, 6.5, 3, 26), 26, 12, 520, 2.0, 15, RANDOM),
                hit("b3_eye_of_storm", new AbilityKind.SafeZones(2, 3.5, 8), 60, 12, 900, 2.6, 8, NEAREST),
                hit("b3_titan_roar", new AbilityKind.Knockback(9.5, 1.4), 38, 12, 0, 1.1, 1, NEAREST));
        List<BossPhase> phases = List.of(
                phase(1.0, 34, 1.0, List.of(), "b3_stomp", "b3_charge", "b3_sand_breath", "b3_sandstone_pillars",
                        "b3_falling_rocks"),
                form(0.65, 30, 1.1, "b3_titan_roar", false, List.of("b3_dust_ring", "b3_quicksand_trail"),
                        "b3_stomp", "b3_charge", "b3_sand_breath", "b3_quicksand", "b3_dust_ring", "b3_sand_servants",
                        "b3_sandstone_pillars", "b3_falling_rocks", "b3_quicksand_trail", "b3_sand_tsunami"),
                form(0.3, 26, 1.2, "b3_titan_roar", false, List.of("b3_sandstorm", "b3_titan_rampage"),
                        "b3_stomp", "b3_charge", "b3_sand_breath", "b3_quicksand", "b3_dust_ring", "b3_sandstorm",
                        "b3_quicksand_trail", "b3_sand_tsunami", "b3_titan_rampage", "b3_eye_of_storm"));
        return boss("b3", "minecraft:ravager", 20, 5750, 9, 6, 1, 5, phases, abilities, ENRAGE_TICKS,
                List.of(minion), "b3_boulder_volley");
    }

    // ------------------------------------------------------------------ b4 凍獄の処刑人

    /** The executioner: chains that reach everywhere, frozen cells and the final verdict. */
    private static BossDefinition b4() {
        String minion = minionTemplateId("b4");
        List<BossAbility> abilities = List.of(
                hit("b4_execution", new AbilityKind.Cone(7, 40), 26, 14, 140, 2.4, 25, HIGHEST_THREAT),
                hit("b4_chain_pull", new AbilityKind.Line(15, 2), 18, 10, 120, 1.4, 20, FARTHEST),
                hit("b4_ice_shackles", new AbilityKind.CircleAtTargets(3, 3), 26, 10, 180, 1.4, 20, RANDOM),
                hit("b4_frost_nova", new AbilityKind.CircleAroundBoss(8), 24, 16, 160, 1.9, 20, NEAREST),
                act("b4_pursuit", new AbilityKind.TeleportToTarget(), 20, 10, 320, 10, FARTHEST),
                act("b4_jailers", new AbilityKind.Summon(minion, 3), 30, 10, 800, 8, NEAREST),
                hit("b4_judgement", new AbilityKind.Soak(4, 2), 56, 10, 600, 3.0, 10, RANDOM),
                hit("b4_frozen_verdict", new AbilityKind.ArenaWide(), 50, 20, 800, 1.8, 6, NEAREST),
                hit("b4_ice_ring", new AbilityKind.Ring(4, 11), 26, 12, 220, 2.0, 20, NEAREST),
                hit("b4_chain_cross", new AbilityKind.Crosses(15, 3, 2, 20), 26, 10, 320, 1.6, 18, NEAREST),
                hit("b4_icicle_drop", new AbilityKind.Scatter(8, 2.5, 12, 4, 0), 28, 10, 340, 1.5, 18, NEAREST),
                hit("b4_shackle_pull", new AbilityKind.Sequence(List.of(
                        new AbilityKind.Part(new AbilityKind.Knockback(15, -1.4), 0, 0.3),
                        AbilityKind.Part.of(new AbilityKind.CircleAroundBoss(6), 18))), 22, 10, 420, 1.9, 15,
                        NEAREST),
                hit("b4_frozen_cells", new AbilityKind.Checkerboard(6, 5, 3, 22), 30, 10, 420, 1.6, 15, NEAREST),
                hit("b4_beheading_spin", new AbilityKind.RotatingBeam(1, 15, 4, 45, 6, 12), 24, 10, 460, 1.8, 15,
                        HIGHEST_THREAT),
                hit("b4_permafrost", new AbilityKind.Scatter(4, 3.5, 10, 8, 160), 32, 10, 500, 1.3, 12, NEAREST),
                hit("b4_cold_fury", new AbilityKind.Knockback(9, 1.4), 38, 12, 0, 1.1, 1, NEAREST));
        List<BossPhase> phases = List.of(
                phase(1.0, 34, 1.0, List.of(), "b4_execution", "b4_chain_pull", "b4_ice_shackles", "b4_frost_nova",
                        "b4_chain_cross", "b4_icicle_drop"),
                form(0.6, 30, 1.1, "b4_cold_fury", false, List.of("b4_jailers", "b4_shackle_pull"),
                        "b4_execution", "b4_chain_pull", "b4_ice_shackles", "b4_frost_nova", "b4_pursuit",
                        "b4_jailers", "b4_chain_cross", "b4_icicle_drop", "b4_shackle_pull", "b4_frozen_cells"),
                form(0.3, 26, 1.2, "b4_cold_fury", false, List.of("b4_frozen_verdict", "b4_beheading_spin"),
                        "b4_execution", "b4_chain_pull", "b4_ice_shackles", "b4_frost_nova", "b4_pursuit",
                        "b4_judgement", "b4_frozen_verdict", "b4_shackle_pull", "b4_frozen_cells",
                        "b4_beheading_spin", "b4_permafrost"));
        return boss("b4", "minecraft:piglin_brute", 26, 6800, 10, 8, 1, 5, phases, abilities, ENRAGE_TICKS,
                List.of(minion), "b4_ice_ring");
    }

    // ------------------------------------------------------------------ b5 氷獄の女王

    /** Queen Ingrid: frost magic in four forms, from rune circles to the last thaw she fears. */
    private static BossDefinition b5() {
        String minion = minionTemplateId("b5");
        List<BossAbility> abilities = List.of(
                hit("b5_ice_lance", new AbilityKind.ProjectileVolley(3, 1.4, 24), 16, 10, 80, 1.2, 30,
                        HIGHEST_THREAT),
                hit("b5_frost_field", new AbilityKind.CircleAtTargets(4, 3), 28, 10, 180, 1.5, 20, RANDOM),
                hit("b5_blizzard", new AbilityKind.Cone(12.5, 30), 24, 16, 160, 1.8, 20, NEAREST),
                hit("b5_glacial_ring", new AbilityKind.Ring(5.5, 14.5), 30, 14, 220, 2.0, 15, NEAREST),
                act("b5_blink", new AbilityKind.TeleportToTarget(), 20, 10, 300, 10, RANDOM),
                act("b5_ice_barrier", new AbilityKind.Shield(minion, 3, 400), 30, 10, 1600, 5, NEAREST),
                hit("b5_frozen_heart", new AbilityKind.Soak(4, 2), 56, 10, 600, 3.0, 10, RANDOM),
                hit("b5_absolute_zero", new AbilityKind.ArenaWide(), 50, 20, 800, 2.0, 6, NEAREST),
                hit("b5_hailstorm", new AbilityKind.CircleAtTargets(3, 5), 24, 10, 200, 1.4, 20, RANDOM),
                hit("b5_frost_runes", new AbilityKind.Checkerboard(4, 7, 2, 22), 30, 10, 360, 1.6, 18, NEAREST),
                hit("b5_frozen_orbs", new AbilityKind.Scatter(4, 4, 10, 10, 100), 34, 10, 400, 1.5, 15, NEAREST),
                hit("b5_winter_waltz", new AbilityKind.Sequence(List.of(
                        AbilityKind.Part.of(new AbilityKind.CircleAroundBoss(7.5), 0),
                        AbilityKind.Part.of(new AbilityKind.Ring(4.5, 16.5), 20))), 28, 10, 420, 1.8, 15, NEAREST),
                hit("b5_snow_veil", new AbilityKind.Trail(2.5, 2, 6, 10, 60), 16, 8, 420, 1.2, 15, RANDOM),
                hit("b5_ice_spike_march", new AbilityKind.Exaflare(4, 6, 3, 7, 6), 28, 10, 420, 1.6, 15,
                        NEAREST),
                hit("b5_glacier_beams", new AbilityKind.RotatingBeam(3, 16.5, 3, 24, 5, 14), 28, 10, 460, 1.5, 15,
                        NEAREST),
                hit("b5_thaw_refuge", new AbilityKind.SafeZones(3, 3, 9), 60, 12, 900, 2.6, 8, NEAREST),
                hit("b5_winter_wrath", new AbilityKind.Knockback(8.5, 1.4), 38, 12, 0, 1.1, 1, NEAREST));
        List<BossPhase> phases = List.of(
                phase(1.0, 32, 1.0, List.of(), "b5_ice_lance", "b5_frost_field", "b5_blizzard", "b5_frost_runes",
                        "b5_frozen_orbs"),
                form(0.75, 30, 1.05, "b5_winter_wrath", false, List.of("b5_ice_barrier"),
                        "b5_ice_lance", "b5_frost_field", "b5_blizzard", "b5_glacial_ring", "b5_blink",
                        "b5_ice_barrier", "b5_frost_runes", "b5_frozen_orbs", "b5_winter_waltz", "b5_snow_veil"),
                form(0.45, 26, 1.15, "b5_winter_wrath", false, List.of("b5_ice_spike_march"),
                        "b5_ice_lance", "b5_frost_field", "b5_blizzard", "b5_glacial_ring", "b5_blink",
                        "b5_frozen_heart", "b5_winter_waltz", "b5_snow_veil", "b5_ice_spike_march",
                        "b5_glacier_beams"),
                form(0.2, 22, 1.25, "b5_winter_wrath", false, List.of("b5_absolute_zero", "b5_thaw_refuge"),
                        "b5_ice_lance", "b5_blizzard", "b5_glacial_ring", "b5_blink", "b5_frozen_heart",
                        "b5_absolute_zero", "b5_ice_spike_march", "b5_glacier_beams", "b5_thaw_refuge"));
        return boss("b5", "minecraft:evoker", 29, 7600, 11, 4, 1, 5, phases, abilities, ENRAGE_TICKS,
                List.of(minion), "b5_hailstorm");
    }

    // ------------------------------------------------------------------ b6 灼炎の竜王

    /** Azrag: breath and meteors, the burning sky, lava tides, and a raging last form. */
    private static BossDefinition b6() {
        String minion = minionTemplateId("b6");
        List<BossAbility> abilities = List.of(
                hit("b6_flame_breath", new AbilityKind.Cone(13.5, 35), 22, 16, 140, 2.0, 25, HIGHEST_THREAT),
                hit("b6_fireballs", new AbilityKind.ProjectileVolley(5, 1.2, 60), 16, 10, 100, 1.2, 25, RANDOM),
                hit("b6_magma_burst", new AbilityKind.CircleAroundBoss(7.5), 22, 14, 140, 1.8, 20, NEAREST),
                hit("b6_meteor", new AbilityKind.CircleAtTargets(4, 4), 30, 10, 200, 1.8, 20, RANDOM),
                hit("b6_inferno_ring", new AbilityKind.Ring(5.5, 13.5), 28, 14, 220, 2.0, 15, NEAREST),
                act("b6_salamanders", new AbilityKind.Summon(minion, 3), 30, 10, 800, 8, NEAREST),
                act("b6_dragon_rage", new AbilityKind.Buff(1.3, 300), 20, 10, 900, 8, NEAREST),
                hit("b6_sunfall", new AbilityKind.Soak(5, 2), 56, 10, 600, 3.2, 10, RANDOM),
                hit("b6_cataclysm", new AbilityKind.ArenaWide(), 50, 20, 800, 2.2, 6, NEAREST),
                hit("b6_flame_dive", new AbilityKind.Line(25.5, 4), 22, 12, 200, 2.2, 20, FARTHEST),
                hit("b6_inferno_cross", new AbilityKind.Crosses(17.5, 5, 2, 22), 28, 10, 340, 1.7, 18, NEAREST),
                hit("b6_meteor_storm", new AbilityKind.Scatter(10, 3.5, 13, 5, 60), 34, 10, 420, 1.5, 15,
                        NEAREST),
                hit("b6_fire_whirl", new AbilityKind.RotatingBeam(2, 16.5, 4, 30, 6, 12), 26, 10, 420, 1.6, 15,
                        NEAREST),
                hit("b6_lava_tide", new AbilityKind.Exaflare(2, 7, 3, 22, 6), 28, 10, 420, 1.6, 15, NEAREST),
                hit("b6_magma_trail", new AbilityKind.Trail(2.5, 2, 6, 10, 120), 16, 8, 460, 1.2, 15, RANDOM),
                hit("b6_dragon_dive", new AbilityKind.Charge(23.5, 7.5, 2, 30), 30, 12, 520, 2.2, 12, FARTHEST),
                hit("b6_wing_gale", new AbilityKind.Sequence(List.of(
                        new AbilityKind.Part(new AbilityKind.Knockback(15.5, -1.4), 0, 0.3),
                        AbilityKind.Part.of(new AbilityKind.CircleAroundBoss(8.5), 20))), 24, 10, 480, 1.9, 12,
                        NEAREST),
                hit("b6_obsidian_refuge", new AbilityKind.SafeZones(3, 3, 9), 60, 12, 900, 2.8, 8, NEAREST),
                hit("b6_dragon_roar", new AbilityKind.Knockback(10.5, 1.5), 38, 12, 0, 1.2, 1, NEAREST));
        List<BossPhase> phases = List.of(
                phase(1.0, 32, 1.0, List.of(), "b6_flame_breath", "b6_fireballs", "b6_magma_burst", "b6_meteor",
                        "b6_inferno_cross"),
                form(0.7, 28, 1.1, "b6_dragon_roar", false, List.of("b6_dragon_rage", "b6_meteor_storm"),
                        "b6_flame_breath", "b6_fireballs", "b6_magma_burst", "b6_meteor", "b6_inferno_ring",
                        "b6_salamanders", "b6_dragon_rage", "b6_inferno_cross", "b6_meteor_storm", "b6_fire_whirl"),
                form(0.4, 26, 1.15, "b6_dragon_roar", false, List.of("b6_lava_tide"),
                        "b6_flame_breath", "b6_fireballs", "b6_magma_burst", "b6_inferno_ring", "b6_dragon_rage",
                        "b6_sunfall", "b6_meteor_storm", "b6_fire_whirl", "b6_lava_tide", "b6_magma_trail",
                        "b6_dragon_dive", "b6_wing_gale"),
                form(0.15, 22, 1.25, "b6_dragon_roar", true, List.of("b6_cataclysm", "b6_obsidian_refuge"),
                        "b6_flame_breath", "b6_magma_burst", "b6_sunfall", "b6_cataclysm", "b6_fire_whirl",
                        "b6_lava_tide", "b6_dragon_dive", "b6_obsidian_refuge"));
        return boss("b6", "minecraft:blaze", 35, 8650, 12, 6, 1, 5, phases, abilities, ENRAGE_TICKS,
                List.of(minion), "b6_flame_dive");
    }

    // ------------------------------------------------------------------ b7 天空の機械神

    /** Ordo: a purge grid and missiles, locked targets, sweeping lasers and an overloaded last protocol. */
    private static BossDefinition b7() {
        String minion = minionTemplateId("b7");
        List<BossAbility> abilities = List.of(
                hit("b7_laser", new AbilityKind.Line(25.5, 3), 22, 14, 140, 2.0, 25, HIGHEST_THREAT),
                hit("b7_missiles", new AbilityKind.ProjectileVolley(6, 1.5, 70), 16, 10, 100, 1.1, 25, RANDOM),
                hit("b7_sweep", new AbilityKind.Cone(10.5, 60), 20, 12, 120, 1.8, 25, NEAREST),
                hit("b7_bombard", new AbilityKind.CircleAtTargets(4, 5), 28, 10, 200, 1.6, 20, RANDOM),
                hit("b7_overload_ring", new AbilityKind.Ring(6.5, 14.5), 28, 14, 220, 2.2, 15, NEAREST),
                act("b7_drone_barrier", new AbilityKind.Shield(minion, 4, 500), 30, 10, 1600, 5, NEAREST),
                act("b7_warp", new AbilityKind.TeleportToTarget(), 20, 10, 300, 10, FARTHEST),
                hit("b7_judgment_beam", new AbilityKind.Soak(5, 3), 56, 10, 600, 3.6, 10, RANDOM),
                hit("b7_system_purge", new AbilityKind.ArenaWide(), 50, 20, 800, 2.4, 6, NEAREST),
                act("b7_self_repair", new AbilityKind.Heal(0.04), 40, 10, 900, 6, NEAREST),
                hit("b7_orbital_strike", new AbilityKind.CircleAtTargets(6, 2), 32, 10, 260, 2.5, 15,
                        HIGHEST_THREAT),
                hit("b7_grid_purge", new AbilityKind.Checkerboard(4, 7, 4, 16), 28, 10, 380, 1.5, 18, NEAREST),
                hit("b7_missile_rain", new AbilityKind.Scatter(10, 3, 13, 4, 0), 30, 10, 380, 1.5, 15, NEAREST),
                hit("b7_target_lock", new AbilityKind.Trail(3, 2, 6, 10, 0), 14, 8, 400, 1.4, 15, RANDOM),
                hit("b7_rotating_lasers", new AbilityKind.RotatingBeam(4, 21.5, 3, 15, 8, 10), 28, 10, 460, 1.5, 15,
                        NEAREST),
                hit("b7_order_lines", new AbilityKind.Exaflare(2, 7, 3, 26, 5), 26, 10, 420, 1.6, 15, NEAREST),
                hit("b7_gravity_core", new AbilityKind.Sequence(List.of(
                        new AbilityKind.Part(new AbilityKind.Knockback(19.5, -1.6), 0, 0.3),
                        AbilityKind.Part.of(new AbilityKind.CircleAroundBoss(7.5), 20))), 26, 10, 480, 2.0, 12,
                        NEAREST),
                hit("b7_safe_sector", new AbilityKind.SafeZones(2, 3.5, 9), 60, 12, 900, 3.0, 8, NEAREST),
                hit("b7_reboot", new AbilityKind.Knockback(10.5, 1.5), 38, 12, 0, 1.2, 1, NEAREST));
        List<BossPhase> phases = List.of(
                phase(1.0, 30, 1.0, List.of(), "b7_laser", "b7_missiles", "b7_sweep", "b7_bombard",
                        "b7_grid_purge"),
                form(0.75, 26, 1.1, "b7_reboot", false, List.of("b7_drone_barrier", "b7_target_lock"),
                        "b7_laser", "b7_missiles", "b7_sweep", "b7_bombard", "b7_overload_ring", "b7_drone_barrier",
                        "b7_warp", "b7_grid_purge", "b7_missile_rain", "b7_target_lock"),
                form(0.45, 24, 1.15, "b7_reboot", false, List.of("b7_rotating_lasers"),
                        "b7_laser", "b7_missiles", "b7_sweep", "b7_overload_ring", "b7_warp", "b7_judgment_beam",
                        "b7_self_repair", "b7_missile_rain", "b7_target_lock", "b7_rotating_lasers",
                        "b7_order_lines", "b7_gravity_core"),
                form(0.15, 20, 1.25, "b7_reboot", true, List.of("b7_system_purge", "b7_safe_sector"),
                        "b7_laser", "b7_sweep", "b7_judgment_beam", "b7_system_purge", "b7_rotating_lasers",
                        "b7_order_lines", "b7_gravity_core", "b7_safe_sector"));
        return boss("b7", "minecraft:iron_golem", 42, 9500, 13, 12, 1, 5, phases, abilities, ENRAGE_TICKS,
                List.of(minion), "b7_orbital_strike");
    }

    // ------------------------------------------------------------------ b8 終焉の門番

    /** Glaus at the gate: crossing blades, void orbs, the tide of the abyss and a last stand by the gate's light. */
    private static BossDefinition b8() {
        String minion = minionTemplateId("b8");
        List<BossAbility> abilities = List.of(
                hit("b8_crushing_blow", new AbilityKind.Cone(7, 45), 24, 14, 120, 2.4, 25, HIGHEST_THREAT),
                hit("b8_shockwave", new AbilityKind.Line(21, 4), 22, 12, 140, 2.0, 20, FARTHEST),
                hit("b8_dark_pillars", new AbilityKind.CircleAtTargets(4, 5), 28, 10, 200, 1.7, 20, RANDOM),
                act("b8_void_step", new AbilityKind.TeleportToTarget(), 20, 10, 300, 10, FARTHEST),
                hit("b8_abyss_ring", new AbilityKind.Ring(6, 15), 28, 14, 220, 2.2, 15, NEAREST),
                act("b8_shades", new AbilityKind.Summon(minion, 4), 30, 10, 800, 8, NEAREST),
                act("b8_gate_seal", new AbilityKind.Shield(minion, 4, 500), 30, 10, 1600, 5, NEAREST),
                hit("b8_soul_brand", new AbilityKind.Soak(5, 2), 56, 10, 600, 3.4, 10, RANDOM),
                hit("b8_end_pulse", new AbilityKind.ArenaWide(), 50, 20, 800, 2.4, 6, NEAREST),
                act("b8_fury", new AbilityKind.Buff(1.25, 400), 20, 10, 900, 8, NEAREST),
                hit("b8_collapse", new AbilityKind.CircleAroundBoss(10), 30, 16, 260, 2.6, 15, NEAREST),
                hit("b8_gate_cross", new AbilityKind.Crosses(19, 4, 3, 18), 28, 10, 360, 1.7, 18, NEAREST),
                hit("b8_void_orbs", new AbilityKind.Scatter(6, 4, 12, 8, 80), 32, 10, 400, 1.5, 15, NEAREST),
                hit("b8_soul_hunt", new AbilityKind.Trail(3, 2, 7, 9, 0), 14, 8, 420, 1.4, 15, RANDOM),
                hit("b8_abyss_tide", new AbilityKind.Exaflare(4, 7, 3, 9, 5), 28, 10, 420, 1.6, 15, NEAREST),
                hit("b8_vigil", new AbilityKind.Sequence(List.of(
                        new AbilityKind.Part(new AbilityKind.Knockback(13, 1.6), 0, 0.5),
                        AbilityKind.Part.of(new AbilityKind.Ring(9, 21), 24))), 26, 10, 480, 2.0, 12, NEAREST),
                hit("b8_judgment_blades", new AbilityKind.RotatingBeam(2, 19, 4, 30, 6, 12), 26, 10, 460, 1.7, 15,
                        NEAREST),
                hit("b8_last_light", new AbilityKind.SafeZones(2, 3.5, 9), 60, 12, 900, 3.0, 8, NEAREST),
                hit("b8_gate_roar", new AbilityKind.Knockback(10, 1.6), 38, 12, 0, 1.2, 1, NEAREST));
        List<BossPhase> phases = List.of(
                phase(1.0, 30, 1.0, List.of(), "b8_crushing_blow", "b8_shockwave", "b8_dark_pillars",
                        "b8_void_step", "b8_gate_cross"),
                form(0.75, 26, 1.1, "b8_gate_roar", false, List.of("b8_gate_seal"),
                        "b8_crushing_blow", "b8_shockwave", "b8_dark_pillars", "b8_void_step", "b8_abyss_ring",
                        "b8_shades", "b8_gate_seal", "b8_gate_cross", "b8_void_orbs", "b8_soul_hunt"),
                form(0.45, 22, 1.2, "b8_gate_roar", false, List.of("b8_end_pulse", "b8_fury"),
                        "b8_crushing_blow", "b8_shockwave", "b8_dark_pillars", "b8_void_step", "b8_abyss_ring",
                        "b8_soul_brand", "b8_end_pulse", "b8_fury", "b8_void_orbs", "b8_soul_hunt", "b8_abyss_tide",
                        "b8_vigil"),
                form(0.15, 20, 1.3, "b8_gate_roar", true, List.of("b8_last_light", "b8_judgment_blades"),
                        "b8_crushing_blow", "b8_shockwave", "b8_soul_brand", "b8_end_pulse", "b8_abyss_tide",
                        "b8_vigil", "b8_judgment_blades", "b8_last_light"));
        return boss("b8", "minecraft:warden", 47, 10600, 14, 10, 1, 5, phases, abilities, ENRAGE_TICKS,
                List.of(minion), "b8_collapse");
    }

    // ------------------------------------------------------------------ final 終焉の王

    /** The King of the End: four forms, each opened by an arena-wide attack; the vessel cracks between them. */
    private static BossDefinition finalBoss() {
        String minion = minionTemplateId("final");
        List<BossAbility> abilities = List.of(
                hit("final_cleave", new AbilityKind.Cone(9, 60), 22, 14, 120, 2.2, 25, HIGHEST_THREAT),
                hit("final_dark_lance", new AbilityKind.Line(29, 4), 24, 12, 140, 2.2, 20, FARTHEST),
                hit("final_meteor_rain", new AbilityKind.CircleAtTargets(4, 8), 30, 10, 200, 1.6, 20, RANDOM),
                hit("final_doom_mark", new AbilityKind.Soak(5, 5), 60, 10, 600, 8.0, 12, RANDOM),
                hit("final_ruin_ring", new AbilityKind.Ring(7, 19), 30, 14, 240, 2.4, 15, NEAREST),
                hit("final_dark_volley", new AbilityKind.ProjectileVolley(8, 1.6, 90), 18, 10, 120, 1.2, 20,
                        RANDOM),
                act("final_royal_guard", new AbilityKind.Shield(minion, 6, 600), 30, 10, 1800, 4, NEAREST),
                act("final_legion", new AbilityKind.Summon(minion, 6), 30, 10, 900, 6, NEAREST),
                act("final_shadow_step", new AbilityKind.TeleportToTarget(), 20, 10, 300, 8, FARTHEST),
                act("final_dark_aura", new AbilityKind.Buff(1.3, 400), 20, 10, 1000, 6, NEAREST),
                hit("final_end_wave", new AbilityKind.ArenaWide(), 60, 20, 900, 2.0, 6, NEAREST),
                hit("final_eclipse", new AbilityKind.ArenaWide(), 60, 20, 900, 2.4, 6, NEAREST),
                hit("final_oblivion", new AbilityKind.ArenaWide(), 60, 20, 900, 2.8, 6, NEAREST),
                hit("final_end_of_all", new AbilityKind.ArenaWide(), 60, 20, 700, 3.2, 8, NEAREST),
                hit("final_void_rift", new AbilityKind.CircleAroundBoss(13), 34, 16, 300, 3.0, 12, NEAREST),
                hit("final_starfall", new AbilityKind.Scatter(14, 4, 22, 4, 0), 36, 10, 420, 1.6, 15, NEAREST),
                hit("final_cross_of_ruin", new AbilityKind.Crosses(27, 5, 3, 18), 30, 10, 380, 1.8, 15, NEAREST),
                hit("final_eclipse_board", new AbilityKind.Checkerboard(5, 9, 2, 22), 32, 10, 420, 1.7, 15,
                        NEAREST),
                hit("final_doom_chase", new AbilityKind.Trail(3, 4, 6, 10, 80), 16, 8, 460, 1.4, 15, RANDOM),
                hit("final_oblivion_beams", new AbilityKind.RotatingBeam(4, 25, 4, 15, 8, 10), 30, 10, 480, 1.6,
                        15, NEAREST),
                hit("final_ruin_tide", new AbilityKind.Exaflare(4, 8, 3, 30, 5), 30, 10, 460, 1.7, 15, NEAREST),
                hit("final_gravity", new AbilityKind.Sequence(List.of(
                        new AbilityKind.Part(new AbilityKind.Knockback(25, -1.8), 0, 0.3),
                        AbilityKind.Part.of(new AbilityKind.CircleAroundBoss(9), 22))), 28, 10, 520, 2.2, 12,
                        NEAREST),
                hit("final_last_light", new AbilityKind.SafeZones(4, 3.5, 14), 60, 14, 1000, 3.2, 8, NEAREST),
                hit("final_vessel_rupture", new AbilityKind.Knockback(12, 1.6), 40, 14, 0, 1.2, 1, NEAREST));
        List<BossPhase> phases = List.of(
                phase(1.0, 30, 1.0, List.of("final_end_wave"),
                        "final_cleave", "final_dark_lance", "final_meteor_rain", "final_doom_mark", "final_end_wave",
                        "final_starfall", "final_cross_of_ruin"),
                form(0.75, 28, 1.1, "final_vessel_rupture", false, List.of("final_eclipse", "final_royal_guard"),
                        "final_cleave", "final_dark_lance", "final_meteor_rain", "final_doom_mark", "final_ruin_ring",
                        "final_dark_volley", "final_royal_guard", "final_eclipse", "final_starfall",
                        "final_cross_of_ruin", "final_eclipse_board", "final_doom_chase"),
                form(0.5, 24, 1.15, "final_vessel_rupture", false, List.of("final_oblivion", "final_legion"),
                        "final_cleave", "final_dark_lance", "final_meteor_rain", "final_doom_mark", "final_ruin_ring",
                        "final_dark_volley", "final_legion", "final_shadow_step", "final_oblivion",
                        "final_eclipse_board", "final_doom_chase", "final_oblivion_beams", "final_ruin_tide"),
                form(0.25, 20, 1.25, "final_vessel_rupture", false,
                        List.of("final_end_of_all", "final_royal_guard", "final_last_light"),
                        "final_cleave", "final_dark_lance", "final_doom_mark", "final_ruin_ring",
                        "final_dark_volley", "final_royal_guard", "final_shadow_step", "final_dark_aura",
                        "final_end_of_all", "final_oblivion_beams", "final_ruin_tide", "final_gravity",
                        "final_last_light"));
        return boss("final", "minecraft:wither_skeleton", 50, 18400, 15, 12, 10, 20, phases, abilities,
                FINAL_ENRAGE_TICKS, List.of(minion), "final_void_rift");
    }

    // ------------------------------------------------------------------ builders

    /** A damaging ability. */
    private static BossAbility hit(String id, AbilityKind kind, int telegraph, int cast, int cooldown,
                                   double multiplier, int weight, TargetRule rule) {
        return new BossAbility(id, kind, telegraph, cast, cooldown, multiplier, weight, rule);
    }

    /** A non-damaging ability (summon, shield, teleport, heal, buff). */
    private static BossAbility act(String id, AbilityKind kind, int telegraph, int cast, int cooldown, int weight,
                                   TargetRule rule) {
        return new BossAbility(id, kind, telegraph, cast, cooldown, 0.0, weight, rule);
    }

    private static BossPhase phase(double startsAt, int globalCooldown, double speed, List<String> openers,
                                   String... abilityIds) {
        return new BossPhase(startsAt, List.of(abilityIds), globalCooldown, speed, openers);
    }

    /** A later form: entered through an invulnerable transition in which the boss uses {@code transition}. */
    private static BossPhase form(double startsAt, int globalCooldown, double speed, String transition, boolean enrages,
                                  List<String> openers, String... abilityIds) {
        return new BossPhase(startsAt, List.of(abilityIds), globalCooldown, speed, openers, transition, enrages);
    }

    private static BossDefinition boss(String id, String entityType, int level, double health, double damage,
                                       double armor, int minPlayers, int maxPlayers, List<BossPhase> phases,
                                       List<BossAbility> abilities, int enrageTicks, List<String> summons,
                                       String exAbility) {
        return new BossDefinition(id, entityType, level, health, damage, armor, PARTY_SCALING, minPlayers, maxPlayers,
                phases, abilities, enrageTicks, summons, DifficultyTuning.NORMAL, DifficultyTuning.hard(),
                DifficultyTuning.ex(exAbility));
    }
}
