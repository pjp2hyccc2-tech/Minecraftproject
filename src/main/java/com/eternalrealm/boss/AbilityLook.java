package com.eternalrealm.boss;

import com.eternalrealm.combat.Telegraph;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * How an ability looks: the colour of its ground markers, the effect the platform plays where it hits and the block
 * used for props (spikes, pillars, meteors, orbs, bombs). Every shipped ability has an entry (BossLooksTest); unknown
 * abilities get a default from their kind.
 *
 * @param block block state of the props, e.g. {@code minecraft:packed_ice} or
 *              {@code minecraft:sweet_berry_bush[age=3]}; {@code null} when the effect has none
 */
public record AbilityLook(Telegraph.Style style, Effect effect, String block) {
    /** What the platform shows where (and while) an ability hits. */
    public enum Effect {
        /** Blade arcs and sweeps along the area. */
        SLASH,
        /** Ground burst: dust, debris and a thud. */
        SLAM,
        /** Explosions at the centre of every area. */
        EXPLOSION,
        /** A bomb prop sits on each circle while it charges, then explodes. */
        BOMB,
        /** Props (spikes, pillars, roots) burst out of the ground over the area and sink back. */
        SPIKES,
        /** A prop falls from the sky onto each circle, landing on the hit. */
        METEOR,
        /** A glowing orb prop hovers over each circle while it charges, then bursts. */
        ORB,
        /** A beam of light along every strip. */
        BEAM,
        /** Visual-only lightning on the areas. */
        LIGHTNING,
        /** Flames and lava bursts. */
        FIRE,
        /** Frost and snow bursts. */
        FROST,
        /** Dark columns and void particles. */
        VOID,
        /** Pillars of golden light. */
        HOLY,
        /** Sand and dust. */
        SAND,
        /** A ring that rushes outward from the boss (and a camera shake). */
        SHOCKWAVE,
        /** Souls rising where adds appear. */
        SUMMON,
        /** An aura around the boss (buffs, heals). */
        AURA
    }

    private static final Map<String, AbilityLook> LOOKS = new HashMap<>();

    public AbilityLook {
        Objects.requireNonNull(style, "style");
        Objects.requireNonNull(effect, "effect");
        boolean props = effect == Effect.BOMB || effect == Effect.SPIKES || effect == Effect.METEOR
                || effect == Effect.ORB;
        if (props != (block != null)) {
            throw new IllegalArgumentException(effect + (props ? " needs" : " takes no") + " prop block");
        }
        if (block != null && !block.matches("[a-z0-9_.-]+:[a-z0-9_/.-]+(\\[[a-z0-9_=,]+])?")) {
            throw new IllegalArgumentException("block state must look like namespace:path[property=value]: " + block);
        }
    }

