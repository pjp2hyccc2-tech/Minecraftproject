package com.eternalrealm.dungeon.decor;

import com.eternalrealm.dungeon.BlockBox;
import com.eternalrealm.dungeon.BlockPoint;
import com.eternalrealm.dungeon.DungeonLayout;
import com.eternalrealm.dungeon.DungeonTheme;
import com.eternalrealm.dungeon.RoomLayout;
import com.eternalrealm.dungeon.decor.Geometry.Link;
import com.eternalrealm.dungeon.decor.Geometry.Side;
import com.eternalrealm.dungeon.decor.Spaces.Kind;
import com.eternalrealm.dungeon.decor.Spaces.Space;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Random;

/**
 * Plans the interior decoration of an instance layout: themed architecture (wall bands, pilasters, cornices, arches,
 * vaults or beams, floor inlays), props, light and set pieces, so each {@link DungeonTheme} reads as a hand-made
 * dungeon instead of palette boxes (docs/modules/dungeons.md).
 *
 * <p>Gameplay stays exactly as the layout made it: the decoration never touches door openings, the lanes in front of
 * them, the monster spawn points, the corridors' passages or the boss arena's floor and air (see {@link Canvas}), and
 * it builds outside the layout's boxes only into boxes it claimed (returned as {@link DecorPlan#extraBoxes()} so the
 * instance clears them). Deterministic: it draws from its own {@link Random} seeded from the layout, so the same
 * layout always gets the same decoration and the layout itself is unchanged.</p>
 *
 * <p>What it adds: the entrance hall opened up with wings (exit portal, revival monument, notice board), up to two
 * set-piece rooms off the combat rooms, the corridor before the arena rebuilt as an antechamber with a gate, and the
 * light every fighting space needs ({@link #TARGET_LIGHT} on every floor block someone can stand on).</p>
 */
public final class DungeonDecor {
    /** Block light every walkable floor block of a fighting space, the hall and the set pieces gets at least. */
    public static final int TARGET_LIGHT = 7;
    private static final long SALT = 0x4445_434F_5253L;

    private DungeonDecor() {
    }

    /** The decoration of {@code layout}. */
    public static DecorPlan plan(DungeonLayout layout) {
        return new Planner(layout).run();
    }

    /** The planner with its canvas after the run (tests inspect the voxels). */
    static Planner planned(DungeonLayout layout) {
        Planner planner = new Planner(layout);
        planner.run();
        return planner;
    }

    /** Light level of the theme's palette light block (the layout's ceiling lights). */
    static int paletteLight(DungeonTheme theme) {
        return theme == DungeonTheme.DARK_TOWER ? 10 : 15;
    }

    static final class Planner {
        final DungeonLayout layout;
        final Canvas c;
        final Random random;
        final Style style;
        final List<Link> links;
        final List<Space> spaces = new ArrayList<>();
        final List<Spaces.SetPiece> setPieces = new ArrayList<>();
        final List<List<BlockBox>> roomOpenings = new ArrayList<>();
        final List<List<BlockBox>> roomLanes = new ArrayList<>();
        Spaces.Hall hall;
        Spaces.Arena arena;
        BlockBox antechamber;
        DecorPlan plan;

        Planner(DungeonLayout layout) {
            this.layout = Objects.requireNonNull(layout, "layout");
            this.c = new Canvas(layout);
            this.random = new Random(layout.seed() * 0x5DEECE66DL ^ layout.id().hashCode() ^ SALT);
            this.style = Styles.of(layout.theme(), c, random);
            this.links = Geometry.links(layout);
        }

