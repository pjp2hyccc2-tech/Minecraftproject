package com.eternalrealm.world.field;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.List;
import java.util.function.LongPredicate;

/**
 * Which chunks of アストリア are made ahead of the players, and in what order (the owner, 2026-11-13:
 * 「12チャンクごとくらいに自動読み込み(通知無し)で入れて動作を軽くする」).
 *
 * <p>The land is fixed ({@link FieldLayout}), so every chunk of it can be generated once, in the background: first the
 * band just beyond what each player sees (up to {@link #LOOKAHEAD} chunks further, the chunks where the player is
 * heading first), then the rest of the map ring by ring from the capital. A chunk made earlier only has to be read
 * from disk when a player arrives, which is much lighter than generating terrain, decoration and set pieces on the
 * spot. Pure: the forge side ({@code world.forge.ChunkPregen}) asks the game for the chunks and reports them here.</p>
 *
 * <p>Chunk coordinates throughout (block coordinate {@code >> 4}). Not thread-safe (the server thread owns it).</p>
 */
public final class ChunkPregenPlan {
    /** Chunks beyond the view distance generated ahead of each player. */
    public static final int LOOKAHEAD = 12;
    /**
     * Chunks beyond the map's edge that belong to the map: a player at the edge (pushed back 24 blocks out) still
     * sees past it.
     */
    public static final int MARGIN = 8;
    /** Above this speed (blocks per tick) a player is heading somewhere: the band ahead comes first. */
    public static final double MOVING = 0.05;
    /** Data version of {@link #toLongs()} (with the map's {@link FieldLayout#VERSION}). */
    public static final int FORMAT = 1;

    private final int minX;
    private final int minZ;
    private final int maxX;
    private final int maxZ;
    private final int width;
    private final BitSet done;
    private int doneCount;
    // the background spiral: ring r around the capital's chunk, step k on that ring
    private final int centerX;
    private final int centerZ;
    private final int lastRing;
    private int ring;
    private int step;
    private boolean spiralFinished;

    /** A player of アストリア: block position and velocity in blocks per tick. */
    public record Viewer(double x, double z, double vx, double vz) {
        int chunkX() {
            return (int) Math.floor(x) >> 4;
        }

        int chunkZ() {
            return (int) Math.floor(z) >> 4;
        }
    }

    /** The whole map plus {@link #MARGIN}, spiralling out from the capital's chunk. */
    public static ChunkPregenPlan astoria() {
        return new ChunkPregenPlan((FieldLayout.MIN_X >> 4) - MARGIN, (FieldLayout.MIN_Z >> 4) - MARGIN,
                (FieldLayout.MAX_X >> 4) + MARGIN, (FieldLayout.MAX_Z >> 4) + MARGIN, 0, 0);
    }

    /** A rectangle of chunks (inclusive corners) and the chunk the spiral starts from. */
    public ChunkPregenPlan(int minX, int minZ, int maxX, int maxZ, int centerX, int centerZ) {
        if (maxX < minX || maxZ < minZ) {
            throw new IllegalArgumentException("empty area");
        }
        if ((long) (maxX - minX + 1) * (maxZ - minZ + 1) > 4_000_000L) {
            throw new IllegalArgumentException("area too large");
        }
        this.minX = minX;
        this.minZ = minZ;
        this.maxX = maxX;
        this.maxZ = maxZ;
        this.width = maxX - minX + 1;
        this.done = new BitSet(total());
        this.centerX = centerX;
        this.centerZ = centerZ;
        this.lastRing = Math.max(Math.max(Math.abs(minX - centerX), Math.abs(maxX - centerX)),
                Math.max(Math.abs(minZ - centerZ), Math.abs(maxZ - centerZ)));
    }

    public int total() {
        return width * (maxZ - minZ + 1);
    }

    public int doneCount() {
        return doneCount;
    }

    public boolean complete() {
        return doneCount >= total();
    }

    public boolean contains(int chunkX, int chunkZ) {
        return chunkX >= minX && chunkX <= maxX && chunkZ >= minZ && chunkZ <= maxZ;
    }

    public boolean isDone(int chunkX, int chunkZ) {
        return contains(chunkX, chunkZ) && done.get(index(chunkX, chunkZ));
    }

    /** A chunk exists (generated or read): it never has to be made again. @return whether it was new to the plan */
    public boolean markDone(int chunkX, int chunkZ) {
        if (!contains(chunkX, chunkZ)) {
            return false;
        }
        int index = index(chunkX, chunkZ);
        if (done.get(index)) {
            return false;
        }
        done.set(index);
        doneCount++;
        return true;
    }

    private int index(int chunkX, int chunkZ) {
        return (chunkZ - minZ) * width + (chunkX - minX);
    }

    // ------------------------------------------------------------------ ahead of the players

