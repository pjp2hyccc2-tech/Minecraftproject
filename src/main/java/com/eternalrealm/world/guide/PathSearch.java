package com.eternalrealm.world.guide;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.PriorityQueue;

/**
 * A walking route over blocks for the quest guide's yellow dots: A* over the places a player can stand, stepping up one
 * block, dropping down up to three, swimming, never cutting corners. It runs a few hundred nodes at a time
 * ({@link #step}) so the client can spread it over ticks, keeps to {@code radius} blocks around the start (the client
 * only knows the chunks it has loaded), and when the goal is farther away it ends at the reachable place closest to it
 * ({@link Status#PARTIAL}): the player follows that and the search starts again from further on. Pure.
 */
public final class PathSearch {
    /** Where a player can stand, and what it costs. */
    public interface Terrain {
        /** Room for a player with their feet in (x, y, z): a floor to stand on (or water to swim in) and head room. */
        boolean standable(int x, int y, int z);

        /** The block at (x, y, z) lets a body through (air, plants, open doors, water...). */
        boolean open(int x, int y, int z);

        /** Extra cost of standing at (x, y, z): 0 on plain ground, more in water; negative = never (lava, fire). */
        double penalty(int x, int y, int z);
    }

    public enum Status {
        /** Still searching: call {@link #step} again. */
        RUNNING,
        /** The route reaches the goal. */
        FOUND,
        /** The goal is out of reach for now; the route ends at the closest place to it. */
        PARTIAL,
        /** Not even the start is a place to stand. */
        FAILED
    }

    public record Node(int x, int y, int z) {
    }