        DecorPlan run() {
            if (plan != null) {
                return plan;
            }
            for (int i = 0; i < layout.rooms().size(); i++) {
                Link entry = links.get(i);
                Link exit = links.get(i + 1);
                roomOpenings.add(new ArrayList<>(List.of(entry.toOpening(), exit.fromOpening())));
                roomLanes.add(new ArrayList<>(List.of(entry.toLane(), exit.fromLane())));
            }
            reserve();
            hall = buildHall();
            Link last = links.get(links.size() - 1);
            arena = new Spaces.Arena(layout.bossArena(), last.toOpening(),
                    Geometry.sideOf(layout.bossArena(), last.toOpening()));
            antechamber = style.antechamber(last, arena);
            planSetPieces();
            style.hall(hall);
            for (int i = 0; i + 1 < links.size(); i++) {
                style.corridor(links.get(i));
            }
            for (int i = 0; i < layout.rooms().size(); i++) {
                RoomLayout room = layout.rooms().get(i);
                style.room(new Spaces.Room(i, room.bounds(), roomOpenings.get(i), roomLanes.get(i),
                        room.spawnPoints(), i == layout.rooms().size() - 1, links.get(i + 1).fromOpening()));
            }
            for (Spaces.SetPiece piece : setPieces) {
                style.setPiece(piece);
            }
            style.arena(arena);
            collectSpaces(last);
            lightUp();
            c.connect();
            BlockPoint revival = style.revival != null ? style.revival : layout.entry();
            List<BlockBox> pieces = new ArrayList<>();
            for (Spaces.SetPiece piece : setPieces) {
                pieces.add(piece.box());
            }
            plan = new DecorPlan(layout.theme(), c.blocks(), c.claims(), c.fluids(), revival, style.revivalYaw,
                    pieces, antechamber, hall.box());
            return plan;
        }

        // ------------------------------------------------------------ reservations

        private void reserve() {
            for (Link link : links) {
                c.reserve(link.fromOpening(), Canvas.HARD);
                c.reserve(link.toOpening(), Canvas.HARD);
                c.reserve(link.fromLane(), Canvas.HARD);
                c.reserve(link.toLane(), Canvas.HARD);
                c.reserve(link.passage(), Canvas.SOFT);
            }
            for (RoomLayout room : layout.rooms()) {
                BlockBox inside = room.bounds().interior();
                for (BlockPoint p : room.spawnPoints()) {
                    c.reserve(new BlockBox(Math.max(inside.minX(), p.x() - 1), 1, Math.max(inside.minZ(), p.z() - 1),
                            Math.min(inside.maxX(), p.x() + 2), 5, Math.min(inside.maxZ(), p.z() + 2)), Canvas.HARD);
                }
            }
            BlockPoint entry = layout.entry();
            c.reserve(new BlockBox(entry.x() - 1, entry.y(), entry.z() - 1, entry.x() + 1, entry.y() + 2,
                    entry.z() + 1), Canvas.HARD);
            c.reserve(layout.bossArena().interior(), Canvas.SOFT);
        }

        // ------------------------------------------------------------ entrance hall

        /** Opens the layout's hall into wings on the three sides away from the entrance door. */
        private Spaces.Hall buildHall() {
            BlockBox core = layout.entranceHall();
            Link first = links.get(0);
            Side front = Geometry.sideOf(core, first.fromOpening());
            boolean raid = layout.rooms().isEmpty();
            int[][] options = raid ? new int[][]{{9, 6, 12}, {7, 5, 11}, {5, 3, 10}, {2, 1, 9}}
                    : new int[][]{{6, 4, 10}, {5, 3, 10}, {4, 2, 9}, {2, 1, 9}};
            BlockBox box = core;
            for (int[] option : options) {
                BlockBox grown = grow(core, front, option[0], option[1], option[2]);
                if (c.claim(grown, core)) {
                    box = grown;
                    break;
                }
            }
            if (box != core) {
                shell(box, core, front);
            }
            return new Spaces.Hall(box, core, front, first.fromOpening(), first.fromLane(), layout.entry());
        }

