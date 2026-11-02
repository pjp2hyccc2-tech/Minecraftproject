package com.sounan.world;

import com.sounan.block.FacingBlock;
import com.sounan.block.LockedGateBlock;
import com.sounan.registry.ModBlocks;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.BellBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BellAttachType;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;

/** All man-made things of the mountain, built piecewise per chunk from {@link PoiLayout}. */
public final class PoiBuilder {
    private PoiBuilder() {}

    public static void build(ChunkSink s, TerrainData t, Pois p) {
        PoiLayout L = PoiLayout.get();
        bagSite(s, t, L);
        rope(s, L);
        deadEnd(s, t, p);
        memos(s, p);
        jizo(s, p);
        signs(s, p);
        village(s, t, L);
        shrine(s, t, L);
        fogRope(s, t, p);
        bridge(s, t, L);
        hut(s, t, L);
        pylon(s, L);
        tunnel(s, t, L);
        trailhead(s, t, L);
    }

    // ------------------------------------------------------------------------------------------------ helpers
    static BlockState face(Block b, Direction d) {
        return b.defaultBlockState().setValue(FacingBlock.FACING, d);
    }

    static BlockState stairs(Block b, Direction d) {
        return b.defaultBlockState().setValue(StairBlock.FACING, d).setValue(StairBlock.HALF, Half.BOTTOM);
    }

    static BlockState slab(Block b, SlabType type) {
        return b.defaultBlockState().setValue(SlabBlock.TYPE, type);
    }

    static BlockState log(Block b, Direction.Axis axis) {
        return b.defaultBlockState().setValue(RotatedPillarBlock.AXIS, axis);
    }

    static void door(ChunkSink s, Block b, int x, int y, int z, Direction facing, boolean locked) {
        BlockState lo = b.defaultBlockState().setValue(DoorBlock.FACING, facing).setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER)
                .setValue(DoorBlock.HINGE, DoorHingeSide.LEFT).setValue(DoorBlock.OPEN, false);
        if (b instanceof LockedGateBlock) lo = lo.setValue(LockedGateBlock.LOCKED, locked);
        s.set(x, y, z, lo);
        s.set(x, y + 1, z, lo.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
    }

    /** Fills from the terrain surface up to (excluding) y with the given block (foundations, abutments). */
    static void foundation(ChunkSink s, TerrainData t, int x, int z, int y, BlockState state) {
        for (int yy = t.height(x, z) + 1; yy < y; yy++) s.set(x, yy, z, state);
    }

    static boolean chance(int x, int y, int z, int salt, double p) {
        return Decorator.rand(x * 31 + y, z * 17 - y, salt) < p;
    }

    static Direction toward(int fx, int fz, int tx, int tz) {
        int dx = tx - fx, dz = tz - fz;
        if (Math.abs(dx) > Math.abs(dz)) return dx > 0 ? Direction.EAST : Direction.WEST;
        return dz > 0 ? Direction.SOUTH : Direction.NORTH;
    }

    /** A cedar forced at a position (used for decorated trees); optional decal on its trunk facing a direction. */
    static void forcedTree(ChunkSink s, TerrainData t, int x, int z, BlockState trunkBand, BlockState decal, Direction decalSide) {
        Decorator.Tree tree = Decorator.makeTree(t, x, z, false);
        if (!s.touches(x - 4, z - 4, x + 4, z + 4)) return;
        Decorator.trunk(s, tree);
        if (trunkBand != null) s.set(x, tree.base() + 2, z, trunkBand);
        if (decal != null) s.set(x + decalSide.getStepX(), tree.base() + 2, z + decalSide.getStepZ(), decal);
        Decorator.crown(s, tree);
    }

    // ------------------------------------------------------------------------------------------------ sawa / gake
    static void bagSite(ChunkSink s, TerrainData t, PoiLayout L) {
        int bx = L.bag.getX(), by = L.bag.getY(), bz = L.bag.getZ();
        if (!s.touches(bx - 7, bz - 4, bx + 4, bz + 3)) return;
        // a fallen cedar across the stream, the bag caught against it
        BlockState log = log(ModBlocks.CEDAR_LOG.get(), Direction.Axis.X);
        for (int x = bx - 6; x <= bx + 2; x++) {
            int z = bz - 1;
            if (t.height(x, z) < by) s.soft(x, by, z, log);
        }
        s.set(bx, by, bz, face(ModBlocks.LOST_BAG.get(), Direction.EAST));
        s.soft(bx + 1, by, bz + 1, face(ModBlocks.FALLEN_LEAVES.get(), Direction.NORTH));
    }

    static void rope(ChunkSink s, PoiLayout L) {
        int x = L.ropeAnchor.getX(), z = L.ropeAnchor.getZ();
        if (!s.contains(x, z)) return;
        s.set(x, L.ropeAnchor.getY(), z, face(ModBlocks.ROPE_ANCHOR.get(), L.ropeFacing));
        s.clear(x, L.ropeAnchor.getY() + 1, z, x, L.ropeAnchor.getY() + 3, z);
    }