    static {
        // b1 地下都市の盗賊王
        put("b1_cleave", Telegraph.Style.DANGER, Effect.SLASH);
        put("b1_dash", Telegraph.Style.DANGER, Effect.SLASH);
        put("b1_smoke_bombs", Telegraph.Style.DANGER, Effect.EXPLOSION);
        put("b1_whirlwind", Telegraph.Style.DANGER, Effect.SLASH);
        put("b1_call_gang", Telegraph.Style.DANGER, Effect.SUMMON);
        put("b1_knife_fan", Telegraph.Style.DANGER, Effect.SLASH);
        put("b1_dagger_cross", Telegraph.Style.DANGER, Effect.SLASH);
        props("b1_bomb_barrage", Telegraph.Style.FIRE, Effect.BOMB, "minecraft:tnt");
        put("b1_greedy_grab", Telegraph.Style.DANGER, Effect.SLAM);
        props("b1_caltrops", Telegraph.Style.DANGER, Effect.SPIKES, "minecraft:pointed_dripstone");
        put("b1_shadow_dash", Telegraph.Style.DANGER, Effect.SLASH);
        put("b1_tantrum", Telegraph.Style.DANGER, Effect.SHOCKWAVE);
        // b2 古代樹の守護者
        props("b2_root_slam", Telegraph.Style.DANGER, Effect.SPIKES, "minecraft:mangrove_roots");
        props("b2_thorn_line", Telegraph.Style.DANGER, Effect.SPIKES, "minecraft:sweet_berry_bush[age=3]");
        put("b2_branch_sweep", Telegraph.Style.DANGER, Effect.SLASH);
        put("b2_spore_burst", Telegraph.Style.DANGER, Effect.EXPLOSION);
        put("b2_sapling_call", Telegraph.Style.DANGER, Effect.SUMMON);
        put("b2_bark_shield", Telegraph.Style.DANGER, Effect.SUMMON);
        put("b2_regrowth", Telegraph.Style.HOLY, Effect.AURA);
        props("b2_vine_ring", Telegraph.Style.DANGER, Effect.SPIKES, "minecraft:mangrove_roots");
        props("b2_root_eruption", Telegraph.Style.DANGER, Effect.SPIKES, "minecraft:mangrove_roots");
        props("b2_seed_pods", Telegraph.Style.DANGER, Effect.BOMB, "minecraft:sniffer_egg");
        props("b2_bramble_trail", Telegraph.Style.DANGER, Effect.SPIKES, "minecraft:sweet_berry_bush[age=3]");
        put("b2_vine_lash", Telegraph.Style.DANGER, Effect.SLAM);
        put("b2_crystal_throb", Telegraph.Style.DARK, Effect.BEAM);
        put("b2_sunbeam_refuge", Telegraph.Style.DARK, Effect.VOID);
        put("b2_awakening", Telegraph.Style.DANGER, Effect.SHOCKWAVE);
        // b3 砂塵の巨神
        put("b3_stomp", Telegraph.Style.DANGER, Effect.SLAM);
        put("b3_charge", Telegraph.Style.DANGER, Effect.SAND);
        put("b3_sand_breath", Telegraph.Style.DANGER, Effect.SAND);
        put("b3_quicksand", Telegraph.Style.DANGER, Effect.SAND);
        put("b3_dust_ring", Telegraph.Style.DANGER, Effect.SAND);
        put("b3_sand_servants", Telegraph.Style.DANGER, Effect.SUMMON);
        put("b3_sandstorm", Telegraph.Style.DANGER, Effect.SAND);
        put("b3_boulder_volley", Telegraph.Style.DANGER, Effect.EXPLOSION);
        props("b3_sandstone_pillars", Telegraph.Style.DANGER, Effect.SPIKES, "minecraft:chiseled_sandstone");
        props("b3_falling_rocks", Telegraph.Style.DANGER, Effect.METEOR, "minecraft:sandstone");
        put("b3_quicksand_trail", Telegraph.Style.DANGER, Effect.SAND);
        props("b3_sand_tsunami", Telegraph.Style.DANGER, Effect.SPIKES, "minecraft:smooth_sandstone");
        put("b3_titan_rampage", Telegraph.Style.DANGER, Effect.SLAM);
        put("b3_eye_of_storm", Telegraph.Style.DANGER, Effect.SAND);
        put("b3_titan_roar", Telegraph.Style.DANGER, Effect.SHOCKWAVE);
        // b4 凍獄の処刑人
        put("b4_execution", Telegraph.Style.DANGER, Effect.SLASH);
        put("b4_chain_pull", Telegraph.Style.DANGER, Effect.SLASH);
        props("b4_ice_shackles", Telegraph.Style.FROST, Effect.SPIKES, "minecraft:packed_ice");
        props("b4_frost_nova", Telegraph.Style.FROST, Effect.SPIKES, "minecraft:packed_ice");
        put("b4_pursuit", Telegraph.Style.DANGER, Effect.AURA);
        put("b4_jailers", Telegraph.Style.DANGER, Effect.SUMMON);
        put("b4_judgement", Telegraph.Style.HOLY, Effect.EXPLOSION);
        put("b4_frozen_verdict", Telegraph.Style.FROST, Effect.FROST);
        props("b4_ice_ring", Telegraph.Style.FROST, Effect.SPIKES, "minecraft:packed_ice");
        put("b4_chain_cross", Telegraph.Style.DANGER, Effect.SLASH);
        props("b4_icicle_drop", Telegraph.Style.FROST, Effect.METEOR, "minecraft:packed_ice");
        put("b4_shackle_pull", Telegraph.Style.DANGER, Effect.SLAM);
        props("b4_frozen_cells", Telegraph.Style.FROST, Effect.SPIKES, "minecraft:blue_ice");
        put("b4_beheading_spin", Telegraph.Style.DANGER, Effect.SLASH);
        props("b4_permafrost", Telegraph.Style.FROST, Effect.SPIKES, "minecraft:packed_ice");
        put("b4_cold_fury", Telegraph.Style.FROST, Effect.SHOCKWAVE);
        // b5 氷獄の女王
        put("b5_ice_lance", Telegraph.Style.FROST, Effect.FROST);
        props("b5_frost_field", Telegraph.Style.FROST, Effect.SPIKES, "minecraft:packed_ice");
        put("b5_blizzard", Telegraph.Style.FROST, Effect.FROST);
        props("b5_glacial_ring", Telegraph.Style.FROST, Effect.SPIKES, "minecraft:blue_ice");
        put("b5_blink", Telegraph.Style.MAGIC, Effect.AURA);
        put("b5_ice_barrier", Telegraph.Style.FROST, Effect.SUMMON);
        put("b5_frozen_heart", Telegraph.Style.HOLY, Effect.FROST);
        put("b5_absolute_zero", Telegraph.Style.FROST, Effect.FROST);
        props("b5_hailstorm", Telegraph.Style.FROST, Effect.METEOR, "minecraft:packed_ice");
        props("b5_frost_runes", Telegraph.Style.MAGIC, Effect.SPIKES, "minecraft:blue_ice");
        props("b5_frozen_orbs", Telegraph.Style.MAGIC, Effect.ORB, "minecraft:blue_ice");
        put("b5_winter_waltz", Telegraph.Style.FROST, Effect.FROST);
        put("b5_snow_veil", Telegraph.Style.FROST, Effect.FROST);
        props("b5_ice_spike_march", Telegraph.Style.FROST, Effect.SPIKES, "minecraft:packed_ice");
        put("b5_glacier_beams", Telegraph.Style.MAGIC, Effect.BEAM);
        put("b5_thaw_refuge", Telegraph.Style.FROST, Effect.FROST);
        put("b5_winter_wrath", Telegraph.Style.FROST, Effect.SHOCKWAVE);
        // b6 灼炎の竜王
        put("b6_flame_breath", Telegraph.Style.FIRE, Effect.FIRE);
        put("b6_fireballs", Telegraph.Style.FIRE, Effect.FIRE);
        put("b6_magma_burst", Telegraph.Style.FIRE, Effect.FIRE);
        props("b6_meteor", Telegraph.Style.FIRE, Effect.METEOR, "minecraft:magma_block");
        put("b6_inferno_ring", Telegraph.Style.FIRE, Effect.FIRE);
        put("b6_salamanders", Telegraph.Style.FIRE, Effect.SUMMON);
        put("b6_dragon_rage", Telegraph.Style.FIRE, Effect.AURA);
        props("b6_sunfall", Telegraph.Style.HOLY, Effect.METEOR, "minecraft:shroomlight");
        put("b6_cataclysm", Telegraph.Style.FIRE, Effect.FIRE);
        put("b6_flame_dive", Telegraph.Style.FIRE, Effect.FIRE);
        put("b6_inferno_cross", Telegraph.Style.FIRE, Effect.FIRE);
        props("b6_meteor_storm", Telegraph.Style.FIRE, Effect.METEOR, "minecraft:magma_block");
        put("b6_fire_whirl", Telegraph.Style.FIRE, Effect.BEAM);
        props("b6_lava_tide", Telegraph.Style.FIRE, Effect.SPIKES, "minecraft:magma_block");
        put("b6_magma_trail", Telegraph.Style.FIRE, Effect.FIRE);
        put("b6_dragon_dive", Telegraph.Style.FIRE, Effect.FIRE);
        put("b6_wing_gale", Telegraph.Style.FIRE, Effect.FIRE);
        put("b6_obsidian_refuge", Telegraph.Style.FIRE, Effect.FIRE);
        put("b6_dragon_roar", Telegraph.Style.FIRE, Effect.SHOCKWAVE);
        // b7 天空の機械神
        put("b7_laser", Telegraph.Style.MAGIC, Effect.BEAM);
        put("b7_missiles", Telegraph.Style.MAGIC, Effect.EXPLOSION);
        put("b7_sweep", Telegraph.Style.DANGER, Effect.SLASH);
        put("b7_bombard", Telegraph.Style.MAGIC, Effect.EXPLOSION);
        put("b7_overload_ring", Telegraph.Style.MAGIC, Effect.LIGHTNING);
        put("b7_drone_barrier", Telegraph.Style.MAGIC, Effect.SUMMON);
        put("b7_warp", Telegraph.Style.MAGIC, Effect.AURA);
        put("b7_judgment_beam", Telegraph.Style.HOLY, Effect.HOLY);
        put("b7_system_purge", Telegraph.Style.MAGIC, Effect.LIGHTNING);
        put("b7_self_repair", Telegraph.Style.MAGIC, Effect.AURA);
        put("b7_orbital_strike", Telegraph.Style.MAGIC, Effect.LIGHTNING);
        put("b7_grid_purge", Telegraph.Style.MAGIC, Effect.LIGHTNING);
        props("b7_missile_rain", Telegraph.Style.MAGIC, Effect.METEOR, "minecraft:lightning_rod");
        put("b7_target_lock", Telegraph.Style.MAGIC, Effect.LIGHTNING);
        put("b7_rotating_lasers", Telegraph.Style.MAGIC, Effect.BEAM);
        props("b7_order_lines", Telegraph.Style.MAGIC, Effect.SPIKES, "minecraft:sea_lantern");
        put("b7_gravity_core", Telegraph.Style.MAGIC, Effect.EXPLOSION);
        put("b7_safe_sector", Telegraph.Style.MAGIC, Effect.LIGHTNING);
        put("b7_reboot", Telegraph.Style.MAGIC, Effect.SHOCKWAVE);
        // b8 終焉の門番
        put("b8_crushing_blow", Telegraph.Style.DANGER, Effect.SLAM);
        put("b8_shockwave", Telegraph.Style.DARK, Effect.SLAM);
        props("b8_dark_pillars", Telegraph.Style.DARK, Effect.SPIKES, "minecraft:obsidian");
        put("b8_void_step", Telegraph.Style.DARK, Effect.AURA);
        put("b8_abyss_ring", Telegraph.Style.DARK, Effect.VOID);
        put("b8_shades", Telegraph.Style.DARK, Effect.SUMMON);
        put("b8_gate_seal", Telegraph.Style.DARK, Effect.SUMMON);
        put("b8_soul_brand", Telegraph.Style.HOLY, Effect.VOID);
        put("b8_end_pulse", Telegraph.Style.DARK, Effect.VOID);
        put("b8_fury", Telegraph.Style.DARK, Effect.AURA);
        props("b8_collapse", Telegraph.Style.DARK, Effect.SPIKES, "minecraft:crying_obsidian");
        put("b8_gate_cross", Telegraph.Style.DARK, Effect.SLASH);
        props("b8_void_orbs", Telegraph.Style.DARK, Effect.ORB, "minecraft:crying_obsidian");
        put("b8_soul_hunt", Telegraph.Style.DARK, Effect.VOID);
        props("b8_abyss_tide", Telegraph.Style.DARK, Effect.SPIKES, "minecraft:obsidian");
        put("b8_vigil", Telegraph.Style.DARK, Effect.VOID);
        put("b8_judgment_blades", Telegraph.Style.DARK, Effect.BEAM);
        put("b8_last_light", Telegraph.Style.DARK, Effect.VOID);
        put("b8_gate_roar", Telegraph.Style.DARK, Effect.SHOCKWAVE);
        // final 終焉の王
        put("final_cleave", Telegraph.Style.DARK, Effect.SLASH);
        put("final_dark_lance", Telegraph.Style.DARK, Effect.BEAM);
        props("final_meteor_rain", Telegraph.Style.DARK, Effect.METEOR, "minecraft:crying_obsidian");
        put("final_doom_mark", Telegraph.Style.HOLY, Effect.VOID);
        put("final_ruin_ring", Telegraph.Style.DARK, Effect.VOID);
        put("final_dark_volley", Telegraph.Style.DARK, Effect.VOID);
        put("final_royal_guard", Telegraph.Style.DARK, Effect.SUMMON);
        put("final_legion", Telegraph.Style.DARK, Effect.SUMMON);
        put("final_shadow_step", Telegraph.Style.DARK, Effect.AURA);
        put("final_dark_aura", Telegraph.Style.DARK, Effect.AURA);
        put("final_end_wave", Telegraph.Style.DARK, Effect.VOID);
        put("final_eclipse", Telegraph.Style.DARK, Effect.VOID);
        put("final_oblivion", Telegraph.Style.MAGIC, Effect.VOID);
        put("final_end_of_all", Telegraph.Style.DARK, Effect.VOID);
        put("final_void_rift", Telegraph.Style.DARK, Effect.VOID);
        props("final_starfall", Telegraph.Style.MAGIC, Effect.METEOR, "minecraft:amethyst_block");
        put("final_cross_of_ruin", Telegraph.Style.DARK, Effect.SLASH);
        props("final_eclipse_board", Telegraph.Style.DARK, Effect.SPIKES, "minecraft:obsidian");
        put("final_doom_chase", Telegraph.Style.DARK, Effect.VOID);
        put("final_oblivion_beams", Telegraph.Style.MAGIC, Effect.BEAM);
        props("final_ruin_tide", Telegraph.Style.DARK, Effect.SPIKES, "minecraft:crying_obsidian");
        put("final_gravity", Telegraph.Style.DARK, Effect.EXPLOSION);
        put("final_last_light", Telegraph.Style.DARK, Effect.VOID);
        put("final_vessel_rupture", Telegraph.Style.DARK, Effect.SHOCKWAVE);
        // the field bosses: every ability in its boss's element colour, the effect from its shape
        for (BossDefinition boss : FieldBosses.all()) {
            Telegraph.Style style = FieldBosses.style(boss.id());
            String block = FieldBosses.block(boss.id());
            for (BossAbility ability : boss.abilities()) {
                Effect effect = fieldEffect(ability.kind(), style);
                boolean prop = effect == Effect.BOMB || effect == Effect.SPIKES || effect == Effect.METEOR
                        || effect == Effect.ORB;
                props(ability.id(), style, effect, prop ? block : null);
            }
        }
    }