        private static BlockBox grow(BlockBox core, Side front, int back, int side, int height) {
            int minX = core.minX() - (front == Side.EAST ? back : front == Side.WEST ? 0 : side);
            int maxX = core.maxX() + (front == Side.WEST ? back : front == Side.EAST ? 0 : side);
            int minZ = core.minZ() - (front == Side.SOUTH ? back : front == Side.NORTH ? 0 : side);
            int maxZ = core.maxZ() + (front == Side.NORTH ? back : front == Side.SOUTH ? 0 : side);
            return new BlockBox(minX, core.minY(), minZ, maxX, core.minY() + height - 1, maxZ);
        }

        /** Walls, floor and ceiling of the grown hall; the old hall's walls inside it are carved away. */
        private void shell(BlockBox box, BlockBox core, Side front) {
            for (int x = box.minX(); x <= box.maxX(); x++) {
                for (int y = box.minY(); y <= box.maxY(); y++) {
                    for (int z = box.minZ(); z <= box.maxZ(); z++) {
                        boolean boundary = x == box.minX() || x == box.maxX() || y == box.minY() || y == box.maxY()
                                || z == box.minZ() || z == box.maxZ();
                        boolean frontPlane = front.dx != 0 ? x == (front == Side.EAST ? core.maxX() : core.minX())
                                : z == (front == Side.SOUTH ? core.maxZ() : core.minZ());
                        boolean oldFrontWall = frontPlane && y <= core.maxY() && core.contains(x, y, z);
                        if (boundary) {
                            if (!oldFrontWall) {
                                c.set(x, y, z, y == box.minY() ? "#floor" : "#wall");
                            }
                        } else {
                            c.set(x, y, z, Canvas.AIR);
                        }
                    }
                }
            }
        }

        // ------------------------------------------------------------ set pieces

        private record Candidate(int room, Side side, int spur, int size) {
        }

        /** Up to two side rooms off the combat rooms, each in free space next to a wall without an opening. */
        private void planSetPieces() {
            int rooms = layout.rooms().size();
            if (rooms == 0) {
                return;
            }
            int wanted = rooms >= 3 ? 2 : 1;
            List<Integer> order = new ArrayList<>();
            for (int i = 0; i < rooms; i++) {
                order.add(i);
            }
            java.util.Collections.shuffle(order, random);
            order.remove(Integer.valueOf(rooms - 1));
            order.add(rooms - 1); // the boss door's room last
            for (int roomIndex : order) {
                if (setPieces.size() >= wanted) {
                    return;
                }
                List<Candidate> candidates = new ArrayList<>();
                BlockBox b = layout.rooms().get(roomIndex).bounds();
                for (Side side : Side.values()) {
                    if (!Style.openingsOn(new Geometry.Wall(b, side), roomOpenings.get(roomIndex)).isEmpty()) {
                        continue;
                    }
                    for (int size : new int[]{17, 15, 13}) {
                        for (int spur = 3; spur <= 6; spur++) {
                            if (fits(b, side, spur, size)) {
                                candidates.add(new Candidate(roomIndex, side, spur, size));
                                break;
                            }
                        }
                    }
                }
                if (!candidates.isEmpty()) {
                    int best = candidates.stream().mapToInt(Candidate::size).max().orElse(13);
                    List<Candidate> biggest = candidates.stream().filter(k -> k.size() == best).toList();
                    build(biggest.get(random.nextInt(biggest.size())));
                }
            }
        }

        private static final int SET_PIECE_HEIGHT = 10;

        private boolean fits(BlockBox b, Side side, int spur, int size) {
            return c.free(spurBox(b, side, spur), 1, b) && c.free(pieceBox(b, side, spur, size), 2);
        }

        private static int lateral(BlockBox b, Side side) {
            return side.dx != 0 ? (b.minZ() + b.maxZ()) / 2 : (b.minX() + b.maxX()) / 2;
        }

        private static int wallOf(BlockBox b, Side side) {
            return switch (side) {
                case EAST -> b.maxX();
                case WEST -> b.minX();
                case SOUTH -> b.maxZ();
                case NORTH -> b.minZ();
            };
        }

