package com.eternalrealm.monster;

import com.eternalrealm.combat.Telegraph;

import java.util.List;
import java.util.Set;

/**
 * Which monsters use telegraphed special moves, and which ones. The rule:
 * <ul>
 *     <li>{@link MonsterRole#ELITE} monsters (gold name plate, field elites and dungeon wave captains): both moves of
 *         their body, every {@link #ELITE_COOLDOWN_TICKS} or so;</li>
 *     <li>{@link MonsterRole#NORMAL} monsters of level {@value #VETERAN_LEVEL} or more (ドラゴニア火山 onwards and the late
 *         dungeons): the first move only, less often ({@link #VETERAN_COOLDOWN_TICKS});</li>
 *     <li>everything else (ordinary monsters below that level, boss summons): none - they fight as before.</li>
 * </ul>
 * Moves match the body: humanoid fighters cleave and slam, casters drop magic circles and novas, beasts charge,
 * pounce and breathe, golems slam and stomp, fliers strike from the air ({@link #forBody}). Monsters wearing a vanilla
 * body use the moves of the closest kind ({@link #forVanillaBody}); creepers keep their explosion as their big move.
 */
public final class SpecialMoves {
    /** Normal monsters from this level on get their first special move. */
    public static final int VETERAN_LEVEL = 30;
    /** The ヌシ (field lords) use all three moves of their bodies, more often than elites. */
    public static final int LORD_MOVES = 3;
    public static final int LORD_COOLDOWN_TICKS = 110;
    public static final int ELITE_COOLDOWN_TICKS = 160;
    public static final int VETERAN_COOLDOWN_TICKS = 300;
    /** Delay after a monster starts fighting before its first special move. */
    public static final int OPENING_DELAY_TICKS = 50;

    private static final Set<String> CASTERS = Set.of("cultist", "frost_mage", "fire_imp", "void_mage", "dryad");
    private static final Set<String> FIRE = Set.of("fire_imp", "salamander", "lava_golem", "fire_spirit");
    private static final Set<String> FROST = Set.of("ice_knight", "frost_mage", "frost_hound", "snow_wolf", "ice_golem",
            "ice_wraith");
    private static final Set<String> DARK = Set.of("dark_knight", "void_mage", "shade", "abyss_hound", "cultist");
    private static final Set<String> MAGIC = Set.of("storm_spirit", "ruin_construct");
    private static final Set<String> HOLY = Set.of("sky_sentinel");

    private SpecialMoves() {
    }

    /** How many of its body's moves a monster uses: 2 for elites, 1 for veterans, otherwise 0. */
    public static int moveCount(MonsterRole role, int level) {
        if (role == MonsterRole.ELITE) {
            return 2;
        }
        return role == MonsterRole.NORMAL && level >= VETERAN_LEVEL ? 1 : 0;
    }

    /** Ticks between two special moves of a monster using {@code count} moves. */
    public static int cooldownTicks(int count) {
        return count >= LORD_MOVES ? LORD_COOLDOWN_TICKS : count >= 2 ? ELITE_COOLDOWN_TICKS : VETERAN_COOLDOWN_TICKS;
    }

