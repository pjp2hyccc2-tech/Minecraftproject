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

    public static Optional<BossDefinition> byId(String id) {
        for (BossDefinition definition : ALL) {
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

    private static BossDefinition b1() {
        String minion = minionTemplateId("b1");
        List<BossAbility> abilities = List.of(
                hit("b1_cleave", new AbilityKind.Cone(5, 50), 16, 10, 80, 1.6, 30, NEAREST),
                hit("b1_dash", new AbilityKind.Line(12, 3), 20, 12, 140, 1.8, 20, FARTHEST),
                hit("b1_smoke_bombs", new AbilityKind.CircleAtTargets(3, 3), 30, 10, 200, 1.4, 20, RANDOM),
                hit("b1_whirlwind", new AbilityKind.CircleAroundBoss(5), 24, 20, 160, 2.0, 25, NEAREST),
                act("b1_call_gang", new AbilityKind.Summon(minion, 3), 30, 10, 700, 10, NEAREST),
                hit("b1_knife_fan", new AbilityKind.ProjectileVolley(5, 1.2, 50), 16, 8, 160, 1.0, 20,
                        HIGHEST_THREAT));
        List<BossPhase> phases = List.of(
                phase(1.0, 36, 1.0, List.of(), "b1_cleave", "b1_dash", "b1_smoke_bombs"),
                phase(0.5, 28, 1.15, List.of("b1_call_gang"),
                        "b1_cleave", "b1_dash", "b1_smoke_bombs", "b1_whirlwind", "b1_call_gang"));
        return boss("b1", "minecraft:vindicator", 6, 3100, 6, 4, 1, 5, phases, abilities, ENRAGE_TICKS,
                List.of(minion), "b1_knife_fan");
    }

    // ------------------------------------------------------------------ b2 古代樹の守護者

    private static BossDefinition b2() {
        String minion = minionTemplateId("b2");
        List<BossAbility> abilities = List.of(
                hit("b2_root_slam", new AbilityKind.CircleAroundBoss(6), 24, 16, 140, 2.0, 25, NEAREST),
                hit("b2_thorn_line", new AbilityKind.Line(16, 3), 20, 10, 120, 1.7, 25, HIGHEST_THREAT),
                hit("b2_branch_sweep", new AbilityKind.Cone(7, 70), 18, 10, 100, 1.5, 25, NEAREST),
                hit("b2_spore_burst", new AbilityKind.CircleAtTargets(3.5, 4), 30, 10, 180, 1.4, 20, RANDOM),
                act("b2_sapling_call", new AbilityKind.Summon(minion, 3), 30, 10, 700, 10, NEAREST),
                act("b2_bark_shield", new AbilityKind.Shield(minion, 3, 400), 30, 10, 1600, 5, NEAREST),
                act("b2_regrowth", new AbilityKind.Heal(0.05), 40, 10, 900, 8, NEAREST),
                hit("b2_vine_ring", new AbilityKind.Ring(3, 9), 26, 12, 220, 2.0, 20, NEAREST));
        List<BossPhase> phases = List.of(
                phase(1.0, 36, 1.0, List.of(), "b2_root_slam", "b2_thorn_line", "b2_branch_sweep"),
                phase(0.6, 30, 1.1, List.of("b2_sapling_call"),
                        "b2_root_slam", "b2_thorn_line", "b2_branch_sweep", "b2_spore_burst", "b2_sapling_call"),
                phase(0.3, 26, 1.2, List.of("b2_bark_shield"),
                        "b2_root_slam", "b2_thorn_line", "b2_branch_sweep", "b2_spore_burst", "b2_bark_shield",
                        "b2_regrowth"));
        return boss("b2", "minecraft:iron_golem", 13, 4300, 7.5, 8, 1, 5, phases, abilities, ENRAGE_TICKS,
                List.of(minion), "b2_vine_ring");
    }

    // ------------------------------------------------------------------ b3 砂塵の巨神

    private static BossDefinition b3() {
        String minion = minionTemplateId("b3");
        List<BossAbility> abilities = List.of(
                hit("b3_stomp", new AbilityKind.CircleAroundBoss(5), 22, 14, 120, 1.8, 25, NEAREST),
                hit("b3_charge", new AbilityKind.Line(20, 4), 24, 14, 160, 2.0, 20, FARTHEST),
                hit("b3_sand_breath", new AbilityKind.Cone(10, 35), 20, 16, 120, 1.6, 25, NEAREST),
                hit("b3_quicksand", new AbilityKind.CircleAtTargets(4, 3), 30, 10, 200, 1.4, 20, RANDOM),
                hit("b3_dust_ring", new AbilityKind.Ring(4, 12), 28, 14, 220, 2.0, 20, NEAREST),
                act("b3_sand_servants", new AbilityKind.Summon(minion, 3), 30, 10, 800, 8, NEAREST),
                hit("b3_sandstorm", new AbilityKind.ArenaWide(), 50, 20, 800, 1.8, 6, NEAREST),
                hit("b3_boulder_volley", new AbilityKind.ProjectileVolley(3, 1.0, 30), 18, 10, 180, 1.2, 20,
                        HIGHEST_THREAT));
        List<BossPhase> phases = List.of(
                phase(1.0, 34, 1.0, List.of(), "b3_stomp", "b3_charge", "b3_sand_breath"),
                phase(0.65, 30, 1.1, List.of("b3_dust_ring"),
                        "b3_stomp", "b3_charge", "b3_sand_breath", "b3_quicksand", "b3_dust_ring",
                        "b3_sand_servants"),
                phase(0.3, 26, 1.2, List.of("b3_sandstorm"),
                        "b3_stomp", "b3_charge", "b3_sand_breath", "b3_quicksand", "b3_dust_ring",
                        "b3_sandstorm"));
        return boss("b3", "minecraft:ravager", 20, 5750, 9, 6, 1, 5, phases, abilities, ENRAGE_TICKS,
                List.of(minion), "b3_boulder_volley");
    }

    // ------------------------------------------------------------------ b4 凍獄の処刑人

    private static BossDefinition b4() {
        String minion = minionTemplateId("b4");
        List<BossAbility> abilities = List.of(
                hit("b4_execution", new AbilityKind.Cone(6, 40), 26, 14, 140, 2.4, 25, HIGHEST_THREAT),
                hit("b4_chain_pull", new AbilityKind.Line(14, 2), 18, 10, 120, 1.4, 20, FARTHEST),
                hit("b4_ice_shackles", new AbilityKind.CircleAtTargets(3, 3), 26, 10, 180, 1.4, 20, RANDOM),
                hit("b4_frost_nova", new AbilityKind.CircleAroundBoss(7), 24, 16, 160, 1.9, 20, NEAREST),
                act("b4_pursuit", new AbilityKind.TeleportToTarget(), 20, 10, 320, 10, FARTHEST),
                act("b4_jailers", new AbilityKind.Summon(minion, 3), 30, 10, 800, 8, NEAREST),
                hit("b4_judgement", new AbilityKind.Soak(4, 2), 56, 10, 600, 3.0, 10, RANDOM),
                hit("b4_frozen_verdict", new AbilityKind.ArenaWide(), 50, 20, 800, 1.8, 6, NEAREST),
                hit("b4_ice_ring", new AbilityKind.Ring(3, 10), 26, 12, 220, 2.0, 20, NEAREST));
        List<BossPhase> phases = List.of(
                phase(1.0, 34, 1.0, List.of(), "b4_execution", "b4_chain_pull", "b4_ice_shackles", "b4_frost_nova"),
                phase(0.6, 30, 1.1, List.of("b4_jailers"),
                        "b4_execution", "b4_chain_pull", "b4_ice_shackles", "b4_frost_nova", "b4_pursuit",
                        "b4_jailers"),
                phase(0.3, 26, 1.2, List.of("b4_frozen_verdict"),
                        "b4_execution", "b4_chain_pull", "b4_ice_shackles", "b4_frost_nova", "b4_pursuit",
                        "b4_judgement", "b4_frozen_verdict"));
        return boss("b4", "minecraft:piglin_brute", 26, 6800, 10, 8, 1, 5, phases, abilities, ENRAGE_TICKS,
                List.of(minion), "b4_ice_ring");
    }

    // ------------------------------------------------------------------ b5 氷獄の女王

    private static BossDefinition b5() {
        String minion = minionTemplateId("b5");
        List<BossAbility> abilities = List.of(
                hit("b5_ice_lance", new AbilityKind.ProjectileVolley(3, 1.4, 24), 16, 10, 80, 1.2, 30,
                        HIGHEST_THREAT),
                hit("b5_frost_field", new AbilityKind.CircleAtTargets(4, 3), 28, 10, 180, 1.5, 20, RANDOM),
                hit("b5_blizzard", new AbilityKind.Cone(12, 30), 24, 16, 160, 1.8, 20, NEAREST),
                hit("b5_glacial_ring", new AbilityKind.Ring(5, 14), 30, 14, 220, 2.0, 15, NEAREST),
                act("b5_blink", new AbilityKind.TeleportToTarget(), 20, 10, 300, 10, RANDOM),
                act("b5_ice_barrier", new AbilityKind.Shield(minion, 3, 400), 30, 10, 1600, 5, NEAREST),
                hit("b5_frozen_heart", new AbilityKind.Soak(4, 2), 56, 10, 600, 3.0, 10, RANDOM),
                hit("b5_absolute_zero", new AbilityKind.ArenaWide(), 50, 20, 800, 2.0, 6, NEAREST),
                hit("b5_hailstorm", new AbilityKind.CircleAtTargets(3, 5), 24, 10, 200, 1.4, 20, RANDOM));
        List<BossPhase> phases = List.of(
                phase(1.0, 32, 1.0, List.of(), "b5_ice_lance", "b5_frost_field", "b5_blizzard"),
                phase(0.6, 28, 1.1, List.of("b5_ice_barrier"),
                        "b5_ice_lance", "b5_frost_field", "b5_blizzard", "b5_glacial_ring", "b5_blink",
                        "b5_ice_barrier"),
                phase(0.3, 24, 1.2, List.of("b5_absolute_zero"),
                        "b5_ice_lance", "b5_frost_field", "b5_blizzard", "b5_glacial_ring", "b5_blink",
                        "b5_frozen_heart", "b5_absolute_zero"));
        return boss("b5", "minecraft:evoker", 29, 7600, 11, 4, 1, 5, phases, abilities, ENRAGE_TICKS,
                List.of(minion), "b5_hailstorm");
    }

    // ------------------------------------------------------------------ b6 灼炎の竜王

    private static BossDefinition b6() {
        String minion = minionTemplateId("b6");
        List<BossAbility> abilities = List.of(
                hit("b6_flame_breath", new AbilityKind.Cone(12, 35), 22, 16, 140, 2.0, 25, HIGHEST_THREAT),
                hit("b6_fireballs", new AbilityKind.ProjectileVolley(5, 1.2, 60), 16, 10, 100, 1.2, 25, RANDOM),
                hit("b6_magma_burst", new AbilityKind.CircleAroundBoss(6), 22, 14, 140, 1.8, 20, NEAREST),
                hit("b6_meteor", new AbilityKind.CircleAtTargets(4, 4), 30, 10, 200, 1.8, 20, RANDOM),
                hit("b6_inferno_ring", new AbilityKind.Ring(4, 12), 28, 14, 220, 2.0, 15, NEAREST),
                act("b6_salamanders", new AbilityKind.Summon(minion, 3), 30, 10, 800, 8, NEAREST),
                act("b6_dragon_rage", new AbilityKind.Buff(1.3, 300), 20, 10, 900, 8, NEAREST),
                hit("b6_sunfall", new AbilityKind.Soak(5, 2), 56, 10, 600, 3.2, 10, RANDOM),
                hit("b6_cataclysm", new AbilityKind.ArenaWide(), 50, 20, 800, 2.2, 6, NEAREST),
                hit("b6_flame_dive", new AbilityKind.Line(24, 4), 22, 12, 200, 2.2, 20, FARTHEST));
        List<BossPhase> phases = List.of(
                phase(1.0, 32, 1.0, List.of(), "b6_flame_breath", "b6_fireballs", "b6_magma_burst", "b6_meteor"),
                phase(0.6, 28, 1.1, List.of("b6_dragon_rage"),
                        "b6_flame_breath", "b6_fireballs", "b6_magma_burst", "b6_meteor", "b6_inferno_ring",
                        "b6_salamanders", "b6_dragon_rage"),
                phase(0.3, 24, 1.2, List.of("b6_cataclysm"),
                        "b6_flame_breath", "b6_fireballs", "b6_magma_burst", "b6_meteor", "b6_inferno_ring",
                        "b6_dragon_rage", "b6_sunfall", "b6_cataclysm"));
        return boss("b6", "minecraft:blaze", 35, 8650, 12, 6, 1, 5, phases, abilities, ENRAGE_TICKS,
                List.of(minion), "b6_flame_dive");
    }

    // ------------------------------------------------------------------ b7 天空の機械神

    private static BossDefinition b7() {
        String minion = minionTemplateId("b7");
        List<BossAbility> abilities = List.of(
                hit("b7_laser", new AbilityKind.Line(24, 3), 22, 14, 140, 2.0, 25, HIGHEST_THREAT),
                hit("b7_missiles", new AbilityKind.ProjectileVolley(6, 1.5, 70), 16, 10, 100, 1.1, 25, RANDOM),
                hit("b7_sweep", new AbilityKind.Cone(9, 60), 20, 12, 120, 1.8, 25, NEAREST),
                hit("b7_bombard", new AbilityKind.CircleAtTargets(4, 5), 28, 10, 200, 1.6, 20, RANDOM),
                hit("b7_overload_ring", new AbilityKind.Ring(5, 13), 28, 14, 220, 2.2, 15, NEAREST),
                act("b7_drone_barrier", new AbilityKind.Shield(minion, 4, 500), 30, 10, 1600, 5, NEAREST),
                act("b7_warp", new AbilityKind.TeleportToTarget(), 20, 10, 300, 10, FARTHEST),
                hit("b7_judgment_beam", new AbilityKind.Soak(5, 3), 56, 10, 600, 3.6, 10, RANDOM),
                hit("b7_system_purge", new AbilityKind.ArenaWide(), 50, 20, 800, 2.4, 6, NEAREST),
                act("b7_self_repair", new AbilityKind.Heal(0.04), 40, 10, 900, 6, NEAREST),
                hit("b7_orbital_strike", new AbilityKind.CircleAtTargets(6, 2), 32, 10, 260, 2.5, 15,
                        HIGHEST_THREAT));
        List<BossPhase> phases = List.of(
                phase(1.0, 30, 1.0, List.of(), "b7_laser", "b7_missiles", "b7_sweep", "b7_bombard"),
                phase(0.6, 26, 1.1, List.of("b7_drone_barrier"),
                        "b7_laser", "b7_missiles", "b7_sweep", "b7_bombard", "b7_overload_ring",
                        "b7_drone_barrier", "b7_warp"),
                phase(0.3, 22, 1.2, List.of("b7_system_purge"),
                        "b7_laser", "b7_missiles", "b7_sweep", "b7_bombard", "b7_overload_ring", "b7_warp",
                        "b7_judgment_beam", "b7_system_purge", "b7_self_repair"));
        return boss("b7", "minecraft:iron_golem", 42, 9500, 13, 12, 1, 5, phases, abilities, ENRAGE_TICKS,
                List.of(minion), "b7_orbital_strike");
    }

    // ------------------------------------------------------------------ b8 終焉の門番

    private static BossDefinition b8() {
        String minion = minionTemplateId("b8");
        List<BossAbility> abilities = List.of(
                hit("b8_crushing_blow", new AbilityKind.Cone(6, 45), 24, 14, 120, 2.4, 25, HIGHEST_THREAT),
                hit("b8_shockwave", new AbilityKind.Line(20, 4), 22, 12, 140, 2.0, 20, FARTHEST),
                hit("b8_dark_pillars", new AbilityKind.CircleAtTargets(4, 5), 28, 10, 200, 1.7, 20, RANDOM),
                act("b8_void_step", new AbilityKind.TeleportToTarget(), 20, 10, 300, 10, FARTHEST),
                hit("b8_abyss_ring", new AbilityKind.Ring(5, 14), 28, 14, 220, 2.2, 15, NEAREST),
                act("b8_shades", new AbilityKind.Summon(minion, 4), 30, 10, 800, 8, NEAREST),
                act("b8_gate_seal", new AbilityKind.Shield(minion, 4, 500), 30, 10, 1600, 5, NEAREST),
                hit("b8_soul_brand", new AbilityKind.Soak(5, 2), 56, 10, 600, 3.4, 10, RANDOM),
                hit("b8_end_pulse", new AbilityKind.ArenaWide(), 50, 20, 800, 2.4, 6, NEAREST),
                act("b8_fury", new AbilityKind.Buff(1.25, 400), 20, 10, 900, 8, NEAREST),
                hit("b8_collapse", new AbilityKind.CircleAroundBoss(9), 30, 16, 260, 2.6, 15, NEAREST));
        List<BossPhase> phases = List.of(
                phase(1.0, 30, 1.0, List.of(), "b8_crushing_blow", "b8_shockwave", "b8_dark_pillars", "b8_void_step"),
                phase(0.6, 26, 1.1, List.of("b8_gate_seal"),
                        "b8_crushing_blow", "b8_shockwave", "b8_dark_pillars", "b8_void_step", "b8_abyss_ring",
                        "b8_shades", "b8_gate_seal"),
                phase(0.3, 22, 1.25, List.of("b8_end_pulse", "b8_fury"),
                        "b8_crushing_blow", "b8_shockwave", "b8_dark_pillars", "b8_void_step", "b8_abyss_ring",
                        "b8_soul_brand", "b8_end_pulse", "b8_fury"));
        return boss("b8", "minecraft:warden", 47, 10600, 14, 10, 1, 5, phases, abilities, ENRAGE_TICKS,
                List.of(minion), "b8_collapse");
    }

    // ------------------------------------------------------------------ final 終焉の王

    private static BossDefinition finalBoss() {
        String minion = minionTemplateId("final");
        List<BossAbility> abilities = List.of(
                hit("final_cleave", new AbilityKind.Cone(8, 60), 22, 14, 120, 2.2, 25, HIGHEST_THREAT),
                hit("final_dark_lance", new AbilityKind.Line(28, 4), 24, 12, 140, 2.2, 20, FARTHEST),
                hit("final_meteor_rain", new AbilityKind.CircleAtTargets(4, 8), 30, 10, 200, 1.6, 20, RANDOM),
                hit("final_doom_mark", new AbilityKind.Soak(5, 5), 60, 10, 600, 8.0, 12, RANDOM),
                hit("final_ruin_ring", new AbilityKind.Ring(6, 18), 30, 14, 240, 2.4, 15, NEAREST),
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
                hit("final_void_rift", new AbilityKind.CircleAroundBoss(12), 34, 16, 300, 3.0, 12, NEAREST));
        List<BossPhase> phases = List.of(
                phase(1.0, 30, 1.0, List.of("final_end_wave"),
                        "final_cleave", "final_dark_lance", "final_meteor_rain", "final_doom_mark", "final_end_wave"),
                phase(0.75, 28, 1.1, List.of("final_eclipse", "final_royal_guard"),
                        "final_cleave", "final_dark_lance", "final_meteor_rain", "final_doom_mark", "final_ruin_ring",
                        "final_dark_volley", "final_royal_guard", "final_eclipse"),
                phase(0.5, 24, 1.15, List.of("final_oblivion", "final_legion"),
                        "final_cleave", "final_dark_lance", "final_meteor_rain", "final_doom_mark", "final_ruin_ring",
                        "final_dark_volley", "final_legion", "final_shadow_step", "final_oblivion"),
                phase(0.25, 20, 1.25, List.of("final_end_of_all", "final_royal_guard"),
                        "final_cleave", "final_dark_lance", "final_meteor_rain", "final_doom_mark", "final_ruin_ring",
                        "final_dark_volley", "final_royal_guard", "final_shadow_step", "final_dark_aura",
                        "final_end_of_all"));
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

    private static BossDefinition boss(String id, String entityType, int level, double health, double damage,
                                       double armor, int minPlayers, int maxPlayers, List<BossPhase> phases,
                                       List<BossAbility> abilities, int enrageTicks, List<String> summons,
                                       String exAbility) {
        return new BossDefinition(id, entityType, level, health, damage, armor, PARTY_SCALING, minPlayers, maxPlayers,
                phases, abilities, enrageTicks, summons, DifficultyTuning.NORMAL, DifficultyTuning.hard(),
                DifficultyTuning.ex(exAbility));
    }
}
