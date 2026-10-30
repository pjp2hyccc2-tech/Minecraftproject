package com.mcbesiege.mission;

import net.minecraft.core.Direction;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;
import java.util.function.Consumer;

/** The siege campaign: two "islands" of missions (Besiege-style objectives with Minecraft structures and mobs). */
public final class Missions {
    private Missions() {}

    public record Def(String id, int island, int stars, Consumer<Arena> build) {}

    public static final List<Def> ALL = List.of(
            new Def("first_siege", 1, 1, Missions::firstSiege),
            new Def("windmill", 1, 1, Missions::windmill),
            new Def("outpost", 1, 2, Missions::outpost),
            new Def("castle_gate", 1, 2, Missions::castleGate),
            new Def("treasure", 1, 2, Missions::treasure),
            new Def("canyon", 2, 3, Missions::canyon),
            new Def("three_towers", 2, 3, Missions::threeTowers),
            new Def("ravager", 2, 3, Missions::ravager),
            new Def("kings_castle", 2, 4, Missions::kingsCastle));

    public static Def byId(String id) {
        for (Def d : ALL) if (d.id().equals(id)) return d;
        return null;
    }

    public static int index(Def d) { return ALL.indexOf(d); }

    // ------------------------------------------------------------------------------------------------ missions

    static void base(Arena a, int clearZ) {
        a.clear(-40, -16, 40, clearZ, 34);
        a.buildZoneMarkers();
    }

    static void firstSiege(Arena a) {
        base(a, 60);
        a.scarecrow(0, 22);
        a.set(-6, 0, 30, Blocks.HAY_BLOCK.defaultBlockState());
        a.set(5, 0, 26, Blocks.HAY_BLOCK.defaultBlockState());
        a.house(-14, 34, false);
    }

    static void windmill(Arena a) {
        base(a, 70);
        a.windmill(0, 32);
        a.house(14, 30, false);
        a.house(-15, 36, true);
        a.fill(-6, 0, 44, 6, 0, 52, Blocks.FARMLAND.defaultBlockState());
        a.fill(-6, 1, 44, 6, 1, 52, Blocks.WHEAT.defaultBlockState());
    }

    static void outpost(Arena a) {
        base(a, 70);
        BlockState log = Blocks.DARK_OAK_LOG.defaultBlockState(), planks = Blocks.DARK_OAK_PLANKS.defaultBlockState();
        a.fill(-4, 0, 30, 4, 0, 38, Blocks.COBBLESTONE.defaultBlockState());
        for (int y = 1; y <= 10; y++) {
            a.hollow(-3, y, 31, 3, y, 37, y == 5 || y == 10 ? planks : Blocks.BIRCH_PLANKS.defaultBlockState());
            a.set(-3, y, 31, log);
            a.set(3, y, 31, log);
            a.set(-3, y, 37, log);
            a.set(3, y, 37, log);
        }
        a.fill(-4, 11, 30, 4, 11, 38, planks);
        a.hollow(-4, 12, 30, 4, 12, 38, Blocks.DARK_OAK_FENCE.defaultBlockState());
        a.banner(0, 9, 30, Direction.NORTH, true);
        a.tent(-14, 30, Blocks.WHITE_WOOL.defaultBlockState());
        a.tent(14, 34, Blocks.LIGHT_GRAY_WOOL.defaultBlockState());
        a.set(9, 0, 26, Blocks.HAY_BLOCK.defaultBlockState());
        a.set(-9, 0, 42, Blocks.HAY_BLOCK.defaultBlockState());
        for (int i = 0; i < 6; i++) a.mob(EntityType.PILLAGER, -10 + i * 4, 0, 26 + (i % 2) * 14);
        a.mob(EntityType.PILLAGER, -2, 12, 34);
        a.mob(EntityType.PILLAGER, 2, 12, 34);
        a.mob(EntityType.VINDICATOR, -6, 0, 28);
        a.mob(EntityType.VINDICATOR, 6, 0, 28);
        a.killAll = true;
    }

