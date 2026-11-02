package com.meikon.game;

import com.meikon.block.AndonBlock;
import com.meikon.block.CandleStandBlock;
import com.meikon.block.KakejikuBlock;
import com.meikon.block.SuzuDaiBlock;
import com.meikon.block.TatamiBlock;
import com.meikon.block.ToriiGateBlock;
import com.meikon.entity.Hanayome;
import com.meikon.entity.Ichimatsu;
import com.meikon.registry.ModBlocks;
import com.meikon.registry.ModEntities;
import com.meikon.registry.ModPaintings;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.decoration.Painting;
import net.minecraft.world.entity.decoration.PaintingVariant;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.LayeredCauldronBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.registries.RegistryObject;

import javax.annotation.Nullable;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * Builds Sayo's mansion: a ring of rooms around an open moonlit courtyard, joined by an engawa corridor,
 * with the storehouse, the well, a dead cherry tree and the bond-severing torii in the courtyard.
 * The layout (room types, doors, furniture, items) is a pure function of the seed; with {@code placing=false}
 * the builder only records positions (used to restore the game after a restart).
 */
public final class MansionBuilder {
    public static final int Y = 64;
    public static final int CEIL = Y + 5;
    static final int OUT = 27;
    static final int PART = 18;
    static final int EDGE = 14;
    static final int[] SEPARATORS = {-9, 0, 9};
    private static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;

    @Nullable private final ServerLevel level;
    private final boolean placing;
    private final Random rand;
    private final Mansion m;
    private final List<Info> infos = new ArrayList<>();
    private final Map<Room, Info> byRoom = new HashMap<>();
    private final List<Runnable> entityJobs = new ArrayList<>();
    private int closetIndex;

    /** Builder-side info of a room. */
    private static final class Info {
        final Room room;
        final Direction back;
        /** Toward the corridor (side rooms) or null (corner rooms). */
        @Nullable final Direction inward;
        final boolean[][] used;
        final List<Info> neighbours = new ArrayList<>();
        final Set<Info> doorTo = new java.util.LinkedHashSet<>();
        boolean corridorDoor;
        boolean andon;
        boolean lit;

        Info(Room room, Direction back, @Nullable Direction inward) {
            this.room = room;
            this.back = back;
            this.inward = inward;
            this.used = new boolean[room.width()][room.depth()];
        }

        int w() {
            return back.getAxis() == Direction.Axis.Z ? room.width() : room.depth();
        }

        int d() {
            return back.getAxis() == Direction.Axis.Z ? room.depth() : room.width();
        }
    }

    private MansionBuilder(@Nullable ServerLevel level, long seed, boolean placing) {
        this.level = level;
        this.placing = placing && level != null;
        this.rand = new Random(seed ^ 0x6D65696B6F6EL);
        this.m = new Mansion(seed);
    }

    public static Mansion build(ServerLevel level, long seed) {
        MansionBuilder b = new MansionBuilder(level, seed, true);
        b.run();
        return b.m;
    }

    /** Recomputes the mansion record without touching the world. */
    public static Mansion record(long seed) {
        MansionBuilder b = new MansionBuilder(null, seed, false);
        b.run();
        return b.m;
    }

    // ================================================================== main
    private void run() {
        if (placing) clear();
        layoutRooms();
        assignTypes();
        chooseDoors();
        buildShell();
        buildCorridor();
        buildDoors();
        buildRoof();
        for (Info i : infos) furnish(i);
        buildCourtyard();
        buildKura();
        distributeItems();
        waypoints();
        if (placing) {
            for (Runnable r : entityJobs) {
                try {
                    r.run();
                } catch (Exception e) {
                    com.meikon.Meikon.LOGGER.warn("[meikon] mansion entity job failed", e);
                }
            }
        }
    }

    private void clear() {
        BlockState air = Blocks.AIR.defaultBlockState();
        for (int x = -36; x <= 36; x++) {
            for (int z = -36; z <= 36; z++) {
                for (int y = Y - 1; y <= Y + 18; y++) {
                    BlockPos p = new BlockPos(x, y, z);
                    if (y < Y) {
                        level.setBlock(p, Blocks.COARSE_DIRT.defaultBlockState(), FLAGS);
                    } else if (!level.getBlockState(p).isAir()) {
                        level.setBlock(p, air, FLAGS);
                    }
                }
            }
        }
        AABB box = new AABB(-40, Y - 4, -40, 40, Y + 30, 40);
        for (var e : level.getEntitiesOfClass(net.minecraft.world.entity.Entity.class, box,
                e -> !(e instanceof net.minecraft.world.entity.player.Player))) {
            e.discard();
        }
    }

    // ================================================================== layout
    private void layoutRooms() {
        int id = 0;
        int[][] spans = {{-17, -10}, {-8, -1}, {1, 8}, {10, 17}};
        // side rooms: north, south, west, east
        for (int s = 0; s < 4; s++) {
            for (int[] span : spans) {
                Room r;
                Direction back;
                switch (s) {
                    case 0 -> { r = new Room(id++, Room.Type.WASHITSU, span[0], -26, span[1], -19); back = Direction.NORTH; }
                    case 1 -> { r = new Room(id++, Room.Type.WASHITSU, span[0], 19, span[1], 26); back = Direction.SOUTH; }
                    case 2 -> { r = new Room(id++, Room.Type.WASHITSU, -26, span[0], -19, span[1]); back = Direction.WEST; }
                    default -> { r = new Room(id++, Room.Type.WASHITSU, 19, span[0], 26, span[1]); back = Direction.EAST; }
                }
                addRoom(new Info(r, back, back.getOpposite()));
            }
        }
        // corners (back wall = their north/south exterior wall)
        addRoom(new Info(new Room(id++, Room.Type.WASHITSU, -26, -26, -19, -19), Direction.NORTH, null));
        addRoom(new Info(new Room(id++, Room.Type.WASHITSU, 19, -26, 26, -19), Direction.NORTH, null));
        addRoom(new Info(new Room(id++, Room.Type.WASHITSU, -26, 19, -19, 26), Direction.SOUTH, null));
        addRoom(new Info(new Room(id, Room.Type.WASHITSU, 19, 19, 26, 26), Direction.SOUTH, null));
        // neighbours (share a wall)
        for (Info a : infos) {
            for (Info b : infos) {
                if (a != b && adjacent(a.room, b.room)) a.neighbours.add(b);
            }
        }
    }

    private void addRoom(Info i) {
        infos.add(i);
        byRoom.put(i.room, i);
        m.rooms.add(i.room);
    }

    private static boolean adjacent(Room a, Room b) {
        boolean xTouch = a.x2 + 2 == b.x1 || b.x2 + 2 == a.x1;
        boolean zTouch = a.z2 + 2 == b.z1 || b.z2 + 2 == a.z1;
        boolean zOverlap = a.z1 <= b.z2 && b.z1 <= a.z2;
        boolean xOverlap = a.x1 <= b.x2 && b.x1 <= a.x2;
        return (xTouch && zOverlap) || (zTouch && xOverlap);
    }

    private Info side(int s, int slot) {
        return infos.get(s * 4 + slot);
    }

    private void assignTypes() {
        // the room of lamplight: south side, second slot
        Info safe = side(1, 1);
        safe.room.type = Room.Type.SAFE;
        m.safeRoom = safe.room;
        // the wedding hall: north side, the two middle rooms joined
        Info w1 = side(0, 1), w2 = side(0, 2);
        w1.room.type = Room.Type.WEDDING;
        w2.room.type = Room.Type.WEDDING;
        List<Room.Type> pool = new ArrayList<>(List.of(Room.Type.BUTSUMA, Room.Type.DOLLS, Room.Type.KITCHEN, Room.Type.BATH,
                Room.Type.CHILD, Room.Type.STUDY, Room.Type.BEDROOM, Room.Type.BEDROOM, Room.Type.BEDROOM,
                Room.Type.WASHITSU, Room.Type.WASHITSU, Room.Type.WASHITSU, Room.Type.WASHITSU, Room.Type.WASHITSU,
                Room.Type.STORAGE, Room.Type.STORAGE, Room.Type.STORAGE));
        Collections.shuffle(pool, rand);
        int k = 0;
        for (Info i : infos) {
            if (i.room.type == Room.Type.SAFE || i.room.type == Room.Type.WEDDING) continue;
            i.room.type = pool.get(k++);
        }
        // keep the dolls away from the lamplight room's neighbours (first steps should be calm)
        for (Info n : safe.neighbours) {
            if (n.room.type == Room.Type.DOLLS) {
                for (Info o : infos) {
                    if (o.room.type == Room.Type.WASHITSU && !safe.neighbours.contains(o)) {
                        o.room.type = Room.Type.DOLLS;
                        n.room.type = Room.Type.WASHITSU;
                        break;
                    }
                }
            }
        }
    }