    private static void put(String id, Telegraph.Style style, Effect effect) {
        if (LOOKS.put(id, new AbilityLook(style, effect, null)) != null) {
            throw new IllegalStateException("duplicate look " + id);
        }
    }

    private static void props(String id, Telegraph.Style style, Effect effect, String block) {
        if (LOOKS.put(id, new AbilityLook(style, effect, block)) != null) {
            throw new IllegalStateException("duplicate look " + id);
        }
    }

    /** The effect of a field boss's ability: from its shape, coloured by the boss's element. */
    private static Effect fieldEffect(AbilityKind kind, Telegraph.Style style) {
        Effect element = switch (style) {
            case FIRE -> Effect.FIRE;
            case FROST -> Effect.FROST;
            case DARK -> Effect.VOID;
            case MAGIC -> Effect.LIGHTNING;
            default -> Effect.EXPLOSION;
        };
        if (kind instanceof AbilityKind.Cone || kind instanceof AbilityKind.Line) {
            return Effect.SLASH;
        }
        if (kind instanceof AbilityKind.CircleAroundBoss || kind instanceof AbilityKind.Sequence) {
            return Effect.SLAM;
        }
        if (kind instanceof AbilityKind.Knockback || kind instanceof AbilityKind.Ring
                || kind instanceof AbilityKind.Charge) {
            return Effect.SHOCKWAVE;
        }
        if (kind instanceof AbilityKind.Scatter) {
            return Effect.METEOR;
        }
        if (kind instanceof AbilityKind.Checkerboard || kind instanceof AbilityKind.Exaflare) {
            return Effect.SPIKES;
        }
        if (kind instanceof AbilityKind.RotatingBeam || kind instanceof AbilityKind.Crosses) {
            return Effect.BEAM;
        }
        if (kind instanceof AbilityKind.ProjectileVolley) {
            return Effect.ORB;
        }
        return element;
    }