        /** Box from {@code near} to {@code far} blocks beyond the wall, {@code half} to each side, y0..y1. */
        private static BlockBox beyond(BlockBox b, Side side, int near, int far, int half, int y0, int y1) {
            int wall = wallOf(b, side);
            int lat = lateral(b, side);
            int sign = side.dx + side.dz;
            int lo = Math.min(wall + sign * near, wall + sign * far);
            int hi = Math.max(wall + sign * near, wall + sign * far);
            return side.dx != 0 ? new BlockBox(lo, y0, lat - half, hi, y1, lat + half)
                    : new BlockBox(lat - half, y0, lo, lat + half, y1, hi);
        }

        private static BlockBox spurBox(BlockBox b, Side side, int spur) {
            return beyond(b, side, 1, spur, 2, 0, 5);
        }

        private static BlockBox pieceBox(BlockBox b, Side side, int spur, int size) {
            return beyond(b, side, spur + 1, spur + size, size / 2, 0, SET_PIECE_HEIGHT - 1);
        }

        private void build(Candidate k) {
            BlockBox room = layout.rooms().get(k.room()).bounds();
            BlockBox spur = spurBox(room, k.side(), k.spur());
            BlockBox box = pieceBox(room, k.side(), k.spur(), k.size());
            if (!c.claim(spur) || !c.claim(box)) {
                return;
            }
            // shells
            shellBox(spur);
            shellBox(box);
            BlockBox roomDoor = beyond(room, k.side(), 0, 0, 1, 1, Geometry.OPENING_TOP);
            BlockBox pieceDoor = beyond(room, k.side(), k.spur() + 1, k.spur() + 1, 1, 1, Geometry.OPENING_TOP);
            BlockBox passage = beyond(room, k.side(), 1, k.spur(), 1, 1, Geometry.OPENING_TOP);
            c.fill(roomDoor, Canvas.AIR);
            c.fill(pieceDoor, Canvas.AIR);
            c.fill(passage, Canvas.AIR);
            BlockBox roomLane = beyond(room, k.side(), -3, -1, 2, 1, Geometry.OPENING_TOP);
            BlockBox pieceLane = beyond(room, k.side(), k.spur() + 2, k.spur() + 4, 2, 1, Geometry.OPENING_TOP);
            c.reserve(roomDoor, Canvas.HARD);
            c.reserve(pieceDoor, Canvas.HARD);
            c.reserve(roomLane, Canvas.HARD);
            c.reserve(pieceLane, Canvas.HARD);
            c.reserve(passage, Canvas.SOFT);
            roomOpenings.get(k.room()).add(roomDoor);
            roomLanes.get(k.room()).add(roomLane);
            Side doorSide = k.side().opposite();
            String kind = style.setPieces[setPieces.size() % style.setPieces.length];
            Spaces.SetPiece piece = new Spaces.SetPiece(setPieces.size(), kind, box, pieceDoor, doorSide);
            setPieces.add(piece);
            Link link = new Link(-1, spur, room, box, k.side().dx, k.side().dz, lateral(room, k.side()), 2, passage,
                    roomDoor, pieceDoor, roomLane, pieceLane);
            style.corridor(link);
        }

        private void shellBox(BlockBox box) {
            for (int x = box.minX(); x <= box.maxX(); x++) {
                for (int y = box.minY(); y <= box.maxY(); y++) {
                    for (int z = box.minZ(); z <= box.maxZ(); z++) {
                        boolean boundary = x == box.minX() || x == box.maxX() || y == box.minY() || y == box.maxY()
                                || z == box.minZ() || z == box.maxZ();
                        if (boundary) {
                            c.set(x, y, z, y == box.minY() ? "#floor" : "#wall");
                        }
                    }
                }
            }
        }

        // ------------------------------------------------------------ light

