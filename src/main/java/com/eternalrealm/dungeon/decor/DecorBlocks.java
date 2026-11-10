package com.eternalrealm.dungeon.decor;

import java.util.Locale;
import java.util.Set;

/**
 * What the decoration planner needs to know about the vanilla blocks it places, without Minecraft: whether a block
 * stops a walker ({@link Shape}), whether it stops light and how much light it gives. Blocks are written as block state
 * strings in the command syntax ({@code minecraft:lantern[hanging=true]}).
 *
 * <p>The tables only cover the blocks the planner uses; the GameTest {@code DecorGameTests} parses every state of
 * every plan with the real registry and checks these answers against the game (collision shape, light emission), so
 * the pure tests (walkable paths, light levels) can trust them.</p>
 */
public final class DecorBlocks {
    /** How a block stops movement. */
    public enum Shape {
        /** No collision at all (air, torches, banners, vines, flowers). */
        NONE,
        /** A sliver on the ground you walk over (carpets, thin snow). */
        THIN,
        /** Collision smaller than a block (stairs, slabs, fences, lanterns, pots). */
        PARTIAL,
        /** A full cube. */
        FULL
    }

    public static final String AIR = "minecraft:air";

    private static final Set<String> NO_COLLISION = Set.of("air", "cave_air", "light", "torch", "wall_torch",
            "soul_torch", "soul_wall_torch", "vine", "glow_lichen", "hanging_roots", "cave_vines", "cave_vines_plant",
            "weeping_vines", "weeping_vines_plant", "twisting_vines", "twisting_vines_plant", "spore_blossom",
            "cobweb", "lever", "tripwire_hook", "redstone_wire", "poppy", "dandelion", "blue_orchid", "allium",
            "azure_bluet", "red_tulip", "orange_tulip", "white_tulip", "pink_tulip", "oxeye_daisy", "cornflower",
            "lily_of_the_valley", "torchflower", "pink_petals", "grass", "tall_grass", "fern", "large_fern",
            "dead_bush", "red_mushroom", "brown_mushroom", "crimson_fungus", "warped_fungus", "crimson_roots",
            "warped_roots", "nether_sprouts", "fire", "soul_fire", "water", "lava", "sugar_cane", "small_dripleaf",
            "light_weighted_pressure_plate", "heavy_weighted_pressure_plate", "glow_berries", "mangrove_propagule",
            "sculk_vein", "seagrass", "tall_seagrass");
    private static final Set<String> THIN = Set.of("moss_carpet", "lily_pad");
    private static final Set<String> PARTIAL = Set.of("iron_bars", "chain", "lantern", "soul_lantern", "candle",
            "flower_pot", "decorated_pot", "campfire", "soul_campfire", "anvil", "chipped_anvil", "damaged_anvil",
            "cauldron", "water_cauldron", "lava_cauldron", "powder_snow_cauldron", "lectern", "brewing_stand",
            "grindstone", "stonecutter", "bell", "end_rod", "lightning_rod", "hopper", "composter", "cake", "ladder",
            "pointed_dripstone", "amethyst_cluster", "large_amethyst_bud", "medium_amethyst_bud",
            "small_amethyst_bud", "sea_pickle", "chest", "trapped_chest", "daylight_detector", "azalea",
            "flowering_azalea", "big_dripleaf", "big_dripleaf_stem", "honey_block", "soul_sand", "mud", "scaffolding",
            "dragon_head", "dragon_wall_head", "conduit", "enchanting_table", "end_portal_frame", "snow",
            "mangrove_roots", "sniffer_egg", "turtle_egg", "frogspawn", "candle_cake", "piston_head", "dirt_path",
            "farmland", "chorus_plant", "chorus_flower", "bamboo");
    /** Full cubes that let light through. */
    private static final Set<String> CLEAR = Set.of("glass", "slime_block", "beacon", "ice", "frosted_ice",
            "spawner", "barrier");

    private DecorBlocks() {
    }

    /** The block id of a state ({@code minecraft:lantern} of {@code minecraft:lantern[hanging=true]}). */
    public static String id(String state) {
        int bracket = state.indexOf('[');
        return bracket < 0 ? state : state.substring(0, bracket);
    }

    /** The id without its namespace ({@code lantern}). */
    public static String name(String state) {
        String id = id(state);
        int colon = id.indexOf(':');
        return colon < 0 ? id : id.substring(colon + 1);
    }

    /** The value of property {@code key}, or null. */
    public static String property(String state, String key) {
        int bracket = state.indexOf('[');
        if (bracket < 0) {
            return null;
        }
        String props = state.substring(bracket + 1, state.length() - 1);
        for (String pair : props.split(",")) {
            int eq = pair.indexOf('=');
            if (eq > 0 && pair.substring(0, eq).trim().equals(key)) {
                return pair.substring(eq + 1).trim();
            }
        }
        return null;
    }