    /**
     * The two moves of an animated body, the signature move first; empty for an unknown rig.
     *
     * @param rig     "humanoid", "beast", "golem" or "flyer"
     * @param variant the body's variant id (e.g. "dark_knight")
     */
    public static List<SpecialMove> forBody(String rig, String variant) {
        List<SpecialMove> bestiary = BestiaryMoves.movesOf(variant);
        if (bestiary != null) {
            return bestiary; // three moves: the ヌシ use the third as well
        }
        switch (variant) {
            case "salamander", "frost_hound" -> {
                return List.of(SpecialMove.BREATH, SpecialMove.CHARGE);
            }
            case "abyss_hound" -> {
                return List.of(SpecialMove.CHARGE, SpecialMove.BREATH);
            }
            case "scorpion" -> {
                return List.of(SpecialMove.POUNCE, SpecialMove.SWEEP);
            }
            case "cave_rat" -> {
                return List.of(SpecialMove.POUNCE, SpecialMove.CHARGE);
            }
            case "jailer" -> {
                return List.of(SpecialMove.SWEEP, SpecialMove.SLAM);
            }
            case "dark_knight", "ice_knight" -> {
                return List.of(SpecialMove.WAVE, SpecialMove.CLEAVE);
            }
            case "crossbowman" -> {
                return List.of(SpecialMove.VOLLEY, SpecialMove.BEAM);
            }
            case "storm_spirit" -> {
                return List.of(SpecialMove.STORM_CROSS, SpecialMove.CIRCLE);
            }
            case "sky_sentinel" -> {
                return List.of(SpecialMove.BEAM, SpecialMove.CIRCLE);
            }
            case "harpy" -> {
                return List.of(SpecialMove.POUNCE, SpecialMove.SWEEP);
            }
            default -> {
                // by rig below
            }
        }
        if (CASTERS.contains(variant)) {
            return List.of(SpecialMove.CIRCLE, SpecialMove.NOVA);
        }
        return switch (rig) {
            case "humanoid" -> List.of(SpecialMove.CLEAVE, SpecialMove.SLAM);
            case "beast" -> List.of(SpecialMove.CHARGE, SpecialMove.POUNCE);
            case "golem" -> List.of(SpecialMove.SLAM, SpecialMove.STOMP);
            case "flyer" -> List.of(SpecialMove.CIRCLE, SpecialMove.NOVA);
            default -> List.of();
        };
    }

    /**
     * The two moves of a monster wearing a vanilla body ({@code MonsterTemplate#baseEntityType}), or none (creepers).
     */
    public static List<SpecialMove> forVanillaBody(String baseEntityType) {
        return switch (baseEntityType) {
            case "minecraft:creeper" -> List.of();
            case "minecraft:skeleton", "minecraft:stray", "minecraft:pillager" ->
                    List.of(SpecialMove.VOLLEY, SpecialMove.CLEAVE);
            case "minecraft:evoker", "minecraft:witch", "minecraft:blaze", "minecraft:enderman" ->
                    List.of(SpecialMove.CIRCLE, SpecialMove.NOVA);
            case "minecraft:slime", "minecraft:magma_cube" -> List.of(SpecialMove.STOMP, SpecialMove.SLAM);
            case "minecraft:spider", "minecraft:cave_spider", "minecraft:silverfish", "minecraft:phantom" ->
                    List.of(SpecialMove.POUNCE, SpecialMove.SWEEP);
            case "minecraft:ravager" -> List.of(SpecialMove.CHARGE, SpecialMove.STOMP);
            default -> List.of(SpecialMove.CLEAVE, SpecialMove.SLAM);
        };
    }

    /** Colour of a vanilla body's markers. */
    public static Telegraph.Style vanillaStyle(String baseEntityType) {
        return switch (baseEntityType) {
            case "minecraft:blaze", "minecraft:magma_cube" -> Telegraph.Style.FIRE;
            case "minecraft:stray" -> Telegraph.Style.FROST;
            case "minecraft:enderman", "minecraft:wither_skeleton" -> Telegraph.Style.DARK;
            case "minecraft:evoker", "minecraft:witch" -> Telegraph.Style.MAGIC;
            default -> Telegraph.Style.DANGER;
        };
    }

    /** The first {@code count} moves of a body (0..3; the first bodies have two). */
    public static List<SpecialMove> movesOf(String rig, String variant, int count) {
        List<SpecialMove> all = forBody(rig, variant);
        return all.subList(0, Math.max(0, Math.min(count, all.size())));
    }

    /** Colour of a body's markers by its element; physical moves are the red danger marker. */
    public static Telegraph.Style style(String variant) {
        Telegraph.Style bestiary = BestiaryMoves.styleOf(variant);
        if (bestiary != null) {
            return bestiary;
        }
        if (FIRE.contains(variant)) {
            return Telegraph.Style.FIRE;
        }
        if (FROST.contains(variant)) {
            return Telegraph.Style.FROST;
        }
        if (DARK.contains(variant)) {
            return Telegraph.Style.DARK;
        }
        if (MAGIC.contains(variant)) {
            return Telegraph.Style.MAGIC;
        }
        if (HOLY.contains(variant)) {
            return Telegraph.Style.HOLY;
        }
        return Telegraph.Style.DANGER;
    }
}