    static void deadEnd(ChunkSink s, TerrainData t, Pois p) {
        Pois.Point d = p.point("dead_end");
        int x = d.x(), z = d.z();
        if (!s.touches(x - 8, z - 8, x + 8, z + 8)) return;
        // bare footprints going in, none coming back
        for (int k = 1; k <= 7; k++) {
            int fx = x + (k % 2 == 0 ? 1 : 0) + (k / 3), fz = z - 2 - k;
            s.soft(fx, t.height(fx, fz) + 1, fz, face(ModBlocks.FOOTPRINTS.get(), Direction.SOUTH));
        }
        // an old rotten bag, opened, and a straw doll on the tree above it
        s.set(x - 1, t.height(x - 1, z + 1) + 1, z + 1, face(ModBlocks.LOST_BAG.get(), Direction.WEST)
                .setValue(com.sounan.block.LostBagBlock.OPEN, true));
        s.soft(x + 1, t.height(x + 1, z + 1) + 1, z + 1, face(ModBlocks.HAIR_STRANDS.get(), Direction.EAST));
        forcedTree(s, t, x + 2, z + 3, null, face(ModBlocks.WARA_NINGYO.get(), Direction.NORTH), Direction.NORTH);
        forcedTree(s, t, x - 3, z + 2, ModBlocks.CEDAR_LOG_RIBBON.get().defaultBlockState(), null, Direction.NORTH);
    }

    static void memos(ChunkSink s, Pois p) {
        for (Pois.Point m : p.memos) {
            if (!s.contains(m.x(), m.z())) continue;
            Direction d = Direction.from2DDataValue(Math.floorMod(Decorator.hash(m.x(), m.z(), 61), 4));
            s.set(m.x(), m.y(), m.z(), face(ModBlocks.MEMO.get(), d));
        }
    }

    static void jizo(ChunkSink s, Pois p) {
        for (Pois.Point j : p.jizo) {
            if (!s.contains(j.x(), j.z())) continue;
            s.set(j.x(), j.y(), j.z(), face(ModBlocks.JIZO.get(), j.facing()));
            s.clear(j.x(), j.y() + 1, j.z(), j.x(), j.y() + 2, j.z());
        }
    }

    static void signs(ChunkSink s, Pois p) {
        for (Pois.Point g : p.signs) {
            if (!s.contains(g.x(), g.z())) continue;
            s.set(g.x(), g.y(), g.z(), face(ModBlocks.SIGNPOST.get(), g.facing()));
        }
    }

    // ------------------------------------------------------------------------------------------------ 廃村
    static void village(ChunkSink s, TerrainData t, PoiLayout L) {
        if (!s.touches(-6, -12, 66, 24)) return;
        for (PoiLayout.House h : L.houses) house(s, t, h, L);
        // bus shelter (checkpoint) + stop sign
        int gy = L.busStop.getY();
        if (s.touches(1, 7, 7, 12)) {
            BlockState plank = ModBlocks.WEATHERED_PLANKS.get().defaultBlockState();
            for (int x = 2; x <= 5; x++) {
                foundation(s, t, x, 11, gy, Blocks.COBBLESTONE.defaultBlockState());
                s.box(x, gy, 11, x, gy + 2, 11, plank);
                s.set(x, gy + 3, 8, slab(Blocks.SPRUCE_SLAB, SlabType.BOTTOM));
                s.set(x, gy + 3, 9, slab(Blocks.SPRUCE_SLAB, SlabType.BOTTOM));
                s.set(x, gy + 3, 10, slab(Blocks.SPRUCE_SLAB, SlabType.BOTTOM));
                s.set(x, gy + 3, 11, slab(Blocks.SPRUCE_SLAB, SlabType.BOTTOM));
                if (x == 3 || x == 4) s.post(x, gy, 10, stairs(Blocks.SPRUCE_STAIRS, Direction.SOUTH));
            }
            s.post(2, gy, 8, Blocks.SPRUCE_FENCE.defaultBlockState());
            s.post(2, gy + 1, 8, Blocks.SPRUCE_FENCE.defaultBlockState());
            s.post(2, gy + 2, 8, Blocks.SPRUCE_FENCE.defaultBlockState());
            s.post(5, gy, 8, Blocks.SPRUCE_FENCE.defaultBlockState());
            s.post(5, gy + 1, 8, Blocks.SPRUCE_FENCE.defaultBlockState());
            s.post(5, gy + 2, 8, Blocks.SPRUCE_FENCE.defaultBlockState());
            s.set(6, t.height(6, 8) + 1, 8, face(ModBlocks.BUS_STOP.get(), Direction.NORTH));
        }
        // phone booth with the old green public phone
        int px = L.publicPhone.getX(), py = L.publicPhone.getY(), pz = L.publicPhone.getZ();
        if (s.touches(px - 1, pz, px + 1, pz + 1)) {
            BlockState pane = Blocks.GLASS_PANE.defaultBlockState();
            for (int y = py; y <= py + 1; y++) {
                s.post(px - 1, y, pz, pane);
                s.post(px + 1, y, pz, pane);
                s.post(px - 1, y, pz + 1, pane);
                s.post(px, y, pz + 1, pane);
                s.post(px + 1, y, pz + 1, pane);
            }
            s.box(px - 1, py + 2, pz, px + 1, py + 2, pz + 1, slab(Blocks.SMOOTH_STONE_SLAB, SlabType.BOTTOM));
            s.set(px, py, pz, face(ModBlocks.PUBLIC_PHONE.get(), Direction.NORTH));
        }
        if (s.contains(L.noticeBoard.getX(), L.noticeBoard.getZ())) {
            s.set(L.noticeBoard.getX(), L.noticeBoard.getY(), L.noticeBoard.getZ(), face(ModBlocks.NOTICE_BOARD.get(), Direction.NORTH));
        }
        // fire watchtower with its bell
        int bx = L.watchBell.getX(), by = L.watchBell.getY(), bz = L.watchBell.getZ();
        if (s.touches(bx - 1, bz - 1, bx + 1, bz + 1)) {
            int wy = by - 11;
            BlockState bars = Blocks.IRON_BARS.defaultBlockState();
            for (int[] c : new int[][]{{-1, -1}, {1, -1}, {-1, 1}, {1, 1}}) {
                foundation(s, t, bx + c[0], bz + c[1], wy, Blocks.COBBLESTONE.defaultBlockState());
                for (int y = wy; y <= by; y++) s.post(bx + c[0], y, bz + c[1], bars);
            }
            for (int y = wy + 3; y < by - 1; y += 3) {
                s.post(bx, y, bz - 1, bars);
                s.post(bx, y, bz + 1, bars);
                s.post(bx - 1, y, bz, bars);
                s.post(bx + 1, y, bz, bars);
            }
            s.box(bx - 1, by - 1, bz - 1, bx + 1, by - 1, bz + 1, slab(Blocks.SPRUCE_SLAB, SlabType.TOP));
            s.set(bx, by - 1, bz, Blocks.AIR.defaultBlockState());
            s.box(bx - 1, by + 1, bz - 1, bx + 1, by + 1, bz + 1, slab(ModBlocks.TIN_ROOF_SLAB.get(), SlabType.BOTTOM));
            s.set(bx, by, bz, Blocks.BELL.defaultBlockState().setValue(BellBlock.ATTACHMENT, BellAttachType.CEILING)
                    .setValue(BellBlock.FACING, Direction.NORTH));
        }
    }