    private void chooseDoors() {
        Info safe = byRoom.get(m.safeRoom);
        for (Info i : infos) {
            if (i.inward != null) i.corridorDoor = rand.nextFloat() < 0.68f;
        }
        safe.corridorDoor = true;
        Info w1 = side(0, 1), w2 = side(0, 2);
        w1.corridorDoor = false;
        w2.corridorDoor = true;
        connect(w1, w2);
        for (Info a : infos) {
            for (Info b : a.neighbours) {
                if (a.room.id < b.room.id && a != safe && b != safe && !isWedding(a) && !isWedding(b) && rand.nextFloat() < 0.5f) {
                    connect(a, b);
                }
            }
        }
        // make sure every room is reachable from the corridor
        while (true) {
            Set<Info> reach = new HashSet<>();
            ArrayDeque<Info> queue = new ArrayDeque<>();
            for (Info i : infos) {
                if (i.corridorDoor) {
                    reach.add(i);
                    queue.add(i);
                }
            }
            while (!queue.isEmpty()) {
                Info c = queue.poll();
                for (Info n : c.doorTo) {
                    if (reach.add(n)) queue.add(n);
                }
            }
            Info fix = null;
            for (Info i : infos) {
                if (!reach.contains(i)) {
                    fix = i;
                    break;
                }
            }
            if (fix == null) break;
            Info via = null;
            for (Info n : fix.neighbours) {
                if (reach.contains(n) && n != safe && !isWedding(n)) {
                    via = n;
                    break;
                }
            }
            if (via != null) connect(fix, via);
            else if (fix.inward != null) fix.corridorDoor = true;
            else connect(fix, fix.neighbours.get(0));
        }
    }

    private static boolean isWedding(Info i) {
        return i.room.type == Room.Type.WEDDING;
    }

    private static void connect(Info a, Info b) {
        a.doorTo.add(b);
        b.doorTo.add(a);
    }

    // ================================================================== block helpers
    private void set(int x, int y, int z, BlockState s) {
        if (placing) level.setBlock(new BlockPos(x, y, z), s, FLAGS);
    }

    private void setUpdate(int x, int y, int z, BlockState s) {
        if (placing) level.setBlock(new BlockPos(x, y, z), s, Block.UPDATE_ALL);
    }

    private void set(BlockPos p, BlockState s) {
        set(p.getX(), p.getY(), p.getZ(), s);
    }