    /**
     * Chunks to make ahead of the players: those in the band from just beyond {@code viewDistance} out to
     * {@link #LOOKAHEAD} chunks further (square distance, like the game's view), not made yet and not {@code skip}ped,
     * nearest first to the point each player is heading for (the middle of the band ahead of them; the player
     * themselves when standing). At most {@code max}; each chunk once.
     */
    public long[] ahead(List<Viewer> viewers, int viewDistance, int max, LongPredicate skip) {
        if (viewers.isEmpty() || max <= 0) {
            return new long[0];
        }
        int inner = Math.max(0, viewDistance);
        int outer = inner + LOOKAHEAD;
        int capacity = Math.min(max, 4096);
        long[] best = new long[capacity];
        double[] scores = new double[capacity];
        int count = 0;
        for (int v = 0; v < viewers.size(); v++) {
            Viewer viewer = viewers.get(v);
            int cx = viewer.chunkX();
            int cz = viewer.chunkZ();
            double speed = Math.hypot(viewer.vx(), viewer.vz());
            double aimX = viewer.x();
            double aimZ = viewer.z();
            if (speed > MOVING) {
                double reach = (inner + LOOKAHEAD / 2.0) * 16.0;
                aimX += viewer.vx() / speed * reach;
                aimZ += viewer.vz() / speed * reach;
            }
            for (int dz = -outer; dz <= outer; dz++) {
                for (int dx = -outer; dx <= outer; dx++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) <= inner) {
                        continue; // the game is making these for the player already
                    }
                    int x = cx + dx;
                    int z = cz + dz;
                    if (!contains(x, z) || done.get(index(x, z)) || seenByAnother(viewers, v, x, z, inner)) {
                        continue;
                    }
                    long key = key(x, z);
                    if (skip != null && skip.test(key)) {
                        continue;
                    }
                    double score = Math.hypot(x * 16.0 + 8.0 - aimX, z * 16.0 + 8.0 - aimZ);
                    count = insert(best, scores, count, key, score);
                }
            }
        }
        return Arrays.copyOf(best, count);
    }

    /** Whether an earlier viewer already offered this chunk, or another viewer sees it (the game makes it). */
    private static boolean seenByAnother(List<Viewer> viewers, int self, int x, int z, int inner) {
        for (int i = 0; i < viewers.size(); i++) {
            if (i == self) {
                continue;
            }
            Viewer other = viewers.get(i);
            int distance = Math.max(Math.abs(x - other.chunkX()), Math.abs(z - other.chunkZ()));
            if (distance <= inner || i < self && distance <= inner + LOOKAHEAD) {
                return true;
            }
        }
        return false;
    }

    /** Keeps the {@code best.length} lowest scores, sorted ascending. @return the new count */
    private static int insert(long[] best, double[] scores, int count, long key, double score) {
        if (count == best.length && score >= scores[count - 1]) {
            return count;
        }
        int at = count == best.length ? count - 1 : count; // full: the worst one makes room
        while (at > 0 && scores[at - 1] > score) {
            best[at] = best[at - 1];
            scores[at] = scores[at - 1];
            at--;
        }
        best[at] = key;
        scores[at] = score;
        return Math.min(count + 1, best.length);
    }

    // ------------------------------------------------------------------ the rest of the map

    /**
     * The next chunks of the background pass (ring by ring around the capital), not made yet and not {@code skip}ped.
     * Looks at no more than {@code budget} positions per call and never at the same position twice in one call; once
     * the spiral is through, the next call starts again from the capital for chunks that could not be made the first
     * time.
     */
    public long[] nextInSpiral(int max, int budget, LongPredicate skip) {
        List<Long> found = new ArrayList<>();
        if (spiralFinished) {
            spiralFinished = false;
            ring = 0;
            step = 0;
        }
        int looked = 0;
        while (found.size() < max && looked < budget && !complete() && !spiralFinished) {
            int x;
            int z;
            if (ring == 0) {
                x = centerX;
                z = centerZ;
            } else {
                int side = step / (2 * ring);
                int along = step % (2 * ring);
                switch (side) {
                    case 0 -> {
                        x = centerX - ring + along;
                        z = centerZ - ring;
                    }
                    case 1 -> {
                        x = centerX + ring;
                        z = centerZ - ring + along;
                    }
                    case 2 -> {
                        x = centerX + ring - along;
                        z = centerZ + ring;
                    }
                    default -> {
                        x = centerX - ring;
                        z = centerZ + ring - along;
                    }
                }
            }
            advance();
            looked++;
            if (!contains(x, z) || done.get(index(x, z))) {
                continue;
            }
            long key = key(x, z);
            if (skip == null || !skip.test(key)) {
                found.add(key);
            }
        }
        long[] keys = new long[found.size()];
        for (int i = 0; i < keys.length; i++) {
            keys[i] = found.get(i);
        }
        return keys;
    }

    private void advance() {
        step++;
        if (ring == 0 || step >= 8 * ring) {
            ring++;
            step = 0;
            if (ring > lastRing) {
                spiralFinished = true;
            }
        }
    }

    // ------------------------------------------------------------------ keys and saving

    /** The same packing as the game's {@code ChunkPos.asLong}. */
    public static long key(int chunkX, int chunkZ) {
        return (long) chunkX & 0xFFFFFFFFL | ((long) chunkZ & 0xFFFFFFFFL) << 32;
    }

    public static int keyX(long key) {
        return (int) key;
    }

    public static int keyZ(long key) {
        return (int) (key >>> 32);
    }

    /** What to save: the map version, this format, the area, then the bits. */
    public long[] toLongs() {
        long[] bits = done.toLongArray();
        long[] out = new long[5 + bits.length];
        out[0] = FieldLayout.VERSION;
        out[1] = FORMAT;
        out[2] = key(minX, minZ);
        out[3] = key(maxX, maxZ);
        out[4] = bits.length;
        System.arraycopy(bits, 0, out, 5, bits.length);
        return out;
    }

    /**
     * Restores what {@link #toLongs()} saved. Data of another map version, format or area is ignored (the chunks
     * are looked at again: a chunk that exists is only read, which is quick). @return whether anything was restored
     */
    public boolean restore(long[] saved) {
        if (saved == null || saved.length < 5 || saved[0] != FieldLayout.VERSION || saved[1] != FORMAT
                || saved[2] != key(minX, minZ) || saved[3] != key(maxX, maxZ) || saved[4] != saved.length - 5) {
            return false;
        }
        BitSet bits = BitSet.valueOf(Arrays.copyOfRange(saved, 5, saved.length));
        if (bits.length() > total()) {
            return false;
        }
        done.clear();
        done.or(bits);
        doneCount = done.cardinality();
        return true;
    }
}