    static void house(ChunkSink s, TerrainData t, PoiLayout.House h, PoiLayout L) {
        int x1 = h.x1(), z1 = h.z1(), x2 = h.x2(), z2 = h.z2(), fy = h.floorY();
        if (!s.touches(x1 - 2, z1 - 2, x2 + 2, z2 + 2)) return;
        boolean collapsed = h.kind() == PoiLayout.HouseKind.COLLAPSED;
        boolean broken = collapsed || h.kind() == PoiLayout.HouseKind.BROKEN;
        BlockState cobble = Blocks.COBBLESTONE.defaultBlockState(), mossy = Blocks.MOSSY_COBBLESTONE.defaultBlockState();
        for (int x = x1; x <= x2; x++) {
            for (int z = z1; z <= z2; z++) {
                for (int y = t.height(x, z) + 1; y < fy; y++) s.set(x, y, z, chance(x, y, z, 71, 0.4) ? mossy : cobble);
            }
        }
        s.clear(x1 - 1, fy + 1, z1 - 1, x2 + 1, fy + 9, z2 + 1);
        BlockState tatami = ModBlocks.TATAMI_OLD.get().defaultBlockState();
        BlockState boards = ModBlocks.DARK_PLANKS.get().defaultBlockState();
        BlockState rotten = ModBlocks.ROTTEN_PLANKS.get().defaultBlockState();
        for (int x = x1; x <= x2; x++) {
            for (int z = z1; z <= z2; z++) {
                boolean edge = x == x1 || x == x2 || z == z1 || z == z2;
                BlockState floor = edge ? boards : (chance(x, fy, z, 72, broken ? 0.3 : 0.08) ? rotten : tatami);
                s.set(x, fy, z, floor);
            }
        }
        BlockState beam = log(ModBlocks.DARK_BEAM.get(), Direction.Axis.Y);
        BlockState plaster = ModBlocks.OLD_PLASTER.get().defaultBlockState();
        BlockState lower = ModBlocks.ROTTEN_PLANKS.get().defaultBlockState();
        BlockState shoji = ModBlocks.SHOJI_TORN.get().defaultBlockState();
        for (int y = fy + 1; y <= fy + 3; y++) {
            for (int x = x1; x <= x2; x++) {
                for (int z = z1; z <= z2; z++) {
                    boolean edgeX = x == x1 || x == x2, edgeZ = z == z1 || z == z2;
                    if (!edgeX && !edgeZ) continue;
                    BlockState w;
                    if (edgeX && edgeZ) w = beam;
                    else if (y == fy + 1) w = lower;
                    else if (y == fy + 2 && ((edgeZ && (x - x1) % 3 == 2) || (edgeX && (z - z1) % 3 == 2))) w = shoji;
                    else w = plaster;
                    if (broken && !(edgeX && edgeZ) && chance(x, y, z, 73, collapsed ? 0.35 : 0.18)) continue;
                    if (collapsed && y == fy + 3 && x > (x1 + x2) / 2) continue;
                    s.set(x, y, z, w);
                }
            }
        }
        // door
        int dx = h.door().getAxis() == Direction.Axis.Z ? (x1 + x2) / 2 : (h.door() == Direction.EAST ? x2 : x1);
        int dz = h.door().getAxis() == Direction.Axis.Z ? (h.door() == Direction.SOUTH ? z2 : z1) : (z1 + z2) / 2;
        door(s, ModBlocks.HOUSE_DOOR.get(), dx, fy + 1, dz, h.door(), false);
        int ox = dx + h.door().getStepX(), oz = dz + h.door().getStepZ();
        if (t.height(ox, oz) < fy) s.set(ox, fy, oz, slab(Blocks.COBBLESTONE_SLAB, SlabType.TOP));
        s.clear(ox, fy + 1, oz, ox, fy + 2, oz);
        // gable roof, ridge along x
        BlockState plankGable = ModBlocks.WEATHERED_PLANKS.get().defaultBlockState();
        int zn = z1 - 1, zs = z2 + 1, layer = 0;
        int ridgeHalfX = collapsed ? (x1 + x2) / 2 : x2 + 1;
        while (zn <= zs) {
            int y = fy + 4 + layer;
            for (int x = x1 - 1; x <= ridgeHalfX; x++) {
                if (chance(x, y, zn, 74, broken ? 0.12 : 0.03)) continue;
                if (zn == zs) s.set(x, y, zn, slab(ModBlocks.TIN_ROOF_SLAB.get(), SlabType.BOTTOM));
                else {
                    s.post(x, y, zn, stairs(ModBlocks.TIN_ROOF_STAIRS.get(), Direction.SOUTH));
                    s.post(x, y, zs, stairs(ModBlocks.TIN_ROOF_STAIRS.get(), Direction.NORTH));
                }
            }
            for (int z = zn + 1; z <= zs - 1; z++) {
                s.set(x1, y, z, plankGable);
                if (!collapsed) s.set(x2, y, z, plankGable);
            }
            zn++;
            zs--;
            layer++;
        }
        // interiors
        int cx = (x1 + x2) / 2, cz = (z1 + z2) / 2;
        switch (h.kind()) {
            case PLAIN -> {
                s.set(cx, fy + 1, cz, face(ModBlocks.BLOOD_STAIN.get(), Direction.NORTH));
                s.set(x1 + 1, fy + 1, z1 + 1, face(ModBlocks.FUTON.get(), Direction.SOUTH));
            }
            case RADIO -> {
                s.set(x1 + 2, fy + 1, z1 + 2, face(ModBlocks.MEMO.get(), Direction.SOUTH));
                s.set(x2 - 1, fy + 1, z1 + 1, face(ModBlocks.FUTON.get(), Direction.SOUTH));
                s.set(x2 - 2, fy + 1, z1 + 1, face(ModBlocks.FUTON.get(), Direction.SOUTH));
            }
            case BROKEN -> {
                s.set(cx, fy + 1, cz, face(ModBlocks.HAIR_STRANDS.get(), Direction.EAST));
                for (int k = 0; k < 4; k++) s.set(x1 + 1 + k, fy + 1, cz + (k % 2), face(ModBlocks.FOOTPRINTS.get(), Direction.EAST));
            }
            case OMIKI -> {
                BlockPos3 sl = new BlockPos3(L.villageSleeper.getX(), L.villageSleeper.getY(), L.villageSleeper.getZ());
                s.set(sl.x, sl.y, sl.z, face(ModBlocks.FUTON.get(), L.villageSleeperFacing));
                s.set(sl.x + 1, sl.y, sl.z, face(ModBlocks.FUTON.get(), L.villageSleeperFacing));
                s.set(L.omiki.getX(), L.omiki.getY() - 1, L.omiki.getZ(), boards);
                s.set(L.omiki.getX(), L.omiki.getY(), L.omiki.getZ(), ModBlocks.OMIKI_BOTTLE.get().defaultBlockState());
                s.set(L.omiki.getX() + 1, fy + 1, L.omiki.getZ(), boards);
                s.set(cx, fy + 1, z1 + 1, face(ModBlocks.HAIR_STRANDS.get(), Direction.SOUTH));
            }
            case COLLAPSED -> {
                s.set(x1 + 2, fy + 1, z2 - 2, face(ModBlocks.MEMO.get(), Direction.NORTH));
                for (int k = 0; k < 6; k++) {
                    int rx = cx + Math.floorMod(Decorator.hash(k, h.x1(), 75), 3), rz = z1 + 1 + Math.floorMod(Decorator.hash(k, h.z1(), 76), 3);
                    s.set(rx, fy + 1, rz, k % 2 == 0 ? plankGable : Blocks.COBBLESTONE.defaultBlockState());
                }
            }
        }
    }

