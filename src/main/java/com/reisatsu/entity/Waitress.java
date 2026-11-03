package com.reisatsu.entity;

import com.reisatsu.game.GameManager;
import com.reisatsu.game.Story;
import com.reisatsu.registry.ModSounds;
import com.reisatsu.world.Fixtures;
import com.reisatsu.world.Layout;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * 給仕 小野寺千夏: still on the night shift. She walks her route through the aisles serving water to customers nobody
 * else sees (a glass appears on the table), goes back to her station, hums the store jingle. When an automatic door
 * opens she hurries to the entrance and bows deep — eyes closed — 「いらっしゃいませ」. Faintly visible.
 * Photo windows: the bow, serving, from the side.
 */
public class Waitress extends Spirit {
    public static final int WALK = 0, SERVE = 1, BOW = 2, STAND = 3, NOTICE = 4, CHASE = 5;
    private static final int S_ROUTE = 0, S_SERVE = 1, S_STATION = 2, S_TO_BOW = 3, S_BOW = 4;
    private int sub = S_STATION;
    private int subTicks;
    private int routeIdx;
    private int serveTable = -1;
    private int stationTicks = 120;
    private int loops;
    private boolean greetPending;

    public Waitress(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
    }

    @Override
    public SpiritKind kind() {
        return SpiritKind.WAITRESS;
    }

    @Override
    public float coneHalfAngle() {
        if (sub == S_BOW && pose() == BOW) return 0.0f; // eyes closed
        if (pose() == SERVE) return 25.0f;
        return 60.0f;
    }

    @Override
    public float lookDownTilt() {
        return pose() == SERVE ? 35.0f : 0.0f;
    }

    @Override
    public boolean photogenic() {
        int p = pose();
        return p == BOW || p == SERVE || p == WALK || p == STAND;
    }

    @Override
    protected double huntSpeed() {
        return 0.25;
    }

    @Override
    protected double walkSpeed() {
        return 0.07;
    }

    /** She chases across the customer area only (and checks the restrooms if she heard you there). */
    @Override
    protected boolean mayHunt(ServerPlayer p) {
        String r = GameManager.state(p).room;
        return r.equals("dining") || r.equals("waiting") || r.equals("vestibule") || r.equals("womens") || r.equals("mens")
                || distanceTo(p) < 6.0;
    }

    @Override
    protected int poseWalk() {
        return WALK;
    }

    @Override
    protected int poseStand() {
        return STAND;
    }

    @Override
    protected int poseNotice() {
        return NOTICE;
    }

    @Override
    protected int poseHunt() {
        return CHASE;
    }

    @Override
    protected Vec3 home() {
        // the night starts (and a fresh spawn begins) at her station; afterwards home is where she was on her round
        if (sub == S_STATION) return Layout.marker("waitress_station").center();
        List<Layout.Marker> route = Layout.markers("waitress_route");
        return route.get(Math.min(routeIdx, route.size() - 1)).center();
    }

    @Override
    protected void onNotice(ServerPlayer p) {
        super.onNotice(p);
        play(ModSounds.WAITRESS_TRAY.get(), 1.0f, 0.8f);
    }

    @Override
    protected void resumeRoutine() {
        sub = S_ROUTE;
        subTicks = 0;
        routeIdx = nearestRoutePoint();
    }

    /** An automatic door opened: she goes to greet whoever came in. */
    public void onDoorChime() {
        if (mode != Mode.ROUTINE) return;
        if (sub == S_TO_BOW || sub == S_BOW) return;
        sub = S_TO_BOW;
        subTicks = 0;
        greetPending = true;
    }

    private int nearestRoutePoint() {
        List<Layout.Marker> route = Layout.markers("waitress_route");
        int best = 0;
        double bd = Double.MAX_VALUE;
        for (int i = 0; i < route.size(); i++) {
            double d = route.get(i).center().distanceToSqr(position());
            if (d < bd) {
                bd = d;
                best = i;
            }
        }
        return best;
    }

