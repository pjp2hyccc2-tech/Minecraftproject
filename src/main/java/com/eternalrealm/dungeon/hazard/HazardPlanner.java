package com.eternalrealm.dungeon.hazard;

import com.eternalrealm.dungeon.BlockBox;
import com.eternalrealm.dungeon.DungeonLayout;
import com.eternalrealm.dungeon.RoomLayout;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Random;

/**
 * Places the traps of an instance from its layout. Deterministic: the same layout (definition and seed) always gets the
 * same traps, like the layout itself ({@link Random} has a fixed algorithm).
 *
 * <p>Rules: traps only in the corridors and the combat rooms, never in the entrance hall (where the party gathers) or
 * the boss arena (the boss has its own attacks). A corridor long enough gets one or two traps of the theme's corridor
 * kinds; a combat room one (two in large rooms) of its room kinds, at least {@link #ROOM_MARGIN} blocks from the walls
 * so the doors stay clear. At most one targeted trap per corridor or room.</p>
 */
public final class HazardPlanner {
    /** Corridors shorter than this (between the two walls) get no trap. */
    public static final int MIN_CORRIDOR_LENGTH = 8;
    /** Corridors at least this long get two fixed traps. */
    public static final int TWO_TRAP_CORRIDOR_LENGTH = 18;
    /** Interior area from which a room gets two traps. */
    public static final int TWO_TRAP_ROOM_AREA = 17 * 17;
    /** Free blocks between a fixed room trap's marker and the walls. */
    public static final int ROOM_MARGIN = 3;
    private static final long SALT = 0x5452_4150_5321L;

    private HazardPlanner() {
    }

    /** The traps of {@code layout}; empty for themes without traps (the final raid). */
    public static List<HazardSite> plan(DungeonLayout layout) {
        Objects.requireNonNull(layout, "layout");
        List<HazardKind> kinds = HazardKind.of(layout.theme());
        if (kinds.isEmpty() || layout.rooms().isEmpty()) {
            return List.of();
        }
        List<HazardKind> corridorKinds = kinds.stream().filter(kind -> kind.placement().corridors()).toList();
        List<HazardKind> roomKinds = kinds.stream().filter(kind -> kind.placement().rooms()).toList();
        Random random = new Random(layout.seed() * 0x2545F4914F6CDD1DL ^ layout.id().hashCode() ^ SALT);
        List<HazardSite> sites = new ArrayList<>();
        if (!corridorKinds.isEmpty()) {
            for (BlockBox corridor : layout.corridors()) {
                corridorSites(random, corridor, corridorKinds, sites);
            }
        }
        if (!roomKinds.isEmpty()) {
            for (RoomLayout room : layout.rooms()) {
                roomSites(random, room.bounds().interior(), roomKinds, sites);
            }
        }
        return List.copyOf(sites);
    }

    // ------------------------------------------------------------------ corridors

    /** A corridor's walkable strip: along X or Z, {@code length} blocks from {@code start}, centred on a lane. */
    record Passage(boolean alongX, int start, int length, int lane, BlockBox zone) {
        double yaw() {
            return alongX ? 270.0 : 0.0; // facing +x (east) or +z (south)
        }

        double centreX(double along) {
            return alongX ? start + along : lane + 0.5;
        }

        double centreZ(double along) {
            return alongX ? lane + 0.5 : start + along;
        }
    }

    /** The passage of a corridor box (walls included), or null if it is too short to tell or to hold a trap. */
    static Passage passage(BlockBox corridor) {
        boolean alongX = corridor.sizeX() > corridor.sizeZ();
        int length = alongX ? corridor.sizeX() : corridor.sizeZ();
        int width = alongX ? corridor.sizeZ() : corridor.sizeX();
        if (length < MIN_CORRIDOR_LENGTH || width < 3 || corridor.sizeY() < 3) {
            return null;
        }
        BlockBox zone = alongX
                ? new BlockBox(corridor.minX(), corridor.minY() + 1, corridor.minZ() + 1, corridor.maxX(),
                corridor.maxY() - 1, corridor.maxZ() - 1)
                : new BlockBox(corridor.minX() + 1, corridor.minY() + 1, corridor.minZ(), corridor.maxX() - 1,
                corridor.maxY() - 1, corridor.maxZ());
        int lane = alongX ? (corridor.minZ() + corridor.maxZ()) / 2 : (corridor.minX() + corridor.maxX()) / 2;
        return new Passage(alongX, alongX ? corridor.minX() : corridor.minZ(), length, lane, zone);
    }