    private record BlockPos3(int x, int y, int z) {}

    // ------------------------------------------------------------------------------------------------ 祠
    static void shrine(ChunkSink s, TerrainData t, PoiLayout L) {
        int hx = L.hokora.getX(), hy = L.hokora.getY(), hz = L.hokora.getZ();
        if (!s.touches(hx - 18, hz - 9, hx + 9, hz + 9)) return;
        // stone steps up the knoll along +x
        BlockState bricks = Blocks.MOSSY_STONE_BRICKS.defaultBlockState();
        for (int x = hx - 15; x <= hx - 4; x++) {
            for (int z = hz - 1; z <= hz + 1; z++) {
                int h0 = t.height(x, z), h1 = t.height(x + 1, z);
                s.set(x, h0, z, chance(x, 0, z, 81, 0.5) ? bricks : Blocks.STONE_BRICKS.defaultBlockState());
                if (h1 > h0) s.post(x, h0 + 1, z, stairs(Blocks.MOSSY_STONE_BRICK_STAIRS, Direction.EAST));
                else s.clear(x, h0 + 1, z, x, h0 + 2, z);
            }
        }
        // weathered vermilion torii at the bottom of the steps
        int tx = hx - 15, ty = t.height(tx, hz) + 1;
        if (s.touches(tx, hz - 3, tx, hz + 3)) {
            BlockState post = log(Blocks.STRIPPED_MANGROVE_LOG, Direction.Axis.Y);
            for (int y = ty; y <= ty + 3; y++) {
                s.set(tx, y, hz - 2, post);
                s.set(tx, y, hz + 2, post);
            }
            for (int z = hz - 3; z <= hz + 3; z++) s.set(tx, ty + 4, z, log(Blocks.STRIPPED_DARK_OAK_LOG, Direction.Axis.Z));
            for (int z = hz - 1; z <= hz + 1; z++) s.set(tx, ty + 3, z, log(Blocks.STRIPPED_MANGROVE_LOG, Direction.Axis.Z));
        }
        // the shrine, lanterns, sacred cedar, straw dolls
        if (s.contains(hx, hz)) {
            s.clear(hx, hy, hz, hx, hy + 2, hz);
            s.set(hx, hy, hz, face(ModBlocks.HOKORA.get(), L.hokoraFacing));
        }
        BlockState lantern = face(ModBlocks.STONE_LANTERN.get(), L.hokoraFacing);
        s.set(hx - 2, t.height(hx - 2, hz - 3) + 1, hz - 3, lantern);
        s.set(hx - 2, t.height(hx - 2, hz + 3) + 1, hz + 3, lantern);
        int cx = hx + 3, cz = hz + 2, base = t.height(cx, cz);
        if (s.touches(cx - 5, cz - 5, cx + 6, cz + 6)) {
            BlockState trunk = log(ModBlocks.CEDAR_LOG.get(), Direction.Axis.Y);
            BlockState band = ModBlocks.CEDAR_LOG_SHIMENAWA.get().defaultBlockState();
            for (int dx = 0; dx <= 1; dx++) {
                for (int dz = 0; dz <= 1; dz++) {
                    for (int y = Math.min(base, t.height(cx + dx, cz + dz)) + 1; y <= base + 26; y++) {
                        s.set(cx + dx, y, cz + dz, y == base + 2 ? band : trunk);
                    }
                }
            }
            BlockState leaves = Decorator.leaves();
            for (int y = base + 11; y <= base + 28; y++) {
                double r = 4.4 * (1.0 - (y - base - 11) / 17.0) + 0.6;
                for (int dx = -5; dx <= 6; dx++) {
                    for (int dz = -5; dz <= 6; dz++) {
                        double ddx = dx - 0.5, ddz = dz - 0.5;
                        if (ddx * ddx + ddz * ddz <= r * r && !chance(cx + dx, y, cz + dz, 82, 0.06)) s.soft(cx + dx, y, cz + dz, leaves);
                    }
                }
            }
        }
        int[][] dolls = {{-5, -5}, {1, -6}, {-6, 4}, {-9, -3}};
        for (int[] d : dolls) {
            int x = hx + d[0], z = hz + d[1];
            Direction side = toward(x, z, hx, hz);
            forcedTree(s, t, x, z, null, face(ModBlocks.WARA_NINGYO.get(), side), side);
        }
    }

