package com.reisatsu.entity;

import com.reisatsu.game.Breaker;
import com.reisatsu.game.GameManager;
import com.reisatsu.game.Stealth;
import com.reisatsu.game.Story;
import com.reisatsu.registry.ModSounds;
import com.reisatsu.world.Circuit;
import com.reisatsu.world.Fixtures;
import com.reisatsu.world.Layout;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 店長 森田浩一: died of overwork at his desk. Phase 1: he sits facing the bank of security monitors, unseen — and sees
 * through every camera (a camera that sees you turns its LED red, the monitors show it). Noticed: the PA
 * 「お客様……」 and he comes out. Cut the cameras' circuit and he is blind: he gets up and walks to the breaker to put
 * it back on. Phase 2 (closing time): he patrols the store, looks around, bows at the entrance
 * 「本日の営業は、終了いたしました」, hears better; the cameras are still his eyes.
 * Photo windows: his left profile through the office window, at the breaker; in phase 2 the bow, looking away.
 */
public class Manager extends Spirit {
    public static final int SIT = 0, STAND = 1, WALK = 2, BOW = 3, LOOK = 4, CHASE = 5, REACH = 6;
    private static final int S_DESK = 0, S_TO_BREAKER = 1, S_AT_BREAKER = 2, S_TO_DESK = 3, S_PATROL = 4, S_LOOK = 5, S_BOW = 6;
    private int sub = S_TO_DESK;
    private int subTicks;
    private boolean phase2;
    private int routeIdx;
    private boolean bowedThisLoop;
    private final Set<BlockPos> seenBy = new HashSet<>();
    private boolean alert;

    public Manager(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
    }

    @Override
    public SpiritKind kind() {
        return SpiritKind.MANAGER;
    }

    /** His chair: a quarter block back from the marker, so the desk is ~0.75 ahead of him (his model's SIT reaches it). */
    public static Vec3 deskSpot() {
        Layout.Marker d = Layout.marker("manager_desk");
        return d.center().subtract(Vec3.atLowerCornerOf(d.facing().getNormal()).scale(0.25));
    }

    public boolean phase2() {
        return phase2;
    }

    /** Closing time: he stands up and starts closing the store. */
    public void closingTime() {
        phase2 = true;
        play(ModSounds.MANAGER_CHAIR.get(), 0.8f, 1.0f);
        if (mode == Mode.ROUTINE) {
            sub = S_PATROL;
            subTicks = 0;
            routeIdx = 0;
        }
    }

    /** A door chimed: now and then, a tired 「いらっしゃいませ」 over the PA. */
    public void onDoorChime(ServerLevel level) {
        if (mode != Mode.ROUTINE || random.nextInt(3) != 0) return;
        for (ServerPlayer p : GameManager.players(level)) {
            if (!GameManager.inGame(p)) continue;
            GameManager.privateSound(p, ModSounds.MANAGER_WELCOME.get(), p.getEyePosition().add(0, 2.5, 0), 0.7f, 0.97f);
            Story.say(p, "pa", 50, "dialogue.reisatsu.manager.welcome");
        }
    }

    /** The cameras' circuit was switched: blind, he goes to the breaker. */
    public void camerasSwitched(boolean on) {
        if (!on && mode == Mode.ROUTINE && (sub == S_DESK || sub == S_PATROL || sub == S_LOOK)) {
            sub = S_TO_BREAKER;
            subTicks = 0;
            play(ModSounds.MANAGER_SIGH.get(), 0.7f, 1.0f);
        }
    }

    @Override
    public float coneHalfAngle() {
        return pose() == BOW ? 0.0f : 60.0f;
    }

    @Override
    public float hearingMul() {
        if (phase2) return 1.3f;
        return sub == S_DESK ? 0.5f : 1.0f;
    }

    @Override
    protected boolean investigates() {
        return phase2;
    }

    @Override
    public boolean photogenic() {
        int p = pose();
        return p == SIT || p == STAND || p == BOW || p == LOOK || p == WALK;
    }

    @Override
    protected double huntSpeed() {
        return 0.23;
    }

    @Override
    protected double walkSpeed() {
        return 0.065;
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
        return STAND;
    }

    @Override
    protected int poseHunt() {
        return CHASE;
    }

    @Override
    protected int poseLook() {
        return LOOK;
    }

    @Override
    protected Vec3 home() {
        if (!phase2) return deskSpot();
        List<Layout.Marker> route = Layout.markers("manager_route");
        return route.get(Math.min(routeIdx, route.size() - 1)).center();
    }

    @Override
    public float eyeAlpha() {
        if (debugShown || flag(F_SHOWN)) return 1.0f;
        if (flag(F_HUNTING)) return 0.85f;
        return pose() == SIT ? 0.0f : 0.4f;
    }

    @Override
    protected void onNotice(ServerPlayer p) {
        super.onNotice(p);
        if (!phase2) {
            GameManager.privateSound(p, ModSounds.MANAGER_CUSTOMER.get(), p.getEyePosition().add(0, 2, 0), 1.0f, 0.95f);
            Story.say(p, "pa", 60, "dialogue.reisatsu.manager.customer");
        } else {
            play(ModSounds.MANAGER_CUSTOMER.get(), 1.0f, 0.9f);
        }
        play(ModSounds.MANAGER_CHAIR.get(), 0.9f, 0.8f);
    }

    @Override
    protected int noticeTicks() {
        return 30;
    }

    @Override
    protected void resumeRoutine() {
        sub = phase2 ? S_PATROL : S_TO_DESK;
        subTicks = 0;
    }