    private void fill(int x1, int y1, int z1, int x2, int y2, int z2, BlockState s) {
        for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++) {
            for (int y = Math.min(y1, y2); y <= Math.max(y1, y2); y++) {
                for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); z++) {
                    set(x, y, z, s);
                }
            }
        }
    }

    private static BlockState st(RegistryObject<Block> b) {
        return b.get().defaultBlockState();
    }

    private static BlockState facing(RegistryObject<Block> b, Direction d) {
        return b.get().defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, d);
    }

    private static BlockState pillar(RegistryObject<Block> b, Direction.Axis a) {
        return b.get().defaultBlockState().setValue(RotatedPillarBlock.AXIS, a);
    }

    private void door(int x, int z, Direction facing, Block door, boolean right) {
        BlockState lower = door.defaultBlockState().setValue(DoorBlock.FACING, facing).setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER)
                .setValue(DoorBlock.HINGE, right ? DoorHingeSide.RIGHT : DoorHingeSide.LEFT).setValue(DoorBlock.OPEN, false);
        set(x, Y + 1, z, lower);
        set(x, Y + 2, z, lower.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
    }

    // ================================================================== shell: floors, walls, ceilings
    private void buildShell() {
        // floors
        for (Info i : infos) {
            Room r = i.room;
            for (int x = r.x1; x <= r.x2; x++) {
                for (int z = r.z1; z <= r.z2; z++) {
                    set(x, Y, z, floorFor(r.type, x, z));
                }
            }
        }
        // the wedding hall is one long room: remove the separator between its halves
        Room w1 = side(0, 1).room;
        // ceilings over the whole building (not the courtyard)
        for (int x = -OUT; x <= OUT; x++) {
            for (int z = -OUT; z <= OUT; z++) {
                if (Math.max(Math.abs(x), Math.abs(z)) <= EDGE - 1) continue;
                boolean corridor = Math.max(Math.abs(x), Math.abs(z)) <= PART - 1;
                set(x, CEIL, z, corridor ? Blocks.SPRUCE_PLANKS.defaultBlockState() : Blocks.DARK_OAK_PLANKS.defaultBlockState());
                if (Math.max(Math.abs(x), Math.abs(z)) == OUT || Math.max(Math.abs(x), Math.abs(z)) == PART) {
                    set(x, Y, z, Blocks.DARK_OAK_PLANKS.defaultBlockState());
                }
            }
        }
        // walls
        for (int a = -OUT; a <= OUT; a++) {
            wallColumn(a, -OUT, WallKind.OUTER);
            wallColumn(a, OUT, WallKind.OUTER);
            wallColumn(-OUT, a, WallKind.OUTER);
            wallColumn(OUT, a, WallKind.OUTER);
            boolean inner = Math.abs(a) <= PART;
            wallColumn(a, -PART, inner ? WallKind.SHOJI : WallKind.FUSUMA);
            wallColumn(a, PART, inner ? WallKind.SHOJI : WallKind.FUSUMA);
            wallColumn(-PART, a, inner ? WallKind.SHOJI : WallKind.FUSUMA);
            wallColumn(PART, a, inner ? WallKind.SHOJI : WallKind.FUSUMA);
        }
        for (int s : SEPARATORS) {
            for (int a = PART; a <= OUT; a++) {
                for (int sign : new int[]{-1, 1}) {
                    int c = a * sign;
                    // north/south rows: separator at x = s
                    if (!(s == 0 && sign < 0)) wallColumn(s, c, WallKind.FUSUMA);
                    wallColumn(c, s, WallKind.FUSUMA);
                }
            }
        }
        // open the wedding hall: clear the separator column x = 0 on the north side
        for (int z = w1.z1; z <= w1.z2; z++) {
            fill(0, Y + 1, z, 0, Y + 4, z, Blocks.AIR.defaultBlockState());
            set(0, Y, z, floorFor(Room.Type.WEDDING, 0, z));
        }
        // pillars at every wall junction / every 4 blocks of outer walls
        for (int a = -OUT; a <= OUT; a++) {
            if (Math.floorMod(a, 3) == 0 || Math.abs(a) == OUT || Math.abs(a) == PART) {
                for (int c : new int[]{-OUT, OUT}) {
                    fill(a, Y + 1, c, a, Y + 4, c, pillar(ModBlocks.DARK_BEAM, Direction.Axis.Y));
                    fill(c, Y + 1, a, c, Y + 4, a, pillar(ModBlocks.DARK_BEAM, Direction.Axis.Y));
                }
            }
        }
        int[] lines = {-PART, -9, 0, 9, PART};
        for (int lx : lines) {
            for (int lz : new int[]{-OUT, -PART, PART, OUT}) {
                if (lx == 0 && lz == -OUT) continue;
                fill(lx, Y + 1, lz, lx, Y + 4, lz, pillar(ModBlocks.DARK_BEAM, Direction.Axis.Y));
                fill(lz, Y + 1, lx, lz, Y + 4, lx, pillar(ModBlocks.DARK_BEAM, Direction.Axis.Y));
            }
        }
    }

    private enum WallKind { OUTER, SHOJI, FUSUMA }

    private void wallColumn(int x, int z, WallKind kind) {
        set(x, Y, z, Blocks.DARK_OAK_PLANKS.defaultBlockState());
        set(x, Y + 1, z, st(ModBlocks.WAINSCOT));
        BlockState mid;
        switch (kind) {
            case OUTER -> mid = rand.nextFloat() < 0.22f ? st(ModBlocks.PLASTER_WALL_STAINED) : st(ModBlocks.PLASTER_WALL);
            case SHOJI -> {
                float f = rand.nextFloat();
                mid = f < 0.06f ? st(ModBlocks.SHOJI_TORN) : f < 0.09f ? st(ModBlocks.SHOJI_BLOODY) : st(ModBlocks.SHOJI);
                m.shoji.add(new BlockPos(x, Y + 2, z));
            }
            default -> mid = rand.nextFloat() < 0.15f ? st(ModBlocks.PLASTER_WALL) : st(ModBlocks.FUSUMA);
        }
        set(x, Y + 2, z, mid);
        set(x, Y + 3, z, kind == WallKind.OUTER ? mid : (kind == WallKind.SHOJI && rand.nextFloat() < 0.1f ? st(ModBlocks.SHOJI_TORN) : mid));
        set(x, Y + 4, z, rand.nextFloat() < 0.12f ? st(ModBlocks.PLASTER_WALL_STAINED) : st(ModBlocks.PLASTER_WALL));
    }

    private BlockState floorFor(Room.Type type, int x, int z) {
        return switch (type) {
            case KITCHEN, STORAGE -> rand.nextFloat() < 0.12f ? st(ModBlocks.ROTTEN_PLANKS) : st(ModBlocks.OLD_PLANKS);
            case BATH -> rand.nextFloat() < 0.3f ? Blocks.MOSSY_STONE_BRICKS.defaultBlockState() : Blocks.POLISHED_ANDESITE.defaultBlockState();
            case CORRIDOR -> st(ModBlocks.ENGAWA);
            default -> ModBlocks.TATAMI.get().defaultBlockState().setValue(TatamiBlock.AXIS,
                    Math.floorMod(x / 2 + z, 2) == 0 ? Direction.Axis.X : Direction.Axis.Z);
        };
    }

    // ================================================================== corridor (engawa) and courtyard edge
    private void buildCorridor() {
        for (int x = -PART + 1; x <= PART - 1; x++) {
            for (int z = -PART + 1; z <= PART - 1; z++) {
                int d = Math.max(Math.abs(x), Math.abs(z));
                if (d >= EDGE && d <= PART - 1) {
                    set(x, Y, z, rand.nextFloat() < 0.06f ? st(ModBlocks.ROTTEN_PLANKS) : st(ModBlocks.ENGAWA));
                    if (d == EDGE) {
                        boolean post = (Math.abs(x) == EDGE && Math.floorMod(z, 4) == 2) || (Math.abs(z) == EDGE && Math.floorMod(x, 4) == 2)
                                || (Math.abs(x) == EDGE && Math.abs(z) == EDGE);
                        if (post) {
                            fill(x, Y + 1, z, x, Y + 4, z, pillar(ModBlocks.DARK_BEAM, Direction.Axis.Y));
                        }
                    }
                }
            }
        }
        // some stretches of the engawa are shuttered with paper screens
        for (int seg = -12; seg <= 12; seg += 4) {
            for (int side = 0; side < 4; side++) {
                if (rand.nextFloat() > 0.3f) continue;
                if (side == 0 && seg >= -2 && seg <= 2) continue; // keep the view to the torii from the south
                for (int k = 1; k <= 3; k++) {
                    int a = seg + k - 2;
                    int x, z;
                    switch (side) {
                        case 0 -> { x = a; z = EDGE; }
                        case 1 -> { x = a; z = -EDGE; }
                        case 2 -> { x = EDGE; z = a; }
                        default -> { x = -EDGE; z = a; }
                    }
                    if (Math.abs(a) >= EDGE - 1) continue;
                    set(x, Y + 1, z, st(ModBlocks.WAINSCOT));
                    set(x, Y + 2, z, rand.nextFloat() < 0.2f ? st(ModBlocks.SHOJI_TORN) : st(ModBlocks.SHOJI));
                    set(x, Y + 3, z, st(ModBlocks.SHOJI));
                    m.shoji.add(new BlockPos(x, Y + 2, z));
                }
            }
        }
        // eave beam along the edge
        for (int a = -EDGE; a <= EDGE; a++) {
            set(a, Y + 4, EDGE, pillar(ModBlocks.DARK_BEAM, Direction.Axis.X));
            set(a, Y + 4, -EDGE, pillar(ModBlocks.DARK_BEAM, Direction.Axis.X));
            set(EDGE, Y + 4, a, pillar(ModBlocks.DARK_BEAM, Direction.Axis.Z));
            set(-EDGE, Y + 4, a, pillar(ModBlocks.DARK_BEAM, Direction.Axis.Z));
        }
        // a few lamps, stains and the old clock
        int[][] lamps = {{-16, -6}, {16, 7}, {5, -16}, {-10, 16}};
        for (int[] l : lamps) {
            if (rand.nextFloat() < 0.6f) {
                boolean lit = rand.nextFloat() < 0.75f;
                set(l[0], Y + 1, l[1], st(ModBlocks.ANDON).setValue(AndonBlock.LIT, lit));
                m.andons.add(new BlockPos(l[0], Y + 1, l[1]));
            }
        }
        for (int n = 0; n < 7; n++) {
            int a = rand.nextInt(31) - 15;
            int b = 15 + rand.nextInt(3);
            int x = rand.nextBoolean() ? a : (rand.nextBoolean() ? b : -b);
            int z = x == a ? (rand.nextBoolean() ? b : -b) : a;
            set(x, Y + 1, z, facing(rand.nextBoolean() ? ModBlocks.BLOOD_STAIN : ModBlocks.HAIR_STRANDS,
                    Direction.Plane.HORIZONTAL.getRandomDirection(net.minecraft.util.RandomSource.create(rand.nextLong()))));
        }
        // the old clock on the corridor wall near the lamplight room
        set(-1, Y + 3, PART - 1, facing(ModBlocks.OLD_CLOCK, Direction.NORTH));
        m.clocks.add(new BlockPos(-1, Y + 3, PART - 1));
        m.safeOutside = new BlockPos(-4, Y + 1, 16);
    }

    // ================================================================== doors
    private void buildDoors() {
        for (Info i : infos) {
            Room r = i.room;
            if (i.corridorDoor && i.inward != null) {
                int x, z;
                Direction f = i.back; // slab on the room side
                switch (i.inward) {
                    case SOUTH -> { x = (r.x1 + r.x2) / 2; z = -PART; }
                    case NORTH -> { x = (r.x1 + r.x2) / 2; z = PART; }
                    case EAST -> { x = -PART; z = (r.z1 + r.z2) / 2; }
                    default -> { x = PART; z = (r.z1 + r.z2) / 2; }
                }
                if (r.type == Room.Type.WEDDING) x = r.x1 > 0 ? 0 : x;
                Block door = r.type == Room.Type.SAFE ? ModBlocks.SEALED_DOOR.get()
                        : (r.type == Room.Type.WEDDING || rand.nextBoolean()) ? ModBlocks.FUSUMA_DOOR.get() : ModBlocks.SHOJI_DOOR.get();
                if (r.type == Room.Type.WEDDING) {
                    // the wedding hall has a double door at x = 0 (the joint of its halves)
                    door(0, -PART, f, door, false);
                    door(1, -PART, f, door, true);
                    markDoor(byRoom.get(side(0, 1).room), 0, -PART);
                    markDoor(i, 1, -PART);
                    set(0, Y + 3, -PART, st(ModBlocks.RANMA));
                    set(1, Y + 3, -PART, st(ModBlocks.RANMA));
                    m.doors.add(new BlockPos(0, Y + 1, -PART));
                    continue;
                }
                door(x, z, f, door, rand.nextBoolean());
                set(x, Y + 3, z, st(ModBlocks.RANMA));
                markDoor(i, x, z);
                m.doors.add(new BlockPos(x, Y + 1, z));
                if (r.type == Room.Type.SAFE) {
                    m.safeDoor = new BlockPos(x, Y + 1, z);
                    m.safeOutside = new BlockPos(x, Y + 1, z + (i.inward == Direction.NORTH ? -2 : 2));
                }
            }
        }
        Set<String> done = new HashSet<>();
        for (Info a : infos) {
            for (Info b : a.doorTo) {
                String key = Math.min(a.room.id, b.room.id) + "-" + Math.max(a.room.id, b.room.id);
                if (!done.add(key)) continue;
                if (isWedding(a) && isWedding(b)) continue;
                Room ra = a.room, rb = b.room;
                int x, z;
                Direction f;
                if (ra.x2 + 2 == rb.x1 || rb.x2 + 2 == ra.x1) {
                    x = ra.x2 + 2 == rb.x1 ? ra.x2 + 1 : rb.x2 + 1;
                    z = (Math.max(ra.z1, rb.z1) + Math.min(ra.z2, rb.z2)) / 2;
                    f = Direction.EAST;
                } else {
                    z = ra.z2 + 2 == rb.z1 ? ra.z2 + 1 : rb.z2 + 1;
                    x = (Math.max(ra.x1, rb.x1) + Math.min(ra.x2, rb.x2)) / 2;
                    f = Direction.SOUTH;
                }
                door(x, z, f, ModBlocks.FUSUMA_DOOR.get(), rand.nextBoolean());
                set(x, Y + 3, z, st(ModBlocks.RANMA));
                markDoor(a, x, z);
                markDoor(b, x, z);
                m.doors.add(new BlockPos(x, Y + 1, z));
            }
        }
    }

    /** Reserves the two cells in front of a door inside a room. */
    private void markDoor(Info i, int dx, int dz) {
        Room r = i.room;
        for (int x = r.x1; x <= r.x2; x++) {
            for (int z = r.z1; z <= r.z2; z++) {
                int dist = Math.abs(x - dx) + Math.abs(z - dz);
                if (dist <= 2) i.used[x - r.x1][z - r.z1] = true;
            }
        }
    }

    // ================================================================== roof (kawara)
    private void buildRoof() {
        BlockState tile = Blocks.DEEPSLATE_TILES.defaultBlockState();
        for (int x = -OUT - 1; x <= OUT + 1; x++) {
            for (int z = -OUT - 1; z <= OUT + 1; z++) {
                int d = Math.max(Math.abs(x), Math.abs(z));
                if (d < EDGE || d > OUT + 1) continue;
                int ridge = 22;
                int y = d <= ridge ? CEIL + 1 + (d - EDGE) : CEIL + 1 + (ridge - EDGE) - (d - ridge);
                if (d == ridge) {
                    setUpdate(x, y, z, tile);
                    setUpdate(x, y + 1, z, Blocks.DEEPSLATE_TILE_SLAB.defaultBlockState());
                    continue;
                }
                Direction out;
                if (Math.abs(x) > Math.abs(z)) out = x > 0 ? Direction.EAST : Direction.WEST;
                else out = z > 0 ? Direction.SOUTH : Direction.NORTH;
                Direction facingDir = d < ridge ? out : out.getOpposite();
                setUpdate(x, y, z, Blocks.DEEPSLATE_TILE_STAIRS.defaultBlockState().setValue(StairBlock.FACING, facingDir)
                        .setValue(StairBlock.HALF, Half.BOTTOM));
                if (d > ridge) {
                    // fill under the outer slope so the eaves look solid from inside the courtyard
                    for (int yy = CEIL + 1; yy < y; yy++) set(x, yy, z, tile);
                }
            }
        }
    }

    // ================================================================== furniture
    /** World position of local cell (u along the back wall, v away from it) at height h above the floor. */
    private BlockPos at(Info i, int u, int v, int h) {
        Room r = i.room;
        return switch (i.back) {
            case NORTH -> new BlockPos(r.x1 + u, Y + h, r.z1 + v);
            case SOUTH -> new BlockPos(r.x2 - u, Y + h, r.z2 - v);
            case WEST -> new BlockPos(r.x1 + v, Y + h, r.z2 - u);
            default -> new BlockPos(r.x2 - v, Y + h, r.z1 + u);
        };
    }

    private boolean free(Info i, int u, int v) {
        if (u < 0 || v < 0 || u >= i.w() || v >= i.d()) return false;
        BlockPos p = at(i, u, v, 0);
        return !i.used[p.getX() - i.room.x1][p.getZ() - i.room.z1];
    }

    private void use(Info i, int u, int v) {
        if (u < 0 || v < 0 || u >= i.w() || v >= i.d()) return;
        BlockPos p = at(i, u, v, 0);
        i.used[p.getX() - i.room.x1][p.getZ() - i.room.z1] = true;
    }

    private boolean put(Info i, int u, int v, int h, BlockState s) {
        if (!free(i, u, v) && h == 1) return false;
        set(at(i, u, v, h), s);
        if (h == 1) use(i, u, v);
        return true;
    }

    private Direction front(Info i) {
        return i.back.getOpposite();
    }

    private Direction rightDir(Info i) {
        return i.back.getClockWise();
    }

    /** A free cell, preferring the given region; null if none. */
    @Nullable
    private int[] freeCell(Info i, int u1, int v1, int u2, int v2) {
        List<int[]> cells = new ArrayList<>();
        for (int u = u1; u <= u2; u++) {
            for (int v = v1; v <= v2; v++) {
                if (free(i, u, v)) cells.add(new int[]{u, v});
            }
        }
        if (cells.isEmpty()) return null;
        return cells.get(rand.nextInt(cells.size()));
    }

    private void furnish(Info i) {
        switch (i.room.type) {
            case SAFE -> furnishSafe(i);
            case WEDDING -> {
                if (i == side(0, 1)) furnishWedding(i);
            }
            case BUTSUMA -> furnishButsuma(i);
            case DOLLS -> furnishDolls(i);
            case KITCHEN -> furnishKitchen(i);
            case BATH -> furnishBath(i);
            case CHILD -> furnishChild(i);
            case STUDY -> furnishStudy(i);
            case BEDROOM -> furnishBedroom(i);
            case STORAGE -> furnishStorage(i);
            default -> furnishWashitsu(i);
        }
        if (i.room.type != Room.Type.SAFE && i.room.type != Room.Type.WEDDING) {
            if (rand.nextFloat() < 0.35f) decal(i, ModBlocks.BLOOD_STAIN);
            if (rand.nextFloat() < 0.25f) decal(i, ModBlocks.HAIR_STRANDS);
            if (!i.andon) m.darkSpots.add(i.room.center());
        }
    }

    private void decal(Info i, RegistryObject<Block> b) {
        int[] c = freeCell(i, 1, 1, i.w() - 2, i.d() - 2);
        if (c != null) set(at(i, c[0], c[1], 1), facing(b, Direction.Plane.HORIZONTAL.getRandomDirection(
                net.minecraft.util.RandomSource.create(rand.nextLong()))));
    }

    private void andon(Info i, int u, int v, float litChance) {
        if (!free(i, u, v)) return;
        boolean lit = rand.nextFloat() < litChance;
        put(i, u, v, 1, st(ModBlocks.ANDON).setValue(AndonBlock.LIT, lit));
        m.andons.add(at(i, u, v, 1));
        i.andon = true;
        i.lit = lit;
    }

    private void tansu(Info i, int u, int v, Direction f) {
        if (!free(i, u, v)) return;
        put(i, u, v, 1, facing(ModBlocks.TANSU, f));
        m.tansuLoot.put(at(i, u, v, 1).asLong(), "");
    }

    private void scroll(Info i, int u) {
        BlockPos p = at(i, u, 0, 2);
        set(p, facing(ModBlocks.KAKEJIKU, front(i)).setValue(KakejikuBlock.FACE, false));
        if (i.room.type != Room.Type.SAFE) m.scrolls.add(p);
    }

    private void letter(Info i, int note) {
        int[] c = freeCell(i, 1, 2, i.w() - 2, i.d() - 2);
        if (c == null) c = freeCell(i, 0, 0, i.w() - 1, i.d() - 1);
        if (c == null) return;
        BlockPos p = at(i, c[0], c[1], 1);
        put(i, c[0], c[1], 1, facing(ModBlocks.LETTER, Direction.Plane.HORIZONTAL.getRandomDirection(
                net.minecraft.util.RandomSource.create(rand.nextLong()))));
        m.letters.put(p.asLong(), note);
    }

    /** A hiding closet against the left (u=0) or right (u=w-1) wall starting at depth v. */
    private boolean closet(Info i, boolean left, int v) {
        int u0 = left ? 0 : i.w() - 1;
        int u1 = left ? 1 : i.w() - 2;
        for (int dv = 0; dv < 3; dv++) {
            if (!free(i, u0, v + dv) || !free(i, u1, v + dv)) return false;
        }
        BlockState wall = st(ModBlocks.FUSUMA);
        BlockState top = st(ModBlocks.PLASTER_WALL);
        for (int dv = 0; dv < 3; dv++) {
            for (int du : new int[]{u0, u1}) {
                use(i, du, v + dv);
                boolean middle = dv == 1;
                if (middle) continue;
                set(at(i, du, v + dv, 1), st(ModBlocks.WAINSCOT));
                set(at(i, du, v + dv, 2), wall);
                set(at(i, du, v + dv, 3), top);
            }
        }
        BlockPos inside = at(i, u0, v + 1, 1);
        BlockPos front = at(i, u1, v + 1, 1);
        set(inside.above(2), top);
        set(front.above(2), st(ModBlocks.RANMA));
        Direction into = left ? rightDir(i) : rightDir(i).getOpposite();
        BlockState lower = ModBlocks.OSHIIRE_DOOR.get().defaultBlockState().setValue(DoorBlock.FACING, into)
                .setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER).setValue(DoorBlock.HINGE, DoorHingeSide.LEFT).setValue(DoorBlock.OPEN, false);
        set(front, lower);
        set(front.above(), lower.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
        m.closets.add(new Closet(closetIndex++, inside, front, into));
        // keep the cell in front of the closet free
        use(i, left ? 2 : i.w() - 3, v + 1);
        return true;
    }

    private void closetSomewhere(Info i) {
        boolean left = rand.nextBoolean();
        for (int attempt = 0; attempt < 6; attempt++) {
            int v = 1 + rand.nextInt(Math.max(1, i.d() - 4));
            if (closet(i, left, v)) return;
            left = !left;
        }
    }

    private void table(Info i, int u, int v) {
        BlockState slab = Blocks.DARK_OAK_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM);
        if (put(i, u, v, 1, slab) && free(i, u + 1, v)) put(i, u + 1, v, 1, slab);
        for (int[] c : new int[][]{{u, v - 1}, {u + 1, v + 1}, {u - 1, v}}) {
            if (free(i, c[0], c[1])) put(i, c[0], c[1], 1, Blocks.BROWN_CARPET.defaultBlockState());
        }
    }

    private void furnishWashitsu(Info i) {
        scroll(i, 3);
        put(i, 3, 0, 1, Blocks.POTTED_DEAD_BUSH.defaultBlockState());
        table(i, 3, 4);
        tansu(i, 0, 0, front(i));
        if (rand.nextFloat() < 0.7f) closetSomewhere(i);
        andon(i, i.w() - 1, 0, 0.55f);
        if (rand.nextFloat() < 0.5f) tansu(i, i.w() - 1, 3, rightDir(i).getOpposite());
    }

    private void furnishBedroom(Info i) {
        put(i, 3, 2, 1, facing(ModBlocks.FUTON, front(i)));
        put(i, 3, 3, 1, facing(ModBlocks.FUTON, front(i)));
        if (rand.nextFloat() < 0.4f) set(at(i, 3, 2, 1), facing(ModBlocks.FUTON, front(i)));
        if (rand.nextFloat() < 0.5f) {
            put(i, 5, 2, 1, facing(ModBlocks.FUTON, front(i)));
            put(i, 5, 3, 1, facing(ModBlocks.FUTON, front(i)));
        }
        closetSomewhere(i);
        if (rand.nextFloat() < 0.5f) closetSomewhere(i);
        tansu(i, i.w() - 1, 0, front(i));
        if (rand.nextFloat() < 0.5f) scroll(i, 1);
        andon(i, 0, 0, 0.35f);
    }

    private void furnishStorage(Info i) {
        BlockState barrel = Blocks.BARREL.defaultBlockState();
        for (int[] c : new int[][]{{0, 0}, {1, 0}, {0, 1}}) {
            if (put(i, c[0], c[1], 1, barrel) && rand.nextBoolean()) set(at(i, c[0], c[1], 2), barrel);
        }
        put(i, i.w() - 1, 0, 1, Blocks.SPRUCE_PLANKS.defaultBlockState());
        set(at(i, i.w() - 1, 0, 2), Blocks.SPRUCE_TRAPDOOR.defaultBlockState());
        for (int[] c : new int[][]{{0, 0}, {i.w() - 1, i.d() - 1}, {i.w() - 1, 0}, {0, i.d() - 1}}) {
            set(at(i, c[0], c[1], 4), Blocks.COBWEB.defaultBlockState());
        }
        closetSomewhere(i);
        if (rand.nextFloat() < 0.6f) closetSomewhere(i);
        tansu(i, 3, 0, front(i));
        tansu(i, 4, 0, front(i));
        if (rand.nextFloat() < 0.3f) andon(i, i.w() - 2, i.d() - 2, 0.4f);
    }

    private void furnishStudy(Info i) {
        for (int u = 0; u < i.w(); u++) {
            if (u == 3 || u == 4) continue;
            if (put(i, u, 0, 1, Blocks.BOOKSHELF.defaultBlockState())) {
                set(at(i, u, 0, 2), Blocks.BOOKSHELF.defaultBlockState());
                if (rand.nextFloat() < 0.6f) set(at(i, u, 0, 3), Blocks.BOOKSHELF.defaultBlockState());
            }
        }
        scroll(i, 3);
        BlockState desk = Blocks.DARK_OAK_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.TOP);
        put(i, 3, 3, 1, desk);
        put(i, 4, 3, 1, desk);
        put(i, 2, 3, 1, Blocks.LECTERN.defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, front(i)));
        andon(i, 6, 3, 0.8f);
        tansu(i, i.w() - 1, 5, rightDir(i).getOpposite());
        painting(i, ModPaintings.FAMILY, true);
        if (rand.nextFloat() < 0.4f) closetSomewhere(i);
    }

    private void furnishKitchen(Info i) {
        for (int u = 0; u < i.w(); u++) {
            for (int v = 0; v < 3; v++) set(at(i, u, v, 0), Blocks.PACKED_MUD.defaultBlockState());
        }
        put(i, 2, 0, 1, facing(ModBlocks.KAMADO, front(i)));
        put(i, 5, 0, 1, facing(ModBlocks.KAMADO, front(i)));
        put(i, 0, 0, 1, Blocks.WATER_CAULDRON.defaultBlockState().setValue(LayeredCauldronBlock.LEVEL, 3));
        put(i, i.w() - 1, 0, 1, Blocks.BARREL.defaultBlockState());
        put(i, i.w() - 1, 1, 1, Blocks.BARREL.defaultBlockState());
        put(i, 3, 0, 1, Blocks.DECORATED_POT.defaultBlockState());
        set(at(i, 3, 2, 4), Blocks.CHAIN.defaultBlockState());
        set(at(i, 5, 4, 4), Blocks.CHAIN.defaultBlockState());
        tansu(i, 0, 4, rightDir(i));
        if (rand.nextFloat() < 0.4f) andon(i, i.w() - 1, i.d() - 2, 0.5f);
    }

    private void furnishBath(Info i) {
        BlockState rim = Blocks.SPRUCE_PLANKS.defaultBlockState();
        for (int u = 2; u <= 4; u++) {
            for (int v = 1; v <= 4; v++) {
                boolean inner = u == 3 && (v == 2 || v == 3);
                use(i, u, v);
                set(at(i, u, v, 1), inner ? Blocks.WATER.defaultBlockState() : rim);
            }
        }
        set(at(i, 6, 0, 2), Blocks.BLACK_STAINED_GLASS_PANE.defaultBlockState());
        set(at(i, 6, 0, 3), Blocks.BLACK_STAINED_GLASS_PANE.defaultBlockState());
        put(i, 6, 2, 1, Blocks.SPRUCE_SLAB.defaultBlockState());
        put(i, 0, 0, 1, Blocks.CAULDRON.defaultBlockState());
        decal(i, ModBlocks.BLOOD_STAIN);
        decal(i, ModBlocks.HAIR_STRANDS);
    }

    private void furnishChild(Info i) {
        put(i, 2, 2, 1, facing(ModBlocks.FUTON, front(i)));
        tansu(i, 0, 0, front(i));
        closetSomewhere(i);
        int[] c = freeCell(i, 4, 0, i.w() - 1, 2);
        if (c != null) {
            use(i, c[0], c[1]);
            BlockPos p = at(i, c[0], c[1], 1);
            m.decorDolls.add(p);
        }
        decal(i, ModBlocks.HAIR_STRANDS);
        andon(i, i.w() - 1, i.d() - 2, 0.3f);
    }

    private void furnishButsuma(Info i) {
        put(i, 3, 0, 1, facing(ModBlocks.BUTSUDAN, front(i)));
        put(i, 4, 0, 1, facing(ModBlocks.BUTSUDAN, front(i)));
        List<Integer> colours = new ArrayList<>(List.of(0, 1, 2));
        Collections.shuffle(colours, rand);
        int[] us = {2, 4, 6};
        for (int k = 0; k < 3; k++) {
            int colour = colours.get(k);
            BlockPos p = at(i, us[k] - (k == 1 ? 1 : 0), 1, 1);
            use(i, us[k] - (k == 1 ? 1 : 0), 1);
            set(p, st(ModBlocks.CANDLE_STAND).setValue(CandleStandBlock.COLOR, colour).setValue(CandleStandBlock.LIT, false));
            m.candles[colour] = p;
        }
        List<Integer> order = new ArrayList<>(List.of(0, 1, 2));
        Collections.shuffle(order, rand);
        for (int k = 0; k < 3; k++) m.candleOrder[k] = order.get(k);
        m.butsumaBell = at(i, 3, 3, 1);
        use(i, 3, 3);
        put(i, 3, 5, 1, Blocks.PURPLE_CARPET.defaultBlockState());
        painting(i, ModPaintings.SAYO, false);
        scroll(i, 6);
    }

    private void furnishDolls(Info i) {
        BlockState step = st(ModBlocks.HINA_DAN);
        for (int u = 1; u <= 6; u++) {
            for (int h = 1; h <= 3; h++) put(i, u, 0, h, step);
            for (int h = 1; h <= 2; h++) put(i, u, 1, h, step);
            put(i, u, 2, 1, step);
            use(i, u, 0);
            use(i, u, 1);
            use(i, u, 2);
        }
        BlockPos head = at(i, 3, 0, 4);
        set(head, facing(ModBlocks.HEADLESS_DOLL, front(i)));
        m.headlessDoll = head;
        int[][] dolls = {{1, 2, 2}, {5, 2, 2}, {2, 1, 3}, {5, 1, 3}, {1, 0, 4}, {6, 0, 4}};
        for (int[] d : dolls) m.dollSpawns.add(at(i, d[0], d[1], d[2]));
        m.dollBell = at(i, 3, 4, 1);
        use(i, 3, 4);
        set(at(i, 0, 0, 4), Blocks.COBWEB.defaultBlockState());
        set(at(i, i.w() - 1, 0, 4), Blocks.COBWEB.defaultBlockState());
    }

    private void furnishWedding(Info i) {
        // the hall spans both north-middle rooms: u 0..16 from x = -8
        Room a = side(0, 1).room, b = side(0, 2).room;
        Room hall = new Room(a.id, Room.Type.WEDDING, a.x1, a.z1, b.x2, b.z2);
        m.weddingHall = hall;
        int cx = 0;
        int zBack = hall.z1;
        for (int z = hall.z1 + 2; z <= hall.z2; z++) set(cx, Y + 1, z, Blocks.RED_CARPET.defaultBlockState());
        BlockPos table = new BlockPos(cx, Y + 1, zBack + 1);
        set(table, facing(ModBlocks.SAKAZUKI_TABLE, Direction.SOUTH));
        m.sakazuki = table;
        set(cx - 1, Y + 1, zBack + 2, Blocks.WHITE_CARPET.defaultBlockState());
        set(cx + 1, Y + 1, zBack + 2, Blocks.BLACK_CARPET.defaultBlockState());
        for (int x = hall.x1 + 1; x <= hall.x2 - 1; x++) {
            if (Math.abs(x) <= 2) continue;
            set(x, Y + 1, zBack, st(ModBlocks.FUSUMA));
            set(x, Y + 2, zBack, st(ModBlocks.FUSUMA));
        }
        for (int[] c : new int[][]{{-3, 1}, {3, 1}, {-6, 4}, {6, 4}}) {
            set(c[0], Y + 1, zBack + c[1], st(ModBlocks.CANDLE_STAND).setValue(CandleStandBlock.COLOR, 0).setValue(CandleStandBlock.LIT, true));
        }
        BlockPos wall = new BlockPos(0, Y + 2, zBack - 1);
        entityJobs.add(() -> hang(wall.relative(Direction.SOUTH), Direction.SOUTH, ModPaintings.MUKASARI));
        BlockPos noteAt = new BlockPos(6, Y + 1, hall.z2 - 1);
        set(noteAt, facing(ModBlocks.LETTER, Direction.NORTH));
        m.letters.put(noteAt.asLong(), Notes.SAKAZUKI);
        m.brideStart = new BlockPos(-5, Y + 1, hall.z2 - 2);
    }

    private void furnishSafe(Info i) {
        put(i, 3, 0, 1, facing(ModBlocks.BUTSUDAN, front(i)));
        put(i, 2, 0, 1, st(ModBlocks.CANDLE_STAND).setValue(CandleStandBlock.COLOR, 0).setValue(CandleStandBlock.LIT, true));
        put(i, 4, 0, 1, st(ModBlocks.CANDLE_STAND).setValue(CandleStandBlock.COLOR, 0).setValue(CandleStandBlock.LIT, true));
        put(i, 0, 0, 1, st(ModBlocks.ANDON).setValue(AndonBlock.LIT, true));
        put(i, i.w() - 1, 0, 1, st(ModBlocks.ANDON).setValue(AndonBlock.LIT, true));
        put(i, 1, 5, 1, facing(ModBlocks.FUTON, rightDir(i)));
        put(i, 2, 5, 1, facing(ModBlocks.FUTON, rightDir(i)));
        tansu(i, i.w() - 1, 3, rightDir(i).getOpposite());
        scroll(i, 5);
        set(at(i, 5, 0, 1), Blocks.POTTED_DEAD_BUSH.defaultBlockState());
        use(i, 5, 0);
        BlockPos n1 = at(i, 3, 3, 1);
        put(i, 3, 3, 1, facing(ModBlocks.LETTER, front(i)));
        m.letters.put(n1.asLong(), Notes.MAID_1);
        BlockPos n2 = at(i, 5, 4, 1);
        put(i, 5, 4, 1, facing(ModBlocks.LETTER, rightDir(i)));
        m.letters.put(n2.asLong(), Notes.LIGHT_HINT);
        BlockPos spawn = at(i, 3, 5, 1);
        m.spawn = spawn;
        m.spawnYaw = i.back.toYRot();
        Room r = i.room;
        m.safeBox = new AABB(r.x1, Y, r.z1, r.x2 + 1, Y + 5, r.z2 + 1);
        painting(i, ModPaintings.MANSION, true);
    }

    private void painting(Info i, RegistryObject<PaintingVariant> variant, boolean leftWall) {
        Direction wallSide = leftWall ? rightDir(i).getOpposite() : rightDir(i);
        int u = leftWall ? 0 : i.w() - 1;
        int v = 3;
        BlockPos hangAt = at(i, u, v, 2);
        Direction face = wallSide.getOpposite();
        entityJobs.add(() -> hang(hangAt, face, variant));
    }

    private void hang(BlockPos pos, Direction face, RegistryObject<PaintingVariant> variant) {
        Holder<PaintingVariant> holder = variant.getHolder().orElse(null);
        if (holder == null || level == null) return;
        Painting p = new Painting(level, pos, face, holder);
        if (p.survives()) level.addFreshEntity(p);
    }

    // ================================================================== courtyard
    private void buildCourtyard() {
        int c = EDGE - 1;
        for (int x = -c; x <= c; x++) {
            for (int z = -c; z <= c; z++) {
                float f = rand.nextFloat();
                BlockState ground = f < 0.55f ? Blocks.COARSE_DIRT.defaultBlockState()
                        : f < 0.75f ? Blocks.PODZOL.defaultBlockState()
                        : f < 0.88f ? Blocks.ROOTED_DIRT.defaultBlockState() : Blocks.MUD.defaultBlockState();
                set(x, Y, z, ground);
                float g = rand.nextFloat();
                if (g < 0.05f) set(x, Y + 1, z, Blocks.DEAD_BUSH.defaultBlockState());
                else if (g < 0.07f) set(x, Y + 1, z, Blocks.BROWN_MUSHROOM.defaultBlockState());
            }
        }
        // path from the south engawa to the torii
        for (int z = -9; z <= c; z++) {
            for (int x = -1; x <= 1; x++) {
                set(x, Y, z, rand.nextFloat() < 0.15f ? Blocks.COBBLESTONE.defaultBlockState() : Blocks.GRAVEL.defaultBlockState());
                set(x, Y + 1, z, Blocks.AIR.defaultBlockState());
            }
        }
        torii();
        // stone lanterns along the path
        for (int[] l : new int[][]{{-3, -7}, {3, -7}, {-3, 5}, {3, 5}}) {
            set(l[0], Y + 1, l[1], facing(ModBlocks.STONE_LANTERN, Direction.SOUTH));
        }
        well(-8, 7);
        deadTree(-7, -5);
        pond(7, 7);
        m.waypoints.add(new BlockPos(0, Y + 1, 4));
        m.waypoints.add(new BlockPos(-8, Y + 1, 2));
        m.waypoints.add(new BlockPos(8, Y + 1, 2));
        m.waypoints.add(new BlockPos(-4, Y + 1, -9));
    }

    private void torii() {
        int z = -10;
        m.toriiCenter = new BlockPos(0, Y + 1, z);
        for (int x : new int[]{-2, 2}) {
            fill(x, Y + 1, z, x, Y + 5, z, pillar(ModBlocks.VERMILION, Direction.Axis.Y));
            set(x, Y, z, Blocks.POLISHED_BLACKSTONE.defaultBlockState());
        }
        for (int x = -3; x <= 3; x++) set(x, Y + 4, z, pillar(ModBlocks.VERMILION, Direction.Axis.X));
        for (int x = -4; x <= 4; x++) set(x, Y + 6, z, st(ModBlocks.BLACK_LACQUER));
        for (int x = -1; x <= 1; x++) {
            for (int y = Y + 1; y <= Y + 2; y++) {
                set(x, y, z, st(ModBlocks.TORII_GATE).setValue(ToriiGateBlock.ACTIVE, false));
                m.gateBlocks.add(new BlockPos(x, y, z));
            }
            set(x, Y + 3, z, facing(ModBlocks.SHIMENAWA, Direction.SOUTH));
        }
        // the small shrine behind it
        fill(-1, Y + 1, -12, 1, Y + 1, -12, Blocks.STONE_BRICKS.defaultBlockState());
        set(0, Y + 2, -12, Blocks.CHISELED_STONE_BRICKS.defaultBlockState());
        for (int x = -1; x <= 1; x++) {
            set(x, Y + 3, -12, Blocks.DARK_OAK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.NORTH));
        }
        set(0, Y + 2, -11, facing(ModBlocks.SHIMENAWA, Direction.SOUTH));
    }

    private void well(int cx, int cz) {
        set(cx, Y, cz, facing(ModBlocks.WELL, Direction.SOUTH));
        set(cx, Y - 1, cz, Blocks.WATER.defaultBlockState());
        m.well = new BlockPos(cx, Y, cz);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dz == 0) continue;
                set(cx + dx, Y + 1, cz + dz, Blocks.MOSSY_COBBLESTONE_SLAB.defaultBlockState());
            }
        }
        for (int[] p : new int[][]{{-1, -1}, {1, 1}}) {
            fill(cx + p[0], Y + 1, cz + p[1], cx + p[0], Y + 3, cz + p[1], Blocks.SPRUCE_FENCE.defaultBlockState());
        }
        fill(cx - 1, Y + 4, cz - 1, cx + 1, Y + 4, cz + 1, Blocks.DARK_OAK_SLAB.defaultBlockState());
        set(cx, Y + 3, cz, Blocks.CHAIN.defaultBlockState());
    }

    private void deadTree(int x, int z) {
        BlockState log = Blocks.STRIPPED_DARK_OAK_LOG.defaultBlockState();
        fill(x, Y + 1, z, x, Y + 6, z, log);
        int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {-1, -1}};
        for (int[] d : dirs) {
            if (rand.nextFloat() < 0.35f) continue;
            int len = 2 + rand.nextInt(3);
            int y = Y + 4 + rand.nextInt(3);
            for (int k = 1; k <= len; k++) {
                set(x + d[0] * k, y + (k > 2 ? 1 : 0), z + d[1] * k,
                        k == len ? Blocks.DARK_OAK_FENCE.defaultBlockState() : Blocks.STRIPPED_DARK_OAK_LOG.defaultBlockState()
                                .setValue(RotatedPillarBlock.AXIS, d[0] != 0 ? Direction.Axis.X : Direction.Axis.Z));
            }
            if (rand.nextBoolean()) {
                set(x + d[0] * len, y + (len > 2 ? 0 : -1), z + d[1] * len, Blocks.HANGING_ROOTS.defaultBlockState());
            }
        }
        set(x, Y + 3, z + 1, facing(ModBlocks.SHIMENAWA, Direction.SOUTH));
        set(x, Y + 1, z + 1, Blocks.DEAD_BUSH.defaultBlockState());
    }

    private void pond(int cx, int cz) {
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                double e = (dx * dx) / 9.0 + (dz * dz) / 4.0;
                if (e <= 1.0) {
                    set(cx + dx, Y, cz + dz, Blocks.WATER.defaultBlockState());
                    set(cx + dx, Y - 1, cz + dz, Blocks.MUD.defaultBlockState());
                    set(cx + dx, Y + 1, cz + dz, Blocks.AIR.defaultBlockState());
                } else if (e <= 1.6) {
                    set(cx + dx, Y, cz + dz, Blocks.MOSSY_COBBLESTONE.defaultBlockState());
                }
            }
        }
    }

    // ================================================================== kura (storehouse)
    private void buildKura() {
        int x1 = 6, x2 = 12, z1 = -12, z2 = -6;
        Room kura = new Room(100, Room.Type.KURA, x1 + 1, z1 + 1, x2 - 1, z2 - 1);
        m.kura = kura;
        for (int x = x1; x <= x2; x++) {
            for (int z = z1; z <= z2; z++) {
                boolean wall = x == x1 || x == x2 || z == z1 || z == z2;
                set(x, Y, z, st(ModBlocks.OLD_PLANKS));
                if (wall) {
                    set(x, Y + 1, z, Blocks.POLISHED_DEEPSLATE.defaultBlockState());
                    fill(x, Y + 2, z, x, Y + 4, z, st(ModBlocks.KURA_WALL));
                }
                set(x, CEIL, z, Blocks.DARK_OAK_PLANKS.defaultBlockState());
            }
        }
        for (int x = x1 - 1; x <= x2 + 1; x++) {
            for (int z = z1 - 1; z <= z2 + 1; z++) {
                int d = Math.max(Math.abs(x - 9), Math.abs(z - (z1 + z2) / 2));
                int y = CEIL + 1 + (4 - d);
                if (d == 0) {
                    setUpdate(x, y, z, Blocks.DEEPSLATE_TILES.defaultBlockState());
                    continue;
                }
                Direction out;
                int dx = x - 9, dz = z - (z1 + z2) / 2;
                if (Math.abs(dx) > Math.abs(dz)) out = dx > 0 ? Direction.WEST : Direction.EAST;
                else out = dz > 0 ? Direction.NORTH : Direction.SOUTH;
                setUpdate(x, y, z, Blocks.DEEPSLATE_TILE_STAIRS.defaultBlockState().setValue(StairBlock.FACING, out));
            }
        }
        fill(x1 + 1, Y + 1, z1 + 1, x2 - 1, Y + 4, z2 - 1, Blocks.AIR.defaultBlockState());
        int dxDoor = 9;
        door(dxDoor, z2, Direction.SOUTH, ModBlocks.KURA_DOOR.get(), false);
        set(dxDoor, Y + 3, z2, Blocks.POLISHED_DEEPSLATE.defaultBlockState());
        m.kuraDoor = new BlockPos(dxDoor, Y + 1, z2);
        // inside: shelves, chests and the bell on its pedestal
        for (int x = x1 + 1; x <= x2 - 1; x++) {
            set(x, Y + 1, z1 + 1, Blocks.BARREL.defaultBlockState());
            if (rand.nextBoolean()) set(x, Y + 2, z1 + 1, Blocks.BARREL.defaultBlockState());
        }
        set(x1 + 1, Y + 1, z2 - 2, Blocks.CHEST.defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, Direction.EAST));
        set(x2 - 1, Y + 1, z2 - 2, Blocks.CHEST.defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, Direction.WEST));
        set(x1 + 1, Y + 4, z1 + 1, Blocks.COBWEB.defaultBlockState());
        m.kuraBell = new BlockPos(9, Y + 1, z1 + 2);
        set(m.kuraBell, st(ModBlocks.SUZU_DAI).setValue(SuzuDaiBlock.HAS_BELL, true));
    }

    // ================================================================== items, notes, bells, dolls
    private void distributeItems() {
        Room safe = m.safeRoom;
        // three free bells in rooms far from the lamplight room
        List<Info> candidates = new ArrayList<>();
        for (Info i : infos) {
            Room.Type t = i.room.type;
            if (t == Room.Type.BEDROOM || t == Room.Type.WASHITSU || t == Room.Type.STORAGE || t == Room.Type.STUDY
                    || t == Room.Type.CHILD || t == Room.Type.BATH || t == Room.Type.KITCHEN) {
                candidates.add(i);
            }
        }
        candidates.sort((a, b) -> Double.compare(dist(b.room, safe), dist(a.room, safe)));
        List<Info> far = new ArrayList<>(candidates.subList(0, Math.min(9, candidates.size())));
        Collections.shuffle(far, rand);
        Set<Direction> usedSides = new HashSet<>();
        int placed = 0;
        for (Info i : far) {
            if (placed >= 3) break;
            if (usedSides.contains(i.back) && far.size() - far.indexOf(i) > 3 - placed) continue;
            int[] c = freeCell(i, 2, 2, i.w() - 3, i.d() - 3);
            if (c == null) continue;
            BlockPos p = at(i, c[0], c[1], 1);
            put(i, c[0], c[1], 1, st(ModBlocks.SUZU_DAI).setValue(SuzuDaiBlock.HAS_BELL, true));
            m.bellSpots.add(p);
            usedSides.add(i.back);
            placed++;
        }
        // loot in drawers
        List<Long> drawers = new ArrayList<>(m.tansuLoot.keySet());
        Collections.sort(drawers);
        Collections.shuffle(drawers, rand);
        List<Long> nonSafe = new ArrayList<>();
        Long safeDrawer = null;
        Long kitchenDrawer = null;
        Long childDrawer = null;
        for (Long l : drawers) {
            BlockPos p = BlockPos.of(l);
            Room r = m.roomAt(p.getX() + 0.5, p.getZ() + 0.5);
            if (r == safe) safeDrawer = l;
            else {
                if (r != null && r.type == Room.Type.KITCHEN && kitchenDrawer == null) kitchenDrawer = l;
                else if (r != null && r.type == Room.Type.CHILD && childDrawer == null) childDrawer = l;
                else nonSafe.add(l);
            }
        }
        if (safeDrawer != null) m.tansuLoot.put(safeDrawer, "lamp_oil,matches,ofuda");
        if (kitchenDrawer != null) m.tansuLoot.put(kitchenDrawer, "matches,lamp_oil");
        if (childDrawer != null) m.tansuLoot.put(childDrawer, "ohajiki,ohajiki,ohajiki,doll_head");
        List<String> loot = new ArrayList<>(List.of("kura_key", "shio", "shio", "lamp_oil", "lamp_oil", "ohajiki,ohajiki",
                "omamori", "ofuda", "scare", "hair", "scare", "lamp_oil"));
        if (childDrawer == null) loot.add(0, "doll_head");
        if (kitchenDrawer == null) loot.add(0, "matches");
        for (int k = 0; k < nonSafe.size(); k++) {
            m.tansuLoot.put(nonSafe.get(k), k < loot.size() ? loot.get(k) : (rand.nextBoolean() ? "" : "scare"));
        }
        // notes in rooms of the right kind
        placeNote(Room.Type.STUDY, Notes.MOTHER);
        placeNote(Room.Type.DOLLS, Notes.DOLLS);
        placeNote(Room.Type.CHILD, Notes.CHILD);
        placeNote(rand.nextBoolean() ? Room.Type.KITCHEN : Room.Type.STUDY, Notes.CANDLES);
        placeNote(Room.Type.WASHITSU, Notes.WARDS);
        placeNote(Room.Type.BEDROOM, Notes.MAID_2);
        placeNote(Room.Type.BUTSUMA, Notes.PRIEST);
        // notes in the corridor near the lamplight room
        BlockPos o = m.safeOutside;
        BlockPos c1 = new BlockPos(o.getX() + 3, Y + 1, o.getZ());
        set(c1, facing(ModBlocks.LETTER, Direction.EAST));
        m.letters.put(c1.asLong(), Notes.CLOSET);
        // dolls (entities)
        for (BlockPos p : m.dollSpawns) {
            BlockPos pos = p;
            entityJobs.add(() -> spawnDoll(pos, false));
        }
        for (BlockPos p : m.decorDolls) {
            BlockPos pos = p;
            entityJobs.add(() -> spawnDoll(pos, false));
        }
        entityJobs.add(this::spawnBride);
    }

    private void placeNote(Room.Type type, int note) {
        for (Info i : infos) {
            if (i.room.type == type) {
                letter(i, note);
                return;
            }
        }
        // no such room: corridor
        BlockPos p = new BlockPos(16, Y + 1, -12 + note * 2);
        set(p, facing(ModBlocks.LETTER, Direction.WEST));
        m.letters.put(p.asLong(), note);
    }

    private static double dist(Room a, Room b) {
        return a.center().distSqr(b.center());
    }

    private void spawnDoll(BlockPos pos, boolean decoration) {
        Ichimatsu doll = ModEntities.ICHIMATSU.get().create(level);
        if (doll == null) return;
        doll.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, rand.nextFloat() * 360.0f, 0.0f);
        doll.setHome(pos, decoration);
        level.addFreshEntity(doll);
    }

    private void spawnBride() {
        Hanayome bride = ModEntities.HANAYOME.get().create(level);
        if (bride == null) return;
        BlockPos p = m.brideStart;
        bride.moveTo(p.getX() + 0.5, p.getY(), p.getZ() + 0.5, 0.0f, 0.0f);
        level.addFreshEntity(bride);
    }

    // ================================================================== waypoints for the bride
    private void waypoints() {
        for (Info i : infos) {
            if (i.room.type == Room.Type.SAFE) continue;
            if (i.room.type == Room.Type.WEDDING && i != side(0, 1)) continue;
            BlockPos c = i.room.type == Room.Type.WEDDING ? new BlockPos(0, Y + 1, -22) : i.room.center();
            m.waypoints.add(c);
        }
        for (int a = -16; a <= 16; a += 8) {
            m.waypoints.add(new BlockPos(a, Y + 1, -16));
            m.waypoints.add(new BlockPos(a, Y + 1, 16));
            m.waypoints.add(new BlockPos(-16, Y + 1, a));
            m.waypoints.add(new BlockPos(16, Y + 1, a));
            m.darkSpots.add(new BlockPos(a + 2, Y + 1, -16));
            m.darkSpots.add(new BlockPos(16, Y + 1, a - 2));
            m.darkSpots.add(new BlockPos(-16, Y + 1, a + 2));
        }
        m.waypoints.removeIf(p -> m.safeBox != null && m.safeBox.inflate(1.0).contains(p.getX() + 0.5, p.getY(), p.getZ() + 0.5));
    }
}
