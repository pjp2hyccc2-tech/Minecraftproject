package com.eternalrealm.dungeon.decor;

import com.eternalrealm.dungeon.BlockBox;
import com.eternalrealm.dungeon.BlockFill;
import com.eternalrealm.dungeon.BlockPoint;
import com.eternalrealm.dungeon.BlockRole;
import com.eternalrealm.dungeon.DungeonLayout;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The voxel model the decoration is planned on: the layout's build plan replayed into a grid of {@link BlockRole}s, the
 * decoration written over it, and reservations that keep gameplay space free.
 *
 * <p>Reservations: {@link #HARD} cells (door openings and the lanes in front of them, monster spawn points, where
 * players arrive and revive) take no decoration at all; {@link #SOFT} cells (corridor passages, the boss arena) only
 * take blocks that nothing collides with (banners, torches, carpets). Every write goes through {@link #set}, which
 * refuses what the reservations forbid, so a style cannot put a barrel on a spawn point by mistake.</p>
 *
 * <p>Outside the layout's boxes the decoration only builds into boxes it {@link #claim claimed} (writes anywhere else
 * are refused); a claim is refused if it touches another space of the layout or another claim.</p>
 */
final class Canvas {
    static final int MIN_Y = -3;
    static final int MAX_Y = DungeonLayout.MAX_HEIGHT - 1;
    static final int SIZE = DungeonLayout.FOOTPRINT;
    static final int HEIGHT = MAX_Y - MIN_Y + 1;
    static final byte FREE = 0;
    static final byte SOFT = 1;
    static final byte HARD = 2;
    static final String AIR = DecorBlocks.AIR;
    private static final BlockRole[] ROLES = BlockRole.values();
    private static final byte AIR_CODE = (byte) (BlockRole.AIR.ordinal() + 1);
    private static final byte DOOR_CODE = (byte) (BlockRole.DOOR.ordinal() + 1);
    private static final byte LIGHT_CODE = (byte) (BlockRole.LIGHT.ordinal() + 1);

    final DungeonLayout layout;
    private final byte[] base;
    private final byte[] reserve;
    private final LinkedHashMap<Integer, String> decor = new LinkedHashMap<>();
    private final Map<Integer, String> nbt = new HashMap<>();
    private final List<BlockBox> occupied;
    private final List<BlockBox> claims = new ArrayList<>();
    private final List<BlockBox> fluids = new ArrayList<>();
    private final List<Integer> emitted = new ArrayList<>();
    /** Cells inside the layout's boxes or a claim: the only cells the decoration may write. */
    private final java.util.BitSet coveredCells;
    /** Writes refused by a reservation (a style tried to build where it must not). */
    int refused;

    Canvas(DungeonLayout layout) {
        this.layout = layout;
        int cells = SIZE * SIZE * HEIGHT;
        base = new byte[cells];
        reserve = new byte[cells];
        for (BlockFill fill : layout.buildPlan()) {
            BlockBox box = fill.box();
            byte code = (byte) (fill.role().ordinal() + 1);
            for (int y = box.minY(); y <= box.maxY(); y++) {
                for (int z = box.minZ(); z <= box.maxZ(); z++) {
                    int start = index(box.minX(), y, z);
                    Arrays.fill(base, start, start + box.sizeX(), code);
                }
            }
        }
        occupied = layout.occupiedBoxes();
        coveredCells = new java.util.BitSet(cells);
        for (BlockBox box : occupied) {
            mark(box);
        }
    }

    private void mark(BlockBox box) {
        for (int y = Math.max(MIN_Y, box.minY()); y <= Math.min(MAX_Y, box.maxY()); y++) {
            for (int z = Math.max(0, box.minZ()); z <= Math.min(SIZE - 1, box.maxZ()); z++) {
                int start = index(Math.max(0, box.minX()), y, z);
                coveredCells.set(start, start + Math.min(SIZE - 1, box.maxX()) - Math.max(0, box.minX()) + 1);
            }
        }
    }

    static boolean inside(int x, int y, int z) {
        return x >= 0 && x < SIZE && z >= 0 && z < SIZE && y >= MIN_Y && y <= MAX_Y;
    }

    static int index(int x, int y, int z) {
        return ((y - MIN_Y) * SIZE + z) * SIZE + x;
    }

    static int x(int index) {
        return index % SIZE;
    }

    static int z(int index) {
        return (index / SIZE) % SIZE;
    }

    static int y(int index) {
        return index / (SIZE * SIZE) + MIN_Y;
    }

    // ------------------------------------------------------------------ reading

    /** The layout's role at a position, or null outside every box. */
    BlockRole role(int x, int y, int z) {
        if (!inside(x, y, z)) {
            return null;
        }
        byte code = base[index(x, y, z)];
        return code == 0 ? null : ROLES[code - 1];
    }

    /** What will stand at a position: the decoration, else {@code #role} for the layout's block, else air. */
    String state(int x, int y, int z) {
        return inside(x, y, z) ? state(index(x, y, z)) : AIR;
    }

    private String state(int index) {
        String placed = decor.get(index);
        if (placed != null) {
            return placed;
        }
        byte code = base[index];
        return code == 0 || code == AIR_CODE ? AIR : "#" + ROLES[code - 1].name().toLowerCase(Locale.ROOT);
    }

    boolean isAir(int x, int y, int z) {
        return state(x, y, z).equals(AIR);
    }

    /** A full block (walls, floors, full decoration blocks). */
    boolean solid(int x, int y, int z) {
        String state = state(x, y, z);
        if (state.startsWith("#")) {
            return !state.equals("#door");
        }
        return DecorBlocks.shape(state) == DecorBlocks.Shape.FULL;
    }

    /** Whether a walker is stopped here (sealed doors count as open). */
    boolean blocks(int x, int y, int z) {
        String state = state(x, y, z);
        if (state.startsWith("#")) {
            return !state.equals("#door");
        }
        return DecorBlocks.blocks(state);
    }

    private boolean opaque(int index) {
        String state = state(index);
        if (state.startsWith("#")) {
            return !state.equals("#door");
        }
        return DecorBlocks.opaque(state);
    }

    byte reserved(int x, int y, int z) {
        return inside(x, y, z) ? reserve[index(x, y, z)] : HARD;
    }

    /** A feet position: two cells nothing collides with above something to stand on. */
    boolean standable(int x, int y, int z) {
        return !blocks(x, y, z) && !blocks(x, y + 1, z) && blocks(x, y - 1, z);
    }

    // ------------------------------------------------------------------ writing

    void reserve(BlockBox box, byte level) {
        for (int y = Math.max(MIN_Y, box.minY()); y <= Math.min(MAX_Y, box.maxY()); y++) {
            for (int z = Math.max(0, box.minZ()); z <= Math.min(SIZE - 1, box.maxZ()); z++) {
                for (int x = Math.max(0, box.minX()); x <= Math.min(SIZE - 1, box.maxX()); x++) {
                    int i = index(x, y, z);
                    if (reserve[i] < level) {
                        reserve[i] = level;
                    }
                }
            }
        }
    }

    /** Whether {@code state} may be written at a position (inside the grid and allowed by its reservation). */
    boolean allowed(int x, int y, int z, String state) {
        if (!inside(x, y, z) || !coveredCells.get(index(x, y, z))) {
            return false;
        }
        byte level = reserve[index(x, y, z)];
        boolean air = state.equals(AIR);
        if (level == HARD) {
            return air && isAir(x, y, z);
        }
        return level != SOFT || air || !DecorBlocks.blocks(state);
    }

    /** Writes a block unless a reservation forbids it. @return whether it was written */
    boolean set(int x, int y, int z, String state) {
        if (!allowed(x, y, z, state)) {
            refused++;
            return false;
        }
        int i = index(x, y, z);
        decor.remove(i);
        if (state.equals(state(i))) {
            return true; // the layout already has it (air on air, #wall on wall)
        }
        decor.put(i, state);
        if (DecorBlocks.light(state) > 0) {
            emitted.add(i);
        }
        return true;
    }

    /** Light sources written since the last call (for incremental light updates). */
    List<Integer> takeEmitted() {
        List<Integer> taken = new ArrayList<>(emitted);
        emitted.clear();
        return taken;
    }

    /** Writes a block only where there is air now (and the reservation allows it). */
    boolean setOnAir(int x, int y, int z, String state) {
        return isAir(x, y, z) && set(x, y, z, state);
    }

    /** Writes a block only over a full block (to restyle walls and floors without filling openings). */
    boolean setOnSolid(int x, int y, int z, String state) {
        return solid(x, y, z) && set(x, y, z, state);
    }

    void fill(BlockBox box, String state) {
        for (int y = box.minY(); y <= box.maxY(); y++) {
            for (int z = box.minZ(); z <= box.maxZ(); z++) {
                for (int x = box.minX(); x <= box.maxX(); x++) {
                    set(x, y, z, state);
                }
            }
        }
    }

    void nbt(int x, int y, int z, String snbt) {
        if (inside(x, y, z)) {
            nbt.put(index(x, y, z), snbt);
        }
    }

    /** A group of blocks placed together or not at all (a prop never stands half-built). */
    Piece piece() {
        return new Piece();
    }

    final class Piece {
        private final List<int[]> positions = new ArrayList<>();
        private final List<String> states = new ArrayList<>();

        Piece add(int x, int y, int z, String state) {
            positions.add(new int[]{x, y, z});
            states.add(state);
            return this;
        }

        /** Places the piece if every block goes on air and is allowed. */
        boolean commit() {
            for (int i = 0; i < positions.size(); i++) {
                int[] p = positions.get(i);
                if (!isAir(p[0], p[1], p[2]) || !allowed(p[0], p[1], p[2], states.get(i))) {
                    return false;
                }
            }
            return force();
        }

        /** Places the piece over whatever is there, if every block is allowed. */
        boolean force() {
            for (int i = 0; i < positions.size(); i++) {
                int[] p = positions.get(i);
                if (!allowed(p[0], p[1], p[2], states.get(i))) {
                    return false;
                }
            }
            for (int i = 0; i < positions.size(); i++) {
                int[] p = positions.get(i);
                set(p[0], p[1], p[2], states.get(i));
            }
            return true;
        }
    }

    // ------------------------------------------------------------------ claims

    /** Whether {@code box} (grown by {@code margin}) stays inside the footprint and away from the layout and claims. */
    boolean free(BlockBox box, int margin, BlockBox... allowed) {
        BlockBox grown = new BlockBox(box.minX() - margin, box.minY(), box.minZ() - margin, box.maxX() + margin,
                box.maxY(), box.maxZ() + margin);
        if (box.minX() < 1 || box.minZ() < 1 || box.maxX() > SIZE - 2 || box.maxZ() > SIZE - 2 || box.minY() < MIN_Y
                || box.maxY() > MAX_Y) {
            return false;
        }
        for (BlockBox other : occupied) {
            if (grown.intersects(other) && !listed(other, allowed)) {
                return false;
            }
        }
        for (BlockBox other : claims) {
            if (grown.intersects(other) && !listed(other, allowed)) {
                return false;
            }
        }
        return true;
    }

    /** Claims a box to build into outside the layout. @return false (nothing claimed) if it is not {@link #free} */
    boolean claim(BlockBox box, BlockBox... allowed) {
        if (!free(box, 0, allowed)) {
            return false;
        }
        claims.add(box);
        mark(box);
        return true;
    }

    private static boolean listed(BlockBox box, BlockBox[] allowed) {
        for (BlockBox candidate : allowed) {
            if (candidate.equals(box)) {
                return true;
            }
        }
        return false;
    }

    List<BlockBox> claims() {
        return List.copyOf(claims);
    }

    void fluid(BlockBox box) {
        fluids.add(box);
    }

    List<BlockBox> fluids() {
        return List.copyOf(fluids);
    }

    /** Whether a position lies in the layout's boxes or a claim (so clearing the instance removes it). */
    boolean covered(int x, int y, int z) {
        for (BlockBox box : occupied) {
            if (box.contains(x, y, z)) {
                return true;
            }
        }
        for (BlockBox box : claims) {
            if (box.contains(x, y, z)) {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------ connections

    /** Joins fences, panes, bars and walls to their neighbours (blocks are set without shape updates in game). */
    void connect() {
        for (Map.Entry<Integer, String> entry : decor.entrySet()) {
            String state = entry.getValue();
            String name = DecorBlocks.name(state);
            int kind = name.endsWith("_fence") ? 1 : name.endsWith("_pane") || name.equals("iron_bars") ? 2
                    : name.endsWith("_wall") ? 3 : 0;
            if (kind == 0) {
                continue;
            }
            int i = entry.getKey();
            int x = x(i);
            int y = y(i);
            int z = z(i);
            boolean north = joins(kind, name, state(x, y, z - 1));
            boolean east = joins(kind, name, state(x + 1, y, z));
            boolean south = joins(kind, name, state(x, y, z + 1));
            boolean west = joins(kind, name, state(x - 1, y, z));
            String id = DecorBlocks.id(state);
            if (kind == 3) {
                boolean straight = north && south && !east && !west || east && west && !north && !south;
                entry.setValue(id + "[east=" + side(east) + ",north=" + side(north) + ",south=" + side(south)
                        + ",up=" + !straight + ",waterlogged=false,west=" + side(west) + "]");
            } else {
                entry.setValue(id + "[east=" + east + ",north=" + north + ",south=" + south + ",waterlogged=false,west="
                        + west + "]");
            }
        }
    }

    private static String side(boolean joined) {
        return joined ? "low" : "none";
    }

    private static boolean joins(int kind, String name, String neighbour) {
        if (neighbour.startsWith("#")) {
            return true; // palette blocks are full blocks, sealed doors are bars or fences
        }
        String other = DecorBlocks.name(neighbour);
        boolean full = DecorBlocks.shape(neighbour) == DecorBlocks.Shape.FULL;
        return switch (kind) {
            case 1 -> full || other.endsWith("_fence_gate") || other.endsWith("_fence")
                    && other.startsWith("nether_brick") == name.startsWith("nether_brick");
            case 2 -> full || other.endsWith("_pane") || other.equals("iron_bars") || other.endsWith("_wall");
            default -> full || other.endsWith("_wall") || other.endsWith("_pane") || other.equals("iron_bars")
                    || other.endsWith("_fence_gate");
        };
    }

    // ------------------------------------------------------------------ light

    /** Block light of every cell (an approximation of the game's: full opaque blocks stop it, the rest passes). */
    byte[] light(int paletteLight) {
        byte[] level = new byte[base.length];
        IntQueue queue = new IntQueue();
        for (int i = 0; i < base.length; i++) {
            if (base[i] == LIGHT_CODE && !decor.containsKey(i)) {
                level[i] = (byte) paletteLight;
                queue.add(i);
            }
        }
        for (Map.Entry<Integer, String> entry : decor.entrySet()) {
            int emission = DecorBlocks.light(entry.getValue());
            if (emission > 0) {
                level[entry.getKey()] = (byte) Math.max(level[entry.getKey()], emission);
                queue.add(entry.getKey());
            }
        }
        spread(level, queue);
        return level;
    }

    /** Adds one light source to a computed light field. */
    void addLight(byte[] level, int x, int y, int z, int emission) {
        int i = index(x, y, z);
        if (level[i] >= emission) {
            return;
        }
        level[i] = (byte) emission;
        IntQueue queue = new IntQueue();
        queue.add(i);
        spread(level, queue);
    }

    private void spread(byte[] level, IntQueue queue) {
        int layer = SIZE * SIZE;
        while (!queue.isEmpty()) {
            int i = queue.poll();
            int next = level[i] - 1;
            if (next <= 0) {
                continue;
            }
            int x = x(i);
            int z = z(i);
            int yIndex = i / layer;
            if (x > 0) {
                push(level, queue, i - 1, next);
            }
            if (x < SIZE - 1) {
                push(level, queue, i + 1, next);
            }
            if (z > 0) {
                push(level, queue, i - SIZE, next);
            }
            if (z < SIZE - 1) {
                push(level, queue, i + SIZE, next);
            }
            if (yIndex > 0) {
                push(level, queue, i - layer, next);
            }
            if (yIndex < HEIGHT - 1) {
                push(level, queue, i + layer, next);
            }
        }
    }

    private void push(byte[] level, IntQueue queue, int j, int value) {
        if (level[j] < value && !opaque(j)) {
            level[j] = (byte) value;
            queue.add(j);
        }
    }

    static int light(byte[] level, int x, int y, int z) {
        return inside(x, y, z) ? level[index(x, y, z)] : 0;
    }

    // ------------------------------------------------------------------ output

    /** The decoration in placement order: structure (full blocks, carved air), then furniture, then attachments. */
    List<DecorBlock> blocks() {
        List<DecorBlock> structure = new ArrayList<>();
        List<DecorBlock> furniture = new ArrayList<>();
        List<DecorBlock> attached = new ArrayList<>();
        for (Map.Entry<Integer, String> entry : decor.entrySet()) {
            int i = entry.getKey();
            String state = entry.getValue();
            DecorBlock block = new DecorBlock(x(i), y(i), z(i), state, nbt.get(i));
            DecorBlocks.Shape shape = state.startsWith("#") ? DecorBlocks.Shape.FULL : DecorBlocks.shape(state);
            if (state.equals(AIR) || shape == DecorBlocks.Shape.FULL) {
                structure.add(block);
            } else if (shape == DecorBlocks.Shape.NONE) {
                attached.add(block);
            } else {
                furniture.add(block);
            }
        }
        List<DecorBlock> all = new ArrayList<>(structure);
        all.addAll(furniture);
        all.addAll(attached);
        return all;
    }

    int decorCount() {
        return decor.size();
    }

    /** Positions reachable on foot from {@code start} (sealed doors count as open); see {@link #standable}. */
    java.util.BitSet walk(BlockPoint start) {
        java.util.BitSet seen = new java.util.BitSet(base.length);
        if (!standable(start.x(), start.y(), start.z())) {
            return seen;
        }
        IntQueue queue = new IntQueue();
        int first = index(start.x(), start.y(), start.z());
        seen.set(first);
        queue.add(first);
        int[][] steps = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        while (!queue.isEmpty()) {
            int i = queue.poll();
            int x = x(i);
            int y = y(i);
            int z = z(i);
            for (int[] step : steps) {
                int nx = x + step[0];
                int nz = z + step[1];
                for (int dy = 1; dy >= -3; dy--) {
                    int ny = y + dy;
                    if (dy == 1 && blocks(x, y + 2, z)) {
                        continue; // no room to jump
                    }
                    if (inside(nx, ny, nz) && standable(nx, ny, nz)) {
                        int j = index(nx, ny, nz);
                        if (!seen.get(j)) {
                            seen.set(j);
                            queue.add(j);
                        }
                        break;
                    }
                    if (dy <= 0 && blocks(nx, ny, nz)) {
                        break; // a wall: no falling through it
                    }
                }
            }
        }
        return seen;
    }

    /** Growable ring buffer of ints (positions). */
    private static final class IntQueue {
        private int[] items = new int[1 << 12];
        private int head;
        private int tail;
        private int size;

        void add(int value) {
            if (size == items.length) {
                int[] grown = new int[items.length * 2];
                for (int k = 0; k < size; k++) {
                    grown[k] = items[(head + k) % items.length];
                }
                items = grown;
                head = 0;
                tail = size;
            }
            items[tail] = value;
            tail = (tail + 1) % items.length;
            size++;
        }

        int poll() {
            int value = items[head];
            head = (head + 1) % items.length;
            size--;
            return value;
        }

        boolean isEmpty() {
            return size == 0;
        }
    }
}