    // ------------------------------------------------------------------------------------------------ the cameras
    @Override
    protected float extraSight(ServerLevel level, ServerPlayer p) {
        if (!Layout.has("cameras") || !Fixtures.circuitOn(level, Circuit.CAMERAS)) return 0.0f;
        float best = 0.0f;
        for (Layout.Marker cam : Layout.markers("cameras")) {
            Vec3 dir = Vec3.directionFromRotation(32.0f, cam.yaw());
            Vec3 eye = new Vec3(cam.pos().getX() + 0.5, cam.pos().getY() - 0.05, cam.pos().getZ() + 0.5).add(dir.scale(0.2));
            float r = Stealth.rateFrom(eye, dir, 52.0f, 10.0f, p);
            if (r > 0) {
                seenBy.add(cam.pos());
                best = Math.max(best, r);
            }
        }
        return best * 0.9f;
    }

    private final Set<BlockPos> ledOn = new HashSet<>();

    private void updateLeds(ServerLevel level) {
        for (BlockPos c : seenBy) if (ledOn.add(c)) Fixtures.setCameraSeen(level, c, true);
        ledOn.removeIf(c -> {
            if (!seenBy.contains(c)) {
                Fixtures.setCameraSeen(level, c, false);
                return true;
            }
            return false;
        });
        boolean a = !seenBy.isEmpty();
        if (a != alert) {
            alert = a;
            Fixtures.setMonitorsAlert(level, a);
        }
        seenBy.clear();
    }

    // ------------------------------------------------------------------------------------------------ routine
    @Override
    protected void routine(ServerLevel level) {
        subTicks++;
        switch (sub) {
            case S_TO_DESK -> {
                setAnimPose(WALK);
                Layout.Marker d = Layout.marker("manager_desk");
                if (walkTo(deskSpot(), walkSpeed()) || subTicks > 20 * 30) {
                    Vec3 c = deskSpot();
                    moveTo(c.x, c.y, c.z, d.yaw(), 0.0f);
                    faceYaw(d.yaw());
                    setAnimPose(SIT);
                    play(ModSounds.MANAGER_CHAIR.get(), 0.6f, 1.0f);
                    sub = S_DESK;
                    subTicks = 0;
                }
            }
            case S_DESK -> {
                setAnimPose(SIT);
                Layout.Marker d = Layout.marker("manager_desk");
                faceYaw(d.yaw());
                // the typing is a client loop while he sits (HorrorAudio.tickWork)
                if (random.nextInt(700) == 0) play(ModSounds.MANAGER_SIGH.get(), 0.5f, 1.0f);
                if (!Fixtures.circuitOn(level, Circuit.CAMERAS) && subTicks > 40) {
                    sub = S_TO_BREAKER;
                    subTicks = 0;
                }
            }
            case S_TO_BREAKER -> {
                setAnimPose(WALK);
                Layout.Marker b = Layout.marker("breaker");
                Vec3 spot = Layout.has("breaker_stand") ? Layout.marker("breaker_stand").center()
                        : new Vec3(b.pos().getX() + 0.5, floorY(), b.pos().getZ() + 0.5).add(Vec3.atLowerCornerOf(b.facing().getNormal()).scale(1.0));
                if (walkTo(spot, walkSpeed()) || subTicks > 20 * 30) {
                    faceTowards(b.center());
                    sub = S_AT_BREAKER;
                    subTicks = 0;
                    setAnimPose(LOOK);
                }
            }
            case S_AT_BREAKER -> {
                if (subTicks < 50) {
                    setAnimPose(LOOK);
                    setYHeadRot(getYRot() + Mth.sin(subTicks * 0.12f) * 60.0f);
                } else {
                    setAnimPose(REACH);
                }
                if (subTicks == 70) Breaker.switchCircuit(level, Circuit.CAMERAS, true, null);
                if (subTicks > 90) {
                    sub = phase2 ? S_PATROL : S_TO_DESK;
                    subTicks = 0;
                }
            }
            case S_PATROL -> {
                List<Layout.Marker> route = Layout.markers("manager_route");
                if (routeIdx >= route.size()) {
                    routeIdx = 0;
                    bowedThisLoop = false;
                }
                setAnimPose(WALK);
                Vec3 to = route.get(routeIdx).center();
                if (walkTo(to, walkSpeed()) || subTicks > 20 * 25) {
                    boolean entrance = !bowedThisLoop && to.distanceToSqr(Layout.marker("door_inner").center()) < 7 * 7;
                    sub = entrance ? S_BOW : S_LOOK;
                    subTicks = 0;
                    routeIdx++;
                }
            }
            case S_LOOK -> {
                setAnimPose(LOOK);
                setYHeadRot(getYRot() + Mth.sin(subTicks * 0.1f) * 75.0f);
                if (subTicks > 40) {
                    sub = S_PATROL;
                    subTicks = 0;
                }
            }
            case S_BOW -> {
                if (subTicks == 1) {
                    faceTowards(Layout.marker("door_inner").center().add(0, 1.4, 0));
                    setAnimPose(BOW);
                    play(ModSounds.MANAGER_CLOSED.get(), 1.1f, 0.97f);
                    ServerPlayer p = nearestPlayer(level, 24.0);
                    if (p != null) Story.say(p, "manager", 80, "dialogue.reisatsu.manager.closed");
                }
                if (subTicks > 60) {
                    bowedThisLoop = true;
                    sub = S_PATROL;
                    subTicks = 0;
                }
            }
            default -> {
            }
        }
    }

    @Override
    protected void ambient(ServerLevel level) {
        if (tickCount % 4 == 0) updateLeds(level);
        if ((pose() == WALK || pose() == CHASE) && tickCount % (pose() == CHASE ? 7 : 11) == 0) {
            play(ModSounds.MANAGER_STEP.get(), 0.6f, 1.0f);
            if (random.nextInt(4) == 0) play(ModSounds.MANAGER_KEYS.get(), 0.4f, 1.0f);
        }
    }
}