    static void fogRope(ChunkSink s, TerrainData t, Pois p) {
        double px = p.fogNz, pz = -p.fogNx;  // along the fog line
        int ax = (int) Math.round(p.fogCx + px * 3.5), az = (int) Math.round(p.fogCz + pz * 3.5);
        int bx = (int) Math.round(p.fogCx - px * 3.5), bz = (int) Math.round(p.fogCz - pz * 3.5);
        if (!s.touches(Math.min(ax, bx) - 4, Math.min(az, bz) - 4, Math.max(ax, bx) + 4, Math.max(az, bz) + 4)) return;
        BlockState band = ModBlocks.CEDAR_LOG_SHIMENAWA.get().defaultBlockState();
        forcedTree(s, t, ax, az, band, null, Direction.NORTH);
        forcedTree(s, t, bx, bz, band, null, Direction.NORTH);
        int n = Math.max(Math.abs(ax - bx), Math.abs(az - bz));
        Direction ropeFace = Math.abs(p.fogNx) > Math.abs(p.fogNz) ? Direction.EAST : Direction.SOUTH;
        int ry = Math.max(t.height(ax, az), t.height(bx, bz)) + 3;
        for (int k = 1; k < n; k++) {
            int x = ax + Math.round((bx - ax) * k / (float) n), z = az + Math.round((bz - az) * k / (float) n);
            s.soft(x, ry, z, face(ModBlocks.SHIMENAWA.get(), ropeFace));
        }
    }

    // ------------------------------------------------------------------------------------------------ 吊り橋
    static int deckY(PoiLayout L, int z) {
        double f = (z - L.bridgeZ0) / (double) Math.max(1, L.bridgeZ1 - L.bridgeZ0);
        return L.bridgeY - (int) Math.round(2.0 * Math.sin(Math.PI * Math.max(0, Math.min(1, f))));
    }

    static void bridge(ChunkSink s, TerrainData t, PoiLayout L) {
        int x = L.bridgeX;
        if (!s.touches(x - 3, L.bridgeZ0 - 3, x + 3, L.bridgeZ1 + 3)) return;
        BlockState plank = ModBlocks.WEATHERED_PLANKS.get().defaultBlockState();
        BlockState fence = Blocks.SPRUCE_FENCE.defaultBlockState();
        BlockState stone = Blocks.MOSSY_STONE_BRICKS.defaultBlockState();
        for (int z = L.bridgeZ0 - 2; z <= L.bridgeZ1 + 2; z++) {
            int y = deckY(L, Math.max(L.bridgeZ0, Math.min(L.bridgeZ1, z)));
            for (int dx = -1; dx <= 1; dx++) {
                boolean missing = dx != 0 && z > L.bridgeZ0 + 2 && z < L.bridgeZ1 - 2 && chance(x + dx, y, z, 91, 0.22);
                if (z < L.bridgeZ0 || z > L.bridgeZ1) {
                    for (int yy = t.height(x + dx, z) + 1; yy <= y; yy++) s.set(x + dx, yy, z, stone);
                } else if (!missing) s.set(x + dx, y, z, plank);
                s.clear(x + dx, y + 1, z, x + dx, y + 3, z);
            }
            if (z >= L.bridgeZ0 - 1 && z <= L.bridgeZ1 + 1) {
                s.post(x - 2, y + 1, z, fence);
                s.post(x + 2, y + 1, z, fence);
            }
        }
        BlockState post = log(Blocks.SPRUCE_LOG, Direction.Axis.Y);
        for (int z : new int[]{L.bridgeZ0 - 2, L.bridgeZ1 + 2}) {
            int y = deckY(L, Math.max(L.bridgeZ0, Math.min(L.bridgeZ1, z)));
            for (int yy = t.height(x - 2, z) + 1; yy <= y + 2; yy++) s.set(x - 2, yy, z, post);
            for (int yy = t.height(x + 2, z) + 1; yy <= y + 2; yy++) s.set(x + 2, yy, z, post);
        }
    }