    private static final int[][] DIRECTIONS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {1, -1}, {-1, 1}, {-1, -1}};
    private static final int MAX_DROP = 3;
    private static final double STEP_UP_COST = 0.6;
    private static final double DROP_COST = 0.25;

    private static final class Open {
        final int x;
        final int y;
        final int z;
        double g;
        double f;
        Open parent;
        boolean closed;

        Open(int x, int y, int z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }
    }

    private final Terrain terrain;
    private final Node start;
    private final double goalX;
    private final double goalZ;
    private final double goalY;
    private final double reach;
    private final int radius;
    private final int maxNodes;
    private final Map<Long, Open> nodes = new HashMap<>();
    private final PriorityQueue<Open> frontier = new PriorityQueue<>((a, b) -> Double.compare(a.f, b.f));
    private Status status = Status.RUNNING;
    private Open best;
    private Open end;
    private int expanded;

    /**
     * @param goalY  the goal's height, or {@link Double#NaN} when unknown (only the horizontal distance counts)
     * @param reach  how close (horizontally, in blocks) counts as there
     * @param radius how far from the start (horizontally) the search may go
     * @param maxNodes how many places it may look at in all
     */
    public PathSearch(Terrain terrain, Node start, double goalX, double goalY, double goalZ, double reach, int radius,
                      int maxNodes) {
        this.terrain = Objects.requireNonNull(terrain, "terrain");
        this.start = Objects.requireNonNull(start, "start");
        this.goalX = goalX;
        this.goalY = goalY;
        this.goalZ = goalZ;
        this.reach = reach;
        this.radius = radius;
        this.maxNodes = maxNodes;
        if (!terrain.standable(start.x(), start.y(), start.z())) {
            status = Status.FAILED;
            return;
        }
        Open first = new Open(start.x(), start.y(), start.z());
        first.f = heuristic(first.x, first.y, first.z);
        nodes.put(key(first.x, first.y, first.z), first);
        frontier.add(first);
        best = first;
    }

    public Status status() {
        return status;
    }

    public int expanded() {
        return expanded;
    }

    /** Looks at up to {@code budget} more places. */
    public Status step(int budget) {
        int left = budget;
        while (status == Status.RUNNING && left-- > 0) {
            Open current = frontier.poll();
            if (current == null || expanded >= maxNodes) {
                end = best;
                status = Status.PARTIAL;
                break;
            }
            if (current.closed) {
                continue;
            }
            current.closed = true;
            expanded++;
            if (atGoal(current)) {
                end = current;
                status = Status.FOUND;
                break;
            }
            if (horizontal(current.x, current.z) < horizontal(best.x, best.z) - 1e-9) {
                best = current;
            }
            if (Math.abs(current.x - start.x()) > radius || Math.abs(current.z - start.z()) > radius) {
                continue; // at the edge of what the client knows: reachable, but not expanded further
            }
            expand(current);
        }
        return status;
    }

    /** The route from the start to the goal (or to the closest place), once the search has ended. */
    public List<Node> path() {
        if (end == null) {
            return List.of();
        }
        List<Node> out = new ArrayList<>();
        for (Open node = end; node != null; node = node.parent) {
            out.add(new Node(node.x, node.y, node.z));
        }
        Collections.reverse(out);
        return out;
    }

    private void expand(Open current) {
        for (int[] direction : DIRECTIONS) {
            int dx = direction[0];
            int dz = direction[1];
            boolean diagonal = dx != 0 && dz != 0;
            int nx = current.x + dx;
            int nz = current.z + dz;
            int y = current.y;
            if (diagonal) {
                // only on level ground, and never through a corner
                if (!terrain.standable(nx, y, nz) || !corner(current.x + dx, y, current.z)
                        || !corner(current.x, y, current.z + dz)) {
                    continue;
                }
                visit(current, nx, y, nz, Math.sqrt(2.0));
                continue;
            }
            if (terrain.standable(nx, y, nz)) {
                visit(current, nx, y, nz, 1.0);
            } else if (terrain.standable(nx, y + 1, nz) && terrain.open(current.x, y + 2, current.z)) {
                visit(current, nx, y + 1, nz, 1.0 + STEP_UP_COST);
            } else if (body(nx, y, nz)) {
                for (int drop = 1; drop <= MAX_DROP; drop++) {
                    if (terrain.standable(nx, y - drop, nz)) {
                        visit(current, nx, y - drop, nz, 1.0 + DROP_COST * drop);
                        break;
                    }
                    if (!terrain.open(nx, y - drop, nz)) {
                        break;
                    }
                }
            }
        }
    }

    private boolean body(int x, int y, int z) {
        return terrain.open(x, y, z) && terrain.open(x, y + 1, z);
    }

    /** A corner cut by a diagonal step: room for the body (over a pit, or standing there on a carpet). */
    private boolean corner(int x, int y, int z) {
        return body(x, y, z) || terrain.standable(x, y, z);
    }

    private void visit(Open from, int x, int y, int z, double cost) {
        double penalty = terrain.penalty(x, y, z);
        if (penalty < 0) {
            return;
        }
        double g = from.g + cost + penalty;
        long key = key(x, y, z);
        Open node = nodes.get(key);
        if (node == null) {
            node = new Open(x, y, z);
            nodes.put(key, node);
        } else if (node.closed || g >= node.g) {
            return;
        }
        node.g = g;
        node.f = g + heuristic(x, y, z);
        node.parent = from;
        frontier.add(node);
    }

    private boolean atGoal(Open node) {
        return horizontal(node.x, node.z) <= reach && (Double.isNaN(goalY) || Math.abs(node.y - goalY) <= 4.0);
    }

    private double horizontal(int x, int z) {
        return Math.hypot(x + 0.5 - goalX, z + 0.5 - goalZ);
    }

    private double heuristic(int x, int y, int z) {
        return horizontal(x, z);
    }

    static long key(int x, int y, int z) {
        return ((long) x & 0x3FFFFFFL) << 38 | ((long) z & 0x3FFFFFFL) << 12 | (y & 0xFFFL);
    }

    /**
     * Points along a route every {@code spacing} blocks (through the centres of the blocks, at feet height): where the
     * yellow dots go.
     */
    public static List<double[]> dots(List<Node> path, double spacing) {
        List<double[]> out = new ArrayList<>();
        if (path.isEmpty()) {
            return out;
        }
        double carry = 0.0;
        double[] previous = centre(path.get(0));
        out.add(previous);
        for (int i = 1; i < path.size(); i++) {
            double[] next = centre(path.get(i));
            double length = Math.sqrt(Math.pow(next[0] - previous[0], 2) + Math.pow(next[1] - previous[1], 2)
                    + Math.pow(next[2] - previous[2], 2));
            double at = spacing - carry;
            while (at <= length) {
                double t = at / length;
                out.add(new double[]{previous[0] + (next[0] - previous[0]) * t,
                        previous[1] + (next[1] - previous[1]) * t, previous[2] + (next[2] - previous[2]) * t});
                at += spacing;
            }
            carry = length - (at - spacing);
            previous = next;
        }
        return out;
    }

    private static double[] centre(Node node) {
        return new double[]{node.x() + 0.5, node.y(), node.z() + 0.5};
    }
}
