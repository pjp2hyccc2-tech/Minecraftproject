package com.eternalrealm.dungeon.decor;

import com.eternalrealm.dungeon.BlockBox;
import com.eternalrealm.dungeon.DungeonLayout;

import java.util.ArrayList;
import java.util.List;

/**
 * What the decoration needs to know about a layout beyond its records: where each corridor meets its two spaces
 * (openings), the lanes in front of the openings, and the walls of a box as frames to build along.
 *
 * <p>Derived from the boxes with the generator's rules ({@code DungeonLayoutGenerator}: openings are the corridor's
 * passage width and {@value #OPENING_TOP} blocks high, lanes {@value #LANE_DEPTH} blocks deep and as wide as the
 * corridor). {@code DungeonDecorTest} checks the derived openings against the layout's doors.</p>
 */
final class Geometry {
    static final int OPENING_TOP = 4;
    static final int LANE_DEPTH = 4;

    private Geometry() {
    }

    /** One corridor and how it joins chain elements {@code index} and {@code index + 1}. */
    record Link(int index, BlockBox corridor, BlockBox from, BlockBox to, int dirX, int dirZ, int lateral, int half,
                BlockBox passage, BlockBox fromOpening, BlockBox toOpening, BlockBox fromLane, BlockBox toLane) {
        boolean alongX() {
            return dirX != 0;
        }

        /** First and last block of the corridor along its axis. */
        int start() {
            return alongX() ? corridor.minX() : corridor.minZ();
        }

        int end() {
            return alongX() ? corridor.maxX() : corridor.maxZ();
        }

        int length() {
            return end() - start() + 1;
        }

        /** World x/z of a point {@code along} the axis and {@code side} blocks off the centre line. */
        int x(int along, int side) {
            return alongX() ? along : lateral + side;
        }

        int z(int along, int side) {
            return alongX() ? lateral + side : along;
        }

        /** The facing pointing from the centre line to side {@code sign} (+1/-1). */
        String sideFacing(int sign) {
            return alongX() ? (sign > 0 ? "south" : "north") : (sign > 0 ? "east" : "west");
        }

        /** The facing of travel (from {@code from} to {@code to}). */
        String forward() {
            return dirX > 0 ? "east" : dirX < 0 ? "west" : dirZ > 0 ? "south" : "north";
        }

        /** Along-axis coordinate stepping towards {@code to}: +1 or -1. */
        int step() {
            return dirX != 0 ? dirX : dirZ;
        }
    }

    /** The links of a layout in walking order (hall to first room ... last room to arena). */
    static List<Link> links(DungeonLayout layout) {
        List<BlockBox> chain = layout.chain();
        List<Link> links = new ArrayList<>();
        for (int i = 0; i < layout.corridors().size(); i++) {
            links.add(link(i, layout.corridors().get(i), chain.get(i), chain.get(i + 1)));
        }
        return links;
    }

    private static Link link(int index, BlockBox corridor, BlockBox from, BlockBox to) {
        int dirX = to.minX() > from.maxX() ? 1 : to.maxX() < from.minX() ? -1 : 0;
        int dirZ = dirX != 0 ? 0 : to.minZ() > from.maxZ() ? 1 : -1;
        if (dirX != 0) {
            int lateral = (corridor.minZ() + corridor.maxZ()) / 2;
            int half = (corridor.sizeZ() - 1) / 2;
            int inner = half - 1;
            int fromWall = dirX > 0 ? from.maxX() : from.minX();
            int toWall = dirX > 0 ? to.minX() : to.maxX();
            return new Link(index, corridor, from, to, dirX, 0, lateral, half,
                    new BlockBox(corridor.minX(), 1, lateral - inner, corridor.maxX(), OPENING_TOP, lateral + inner),
                    new BlockBox(fromWall, 1, lateral - inner, fromWall, OPENING_TOP, lateral + inner),
                    new BlockBox(toWall, 1, lateral - inner, toWall, OPENING_TOP, lateral + inner),
                    span(fromWall - dirX, fromWall - dirX * LANE_DEPTH, lateral - half, lateral + half, true),
                    span(toWall + dirX, toWall + dirX * LANE_DEPTH, lateral - half, lateral + half, true));
        }
        int lateral = (corridor.minX() + corridor.maxX()) / 2;
        int half = (corridor.sizeX() - 1) / 2;
        int inner = half - 1;
        int fromWall = dirZ > 0 ? from.maxZ() : from.minZ();
        int toWall = dirZ > 0 ? to.minZ() : to.maxZ();
        return new Link(index, corridor, from, to, 0, dirZ, lateral, half,
                new BlockBox(lateral - inner, 1, corridor.minZ(), lateral + inner, OPENING_TOP, corridor.maxZ()),
                new BlockBox(lateral - inner, 1, fromWall, lateral + inner, OPENING_TOP, fromWall),
                new BlockBox(lateral - inner, 1, toWall, lateral + inner, OPENING_TOP, toWall),
                span(fromWall - dirZ, fromWall - dirZ * LANE_DEPTH, lateral - half, lateral + half, false),
                span(toWall + dirZ, toWall + dirZ * LANE_DEPTH, lateral - half, lateral + half, false));
    }