    // ------------------------------------------------------------------------------------------------ 山小屋
    static void hut(ChunkSink s, TerrainData t, PoiLayout L) {
        PoiLayout.House h = L.hut;
        int x1 = h.x1(), z1 = h.z1(), x2 = h.x2(), z2 = h.z2(), fy = h.floorY();
        if (!s.touches(x1 - 2, z1 - 3, x2 + 6, z2 + 3)) return;
        BlockState cobble = Blocks.MOSSY_COBBLESTONE.defaultBlockState();
        for (int x = x1; x <= x2; x++)
            for (int z = z1; z <= z2; z++) foundation(s, t, x, z, fy, cobble);
        s.clear(x1 - 1, fy + 1, z1 - 1, x2 + 1, fy + 9, z2 + 1);
        s.box(x1, fy, z1, x2, fy, z2, ModBlocks.DARK_PLANKS.get().defaultBlockState());
        BlockState wall = ModBlocks.WEATHERED_PLANKS.get().defaultBlockState();
        BlockState corner = log(Blocks.SPRUCE_LOG, Direction.Axis.Y);
        BlockState pane = Blocks.GLASS_PANE.defaultBlockState();
        for (int y = fy + 1; y <= fy + 3; y++) {
            for (int x = x1; x <= x2; x++) {
                for (int z = z1; z <= z2; z++) {
                    boolean ex = x == x1 || x == x2, ez = z == z1 || z == z2;
                    if (!ex && !ez) continue;
                    if (ex && ez) s.set(x, y, z, corner);
                    else if (y == fy + 2 && ez && (x == x1 + 2 || x == x2 - 2)) {
                        if (!chance(x, y, z, 95, 0.5)) s.post(x, y, z, pane);
                    } else s.set(x, y, z, wall);
                }
            }
        }
        door(s, ModBlocks.HUT_DOOR.get(), L.hutDoor.getX(), L.hutDoor.getY(), L.hutDoor.getZ(), Direction.EAST, false);
        s.clear(x2 + 1, fy + 1, h.cz(), x2 + 1, fy + 2, h.cz());
        // tin roof, ridge along x, blue tarp patch
        int zn = z1 - 1, zs = z2 + 1, layer = 0;
        while (zn <= zs) {
            int y = fy + 4 + layer;
            for (int x = x1 - 1; x <= x2 + 1; x++) {
                if (zn == zs) s.set(x, y, zn, slab(ModBlocks.TIN_ROOF_SLAB.get(), SlabType.BOTTOM));
                else {
                    s.post(x, y, zn, stairs(ModBlocks.TIN_ROOF_STAIRS.get(), Direction.SOUTH));
                    s.post(x, y, zs, stairs(ModBlocks.TIN_ROOF_STAIRS.get(), Direction.NORTH));
                }
            }
            for (int z = zn + 1; z <= zs - 1; z++) {
                s.set(x1, y, z, wall);
                s.set(x2, y, z, wall);
            }
            zn++;
            zs--;
            layer++;
        }
        // inside: the sleeper's futon, the key board, the logbook on a crate, cobwebs high in the corners
        int sx = L.hutSleeper.getX(), sy = L.hutSleeper.getY(), sz = L.hutSleeper.getZ();
        s.set(sx, sy, sz, face(ModBlocks.FUTON.get(), L.hutSleeperFacing));
        s.set(sx + 1, sy, sz, face(ModBlocks.FUTON.get(), L.hutSleeperFacing));
        s.set(L.hutKeyHook.getX(), L.hutKeyHook.getY(), L.hutKeyHook.getZ(), face(ModBlocks.KEY_HOOK.get(), Direction.SOUTH));
        s.set(L.hutLogbook.getX(), L.hutLogbook.getY() - 1, L.hutLogbook.getZ(), log(Blocks.STRIPPED_SPRUCE_WOOD, Direction.Axis.Y));
        s.set(L.hutLogbook.getX(), L.hutLogbook.getY(), L.hutLogbook.getZ(), face(ModBlocks.LOGBOOK.get(), Direction.WEST));
        s.set(x1 + 1, fy + 3, z1 + 1, Blocks.COBWEB.defaultBlockState());
        s.set(x2 - 1, fy + 3, z1 + 1, Blocks.COBWEB.defaultBlockState());
        s.set(x1 + 3, fy + 1, z1 + 2, face(ModBlocks.BLOOD_STAIN.get(), Direction.EAST));
        // outside: dead campfire, tarp, firewood
        int fx = L.hutCampfire.getX(), fz = L.hutCampfire.getZ();
        s.set(fx, L.hutCampfire.getY(), fz, Blocks.CAMPFIRE.defaultBlockState().setValue(CampfireBlock.LIT, false));
        for (int x = x2 + 2; x <= x2 + 4; x++)
            for (int z = z2 + 1; z <= z2 + 2; z++) s.soft(x, t.height(x, z) + 1, z, face(ModBlocks.BLUE_TARP.get(), Direction.NORTH));
        BlockState wood = log(Blocks.SPRUCE_LOG, Direction.Axis.Z);
        for (int x = x1 + 1; x <= x1 + 3; x++) {
            s.set(x, fy + 1, z1 - 1, wood);
            if (x != x1 + 3) s.set(x, fy + 2, z1 - 1, wood);
        }
    }