    static void castleGate(Arena a) {
        base(a, 70);
        a.wallX(-24, 24, 32, 8);
        a.tower(-5, 33, 2, 12, Arena.bricks());
        a.tower(5, 33, 2, 12, Arena.bricks());
        a.tower(-24, 33, 2, 11, Arena.stone());
        a.tower(24, 33, 2, 11, Arena.stone());
        a.fill(-2, 0, 32, 2, 7, 33, Blocks.AIR.defaultBlockState());
        a.gate(-2, 2, 32, 6);
        a.fill(-2, 6, 32, 2, 7, 33, Arena.bricks());
        a.banner(-5, 9, 30, Direction.NORTH, false);
        a.banner(5, 9, 30, Direction.NORTH, false);
        a.banner(-12, 6, 31, Direction.NORTH, true);
        a.banner(12, 6, 31, Direction.NORTH, true);
        for (int x = -4; x <= 4; x += 4) {
            a.set(x, 0, 40, Arena.stone());
            a.target(x, 1, 40);
        }
        a.house(-12, 44, true);
        a.house(12, 44, false);
    }

    static void treasure(Arena a) {
        base(a, 80);
        a.fill(-2, -1, 30, 2, -1, 34, Blocks.STONE_BRICKS.defaultBlockState());
        a.props.add(new Arena.Prop(0, 0, 32, 2.5f));
        a.goal(-5, 52, 5, 62);
        a.set(-7, 0, 40, Blocks.HAY_BLOCK.defaultBlockState());
        a.set(7, 0, 44, Blocks.HAY_BLOCK.defaultBlockState());
        a.deliver = true;
    }

    static void canyon(Arena a) {
        base(a, 70);
        a.fill(-40, -14, 22, 40, -1, 35, Blocks.AIR.defaultBlockState());
        a.fill(-40, -15, 22, 40, -15, 35, Blocks.STONE.defaultBlockState());
        a.fill(-40, -14, 22, 40, -13, 35, Blocks.WATER.defaultBlockState());
        a.goal(-6, 42, 6, 54);
        a.reach = true;
    }

    static void threeTowers(Arena a) {
        base(a, 70);
        int[][] t = {{-13, 34}, {0, 46}, {13, 34}};
        for (int[] p : t) {
            int top = a.tower(p[0], p[1], 1, 10, Arena.stone());
            a.target(p[0], top, p[1]);
            a.banner(p[0], top - 2, p[1] - 2, Direction.NORTH, p[0] == 0);
        }
        a.house(-22, 50, false);
        a.house(22, 50, true);
    }

    static void ravager(Arena a) {
        base(a, 70);
        a.palisade(0, 40, 13, 3);
        a.tent(-6, 44, Blocks.RED_WOOL.defaultBlockState());
        a.tent(6, 44, Blocks.RED_WOOL.defaultBlockState());
        a.mob(EntityType.RAVAGER, 0, 0, 40);
        a.mob(EntityType.PILLAGER, -5, 0, 36);
        a.mob(EntityType.PILLAGER, 5, 0, 36);
        a.mob(EntityType.VINDICATOR, 0, 0, 46);
        a.killAll = true;
    }

    static void kingsCastle(Arena a) {
        base(a, 90);
        int z0 = 40, z1 = 70, x0 = -16, x1 = 16;
        a.wallX(x0, x1, z0, 8);
        a.wallX(x0, x1, z1, 8);
        a.wallZ(x0, z0, z1, 8);
        a.wallZ(x1, z0, z1, 8);
        a.tower(x0, z0, 3, 13, Arena.stone());
        a.tower(x1, z0, 3, 13, Arena.stone());
        a.tower(x0, z1, 3, 13, Arena.stone());
        a.tower(x1, z1, 3, 13, Arena.stone());
        a.fill(-2, 0, z0, 2, 7, z0 + 1, Blocks.AIR.defaultBlockState());
        a.gate(-2, 2, z0, 6);
        a.fill(-2, 6, z0, 2, 7, z0 + 1, Arena.bricks());
        int top = a.tower(0, 57, 4, 16, Arena.bricks());
        a.target(-3, top + 1, 54);
        a.target(3, top + 1, 54);
        a.target(-3, top + 1, 60);
        a.target(3, top + 1, 60);
        a.banner(0, 12, 52, Direction.NORTH, true);
        a.banner(-8, 6, z0 - 1, Direction.NORTH, false);
        a.banner(8, 6, z0 - 1, Direction.NORTH, false);
        a.mob(EntityType.PILLAGER, -10, 9, z0);
        a.mob(EntityType.PILLAGER, 10, 9, z0);
        a.mob(EntityType.PILLAGER, -6, 9, z0);
        a.mob(EntityType.PILLAGER, 6, 9, z0);
        a.mob(EntityType.VINDICATOR, -6, 0, 47);
        a.mob(EntityType.VINDICATOR, 6, 0, 47);
        a.mob(EntityType.VINDICATOR, -8, 0, 64);
        a.mob(EntityType.VINDICATOR, 8, 0, 64);
        a.killAll = true;
    }
}