    /** The table next to a route point, or -1. */
    private int tableNear(Vec3 at) {
        int best = -1;
        double bd = 2.6 * 2.6;
        for (int n = 1; n <= 17; n++) {
            if (!Layout.has("table_" + n)) continue;
            double d = Layout.marker("table_" + n).center().distanceToSqr(at);
            if (d < bd) {
                bd = d;
                best = n;
            }
        }
        return best;
    }

    @Override
    protected void routine(ServerLevel level) {
        subTicks++;
        switch (sub) {
            case S_STATION -> {
                Layout.Marker st = Layout.marker("waitress_station");
                if (horizontalDistance(st.center()) > 0.4) {
                    setAnimPose(WALK);
                    walkTo(st.center(), walkSpeed());
                    subTicks = 0;
                } else {
                    setAnimPose(STAND);
                    if (subTicks == 1) faceYaw(st.yaw());
                    if (subTicks == 30 && random.nextInt(2) == 0) play(ModSounds.WAITRESS_HUM.get(), 0.6f, 1.0f);
                    if (subTicks > stationTicks) {
                        sub = S_ROUTE;
                        subTicks = 0;
                        routeIdx = 0;
                    }
                }
            }
            case S_ROUTE -> {
                List<Layout.Marker> route = Layout.markers("waitress_route");
                if (routeIdx >= route.size()) {
                    loops++;
                    sub = S_STATION;
                    subTicks = 0;
                    stationTicks = 20 * (5 + random.nextInt(6));
                    return;
                }
                setAnimPose(WALK);
                Vec3 p = route.get(routeIdx).center();
                if (walkTo(p, walkSpeed())) {
                    int t = tableNear(p);
                    if (t > 0 && random.nextFloat() < 0.55f) {
                        serveTable = t;
                        sub = S_SERVE;
                        subTicks = 0;
                    }
                    routeIdx++;
                }
            }
            case S_SERVE -> {
                Vec3 table = Layout.marker("table_" + serveTable).center();
                if (subTicks == 1) {
                    faceTowards(table.add(0, 0.6, 0));
                    setAnimPose(SERVE);
                }
                // the glass leaves her hand at SERVE tick 24
                if (pose() == SERVE && poseTicks() == 24) {
                    Fixtures.placeWaterGlass(level, serveTable);
                    play(ModSounds.FIXTURE_GLASS_SET.get(), 0.6f, 1.0f);
                    if (random.nextInt(3) == 0) {
                        play(ModSounds.WAITRESS_WATER.get(), 0.8f, 1.0f);
                        ServerPlayer near = nearestPlayer(level, 10.0);
                        if (near != null) Story.say(near, "waitress", 60, "dialogue.reisatsu.waitress.water");
                    }
                }
                if (subTicks > 50) {
                    sub = S_ROUTE;
                    subTicks = 0;
                    setAnimPose(WALK);
                }
            }
            case S_TO_BOW -> {
                Layout.Marker b = Layout.marker("waitress_bow");
                setAnimPose(WALK);
                if (walkTo(b.center(), 0.12) || subTicks > 20 * 15) {
                    sub = S_BOW;
                    subTicks = 0;
                    faceYaw(b.yaw());
                    setAnimPose(BOW);
                }
            }
            case S_BOW -> {
                if (subTicks == 6 && greetPending) {
                    greetPending = false;
                    play(ModSounds.WAITRESS_GREET.get(), 1.0f, 0.97f + random.nextFloat() * 0.06f);
                    ServerPlayer near = nearestPlayer(level, 16.0);
                    if (near != null) Story.say(near, "waitress", 50, "dialogue.reisatsu.waitress.greet");
                }
                if (subTicks > 46) {
                    setAnimPose(STAND);
                    sub = S_ROUTE;
                    subTicks = 0;
                    routeIdx = nearestRoutePoint();
                }
            }
            default -> {
            }
        }
    }

    @Override
    protected void ambient(ServerLevel level) {
        if (pose() == WALK || pose() == CHASE) {
            if (tickCount % (pose() == CHASE ? 6 : 10) == 0) play(ModSounds.WAITRESS_STEP.get(), 0.5f, 1.0f);
        }
    }
}