    private static BlockBox span(int depthA, int depthB, int lateralMin, int lateralMax, boolean alongX) {
        int low = Math.min(depthA, depthB);
        int high = Math.max(depthA, depthB);
        return alongX ? new BlockBox(low, 1, lateralMin, high, OPENING_TOP, lateralMax)
                : new BlockBox(lateralMin, 1, low, lateralMax, OPENING_TOP, high);
    }

    // ------------------------------------------------------------------ walls

    /** The four sides of a box, by the direction their wall faces outwards. */
    enum Side {
        NORTH(0, -1, "north"), SOUTH(0, 1, "south"), WEST(-1, 0, "west"), EAST(1, 0, "east");

        final int dx;
        final int dz;
        final String name;

        Side(int dx, int dz, String name) {
            this.dx = dx;
            this.dz = dz;
            this.name = name;
        }

        Side opposite() {
            return switch (this) {
                case NORTH -> SOUTH;
                case SOUTH -> NORTH;
                case WEST -> EAST;
                case EAST -> WEST;
            };
        }

        static Side of(int dx, int dz) {
            return dx > 0 ? EAST : dx < 0 ? WEST : dz > 0 ? SOUTH : NORTH;
        }

        /** Minecraft yaw of someone facing this way (0 = +z). */
        float yaw() {
            return switch (this) {
                case SOUTH -> 0.0F;
                case WEST -> 90.0F;
                case NORTH -> 180.0F;
                case EAST -> -90.0F;
            };
        }

        /** Banner/stairs rotation: 0 = south, 4 = west, 8 = north, 12 = east. */
        int rotation() {
            return switch (this) {
                case SOUTH -> 0;
                case WEST -> 4;
                case NORTH -> 8;
                case EAST -> 12;
            };
        }
    }

    /**
     * One wall of a box seen from inside: {@code a} runs along the wall over the interior span, {@code d} counts blocks
     * inwards from the wall plane (0 = the wall itself, 1 = the first block inside, -1 = just outside).
     */
    record Wall(BlockBox box, Side side) {
        boolean alongX() {
            return side == Side.NORTH || side == Side.SOUTH;
        }

        /** First and last interior coordinate along the wall. */
        int min() {
            return alongX() ? box.minX() + 1 : box.minZ() + 1;
        }

        int max() {
            return alongX() ? box.maxX() - 1 : box.maxZ() - 1;
        }

        int length() {
            return max() - min() + 1;
        }

        int mid() {
            return (min() + max()) / 2;
        }

        int x(int a, int d) {
            return switch (side) {
                case NORTH, SOUTH -> a;
                case WEST -> box.minX() + d;
                case EAST -> box.maxX() - d;
            };
        }

        int z(int a, int d) {
            return switch (side) {
                case WEST, EAST -> a;
                case NORTH -> box.minZ() + d;
                case SOUTH -> box.maxZ() - d;
            };
        }

        /** Facing away from the wall, into the room (wall torches, banners and signs on this wall face this way). */
        String in() {
            return side.opposite().name;
        }

        /** Facing towards the wall (stairs whose back leans on it). */
        String out() {
            return side.name;
        }

        /** Facing along the wall towards larger {@code a}. */
        String along() {
            return alongX() ? "east" : "south";
        }

        String alongBack() {
            return alongX() ? "west" : "north";
        }
    }

    static List<Wall> walls(BlockBox box) {
        List<Wall> walls = new ArrayList<>();
        for (Side side : Side.values()) {
            walls.add(new Wall(box, side));
        }
        return walls;
    }

    /** The side of {@code box} whose wall plane contains {@code opening}, or null. */
    static Side sideOf(BlockBox box, BlockBox opening) {
        if (opening.minX() == box.minX() && opening.maxX() == box.minX()) {
            return Side.WEST;
        }
        if (opening.minX() == box.maxX() && opening.maxX() == box.maxX()) {
            return Side.EAST;
        }
        if (opening.minZ() == box.minZ() && opening.maxZ() == box.minZ()) {
            return Side.NORTH;
        }
        if (opening.minZ() == box.maxZ() && opening.maxZ() == box.maxZ()) {
            return Side.SOUTH;
        }
        return null;
    }
}
