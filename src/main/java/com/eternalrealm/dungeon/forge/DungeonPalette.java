package com.eternalrealm.dungeon.forge;

import com.eternalrealm.dungeon.BlockRole;
import com.eternalrealm.dungeon.DungeonTheme;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/** The blocks of each dungeon theme per {@link BlockRole}; floors and walls mix two blocks for texture. */
public final class DungeonPalette {
    private DungeonPalette() {
    }

    /** @param x/y/z world position, so the mix is stable when the same layout is rebuilt */
    public static BlockState block(DungeonTheme theme, BlockRole role, int x, int y, int z) {
        boolean alt = Math.floorMod(x * 31 + y * 17 + z * 13 + (x ^ z) * 7, 7) == 0;
        return switch (role) {
            case AIR -> Blocks.AIR.defaultBlockState();
            case DOOR -> door(theme);
            case FLOOR -> pick(alt, floor(theme), floorAlt(theme));
            case WALL -> pick(alt, wall(theme), wallAlt(theme));
            case PILLAR -> pillar(theme).defaultBlockState();
            case LIGHT -> light(theme).defaultBlockState();
            case ACCENT -> accent(theme).defaultBlockState();
        };
    }

    private static BlockState pick(boolean alt, Block normal, Block other) {
        return (alt ? other : normal).defaultBlockState();
    }

    /** Sealed doors: bars you can see the next room through. */
    public static BlockState door(DungeonTheme theme) {
        return switch (theme) {
            case FOREST -> Blocks.DARK_OAK_FENCE.defaultBlockState();
            case VOLCANO, DARK_TOWER, END_THRONE -> Blocks.CHAIN.defaultBlockState();
            default -> Blocks.IRON_BARS.defaultBlockState();
        };
    }

    private static Block floor(DungeonTheme theme) {
        return switch (theme) {
            case UNDERGROUND_CITY -> Blocks.STONE_BRICKS;
            case FOREST -> Blocks.MOSS_BLOCK;
            case DESERT_TEMPLE -> Blocks.SMOOTH_SANDSTONE;
            case ICE_PRISON -> Blocks.SNOW_BLOCK;
            case ICE_CASTLE -> Blocks.POLISHED_DIORITE;
            case VOLCANO -> Blocks.POLISHED_BLACKSTONE;
            case SKY_RUINS -> Blocks.SMOOTH_QUARTZ;
            case DARK_TOWER -> Blocks.DEEPSLATE_TILES;
            case END_THRONE -> Blocks.END_STONE_BRICKS;
        };
    }

    private static Block floorAlt(DungeonTheme theme) {
        return switch (theme) {
            case UNDERGROUND_CITY -> Blocks.CRACKED_STONE_BRICKS;
            case FOREST -> Blocks.ROOTED_DIRT;
            case DESERT_TEMPLE -> Blocks.CUT_SANDSTONE;
            case ICE_PRISON -> Blocks.PACKED_ICE;
            case ICE_CASTLE -> Blocks.CALCITE;
            case VOLCANO -> Blocks.BASALT;
            case SKY_RUINS -> Blocks.CALCITE;
            case DARK_TOWER -> Blocks.CRACKED_DEEPSLATE_TILES;
            case END_THRONE -> Blocks.PURPUR_BLOCK;
        };
    }

    private static Block wall(DungeonTheme theme) {
        return switch (theme) {
            case UNDERGROUND_CITY -> Blocks.DEEPSLATE_BRICKS;
            case FOREST -> Blocks.DARK_OAK_LOG;
            case DESERT_TEMPLE -> Blocks.CUT_SANDSTONE;
            case ICE_PRISON -> Blocks.PACKED_ICE;
            case ICE_CASTLE -> Blocks.QUARTZ_BRICKS;
            case VOLCANO -> Blocks.POLISHED_BLACKSTONE_BRICKS;
            case SKY_RUINS -> Blocks.QUARTZ_BRICKS;
            case DARK_TOWER -> Blocks.POLISHED_BLACKSTONE_BRICKS;
            case END_THRONE -> Blocks.PURPUR_BLOCK;
        };
    }

    private static Block wallAlt(DungeonTheme theme) {
        return switch (theme) {
            case UNDERGROUND_CITY -> Blocks.MOSSY_STONE_BRICKS;
            case FOREST -> Blocks.MOSSY_COBBLESTONE;
            case DESERT_TEMPLE -> Blocks.CHISELED_SANDSTONE;
            case ICE_PRISON -> Blocks.BLUE_ICE;
            case ICE_CASTLE -> Blocks.PACKED_ICE;
            case VOLCANO -> Blocks.CRACKED_POLISHED_BLACKSTONE_BRICKS;
            case SKY_RUINS -> Blocks.CHISELED_QUARTZ_BLOCK;
            case DARK_TOWER -> Blocks.CRYING_OBSIDIAN;
            case END_THRONE -> Blocks.END_STONE_BRICKS;
        };
    }

    private static Block pillar(DungeonTheme theme) {
        return switch (theme) {
            case UNDERGROUND_CITY -> Blocks.POLISHED_DEEPSLATE;
            case FOREST -> Blocks.OAK_LOG;
            case DESERT_TEMPLE -> Blocks.CHISELED_SANDSTONE;
            case ICE_PRISON -> Blocks.BLUE_ICE;
            case ICE_CASTLE -> Blocks.QUARTZ_PILLAR;
            case VOLCANO -> Blocks.BASALT;
            case SKY_RUINS -> Blocks.QUARTZ_PILLAR;
            case DARK_TOWER -> Blocks.OBSIDIAN;
            case END_THRONE -> Blocks.PURPUR_PILLAR;
        };
    }

    private static Block light(DungeonTheme theme) {
        return switch (theme) {
            case UNDERGROUND_CITY -> Blocks.GLOWSTONE;
            case FOREST -> Blocks.SHROOMLIGHT;
            case DESERT_TEMPLE -> Blocks.OCHRE_FROGLIGHT;
            case ICE_PRISON, ICE_CASTLE -> Blocks.SEA_LANTERN;
            case VOLCANO -> Blocks.SHROOMLIGHT;
            case SKY_RUINS -> Blocks.PEARLESCENT_FROGLIGHT;
            case DARK_TOWER -> Blocks.CRYING_OBSIDIAN;
            case END_THRONE -> Blocks.PEARLESCENT_FROGLIGHT;
        };
    }

    private static Block accent(DungeonTheme theme) {
        return switch (theme) {
            case UNDERGROUND_CITY -> Blocks.CHISELED_STONE_BRICKS;
            case FOREST -> Blocks.AZALEA_LEAVES;
            case DESERT_TEMPLE -> Blocks.BLUE_TERRACOTTA;
            case ICE_PRISON -> Blocks.IRON_BLOCK;
            case ICE_CASTLE -> Blocks.BLUE_ICE;
            case VOLCANO -> Blocks.GILDED_BLACKSTONE;
            case SKY_RUINS -> Blocks.GOLD_BLOCK;
            case DARK_TOWER -> Blocks.AMETHYST_BLOCK;
            case END_THRONE -> Blocks.OBSIDIAN;
        };
    }
}
