package com.eternalrealm.dungeon.hazard;

import com.eternalrealm.combat.Telegraph;
import com.eternalrealm.core.ModInfo;
import com.eternalrealm.dungeon.DungeonTheme;

import java.util.List;
import java.util.Locale;

/**
 * The traps and terrain gimmicks of the dungeon instances. Every kind first shows a ground {@link Telegraph} for
 * {@link #warnTicks()} and only then hits, and only who stands inside the marker at that moment. Damage is a fraction
 * of the victim's maximum health (traps have no level), scaled by the difficulty.
 *
 * @param shape          marker shape: CIRCLE (a = radius), SQUARE (a = half size), RECT (a = length, b = width; laid
 *                       along a corridor), CROSS (a = arm length, b = arm width), RING (a = inner, b = outer radius)
 * @param trigger        how it goes off
 * @param placement      where the planner may put it
 * @param warnTicks      time from the marker appearing to the hit
 * @param cooldownTicks  period of periodic and targeted kinds; rest time of proximity kinds after a hit
 * @param damageFraction share of the victim's maximum health one hit takes (NORMAL difficulty)
 */
public enum HazardKind {
    // ---- d1 失われた地下都市
    /** Rocks come down from the ceiling on whoever walks below. */
    ROCKFALL(Telegraph.Kind.CIRCLE, Telegraph.Style.DANGER, Trigger.TARGETED, Placement.ANY, 1.8, 0.0, 26, 70, 0.14,
            Effect.SLOW),
    /** A cracked floor tile caves in under the feet. */
    CRUMBLING_FLOOR(Telegraph.Kind.SQUARE, Telegraph.Style.DANGER, Trigger.PROXIMITY, Placement.ROOM, 1.5, 0.0, 18,
            100, 0.12, Effect.SLOW),
    /** Dart launchers in the walls sweep a stretch of the corridor. */
    DART_VOLLEY(Telegraph.Kind.RECT, Telegraph.Style.DANGER, Trigger.PERIODIC, Placement.CORRIDOR, 7.0, 3.0, 24, 80,
            0.12, Effect.NONE),
    // ---- d2 古代樹の迷宮
    /** A giant mushroom bursts into a cloud of poison spores. */
    SPORE_BLOOM(Telegraph.Kind.CIRCLE, Telegraph.Style.DANGER, Trigger.PERIODIC, Placement.ROOM, 2.6, 0.0, 30, 100,
            0.10, Effect.POISON),
    /** Thorned roots lash across the corridor. */
    THORN_LASH(Telegraph.Kind.RECT, Telegraph.Style.DANGER, Trigger.PERIODIC, Placement.CORRIDOR, 7.0, 3.0, 24, 80, 0.14,
            Effect.NONE),
    /** Roots grab the ankles of whoever steps on them. */
    ROOT_SNARE(Telegraph.Kind.SQUARE, Telegraph.Style.DANGER, Trigger.PROXIMITY, Placement.ROOM, 1.5, 0.0, 16, 90, 0.08,
            Effect.ROOT),
    // ---- d3 砂塵の神殿
    /** Sand erupts from a vent and throws everyone on it into the air. */
    SAND_GEYSER(Telegraph.Kind.CIRCLE, Telegraph.Style.DANGER, Trigger.PERIODIC, Placement.ROOM, 2.2, 0.0, 28, 90, 0.14,
            Effect.LAUNCH),
    /** A beam of sunlight from the temple mirrors burns along the corridor. */
    SUN_BEAM(Telegraph.Kind.RECT, Telegraph.Style.HOLY, Trigger.PERIODIC, Placement.CORRIDOR, 8.0, 3.0, 26, 90, 0.14,
            Effect.BURN),
    /** Quicksand swallows the legs of whoever stands on it. */
    QUICKSAND(Telegraph.Kind.SQUARE, Telegraph.Style.DANGER, Trigger.PROXIMITY, Placement.ROOM, 1.5, 0.0, 18, 100, 0.08,
            Effect.ROOT),
    // ---- d4 凍てつく地下牢
    /** Ice spikes burst out of the floor under a prisoner of the dungeon. */
    ICE_SPIKES(Telegraph.Kind.CIRCLE, Telegraph.Style.FROST, Trigger.TARGETED, Placement.ANY, 1.8, 0.0, 26, 70, 0.14,
            Effect.FREEZE),
    /** A freezing grate blasts cold air upwards. */
    FROST_GRATE(Telegraph.Kind.SQUARE, Telegraph.Style.FROST, Trigger.PROXIMITY, Placement.ROOM, 1.5, 0.0, 18, 100,
            0.10, Effect.FREEZE),
    // ---- d5 氷結の城
    /** Icicles break off the vaults above. */
    ICICLE_FALL(Telegraph.Kind.CIRCLE, Telegraph.Style.FROST, Trigger.TARGETED, Placement.ANY, 1.6, 0.0, 24, 60, 0.13,
            Effect.SLOW),
    /** A frost rune in the floor discharges along its two axes. */
    FROST_RUNE(Telegraph.Kind.CROSS, Telegraph.Style.FROST, Trigger.PERIODIC, Placement.ROOM, 6.0, 2.4, 30, 110, 0.14,
            Effect.FREEZE),
    // ---- d6 灼熱の火山洞窟
    /** Lava shoots up from a crack in the floor. */
    LAVA_GEYSER(Telegraph.Kind.CIRCLE, Telegraph.Style.FIRE, Trigger.PERIODIC, Placement.ROOM, 2.3, 0.0, 28, 90, 0.16,
            Effect.BURN),
    /** Fire jets from the walls fill a stretch of the corridor. */
    FLAME_JET(Telegraph.Kind.RECT, Telegraph.Style.FIRE, Trigger.PERIODIC, Placement.CORRIDOR, 8.0, 3.0, 24, 80, 0.15,
            Effect.BURN),
    /** Embers rain from the cave roof onto whoever stands below. */
    EMBER_RAIN(Telegraph.Kind.CIRCLE, Telegraph.Style.FIRE, Trigger.TARGETED, Placement.ROOM, 2.0, 0.0, 26, 80, 0.12,
            Effect.BURN),
    // ---- d7 天空の古代遺跡
    /** An ancient rune calls lightning down onto an intruder. */
    LIGHTNING_RUNE(Telegraph.Kind.CIRCLE, Telegraph.Style.MAGIC, Trigger.TARGETED, Placement.ANY, 2.0, 0.0, 24, 70, 0.15,
            Effect.SHOCK),
    /** A gale blows through the corridor and pushes everyone back. */
    GALE(Telegraph.Kind.RECT, Telegraph.Style.MAGIC, Trigger.PERIODIC, Placement.CORRIDOR, 9.0, 3.0, 26, 90, 0.08,
            Effect.PUSH),
    // ---- d8 終焉の塔
    /** The void erupts under the feet of a living soul. */
    VOID_ERUPTION(Telegraph.Kind.CIRCLE, Telegraph.Style.DARK, Trigger.TARGETED, Placement.ANY, 2.2, 0.0, 26, 70, 0.16,
            Effect.WITHER),
    /** A ring of shadow spreads from the centre of the room (safe in the middle). */
    SHADOW_RING(Telegraph.Kind.RING, Telegraph.Style.DARK, Trigger.PERIODIC, Placement.ROOM, 2.5, 6.0, 32, 120, 0.15,
            Effect.NONE),
    /** A lance of void pierces the corridor. */
    VOID_LANCE(Telegraph.Kind.RECT, Telegraph.Style.DARK, Trigger.PERIODIC, Placement.CORRIDOR, 8.0, 3.0, 24, 80, 0.15,
            Effect.NONE);