    // ------------------------------------------------------------------------------------------------ 鉄塔
    static void pylon(ChunkSink s, PoiLayout L) {
        int px = L.pylon.getX(), py = L.pylon.getY(), pz = L.pylon.getZ();
        if (!s.touches(px - 9, pz - 4, px + 9, pz + 4)) return;
        BlockState lattice = ModBlocks.STEEL_LATTICE.get().defaultBlockState();
        final int H = 26;
        for (int k = 0; k <= H; k++) {
            int inset = (int) Math.floor(k * 2.6 / H);
            int lo = -3 + inset, hi = 3 - inset;
            int y = py + k;
            for (int[] c : new int[][]{{lo, lo}, {hi, lo}, {lo, hi}, {hi, hi}}) s.set(px + c[0], y, pz + c[1], lattice);
            if (k % 6 == 5) {
                for (int a = lo; a <= hi; a++) {
                    s.set(px + a, y, pz + lo, lattice);
                    s.set(px + a, y, pz + hi, lattice);
                    s.set(px + lo, y, pz + a, lattice);
                    s.set(px + hi, y, pz + a, lattice);
                }
            }
        }
        BlockState bars = Blocks.IRON_BARS.defaultBlockState();
        for (int arm : new int[]{H - 6, H - 1}) {
            for (int a = -8; a <= 8; a++) s.post(px + a, py + arm, pz, bars);
            for (int a : new int[]{-8, -5, 5, 8}) {
                s.post(px + a, py + arm - 1, pz, Blocks.CHAIN.defaultBlockState());
                s.post(px + a, py + arm - 2, pz, Blocks.CHAIN.defaultBlockState());
            }
        }
        // rusty fence around the foot
        for (int a = -5; a <= 5; a++) {
            for (int[] c : new int[][]{{a, -5}, {a, 5}, {-5, a}, {5, a}}) {
                if (c[0] == 0 && c[1] == 5) continue; // gap
                s.post(px + c[0], py, pz + c[1], bars);
            }
        }
    }

    // ------------------------------------------------------------------------------------------------ 廃トンネル
    static void tunnel(ChunkSink s, TerrainData t, PoiLayout L) {
        int tx = L.tunnelX;
        if (!s.touches(tx - 7, L.tunnelZ0 - 2, tx + 7, L.tunnelZ1 + 2)) return;
        BlockState concrete = ModBlocks.CONCRETE_OLD.get().defaultBlockState();
        BlockState moss = ModBlocks.CONCRETE_MOSS.get().defaultBlockState();
        BlockState road = ModBlocks.ASPHALT.get().defaultBlockState();
        BlockState air = Blocks.AIR.defaultBlockState();
        for (int z = L.tunnelZ0; z <= L.tunnelZ1; z++) {
            int ry = L.roadY(z);
            for (int dx = -4; dx <= 4; dx++) {
                int x = tx + dx;
                int ground = t.height(x, z);
                s.set(x, ry - 1, z, concrete);
                s.set(x, ry, z, Math.abs(dx) == 4 ? concrete : road);
                for (int y = ry + 1; y <= ry + 6; y++) {
                    boolean wall = Math.abs(dx) == 4 || (Math.abs(dx) == 3 && y == ry + 6);
                    s.set(x, y, z, wall ? concrete : air);
                }
                s.set(x, ry + 7, z, concrete);
                for (int y = ry + 8; y <= Math.max(ground, ry + 8); y++) {
                    if (y > ground) s.set(x, y, z, ModBlocks.MOUNTAIN_ROCK.get().defaultBlockState());
                }
            }
            if (z % 4 < 2) s.set(tx, ry + 1, z, Blocks.WHITE_CARPET.defaultBlockState());
            if (Math.floorMod(z, 11) == 3) {
                s.set(tx - 2, ry + 6, z, Blocks.COBWEB.defaultBlockState());
                s.set(tx + 2, ry + 1, z, Blocks.GRAVEL.defaultBlockState());
            }
            if (Math.floorMod(z, 13) == 7) s.set(tx + 3, ry + 1, z, face(ModBlocks.FOOTPRINTS.get(), Direction.SOUTH));
        }
        // portal facades
        for (int z : new int[]{L.tunnelZ0, L.tunnelZ1}) {
            int ry = L.roadY(z);
            for (int dx = -7; dx <= 7; dx++) {
                for (int y = ry; y <= ry + 10; y++) {
                    boolean opening = Math.abs(dx) <= 3 && y >= ry + 1 && y <= ry + 6 && !(Math.abs(dx) == 3 && y == ry + 6);
                    if (opening) continue;
                    if (Math.abs(dx) <= 4 && y <= ry) continue;
                    s.set(tx + dx, y, z, chance(tx + dx, y, z, 101, 0.55) ? moss : concrete);
                }
                for (int y = t.height(tx + dx, z) + 1; y < ry; y++) s.set(tx + dx, y, z, concrete);
            }
        }
        // the chained gate just inside the north portal
        int gz = L.gate.getZ(), gy = L.gate.getY();
        BlockState bars = Blocks.IRON_BARS.defaultBlockState();
        for (int dx = -3; dx <= 3; dx++) {
            for (int y = gy; y <= gy + 4; y++) {
                if (dx == 0 && y <= gy + 1) continue;
                if (Math.abs(dx) == 3 && y == gy + 4) continue;
                s.post(tx + dx, y, gz, bars);
            }
        }
        door(s, ModBlocks.IRON_GATE.get(), tx, gy, gz, Direction.NORTH, true);
        for (var lamp : L.tunnelLamps) {
            Direction f = lamp.getX() < tx ? Direction.EAST : Direction.WEST;
            s.set(lamp.getX(), lamp.getY(), lamp.getZ(), face(ModBlocks.TUNNEL_LAMP.get(), f));
        }
        if (s.contains(L.tunnelMemo.getX(), L.tunnelMemo.getZ())) {
            s.set(L.tunnelMemo.getX(), L.tunnelMemo.getY(), L.tunnelMemo.getZ(), face(ModBlocks.MEMO.get(), Direction.EAST));
        }
    }