    /** The state with property {@code key} set to {@code value} (added if missing). */
    public static String with(String state, String key, String value) {
        int bracket = state.indexOf('[');
        if (bracket < 0) {
            return state + "[" + key + "=" + value + "]";
        }
        String props = state.substring(bracket + 1, state.length() - 1);
        StringBuilder out = new StringBuilder(state.substring(0, bracket)).append('[');
        boolean found = false;
        boolean first = true;
        for (String pair : props.split(",")) {
            if (pair.isBlank()) {
                continue;
            }
            int eq = pair.indexOf('=');
            String name = pair.substring(0, eq).trim();
            if (!first) {
                out.append(',');
            }
            first = false;
            if (name.equals(key)) {
                out.append(key).append('=').append(value);
                found = true;
            } else {
                out.append(pair.trim());
            }
        }
        if (!found) {
            out.append(first ? "" : ",").append(key).append('=').append(value);
        }
        return out.append(']').toString();
    }

    public static Shape shape(String state) {
        String name = name(state);
        if (NO_COLLISION.contains(name) || name.endsWith("_banner") || name.endsWith("_sign")
                || name.endsWith("_button") || name.endsWith("_pressure_plate") || name.endsWith("_sapling")
                || name.endsWith("_rail") || name.equals("rail")) {
            return Shape.NONE;
        }
        if (name.equals("snow")) {
            String layers = property(state, "layers");
            return layers == null || Integer.parseInt(layers) <= 2 ? Shape.THIN : Shape.PARTIAL;
        }
        if (THIN.contains(name) || name.endsWith("_carpet")) {
            return Shape.THIN;
        }
        if (name.endsWith("_slab")) {
            return "double".equals(property(state, "type")) ? Shape.FULL : Shape.PARTIAL;
        }
        if (PARTIAL.contains(name) || name.endsWith("_stairs") || name.endsWith("_wall") || name.endsWith("_fence")
                || name.endsWith("_fence_gate") || name.endsWith("_pane") || name.endsWith("_trapdoor")
                || name.endsWith("_door") || name.endsWith("_candle") || name.endsWith("_skull")
                || name.endsWith("_head") || name.startsWith("potted_") || name.endsWith("_bed")
                || name.endsWith("_candle_cake") || name.endsWith("_cluster")) {
            return Shape.PARTIAL;
        }
        return Shape.FULL;
    }

    /** Whether a walker is stopped by the block. */
    public static boolean blocks(String state) {
        Shape shape = shape(state);
        return shape == Shape.PARTIAL || shape == Shape.FULL;
    }

    /** Whether light stops at the block (full cubes that are not see-through). */
    public static boolean opaque(String state) {
        String name = name(state);
        if (name.equals("tinted_glass")) {
            return true;
        }
        return shape(state) == Shape.FULL && !CLEAR.contains(name) && !name.endsWith("_glass")
                && !name.endsWith("_leaves");
    }

    /** Whether the block is a fluid (cleared first, so nothing can flow when the walls around it go). */
    public static boolean fluid(String state) {
        String name = name(state);
        return name.equals("water") || name.equals("lava") || "true".equals(property(state, "waterlogged"));
    }

    /** Light level the block gives off (0-15). */
    public static int light(String state) {
        String name = name(state);
        switch (name) {
            case "glowstone", "sea_lantern", "shroomlight", "ochre_froglight", "verdant_froglight",
                 "pearlescent_froglight", "jack_o_lantern", "lantern", "lava", "fire", "beacon", "conduit",
                 "lava_cauldron" -> {
                return 15;
            }
            case "torch", "wall_torch", "end_rod" -> {
                return 14;
            }
            case "soul_lantern", "soul_torch", "soul_wall_torch", "soul_fire", "crying_obsidian" -> {
                return 10;
            }
            case "glow_lichen", "enchanting_table", "ender_chest" -> {
                return 7;
            }
            case "amethyst_cluster" -> {
                return 5;
            }
            case "large_amethyst_bud" -> {
                return 4;
            }
            case "magma_block" -> {
                return 3;
            }
            case "medium_amethyst_bud" -> {
                return 2;
            }
            case "small_amethyst_bud", "brewing_stand", "end_portal_frame", "brown_mushroom" -> {
                return 1;
            }
            case "campfire" -> {
                return "false".equals(property(state, "lit")) ? 0 : 15;
            }
            case "soul_campfire" -> {
                return "false".equals(property(state, "lit")) ? 0 : 10;
            }
            case "cave_vines", "cave_vines_plant" -> {
                return "true".equals(property(state, "berries")) ? 14 : 0;
            }
            case "light" -> {
                String level = property(state, "level");
                return level == null ? 15 : Integer.parseInt(level);
            }
            default -> {
                if (name.equals("candle") || name.endsWith("_candle")) {
                    if (!"true".equals(property(state, "lit"))) {
                        return 0;
                    }
                    String candles = property(state, "candles");
                    return 3 * (candles == null ? 1 : Integer.parseInt(candles));
                }
                return 0;
            }
        }
    }

    /** Lower-case check that a state string is well formed ({@code namespace:path[key=value,...]}). */
    public static boolean wellFormed(String state) {
        if (!state.equals(state.toLowerCase(Locale.ROOT)) || !state.contains(":")) {
            return false;
        }
        int bracket = state.indexOf('[');
        if (bracket < 0) {
            return state.matches("[a-z0-9_.-]+:[a-z0-9_/.-]+");
        }
        return state.endsWith("]") && id(state).matches("[a-z0-9_.-]+:[a-z0-9_/.-]+")
                && state.substring(bracket + 1, state.length() - 1).matches("([a-z0-9_]+=[a-z0-9_]+)(,[a-z0-9_]+=[a-z0-9_]+)*");
    }
}