    /** How a trap goes off. */
    public enum Trigger {
        /** On a fixed spot, every {@link #cooldownTicks()} while someone is in its zone. */
        PERIODIC,
        /** On a fixed spot, when someone steps onto it (then rests for {@link #cooldownTicks()}). */
        PROXIMITY,
        /** On the spot where someone in its zone stands when it starts, every {@link #cooldownTicks()}. */
        TARGETED
    }

    /** Where the planner may put a kind. */
    public enum Placement {
        CORRIDOR, ROOM, ANY;

        public boolean corridors() {
            return this != ROOM;
        }

        public boolean rooms() {
            return this != CORRIDOR;
        }
    }

    /** What a hit does besides the damage. */
    public enum Effect {
        NONE, SLOW, ROOT, POISON, BURN, FREEZE, LAUNCH, SHOCK, PUSH, WITHER
    }

    /** Shortest warning any kind may have: the marker must be readable before it hits. */
    public static final int MIN_WARN_TICKS = 16;

    private final Telegraph.Kind shape;
    private final Telegraph.Style style;
    private final Trigger trigger;
    private final Placement placement;
    private final double a;
    private final double b;
    private final int warnTicks;
    private final int cooldownTicks;
    private final double damageFraction;
    private final Effect effect;

    HazardKind(Telegraph.Kind shape, Telegraph.Style style, Trigger trigger, Placement placement, double a, double b,
               int warnTicks, int cooldownTicks, double damageFraction, Effect effect) {
        this.shape = shape;
        this.style = style;
        this.trigger = trigger;
        this.placement = placement;
        this.a = a;
        this.b = b;
        this.warnTicks = warnTicks;
        this.cooldownTicks = cooldownTicks;
        this.damageFraction = damageFraction;
        this.effect = effect;
    }

    /** The trap kinds of a dungeon theme (none in the final raid arena: the final boss has its own attacks). */
    public static List<HazardKind> of(DungeonTheme theme) {
        return switch (theme) {
            case UNDERGROUND_CITY -> List.of(ROCKFALL, CRUMBLING_FLOOR, DART_VOLLEY);
            case FOREST -> List.of(SPORE_BLOOM, THORN_LASH, ROOT_SNARE);
            case DESERT_TEMPLE -> List.of(SAND_GEYSER, SUN_BEAM, QUICKSAND);
            case ICE_PRISON -> List.of(ICE_SPIKES, FROST_GRATE);
            case ICE_CASTLE -> List.of(ICICLE_FALL, FROST_RUNE);
            case VOLCANO -> List.of(LAVA_GEYSER, FLAME_JET, EMBER_RAIN);
            case SKY_RUINS -> List.of(LIGHTNING_RUNE, GALE);
            case DARK_TOWER -> List.of(VOID_ERUPTION, SHADOW_RING, VOID_LANCE);
            case END_THRONE -> List.of();
        };
    }

    /** Lower-case id, e.g. "rockfall". */
    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** Name shown on the map, {@code hazard.eternalrealm.<id>}. */
    public String translationKey() {
        return "hazard." + ModInfo.MOD_ID + "." + id();
    }

    public Telegraph.Kind shape() {
        return shape;
    }

    public Telegraph.Style style() {
        return style;
    }

    public Trigger trigger() {
        return trigger;
    }

    public Placement placement() {
        return placement;
    }

    /** First size parameter of the marker (see the class comment). */
    public double a() {
        return a;
    }

    /** Second size parameter of the marker (see the class comment). */
    public double b() {
        return b;
    }

    public int warnTicks() {
        return warnTicks;
    }

    public int cooldownTicks() {
        return cooldownTicks;
    }

    public double damageFraction() {
        return damageFraction;
    }

    public Effect effect() {
        return effect;
    }
}