    // ------------------------------------------------------------------------------------------------ 登山口
    static void trailhead(ChunkSink s, TerrainData t, PoiLayout L) {
        if (!s.touches(126, 228, 180, 252)) return;
        // parking stalls
        for (int x = 154; x <= 170; x += 4) {
            for (int z = 244; z <= 247; z++) s.soft(x, t.height(x, z) + 1, z, Blocks.WHITE_CARPET.defaultBlockState());
        }
        // your car
        int cx = L.carDoor.getX(), cy = L.carDoor.getY() - 1, cz = L.carDoor.getZ() - 1;
        if (s.touches(cx - 3, cz - 2, cx + 3, cz + 2)) {
            BlockState white = Blocks.WHITE_CONCRETE.defaultBlockState(), gray = Blocks.GRAY_CONCRETE.defaultBlockState();
            BlockState black = Blocks.BLACK_CONCRETE.defaultBlockState(), glass = Blocks.TINTED_GLASS.defaultBlockState();
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    int x = cx + dx, z = cz + dz;
                    for (int y = t.height(x, z) + 1; y < cy; y++) s.set(x, y, z, Blocks.GRAVEL.defaultBlockState());
                    boolean wheel = Math.abs(dx) == 2 && Math.abs(dz) == 1;
                    s.set(x, cy, z, wheel ? black : (Math.abs(dz) == 1 ? air() : gray));
                    BlockState body = white;
                    if (dx == -2 && dz != 0) body = Blocks.QUARTZ_BLOCK.defaultBlockState();
                    if (dx == 2 && dz != 0) body = Blocks.RED_CONCRETE.defaultBlockState();
                    s.set(x, cy + 1, z, body);
                    if (Math.abs(dx) == 2) s.set(x, cy + 2, z, slab(Blocks.QUARTZ_SLAB, SlabType.BOTTOM));
                    else s.set(x, cy + 2, z, (dx == 0 && dz == 0) ? air() : glass);
                    if (Math.abs(dx) <= 1) s.set(x, cy + 3, z, slab(Blocks.QUARTZ_SLAB, SlabType.BOTTOM));
                    if (Math.abs(dx) <= 1 && Decorator.rand(x, z, 111) < 0.5) s.soft(x, cy + 4, z, face(ModBlocks.FALLEN_LEAVES.get(), Direction.NORTH));
                }
            }
            s.set(L.carDoor.getX(), L.carDoor.getY(), L.carDoor.getZ(), face(ModBlocks.CAR_DOOR.get(), Direction.SOUTH));
        }
        // memorial board for the missing, and one working street lamp
        if (s.contains(L.memorial.getX(), L.memorial.getZ())) {
            s.set(L.memorial.getX(), L.memorial.getY(), L.memorial.getZ(), face(ModBlocks.NOTICE_BOARD.get(), Direction.SOUTH));
        }
        int lx = L.streetLamp.getX(), ly = L.streetLamp.getY(), lz = L.streetLamp.getZ();
        if (s.contains(lx, lz)) {
            for (int y = ly; y <= ly + 4; y++) s.post(lx, y, lz, Blocks.IRON_BARS.defaultBlockState());
            s.set(lx, ly + 5, lz, slab(Blocks.SMOOTH_STONE_SLAB, SlabType.BOTTOM));
            s.set(lx + 1, ly + 5, lz, slab(Blocks.SMOOTH_STONE_SLAB, SlabType.BOTTOM));
            s.set(lx + 1, ly + 4, lz, Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true));
        }
        int sx = 130, sz = 233;
        if (s.contains(sx, sz)) s.set(sx, t.height(sx, sz) + 1, sz, face(ModBlocks.SIGNPOST.get(), Direction.NORTH));
        // guardrail along the forest road
        for (int x = 134; x <= 150; x++) {
            int z = 243;
            if (s.contains(x, z)) s.set(x, t.height(x, z) + 1, z, face(ModBlocks.GUARDRAIL.get(), Direction.NORTH));
        }
    }

    static BlockState air() {
        return Blocks.AIR.defaultBlockState();
    }
}
