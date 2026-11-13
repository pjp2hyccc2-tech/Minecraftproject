package com.eternalrealm.client.guide;

import com.eternalrealm.client.ClientUiState;
import com.eternalrealm.config.ERClientConfig;
import com.eternalrealm.world.guide.PathSearch;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The quest guide on the client (クエストの道案内): the server says where the guided quest wants the player next (UI
 * channel {@value #CHANNEL}); this walks there over the loaded terrain ({@link PathSearch}, a little each tick) and keeps
 * the yellow dots of the route for the ground ({@link GuideRenderer}), the minimap and the label under it. A route that
 * cannot reach the place yet ends where it is closest; it is searched again as the player walks along it, and at once
 * when they stray from it. Client thread only.
 */
public final class GuideClient {
    public static final String CHANNEL = "quest_guide";
    /** How far around the start the route may go (blocks): about what the client has loaded. */
    private static final int RADIUS = 96;
    private static final int MAX_NODES = 30_000;
    /** Places looked at per tick (a millisecond or two). */
    private static final int BUDGET = 1_500;
    /** Blocks between two dots of the route. */
    public static final double SPACING = 1.25;
    /** Farther than this from every dot ahead: search again. */
    private static final double OFF_PATH = 4.0;
    /** Walking along: search again this often (ticks), to see further ahead. */
    private static final int REFRESH_TICKS = 60;
    /** Never search more often than this (ticks). */
    private static final int MIN_GAP_TICKS = 10;
    /** Closer than this to the place: no dots (the marker is enough). */
    private static final double ARRIVED = 3.0;

    private static JsonObject guide;
    private static String targetKey = "";
    private static PathSearch search;
    private static ClientTerrain terrain;
    private static List<double[]> dots = List.of();
    private static boolean reachesTarget;
    private static int sinceSearch = MIN_GAP_TICKS;
    private static BlockPos searchedFrom;
    private static Component label;

    private GuideClient() {
    }

    public static void accept(JsonObject data) {
        guide = data;
        label = null;
        String key = ClientUiState.stringOr(data, "dim", "") + "|" + ClientUiState.stringOr(data, "x", "") + "|"
                + ClientUiState.stringOr(data, "z", "") + "|" + bool(data, "active");
        if (!key.equals(targetKey)) {
            targetKey = key;
            forget();
        }
    }

    private static boolean bool(JsonObject data, String key) {
        return data.has(key) && data.get(key).isJsonPrimitive() && data.get(key).getAsJsonPrimitive().isBoolean()
                && data.get(key).getAsBoolean();
    }

    /** Forgets the route (a new target, another world). */
    private static void forget() {
        search = null;
        terrain = null;
        dots = List.of();
        reachesTarget = false;
        sinceSearch = MIN_GAP_TICKS;
        searchedFrom = null;
    }

    public static void clear() {
        guide = null;
        targetKey = "";
        label = null;
        forget();
    }

    public static boolean enabled() {
        try {
            return ERClientConfig.QUEST_PATH.get();
        } catch (IllegalStateException e) {
            return true;
        }
    }

    /** A place to go to in the world the player is in. */
    public static boolean active() {
        Minecraft minecraft = Minecraft.getInstance();
        return enabled() && guide != null && bool(guide, "active") && minecraft.level != null
                && minecraft.level.dimension().location().toString().equals(ClientUiState.stringOr(guide, "dim", ""));
    }

    /** The place ({x, z}). */
    public static Optional<double[]> target() {
        if (!active()) {
            return Optional.empty();
        }
        return Optional.of(new double[]{ClientUiState.doubleOr(guide, "x", 0), ClientUiState.doubleOr(guide, "z", 0)});
    }

    /** What the place is for ("森の賢者と話す"). */
    public static Optional<Component> label() {
        if (!active()) {
            return Optional.empty();
        }
        if (label == null) {
            try {
                label = Component.Serializer.fromJson(ClientUiState.stringOr(guide, "label", "\"\""));
            } catch (JsonParseException | IllegalStateException e) {
                label = Component.empty();
            }
        }
        return Optional.ofNullable(label);
    }

    /** The dots ahead ({x, y, z} each: y is the floor they lie on), nearest first. */
    public static List<double[]> dots() {
        return active() ? dots : List.of();
    }

    /** The dots run all the way to the place (not just towards it). */
    public static boolean reachesTarget() {
        return reachesTarget;
    }

    public static void tick() {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (!active() || player == null || minecraft.level == null) {
            if (search != null || !dots.isEmpty()) {
                forget();
            }
            return;
        }
        double[] goal = target().orElseThrow();
        if (Math.hypot(goal[0] - player.getX(), goal[1] - player.getZ()) < ARRIVED) {
            dots = List.of();
            search = null;
            return;
        }
        trim(player);
        sinceSearch++;
        if (search == null && sinceSearch >= MIN_GAP_TICKS && needsSearch(player)) {
            start(minecraft, player, goal);
        }
        if (search != null) {
            if (search.step(BUDGET) != PathSearch.Status.RUNNING) {
                finish();
            }
        }
    }

    private static boolean needsSearch(LocalPlayer player) {
        if (dots.isEmpty()) {
            return true;
        }
        if (distanceToDots(player) > OFF_PATH) {
            return true;
        }
        return sinceSearch >= REFRESH_TICKS && (!reachesTarget || dots.size() < 24) && searchedFrom != null
                && searchedFrom.distSqr(player.blockPosition()) > 12 * 12;
    }

    private static void start(Minecraft minecraft, LocalPlayer player, double[] goal) {
        terrain = new ClientTerrain(minecraft.level);
        BlockPos feet = standingAt(player);
        if (feet == null) {
            return;
        }
        searchedFrom = feet;
        search = new PathSearch(terrain, new PathSearch.Node(feet.getX(), feet.getY(), feet.getZ()), goal[0],
                Double.NaN, goal[1], 2.0, RADIUS, MAX_NODES);
        sinceSearch = 0;
        if (search.status() == PathSearch.Status.FAILED) {
            search = null;
        }
    }

    /** The cell the player stands in (or the nearest one under them while they jump or fall). */
    private static BlockPos standingAt(LocalPlayer player) {
        BlockPos base = player.blockPosition();
        ClientTerrain probe = new ClientTerrain((net.minecraft.client.multiplayer.ClientLevel) player.level());
        for (int dy = 0; dy >= -4; dy--) {
            BlockPos at = base.offset(0, dy, 0);
            if (probe.standable(at.getX(), at.getY(), at.getZ())) {
                return at;
            }
        }
        BlockPos up = base.above();
        return probe.standable(up.getX(), up.getY(), up.getZ()) ? up : null;
    }

    private static void finish() {
        List<PathSearch.Node> route = search.path();
        reachesTarget = search.status() == PathSearch.Status.FOUND;
        List<double[]> placed = new ArrayList<>();
        for (double[] dot : PathSearch.dots(route, SPACING)) {
            int cellY = (int) Math.floor(dot[1] + 0.01);
            placed.add(new double[]{dot[0], terrain.dotHeight(dot[0], cellY, dot[2]), dot[2]});
        }
        dots = List.copyOf(placed);
        search = null;
        sinceSearch = 0;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null) {
            trim(minecraft.player);
        }
    }

    /** Drops the dots the player has walked past. */
    private static void trim(LocalPlayer player) {
        if (dots.size() < 2) {
            return;
        }
        int nearest = 0;
        double best = Double.MAX_VALUE;
        int look = Math.min(dots.size(), 16);
        for (int i = 0; i < look; i++) {
            double[] dot = dots.get(i);
            double d = sq(dot[0] - player.getX()) + sq(dot[1] - player.getY()) * 0.25 + sq(dot[2] - player.getZ());
            if (d < best) {
                best = d;
                nearest = i;
            }
        }
        if (nearest > 0) {
            dots = List.copyOf(dots.subList(nearest, dots.size()));
        }
    }

    private static double distanceToDots(LocalPlayer player) {
        double best = Double.MAX_VALUE;
        int look = Math.min(dots.size(), 24);
        for (int i = 0; i < look; i++) {
            double[] dot = dots.get(i);
            best = Math.min(best, Math.sqrt(sq(dot[0] - player.getX()) + sq(dot[2] - player.getZ())));
        }
        return best;
    }

    private static double sq(double value) {
        return value * value;
    }
}