        private void collectSpaces(Link last) {
            spaces.add(new Space(Kind.HALL, hall.box().interior(), hall.box().maxY()));
            for (RoomLayout room : layout.rooms()) {
                spaces.add(new Space(Kind.ROOM, room.bounds().interior(), ceilingOver(room.bounds())));
            }
            for (int i = 0; i + 1 < links.size(); i++) {
                spaces.add(new Space(Kind.CORRIDOR, links.get(i).passage(), Geometry.OPENING_TOP + 1));
            }
            spaces.add(new Space(antechamber != null ? Kind.ANTECHAMBER : Kind.CORRIDOR, last.passage(),
                    antechamber != null ? antechamber.maxY() : Geometry.OPENING_TOP + 1));
            spaces.add(new Space(Kind.ARENA, layout.bossArena().interior(), layout.bossArena().maxY()));
            for (Spaces.SetPiece piece : setPieces) {
                spaces.add(new Space(Kind.SET_PIECE, piece.box().interior(), piece.box().maxY()));
            }
        }

        private int ceilingOver(BlockBox room) {
            BlockPoint center = room.center(room.maxY());
            for (int y = room.maxY() + 3; y >= room.maxY(); y--) {
                if (c.solid(center.x(), y, center.z()) && !c.solid(center.x(), y - 1, center.z())) {
                    return y;
                }
            }
            return room.maxY();
        }

        /** The feet cell of column (x, z) in a space: the lowest standable cell of its first three levels. */
        int feet(Space space, int x, int z) {
            BlockBox in = space.inside();
            for (int y = in.minY(); y <= Math.min(in.maxY() - 1, in.minY() + 2); y++) {
                if (c.standable(x, y, z)) {
                    return y;
                }
            }
            return Integer.MIN_VALUE;
        }

        private void lightUp() {
            byte[] light = c.light(paletteLight(layout.theme()));
            c.takeEmitted();
            for (Space space : spaces) {
                BlockBox in = space.inside();
                for (int x = in.minX(); x <= in.maxX(); x++) {
                    for (int z = in.minZ(); z <= in.maxZ(); z++) {
                        int y = feet(space, x, z);
                        if (y == Integer.MIN_VALUE || Canvas.light(light, x, y, z) >= TARGET_LIGHT) {
                            continue;
                        }
                        if (style.lightUp(space, x, z)) {
                            absorb(light);
                        }
                        if (Canvas.light(light, x, y, z) < TARGET_LIGHT) {
                            hiddenLight(space, x, y, z, light);
                        }
                    }
                }
            }
        }

        private void absorb(byte[] light) {
            for (int i : c.takeEmitted()) {
                c.addLight(light, Canvas.x(i), Canvas.y(i), Canvas.z(i), DecorBlocks.light(c.state(Canvas.x(i),
                        Canvas.y(i), Canvas.z(i))));
            }
        }

        /**
         * Last resort: invisible light blocks (nothing collides with them) above or beside the dark spot, until it is lit
         * (a few tries: something opaque may stand between).
         */
        private void hiddenLight(Space space, int x, int y, int z, byte[] light) {
            int tries = 0;
            for (int reach = 0; reach <= 2; reach++) {
                for (int up = 1; up < space.ceiling() - y; up++) {
                    for (int dx = -reach; dx <= reach; dx++) {
                        for (int dz = -reach; dz <= reach; dz++) {
                            if (Math.max(Math.abs(dx), Math.abs(dz)) != reach
                                    || 15 - up - Math.abs(dx) - Math.abs(dz) < TARGET_LIGHT) {
                                continue;
                            }
                            if (c.isAir(x + dx, y + up, z + dz) && c.set(x + dx, y + up, z + dz,
                                    "minecraft:light[level=15,waterlogged=false]")) {
                                absorb(light);
                                if (Canvas.light(light, x, y, z) >= TARGET_LIGHT || ++tries >= 6) {
                                    return;
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