    private static void corridorSites(Random random, BlockBox corridor, List<HazardKind> kinds, List<HazardSite> sites) {
        Passage passage = passage(corridor);
        if (passage == null) {
            return;
        }
        HazardKind kind = kinds.get(random.nextInt(kinds.size()));
        double width = (passage.alongX() ? passage.zone().sizeZ() : passage.zone().sizeX());
        int delay = 20 + random.nextInt(30);
        if (kind.trigger() == HazardKind.Trigger.TARGETED) {
            double middle = passage.length() / 2.0;
            sites.add(new HazardSite(sites.size(), kind, passage.zone(), passage.centreX(middle), passage.centreZ(middle),
                    passage.yaw(), kind.a(), kind.b(), delay));
            return;
        }
        int count = passage.length() >= TWO_TRAP_CORRIDOR_LENGTH ? 2 : 1;
        double segment = passage.length() / (double) count;
        for (int i = 0; i < count; i++) {
            double along = segment * (i + 0.5) + (random.nextInt(3) - 1);
            along = Math.max(2.0, Math.min(passage.length() - 2.0, along));
            double a = kind.a();
            double b = kind.b();
            if (kind.shape() == com.eternalrealm.combat.Telegraph.Kind.RECT) {
                a = Math.max(3.0, Math.min(kind.a(), segment - 1.0));
                b = width;
            } else if (kind.shape() == com.eternalrealm.combat.Telegraph.Kind.SQUARE) {
                a = Math.min(kind.a(), width / 2.0);
            }
            sites.add(new HazardSite(sites.size(), kind, passage.zone(), passage.centreX(along),
                    passage.centreZ(along), passage.yaw(), a, b, delay + i * 17));
        }
    }

    // ------------------------------------------------------------------ rooms

    private static void roomSites(Random random, BlockBox inside, List<HazardKind> kinds, List<HazardSite> sites) {
        int area = inside.sizeX() * inside.sizeZ();
        int count = Math.min(kinds.size(), area >= TWO_TRAP_ROOM_AREA ? 2 : 1);
        List<HazardKind> pool = new ArrayList<>(kinds);
        List<HazardSite> placed = new ArrayList<>();
        boolean targeted = false;
        for (int i = 0; i < count && !pool.isEmpty(); i++) {
            HazardKind kind = pool.remove(random.nextInt(pool.size()));
            if (kind.trigger() == HazardKind.Trigger.TARGETED) {
                if (targeted) {
                    continue;
                }
                targeted = true;
            }
            HazardSite site = roomSite(random, inside, kind, sites.size(), placed);
            if (site != null) {
                sites.add(site);
                placed.add(site);
            }
        }
    }

    private static HazardSite roomSite(Random random, BlockBox inside, HazardKind kind, int index,
                                       List<HazardSite> placed) {
        double centreX = inside.minX() + inside.sizeX() / 2.0;
        double centreZ = inside.minZ() + inside.sizeZ() / 2.0;
        double halfMin = Math.min(inside.sizeX(), inside.sizeZ()) / 2.0;
        int delay = 20 + random.nextInt(40);
        switch (kind.shape()) {
            case RING -> {
                double outer = Math.min(kind.b(), halfMin - 1.0);
                double inner = Math.max(1.0, Math.min(kind.a(), outer - 2.0));
                return outer <= inner ? null
                        : new HazardSite(index, kind, inside, centreX, centreZ, 0.0, inner, outer, delay);
            }
            case CROSS -> {
                double arm = Math.min(kind.a(), halfMin - 1.0);
                return new HazardSite(index, kind, inside, centreX, centreZ, random.nextBoolean() ? 0.0 : 45.0, arm,
                        kind.b(), delay);
            }
            default -> {
                if (kind.trigger() == HazardKind.Trigger.TARGETED) {
                    return new HazardSite(index, kind, inside, centreX, centreZ, 0.0, kind.a(), kind.b(), delay);
                }
                double reach = kind.shape() == com.eternalrealm.combat.Telegraph.Kind.SQUARE
                        ? kind.a() * Math.sqrt(2.0) : kind.a();
                double margin = reach + ROOM_MARGIN;
                double spanX = inside.sizeX() - 2.0 * margin;
                double spanZ = inside.sizeZ() - 2.0 * margin;
                if (spanX < 0.0 || spanZ < 0.0) {
                    return new HazardSite(index, kind, inside, centreX, centreZ, 0.0, kind.a(), kind.b(), delay);
                }
                HazardSite best = null;
                double bestGap = -1.0;
                for (int attempt = 0; attempt < 16; attempt++) {
                    double x = Math.floor(inside.minX() + margin + random.nextDouble() * spanX) + 0.5;
                    double z = Math.floor(inside.minZ() + margin + random.nextDouble() * spanZ) + 0.5;
                    double gap = Double.MAX_VALUE;
                    for (HazardSite other : placed) {
                        gap = Math.min(gap, Math.hypot(other.x() - x, other.z() - z) - other.a() - kind.a());
                    }
                    if (gap > bestGap) {
                        bestGap = gap;
                        best = new HazardSite(index, kind, inside, x, z, 0.0, kind.a(), kind.b(), delay);
                    }
                    if (gap >= 3.0) {
                        break;
                    }
                }
                return best;
            }
        }
    }
}