    /** The look defined for {@code abilityId}, if any. */
    public static Optional<AbilityLook> defined(String abilityId) {
        return Optional.ofNullable(LOOKS.get(abilityId));
    }

    /** The look of {@code ability}: its own entry, or a default from its kind. */
    public static AbilityLook of(BossAbility ability) {
        AbilityLook look = LOOKS.get(ability.id());
        if (look != null) {
            return look;
        }
        AbilityKind kind = ability.kind();
        if (kind instanceof AbilityKind.Soak) {
            return new AbilityLook(Telegraph.Style.HOLY, Effect.EXPLOSION, null);
        }
        if (kind instanceof AbilityKind.Knockback) {
            return new AbilityLook(Telegraph.Style.DANGER, Effect.SHOCKWAVE, null);
        }
        if (kind instanceof AbilityKind.ArenaWide || kind instanceof AbilityKind.SafeZones) {
            return new AbilityLook(Telegraph.Style.DARK, Effect.VOID, null);
        }
        if (kind instanceof AbilityKind.RotatingBeam || kind instanceof AbilityKind.ProjectileVolley) {
            return new AbilityLook(Telegraph.Style.MAGIC, Effect.BEAM, null);
        }
        if (kind instanceof AbilityKind.Summon || kind instanceof AbilityKind.Shield) {
            return new AbilityLook(Telegraph.Style.DANGER, Effect.SUMMON, null);
        }
        if (!kind.damaging()) {
            return new AbilityLook(Telegraph.Style.MAGIC, Effect.AURA, null);
        }
        if (kind instanceof AbilityKind.Cone || kind instanceof AbilityKind.Crosses) {
            return new AbilityLook(Telegraph.Style.DANGER, Effect.SLASH, null);
        }
        return new AbilityLook(Telegraph.Style.DANGER, Effect.SLAM, null);
    }

    /**
     * Style of the markers of one hit: pulls are violet (magic), stack circles gold, everything else the ability's
     * own colour.
     */
    public Telegraph.Style styleFor(AbilityKind hitKind) {
        if (hitKind instanceof AbilityKind.Knockback knockback && knockback.pulls()) {
            return Telegraph.Style.MAGIC;
        }
        if (hitKind instanceof AbilityKind.Soak) {
            return Telegraph.Style.HOLY;
        }
        return style;
    }
}
