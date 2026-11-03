package com.hitori.entity;

import com.hitori.HitoriConfig;
import com.hitori.game.Apt;
import com.hitori.game.GameData;
import com.hitori.game.GameManager;
import com.hitori.game.NoiseSystem;
import com.hitori.game.PlayerState;
import com.hitori.game.Recording;
import com.hitori.game.Sight;
import com.hitori.game.Watch;
import com.hitori.registry.ModParticles;
import com.hitori.registry.ModSounds;
import com.hitori.world.Layout;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.BodyRotationControl;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * 鬼 (docs/DESIGN.md §3.4, §5): the stuffed rabbit you filled with rice and your nails, sewed shut with red thread,
 * named, drowned and stabbed. After 「次は〇〇が鬼だから」 it wakes in the tub, floats there for a minute or three
 * (face down, on camera), and then it is not there any more.
 * <p>
 * <b>Movement</b>: it drags itself fast on its arms while nobody watches; seen through a camera it moves in stiff
 * marionette steps (the client shows it in stop-motion); seen with your own eyes it does not move at all — and when you
 * look away and back, its head has turned toward you. It glides along planned paths (doors, fusuma and sliding doors
 * are no obstacle) at the floor of whatever room it is in.
 * <p>
 * <b>The search</b> (phase 2): room by room along {@code doll_route}, pausing, lurking at {@code doll_hides}, walking to
 * noises, checking hiding places (the western closet, behind doors, the bathtub) and, after each round, the 押し入れ.
 * Its suspicion of your hiding place rises with your noises and with light from the fusuma's gap (peeking, the
 * laptop) while it is in the tatami room facing it; at 1.0 it opens the fusuma — unless salt water is still in your
 * mouth: then it stands there, breathing, for a long time, and leaves.
 * <p>
 * <b>Phase 3</b>: it waits somewhere (sitting, facing the wall) and moves only while nobody looks; after six minutes it
 * hunts you openly. Salt water spat on it: it shrieks and goes limp. Burnt: it twitches in the fire.
 * <p>
 * Never saved: the game keeps its state and respawns it ({@code game/Doll}).
 */
public class OniDoll extends PathfinderMob implements Animated {
    private static final EntityDataAccessor<Integer> DATA_POSE = SynchedEntityData.defineId(OniDoll.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_FLAGS = SynchedEntityData.defineId(OniDoll.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> DATA_GLITCH = SynchedEntityData.defineId(OniDoll.class, EntityDataSerializers.FLOAT);

    // poses (docs §5)
    public static final int FLOAT = 0, SIT = 1, CRAWL = 2, WALK = 3, STAND = 4, TURN = 5, PEEK = 6, LUNGE = 7, LIMP = 8, BURN = 9;
    // synced flags
    public static final int F_KNIFE = 1, F_WET = 2, F_ON_CAMERA = 4, F_SEEN = 8, F_MOVING = 16, F_AT_FUSUMA = 32;
    /** Your eyes are on it: it holds still in whatever pose it was in, its pose time held (the models freeze exactly). */
    public static final int F_FROZEN = 64;

    public enum State { DORMANT, TUB, SEARCH, INVESTIGATE, CHECK, FUSUMA, STANDOFF, OPENING, WAIT, HUNT, LIMP, BURN, SCRIPTED }

    private int poseStart;
    private State state = State.DORMANT;
    private int stateTicks;
    /** Ticks it still floats in the tub after waking. */
    public int tubTicks = 20 * 90;
    /** Suspicion of the player's hiding place, 0..1. */
    public float suspicion;
    private long lastNoise = NoiseSystem.lastId();
    private int routeIndex = -1;
    private int pauseTicks;
    @Nullable private Vec3 goal;
    private float goalYaw = Float.NaN;
    private int goalPose = STAND;
    private int listenTicks;
    private int checkTicks;
    private String checking = "";
    private int wetTicks;
    private int stepSound;
    private double moved;
    private int unobserved;
    private boolean seenBefore;
    private int turnTicks;
    private int relocateIn = 20 * 45;
    private int blackoutAsked = -1;
    /** Burning: ticks until the fire has done its work. */
    private int burnTicks;
    /** The lunge's goal (OPENING): the model springs 0.56 blocks by itself, the entity carries the rest. */
    @Nullable private Vec3 lungeTo;
    /** Debug / self test: perceives, but does not move by itself. */
    public boolean debugStill;
    /** Observation this tick (server). */
    public boolean seenByEyes, seenByCamera;

    // path following
    @Nullable private List<Vec3> path;
    private int pathIndex;
    @Nullable private BlockPos pathGoal;
    private int pathAge;

    public OniDoll(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
        setPersistenceRequired();
        setInvulnerable(true);
        setNoGravity(true);
        noPhysics = true;
        xpReward = 0;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 100.0)
                .add(Attributes.MOVEMENT_SPEED, 0.25)
                .add(Attributes.FOLLOW_RANGE, 48.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0);
    }

    // ================================================================================================ synced data
    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(DATA_POSE, FLOAT);
        entityData.define(DATA_FLAGS, F_KNIFE | F_WET);
        entityData.define(DATA_GLITCH, 0.0f);
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (DATA_POSE.equals(key)) poseStart = tickCount;
    }

    @Override
    public int pose() {
        return puppetPose >= 0 ? puppetPose : entityData.get(DATA_POSE);
    }

    @Override
    public float poseTime(float partialTick) {
        if (puppetPose >= 0) return puppetPoseTime;
        return tickCount - poseStart + (flag(F_FROZEN) ? 0.0f : partialTick);
    }

    @Override
    public float glitch(float partialTick) {
        return puppetPose >= 0 ? puppetGlitch : entityData.get(DATA_GLITCH);
    }

    // client-side film actors (com.hitori.client.cine): the pose, how far into it and the glitch, set every frame
    private int puppetPose = -1;
    private float puppetPoseTime, puppetGlitch;

    public void puppetPose(int pose, float ticks, float glitch) {
        puppetPose = pose;
        puppetPoseTime = ticks;
        puppetGlitch = glitch;
    }

    public int flags() {
        return entityData.get(DATA_FLAGS);
    }

    public boolean flag(int f) {
        return (flags() & f) != 0;
    }

    private void setFlag(int f, boolean on) {
        int v = flags();
        int n = on ? v | f : v & ~f;
        if (n != v) entityData.set(DATA_FLAGS, n);
    }

    public void setAnimPose(int pose) {
        if (entityData.get(DATA_POSE) != pose) {
            entityData.set(DATA_POSE, pose);
            poseStart = tickCount;
        }
    }

    public State state() {
        return state;
    }

    public int stateTicks() {
        return stateTicks;
    }

    public void setState(State s) {
        if (state != s) {
            state = s;
            stateTicks = 0;
            clearPath();
        }
    }

    public boolean hasKnife() {
        return flag(F_KNIFE);
    }

    public void setKnife(boolean k) {
        setFlag(F_KNIFE, k);
    }

    // ================================================================================================ places
    /** Where it floats in the full tub (0 or 1: the tub's two blocks): FLOAT has its back at the water's surface. */
    public static Vec3 tubSpot(int i) {
        return tubAt(i, 10.5 / 16.0);
    }

    /** The drained tub's floor (block Y + 2/16): BURN and LIMP lie at the entity origin, and you would stand here. */
    public static Vec3 tubFloor(int i) {
        return tubAt(i, 2.0 / 16.0);
    }

    private static Vec3 tubAt(int i, double dy) {
        List<Layout.Marker> tub = Layout.markers("bathtub");
        Layout.Marker m = tub.get(Math.min(i, tub.size() - 1));
        return new Vec3(m.pos().getX() + 0.5, m.pos().getY() + dy, m.pos().getZ() + 0.5);
    }

    /** The floor just outside the tub. */
    public static Vec3 outsideTub() {
        Layout.Marker m = Layout.marker("bathtub");
        BlockPos out = m.pos().relative(m.facing());
        return Vec3.atBottomCenterOf(out);
    }

    /** The tatami in front of the closet's middle fusuma, and the yaw that faces it. */
    public static Vec3 fusumaFront() {
        List<Layout.Marker> f = Layout.markers("closet_fusuma");
        Layout.Marker m = f.get(f.size() / 2);
        return Vec3.atBottomCenterOf(m.pos().relative(m.facing()));
    }

    public static float fusumaYaw() {
        List<Layout.Marker> f = Layout.markers("closet_fusuma");
        return f.get(f.size() / 2).facing().getOpposite().toYRot();
    }

    /** The middle of the fusuma (where light leaks and where it listens). */
    public static Vec3 fusumaCentre() {
        List<Layout.Marker> f = Layout.markers("closet_fusuma");
        return Vec3.atCenterOf(f.get(f.size() / 2).pos());
    }

    public static Vec3 westernClosetFront() {
        Layout.Marker m = Layout.marker("western_closet");
        return Vec3.atBottomCenterOf(m.pos().relative(m.facing()));
    }

    public static boolean inTatami(Vec3 p) {
        Layout.Room r = Layout.roomAt(BlockPos.containing(p.x, p.y + 0.3, p.z));
        return r != null && r.key().equals("tatami");
    }

    // ================================================================================================ commands from the game
    /** The ritual put it in the tub (step 12). */
    public void dormantInTub() {
        Vec3 t = tubSpot(0);
        moveTo(t.x, t.y, t.z, Layout.marker("bathtub").yaw(), 0.0f);
        setState(State.DORMANT);
        setAnimPose(FLOAT);
        setKnife(false);
        setFlag(F_WET, true);
    }

    /** Something moved in the water while your eyes were closed: it is at the other end of the tub, turned. */
    public void shiftInTub() {
        Vec3 t = tubSpot(1);
        moveTo(t.x, t.y, t.z, getYRot() + 180.0f, 0.0f);
        yBodyRot = getYRot();
        setYHeadRot(getYRot());
    }

    /** 「次は〇〇が鬼だから」: it wakes (floating a while longer). */
    public void wake() {
        setKnife(true);
        setState(State.TUB);
        setAnimPose(FLOAT);
        int min = 20 * 60, max = 20 * 180;
        tubTicks = min + random.nextInt(max - min);
    }

    /** Phase 3: it stops searching and waits. */
    public void startWaiting() {
        if (state == State.LIMP || state == State.BURN) return;
        setState(State.WAIT);
        relocateIn = 20 * 5;
        suspicion = 0.0f;
    }

    /** Phase 3, too slow: it hunts you openly. */
    public void startHunting() {
        if (state == State.LIMP || state == State.BURN) return;
        setState(State.HUNT);
    }

    /** Salt water spat on it: it shrieks and collapses. */
    public void spatOn() {
        setState(State.LIMP);
        setAnimPose(LIMP);
        play(ModSounds.DOLL_SHRIEK.get(), 1.6f, 1.0f);
        if (level() instanceof ServerLevel sl) {
            sl.sendParticles(ModParticles.SALT_SPRAY.get(), getX(), getY() + 0.4, getZ(), 30, 0.25, 0.25, 0.25, 0.08);
            sl.sendParticles(ModParticles.RICE_GRAIN.get(), getX(), getY() + 0.3, getZ(), 12, 0.15, 0.1, 0.15, 0.05);
        }
    }

    /** In the sink / the drained tub, burning. */
    public void burn() {
        setState(State.BURN);
        setAnimPose(BURN);
        burnTicks = 20 * 9;
        play(ModSounds.RITUAL_BURN_IGNITE.get(), 1.2f, 1.0f);
    }

    public boolean burning() {
        return state == State.BURN;
    }

    /** It springs at you (LUNGE): the model's spring carries 0.56 blocks, the entity the rest. */
    public void lungeAt(Vec3 at) {
        setState(State.OPENING);
        setAnimPose(LUNGE);
        lungeTo = at;
        float yaw = yawTo(position(), at);
        setYRot(yaw);
        yBodyRot = yaw;
        setYHeadRot(yaw);
    }

    public void script(Vec3 at, float yaw, int pose) {
        setState(State.SCRIPTED);
        moveTo(at.x, at.y, at.z, yaw, 0.0f);
        yBodyRot = yaw;
        setYHeadRot(yaw);
        setAnimPose(pose);
    }

    // ================================================================================================ ticking
    @Override
    public void tick() {
        noPhysics = true;
        setNoGravity(true);
        setDeltaMovement(Vec3.ZERO);
        super.tick();
        setOnGround(true);
        fallDistance = 0;
        if (level().isClientSide) {
            // frozen: the pose's clock stands still with it
            if (flag(F_FROZEN)) poseStart++;
            return;
        }
        ServerLevel level = (ServerLevel) level();
        stateTicks++;
        observe(level);
        GameData data = GameData.get(level.getServer());
        boolean running = GameManager.running(level);
        switch (state) {
            case DORMANT, SCRIPTED -> {
            }
            case TUB -> {
                if (running) tickTub(level);
            }
            case SEARCH -> {
                if (running) {
                    listen(level, data);
                    tickSearch(level, data);
                }
            }
            case INVESTIGATE -> {
                if (running) {
                    listen(level, data);
                    tickInvestigate(level, data);
                }
            }
            case CHECK -> {
                if (running) {
                    listen(level, data);
                    tickCheck(level, data);
                }
            }
            case FUSUMA -> {
                if (running) {
                    listen(level, data);
                    tickFusuma(level, data);
                }
            }
            case STANDOFF -> tickStandoff(level);
            case OPENING -> tickOpening(level);
            case WAIT -> {
                if (running) tickWait(level, data);
            }
            case HUNT -> {
                if (running) tickHunt(level, data);
            }
            case LIMP -> tickLimp(level);
            case BURN -> tickBurn(level);
        }
        // what it did not listen for is not heard later (no backlog of the noises made while it floated)
        if (state != State.SEARCH && state != State.INVESTIGATE && state != State.CHECK && state != State.FUSUMA) lastNoise = NoiseSystem.lastId();
        ambient(level);
        sync(level);
    }

    /** Who sees it right now: your own eyes (it freezes) or a camera you are watching (it moves stiffly). */
    private void observe(ServerLevel level) {
        boolean eyes = false, cam = false;
        Vec3 c = position().add(0.0, getBbHeight() * 0.6, 0.0);
        for (ServerPlayer p : GameManager.players(level)) {
            if (!GameManager.inGame(p)) continue;
            if (Watch.eyesOn(p, c)) eyes = true;
            if (Watch.cameraOn(p, c)) cam = true;
        }
        if (blackoutAsked >= 0 && --blackoutAsked >= 0) cam = false; // the feed is black for a moment
        boolean wasSeen = seenByEyes;
        seenByEyes = eyes;
        seenByCamera = cam;
        unobserved = eyes || cam ? 0 : unobserved + 1;
        // looked away and back: its head has turned toward you
        if (eyes && !wasSeen && seenBefore && movesFreely()) {
            ServerPlayer p = nearestWatcher(level);
            if (p != null) {
                Vec3 to = p.getEyePosition().subtract(position());
                float yaw = (float) (Mth.atan2(to.z, to.x) * Mth.RAD_TO_DEG) - 90.0f;
                float diff = Math.abs(Mth.wrapDegrees(yaw - getYRot()));
                setYHeadRot(yaw);
                setXRot(Mth.clamp((float) -(Mth.atan2(to.y, Math.sqrt(to.x * to.x + to.z * to.z)) * Mth.RAD_TO_DEG), -40.0f, 40.0f));
                if (diff > 100.0f) {
                    setAnimPose(TURN);
                    turnTicks = 50;
                }
            }
        }
        if (eyes) seenBefore = true;
        if (turnTicks > 0) turnTicks--;
    }

    @Nullable
    private ServerPlayer nearestWatcher(ServerLevel level) {
        ServerPlayer best = null;
        double bd = Double.MAX_VALUE;
        for (ServerPlayer p : GameManager.players(level)) {
            if (!GameManager.inGame(p)) continue;
            double d = distanceToSqr(p);
            if (d < bd) {
                bd = d;
                best = p;
            }
        }
        return best;
    }

    /** States in which it walks about (and so freezes when looked at). */
    private boolean movesFreely() {
        return switch (state) {
            case SEARCH, INVESTIGATE, CHECK, FUSUMA, WAIT, HUNT -> true;
            default -> false;
        };
    }

    /** Speed now, blocks per tick (0 while your eyes are on it). */
    private double speed(GameData data) {
        if (seenByEyes) return 0.0;
        double mul = HitoriConfig.dollSpeed();
        if (seenByCamera) return 0.022 * mul;  // the model's planted WALK is 0.015 b/t: in stop-motion a little slide is fine
        return (0.085 + 0.035 * tension(data)) * mul;
    }

    /** 0 → 1 over the first twelve minutes of hiding: shorter pauses, more visits to the closet. */
    public static float tension(GameData data) {
        if (data.hideStartTick < 0) return 0.0f;
        return Mth.clamp((data.playTicks - data.hideStartTick) / (20.0f * 60.0f * 12.0f), 0.0f, 1.0f);
    }

    private int movePose() {
        return seenByCamera ? WALK : CRAWL;
    }

    /** Moves toward a point at the current speed with the moving pose; true when there. */
    private boolean go(Vec3 to, GameData data) {
        double sp = speed(data);
        if (sp <= 0.0) return false;  // looked at: it holds the pose it was caught in
        if (turnTicks <= 0) setAnimPose(movePose());
        return walkTo(to, sp);
    }

    // ------------------------------------------------------------------------------------------------ the tub
    private void tickTub(ServerLevel level) {
        setAnimPose(FLOAT);
        if (tubTicks > 0) {
            tubTicks--;
            if (random.nextInt(260) == 0) play(ModSounds.FIXTURE_WATER_SLOSH.get(), 0.5f, 0.9f + random.nextFloat() * 0.2f);
            return;
        }
        if (seenByEyes) return;
        if (seenByCamera) {
            // watched without pause: the feed drops out for a moment, and then the tub is empty
            if (blackoutAsked < 0 && stateTicks % 20 == 0 && random.nextInt(6) == 0) {
                for (ServerPlayer p : GameManager.players(level)) {
                    if (Watch.cameraOn(p, position())) GameManager.event(p, GameManager.EV_FEED, 2, "blackout");
                }
                blackoutAsked = 30;
            }
            return;
        }
        leaveTub(level);
    }

    private void leaveTub(ServerLevel level) {
        Vec3 out = outsideTub();
        moveTo(out.x, out.y, out.z, getYRot(), 0.0f);
        wetTicks = 20 * 180;
        setKnife(true);
        setState(State.SEARCH);
        routeIndex = -1;
        pauseTicks = 30;
        setAnimPose(CRAWL);
        play(ModSounds.FIXTURE_WATER_SLOSH.get(), 0.9f, 0.8f);
        Recording.event(level, "tub_empty", tubSpot(0));
        GameData d = GameData.get(level.getServer());
        d.flag("doll_left_tub");
    }

    // ------------------------------------------------------------------------------------------------ search
    private void tickSearch(ServerLevel level, GameData data) {
        List<Layout.Marker> route = Layout.markers("doll_route");
        if (route.isEmpty()) return;
        if (routeIndex < 0) routeIndex = nearestIndex(route);
        if (debugStill) {
            setAnimPose(STAND);
            return;
        }
        if (pauseTicks > 0) {
            pauseTicks--;
            setAnimPose(turnTicks > 0 ? TURN : goalPose);
            if (!Float.isNaN(goalYaw) && !seenByEyes) turnBody(goalYaw, 10.0f);
            // look about while it stands
            if (!seenByEyes && turnTicks <= 0) setYHeadRot(getYRot() + Mth.sin(stateTicks * 0.07f) * 60.0f);
            return;
        }
        Layout.Marker m = route.get(routeIndex);
        Vec3 target = Vec3.atBottomCenterOf(m.pos());
        if (go(target, data)) {
            float t = tension(data);
            routeIndex++;
            goalYaw = m.yaw();
            goalPose = random.nextInt(3) == 0 ? PEEK : STAND;
            pauseTicks = (int) ((40 + random.nextInt(100)) * (1.0f - 0.5f * t));
            // a lurking spot nearby: it stops there for a long time
            if (random.nextFloat() < 0.18f + 0.1f * t) {
                Layout.Marker hide = nearestOf(Layout.markers("doll_hides"), target, 4.0);
                if (hide != null) {
                    startCheck("lurk", Vec3.atBottomCenterOf(hide.pos()), hide.yaw(), random.nextBoolean() ? SIT : PEEK,
                            100 + random.nextInt(200));
                    return;
                }
            }
            // hiding places on the way: the western closet, the bathtub
            if (target.distanceTo(westernClosetFront()) < 2.5 && random.nextFloat() < 0.6f) {
                startCheck("western", westernClosetFront(), Layout.marker("western_closet").facing().getOpposite().toYRot(), STAND, 60 + random.nextInt(60));
                return;
            }
            if (routeIndex >= route.size()) {
                // the round is over: last, the closet
                routeIndex = random.nextInt(Math.max(1, route.size() / 2));
                startFusuma(data, 140 + random.nextInt(140) + (int) (120 * t));
            }
        }
    }

    private void startCheck(String what, Vec3 at, float yaw, int pose, int ticks) {
        checking = what;
        goal = at;
        goalYaw = yaw;
        goalPose = pose;
        checkTicks = ticks;
        setState(State.CHECK);
    }

    private void tickCheck(ServerLevel level, GameData data) {
        if (goal == null) {
            setState(State.SEARCH);
            return;
        }
        if (horizontalDistance(goal) > 0.35) {
            go(goal, data);
            if (stateTicks > 20 * 30) setState(State.SEARCH);
            return;
        }
        if (!seenByEyes) turnBody(goalYaw, 12.0f);
        setAnimPose(turnTicks > 0 ? TURN : goalPose);
        if (checking.equals("western")) {
            for (ServerPlayer p : GameManager.players(level)) {
                PlayerState ps = GameManager.state(p);
                if (ps.hiding() && "western".equals(ps.hideSpot)) {
                    suspicion = Math.min(1.0f, suspicion + 0.004f);
                    GameManager.reportDanger(p, Math.max(0.6f, suspicion));
                    if (suspicion >= 1.0f) {
                        decide(level, p);
                        return;
                    }
                }
            }
        }
        if (--checkTicks <= 0) {
            setState(State.SEARCH);
            pauseTicks = 10;
        }
    }

    private void startFusuma(GameData data, int ticks) {
        listenTicks = ticks;
        goal = fusumaFront();
        goalYaw = fusumaYaw();
        setState(State.FUSUMA);
    }

    private void tickFusuma(ServerLevel level, GameData data) {
        Vec3 front = fusumaFront();
        if (horizontalDistance(front) > 0.35) {
            go(front, data);
            setFlag(F_AT_FUSUMA, false);
            if (stateTicks > 20 * 40) setState(State.SEARCH);
            return;
        }
        setFlag(F_AT_FUSUMA, true);
        if (!seenByEyes) {
            turnBody(fusumaYaw(), 15.0f);
            setYHeadRot(fusumaYaw() + Mth.sin(stateTicks * 0.05f) * 12.0f);
        }
        setAnimPose(turnTicks > 0 ? TURN : STAND);
        ServerPlayer hidden = hiddenInCloset(level, "oshiire");
        if (hidden != null) {
            GameManager.reportDanger(hidden, Math.max(0.55f, suspicion));
            if (suspicion >= 1.0f) {
                decide(level, hidden);
                return;
            }
            // it listens: the quiet lets it doubt
            if (stateTicks % 20 == 0) suspicion = Math.max(0.0f, suspicion - 0.01f);
        } else if (stateTicks % 20 == 0) {
            suspicion = Math.max(0.0f, suspicion - 0.03f);
        }
        // what you hear through the paper
        if (stateTicks % 60 == 17 && random.nextInt(3) == 0) play(ModSounds.DOLL_BREATH.get(), 0.6f, 1.0f);
        if (stateTicks % 80 == 41 && random.nextInt(3) == 0) play(ModSounds.DOLL_RICE.get(), 0.6f, 1.0f);
        if (stateTicks == 50 && random.nextInt(3) == 0) play(ModSounds.DOLL_VOICE_DOKO.get(), 0.8f, 1.0f);
        if (suspicion > 0.6f && stateTicks % 90 == 30 && random.nextInt(2) == 0) play(ModSounds.DOLL_KNIFE_DRAG.get(), 0.7f, 1.1f);
        if (suspicion > 0.82f && stateTicks % 140 == 70 && random.nextInt(3) == 0) {
            play(ModSounds.DOLL_KNIFE_STAB.get(), 1.0f, 1.0f);
            if (hidden != null) {
                GameManager.event(hidden, GameManager.EV_STINGER, 0, "");
                GameManager.addFear(hidden, 0.15f);
            }
        }
        if (--listenTicks <= 0) {
            suspicion *= 0.6f;
            setState(State.SEARCH);
            pauseTicks = 20;
            routeIndex = farthestIndex(Layout.markers("doll_route"));
        }
    }

    /** At 1.0: with salt water in your mouth it stops and stands there; without, it opens. */
    private void decide(ServerLevel level, ServerPlayer p) {
        PlayerState ps = GameManager.state(p);
        if (ps.mouth > 0.0f) {
            setState(State.STANDOFF);
            listenTicks = 20 * (20 + random.nextInt(16));
            setAnimPose(STAND);
            GameManager.addFear(p, 0.3f);
            GameManager.event(p, GameManager.EV_STINGER, 1, "");
            Recording.event(level, "doll_fusuma", fusumaCentre());
        } else {
            lungeAt(p.position());
            Apt.setClosetFusuma(level, 2);
            playAt(fusumaCentre(), ModSounds.DOOR_FUSUMA.get(), 1.2f, 0.85f);
            GameManager.caughtByDoll(p, this);
        }
    }

    private void tickStandoff(ServerLevel level) {
        setFlag(F_AT_FUSUMA, true);
        setAnimPose(STAND);
        ServerPlayer hidden = hiddenInCloset(level, null);
        if (hidden != null) {
            GameManager.reportDanger(hidden, 1.0f);
            // the salt water is gone while it stands there: nothing stops it now
            if (GameManager.state(hidden).mouth <= 0.0f && stateTicks > 20) {
                decide(level, hidden);
                return;
            }
        }
        if (stateTicks % 50 == 10) play(ModSounds.DOLL_BREATH.get(), 0.7f, 0.95f + random.nextFloat() * 0.1f);
        if (stateTicks % 70 == 35) play(ModSounds.DOLL_RICE.get(), 0.6f, 1.0f);
        if (--listenTicks <= 0 || hidden == null) {
            suspicion = 0.15f;
            setFlag(F_AT_FUSUMA, false);
            setState(GameData.get(level.getServer()).phase == GameData.PHASE_FINISH ? State.HUNT : State.SEARCH);
            routeIndex = farthestIndex(Layout.markers("doll_route"));
            pauseTicks = 10;
        }
    }

    private void tickOpening(ServerLevel level) {
        setAnimPose(LUNGE);
        // LUNGE: crouched 0-2, springs from 2 (0.56 blocks in the model), the knife down at 6-8; the entity carries the
        // rest of the way to you over the spring
        if (lungeTo != null && stateTicks >= 2 && stateTicks <= 6) {
            double rest = horizontalDistance(lungeTo) - 0.56 - 0.35;
            if (rest > 0.0) {
                double dx = lungeTo.x - getX(), dz = lungeTo.z - getZ(), d = Math.sqrt(dx * dx + dz * dz);
                double step = Math.min(rest, rest / Math.max(1, 7 - stateTicks));
                setPos(getX() + dx / d * step, getY(), getZ() + dz / d * step);
            }
        }
    }

    /** A noise it heard: suspicion of the hiding place, or a walk over to look. */
    private void listen(ServerLevel level, GameData data) {
        for (NoiseSystem.Noise n : NoiseSystem.since(lastNoise)) hear(level, data, n);
        lastNoise = NoiseSystem.lastId();
        // light from the fusuma's gap (peeking, the laptop's screen) while it is in the tatami room, facing the gap
        Vec3 gap = fusumaCentre();
        if (inTatami(position())) {
            ServerPlayer p = hiddenInCloset(level, "oshiire");
            if (p != null) {
                PlayerState ps = GameManager.state(p);
                float light = ps.peeking ? 0.006f : ps.appCamera > 0 && ps.appDevice == 0 ? 0.0025f : ps.appCamera > 0 ? 0.0012f : 0.0f;
                if (GameManager.phoneLit(p)) light = Math.max(light, 0.003f);
                if (light > 0.0f && facing(gap, 70.0) && Sight.clear(level, getEyePosition(), gap)) {
                    suspicion = Math.min(1.0f, suspicion + light * (float) HitoriConfig.awarenessRate());
                    if (state == State.SEARCH && suspicion > 0.45f && random.nextInt(40) == 0) startFusuma(data, 200);
                }
            }
        }
        // a player out in the open, in its sight: it comes (only while not looked at)
        if (state != State.FUSUMA) {
            for (ServerPlayer p : GameManager.players(level)) {
                if (!GameManager.huntable(p) || GameManager.state(p).hiding()) continue;
                if (data.phase != GameData.PHASE_HIDING) continue;
                if (distanceTo(p) < (Watch.lit(p) ? 14.0 : 8.0) && facing(p.getEyePosition(), 80.0) && Sight.clear(level, getEyePosition(), p.getEyePosition())) {
                    setState(State.HUNT);
                    play(ModSounds.DOLL_GIGGLE.get(), 1.0f, 1.0f);
                    return;
                }
            }
        }
    }

    private void hear(ServerLevel level, GameData data, NoiseSystem.Noise n) {
        double d = n.pos().distanceTo(position().add(0.0, 0.4, 0.0));
        if (d > n.radius() || n.radius() <= 0.0f) return;
        float strength = (float) (1.0 - d / n.radius());
        ServerPlayer src = n.player(level);
        if (src != null && GameManager.huntable(src)) {
            PlayerState ps = GameManager.state(src);
            if (ps.hiding()) {
                float mul = "western".equals(ps.hideSpot) ? 2.0f : 1.0f;
                suspicion = Math.min(1.0f, suspicion + (0.08f + 0.3f * strength) * n.weight() * mul * (float) HitoriConfig.awarenessRate());
                if ((state == State.SEARCH || state == State.INVESTIGATE || state == State.CHECK) && suspicion > 0.45f) {
                    if ("western".equals(ps.hideSpot)) startCheck("western", westernClosetFront(),
                            Layout.marker("western_closet").facing().getOpposite().toYRot(), STAND, 160);
                    else startFusuma(data, 160 + random.nextInt(120));
                } else if (state == State.SEARCH && strength > 0.15f) {
                    investigate(n.pos());
                }
                return;
            }
        }
        if (state == State.SEARCH && (src != null || n.weight() >= 1.2f) && strength > 0.1f) investigate(n.pos());
    }

    private void investigate(Vec3 at) {
        goal = at;
        checkTicks = 60 + random.nextInt(80);
        setState(State.INVESTIGATE);
    }

    private void tickInvestigate(ServerLevel level, GameData data) {
        if (goal == null || stateTicks > 20 * 25) {
            setState(State.SEARCH);
            return;
        }
        if (horizontalDistance(goal) > 1.4 && stateTicks < 20 * 18) {
            go(goal, data);
            return;
        }
        setAnimPose(turnTicks > 0 ? TURN : PEEK);
        if (!seenByEyes) setYHeadRot(getYRot() + Mth.sin(stateTicks * 0.09f) * 70.0f);
        if (--checkTicks <= 0) {
            setState(State.SEARCH);
            pauseTicks = 10;
        }
    }

    // ------------------------------------------------------------------------------------------------ phase 3
    private void tickWait(ServerLevel level, GameData data) {
        if (pose() != SIT && pose() != PEEK && turnTicks <= 0) setAnimPose(SIT);
        if (turnTicks > 0) setAnimPose(TURN);
        if (data.finishStartTick >= 0 && data.playTicks - data.finishStartTick > 20L * 60 * 6) {
            startHunting();
            play(ModSounds.DOLL_GIGGLE.get(), 1.0f, 0.9f);
            return;
        }
        // it moves only while nobody looks
        if (--relocateIn <= 0 && unobserved > 60) {
            List<Layout.Marker> spots = new ArrayList<>(Layout.markers("doll_hides"));
            java.util.Collections.shuffle(spots, new java.util.Random(random.nextLong()));
            for (Layout.Marker m : spots) {
                Vec3 at = Vec3.atBottomCenterOf(m.pos());
                if (at.distanceTo(position()) < 2.0 || observedBySomeone(level, at.add(0, 0.35, 0))) continue;
                moveTo(at.x, at.y, at.z, m.yaw(), 0.0f);
                yBodyRot = m.yaw();
                setYHeadRot(m.yaw());
                setAnimPose(random.nextInt(3) == 0 ? PEEK : SIT);
                break;
            }
            relocateIn = 20 * (40 + random.nextInt(50));
        }
    }

    private boolean observedBySomeone(ServerLevel level, Vec3 at) {
        for (ServerPlayer p : GameManager.players(level)) {
            if (!GameManager.inGame(p)) continue;
            if (Watch.eyesOn(p, at) || Watch.cameraOn(p, at)) return true;
        }
        return false;
    }

    private void tickHunt(ServerLevel level, GameData data) {
        ServerPlayer p = nearestHuntable(level);
        if (p == null) {
            setAnimPose(STAND);
            return;
        }
        PlayerState ps = GameManager.state(p);
        GameManager.reportDanger(p, Mth.clamp(1.0f - distanceTo(p) / 14.0f, 0.3f, 1.0f));
        if (ps.hiding()) {
            // into a closet again: it goes to the fusuma, sure of it
            suspicion = 1.0f;
            if ("western".equals(ps.hideSpot)) startCheck("western", westernClosetFront(), Layout.marker("western_closet").facing().getOpposite().toYRot(), STAND, 100);
            else startFusuma(data, 200);
            return;
        }
        if (seenByEyes) return;
        go(p.position(), data);
        if (!seenByCamera) lookHead(p.getEyePosition());
        if (horizontalDistance(p.position()) < 0.75 && Math.abs(p.getY() - getY()) < 1.6) {
            lungeAt(p.position());
            GameManager.caughtByDoll(p, this);
        }
    }

    @Nullable
    private ServerPlayer nearestHuntable(ServerLevel level) {
        ServerPlayer best = null;
        double bd = 40 * 40;
        for (ServerPlayer p : GameManager.players(level)) {
            if (!GameManager.huntable(p)) continue;
            double d = distanceToSqr(p);
            if (d < bd) {
                bd = d;
                best = p;
            }
        }
        return best;
    }

    /** Collapsing (the model: the knife flies out of its hand at 3.5, it lands on its back at 9). */
    private void tickLimp(ServerLevel level) {
        if (stateTicks == 4 && hasKnife()) {
            setKnife(false);
            play(ModSounds.FIXTURE_THING_FALL.get(), 0.7f, 1.35f);
        }
    }

    private void tickBurn(ServerLevel level) {
        setAnimPose(BURN);
        if (stateTicks % 2 == 0) {
            level.sendParticles(ParticleTypes.FLAME, getX(), getY() + 0.25, getZ(), 2, 0.12, 0.15, 0.12, 0.01);
            level.sendParticles(ModParticles.EMBER.get(), getX(), getY() + 0.4, getZ(), 1, 0.15, 0.2, 0.15, 0.02);
        }
        if (stateTicks % 5 == 0) level.sendParticles(ModParticles.SMOKE.get(), getX(), getY() + 0.6, getZ(), 2, 0.1, 0.1, 0.1, 0.01);
        if (stateTicks % 40 == 1) play(ModSounds.RITUAL_BURN_LOOP.get(), 1.0f, 1.0f);
        if (stateTicks == 60) play(ModSounds.DOLL_SHRIEK.get(), 0.7f, 0.7f);
        if (--burnTicks <= 0) {
            level.sendParticles(ParticleTypes.LARGE_SMOKE, getX(), getY() + 0.3, getZ(), 12, 0.2, 0.2, 0.2, 0.02);
            discard();
        }
    }

    // ------------------------------------------------------------------------------------------------ hiding players
    /** The hidden player in this kind of closet ("oshiire", "western", or null for any). */
    @Nullable
    private ServerPlayer hiddenInCloset(ServerLevel level, @Nullable String spot) {
        for (ServerPlayer p : GameManager.players(level)) {
            if (!GameManager.huntable(p)) continue;
            PlayerState ps = GameManager.state(p);
            if (ps.hiding() && (spot == null || spot.equals(ps.hideSpot))) return p;
        }
        return null;
    }

    private boolean facing(Vec3 at, double halfAngle) {
        Vec3 to = at.subtract(getEyePosition());
        double d = to.length();
        if (d < 1e-3) return true;
        Vec3 look = Vec3.directionFromRotation(0.0f, getYHeadRot());
        double cos = to.scale(1.0 / d).dot(look);
        return cos >= Math.cos(Math.toRadians(halfAngle));
    }

    // ------------------------------------------------------------------------------------------------ sounds, drips, sync
    private void ambient(ServerLevel level) {
        boolean moving = pose() == CRAWL || pose() == WALK;
        setFlag(F_MOVING, moving && (getX() != xo || getZ() != zo));
        if (wetTicks > 0) {
            wetTicks--;
            if (random.nextInt(9) == 0) level.sendParticles(ModParticles.WATER_DRIP.get(), getX(), getY() + 0.3, getZ(), 1, 0.08, 0.05, 0.08, 0.0);
            if (random.nextInt(140) == 0) play(ModSounds.DOLL_DRIP.get(), 0.5f, 1.0f);
        }
        setFlag(F_WET, wetTicks > 0 || state == State.TUB || state == State.DORMANT);
        double dx = getX() - xo, dz = getZ() - zo;
        double step = Math.sqrt(dx * dx + dz * dz);
        if (step > 0.001 && step < 1.0) {
            moved += step;
            if (moved > (pose() == CRAWL ? 0.45 : 0.6)) {
                moved = 0;
                if (pose() == CRAWL) play(ModSounds.DOLL_CRAWL.get(), 0.7f, 0.95f + random.nextFloat() * 0.1f);
                else play(ModSounds.DOLL_STEP.get(), 0.6f, 0.95f + random.nextFloat() * 0.1f);
                if (hasKnife() && random.nextInt(3) == 0) play(ModSounds.DOLL_KNIFE_DRAG.get(), 0.45f, 1.0f);
                if (random.nextInt(4) == 0) play(ModSounds.DOLL_RICE.get(), 0.35f, 1.0f);
                if (wetTicks > 0 && ++stepSound % 2 == 0) Apt.wetMark(level, blockPosition());
            }
        }
        if (movesFreely() && random.nextInt(1800) == 0) play(ModSounds.DOLL_GIGGLE.get(), 0.7f, 0.95f + random.nextFloat() * 0.1f);
    }

    private void sync(ServerLevel level) {
        setFlag(F_ON_CAMERA, seenByCamera);
        setFlag(F_SEEN, seenByEyes);
        setFlag(F_FROZEN, seenByEyes && movesFreely() && pose() != TURN && pose() != LUNGE);
        float g = switch (state) {
            case OPENING, HUNT -> 0.5f;
            case FUSUMA, STANDOFF -> 0.35f + suspicion * 0.4f;
            case BURN -> 0.8f;
            case LIMP, DORMANT -> 0.0f;
            default -> seenByCamera ? 0.5f : 0.2f;
        };
        if (Math.abs(entityData.get(DATA_GLITCH) - g) > 0.02f) entityData.set(DATA_GLITCH, g);
    }

    // ================================================================================================ movement
    @Override
    protected BodyRotationControl createBodyControl() {
        return new BodyRotationControl(this) {
            @Override
            public void clientTick() {
                OniDoll.this.yBodyRot = OniDoll.this.getYRot();
            }
        };
    }

    @Override
    protected PathNavigation createNavigation(Level level) {
        return new DollNavigation(this, level);
    }

    public double horizontalDistance(Vec3 to) {
        double dx = to.x - getX(), dz = to.z - getZ();
        return Math.sqrt(dx * dx + dz * dz);
    }

    private void clearPath() {
        path = null;
        pathGoal = null;
        pathIndex = 0;
    }

    /** Along a planned path (straight when none is found); true on arrival. */
    private boolean walkTo(Vec3 goalPos, double speed) {
        BlockPos gb = BlockPos.containing(goalPos.x, goalPos.y + 0.2, goalPos.z);
        if (path == null || pathGoal == null || pathGoal.distSqr(gb) > 2.5 || ++pathAge > 40) computePath(gb);
        if (horizontalDistance(goalPos) < 0.3) return true;
        if (path == null || pathIndex >= path.size()) return glide(goalPos, speed);
        Vec3 node = pathIndex == path.size() - 1 ? goalPos : path.get(pathIndex);
        if (glide(node, speed)) pathIndex++;
        return horizontalDistance(goalPos) < 0.3;
    }

    private void computePath(BlockPos goalPos) {
        pathGoal = goalPos;
        pathAge = 0;
        pathIndex = 0;
        path = null;
        try {
            Path p = getNavigation().createPath(goalPos, 0);
            if (p == null || p.getNodeCount() == 0) return;
            List<Vec3> pts = new ArrayList<>();
            for (int i = 0; i < p.getNodeCount(); i++) {
                BlockPos n = p.getNodePos(i);
                pts.add(new Vec3(n.getX() + 0.5, n.getY(), n.getZ() + 0.5));
            }
            if (!pts.isEmpty() && horizontalDistance(pts.get(0)) < 0.75) pts.remove(0);
            path = pts;
        } catch (Exception ignored) {
            path = null;
        }
    }

    /** One straight step toward a point, turning the body; true on arrival. Keeps to the floor of the target. */
    private boolean glide(Vec3 to, double speed) {
        double dx = to.x - getX(), dz = to.z - getZ();
        double d = Math.sqrt(dx * dx + dz * dz);
        double y = getY() + Mth.clamp(to.y - getY(), -0.2, 0.2);
        if (d < 1e-4) {
            setPos(getX(), y, getZ());
            return Math.abs(to.y - getY()) < 0.05;
        }
        float yaw = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90.0f;
        turnBody(yaw, seenByCamera ? 45.0f : 25.0f);
        if (d <= speed) {
            setPos(to.x, y, to.z);
            return true;
        }
        setPos(getX() + dx / d * speed, y, getZ() + dz / d * speed);
        return false;
    }

    private void turnBody(float yaw, float maxStep) {
        float cur = getYRot();
        float diff = Mth.wrapDegrees(yaw - cur);
        float n = cur + Mth.clamp(diff, -maxStep, maxStep);
        setYRot(n);
        yBodyRot = n;
        setYHeadRot(n);
    }

    private void lookHead(Vec3 at) {
        Vec3 eye = getEyePosition();
        double dx = at.x - eye.x, dy = at.y - eye.y, dz = at.z - eye.z;
        setYHeadRot((float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90.0f);
        setXRot(Mth.clamp((float) -(Mth.atan2(dy, Math.sqrt(dx * dx + dz * dz)) * Mth.RAD_TO_DEG), -50.0f, 50.0f));
    }

    private int nearestIndex(List<Layout.Marker> l) {
        int best = 0;
        double bd = Double.MAX_VALUE;
        for (int i = 0; i < l.size(); i++) {
            double d = Vec3.atBottomCenterOf(l.get(i).pos()).distanceToSqr(position());
            if (d < bd) {
                bd = d;
                best = i;
            }
        }
        return best;
    }

    private int farthestIndex(List<Layout.Marker> l) {
        int best = 0;
        double bd = -1;
        for (int i = 0; i < l.size(); i++) {
            double d = Vec3.atBottomCenterOf(l.get(i).pos()).distanceToSqr(position());
            if (d > bd) {
                bd = d;
                best = i;
            }
        }
        return best;
    }

    @Nullable
    private static Layout.Marker nearestOf(List<Layout.Marker> l, Vec3 at, double max) {
        Layout.Marker best = null;
        double bd = max * max;
        for (Layout.Marker m : l) {
            double d = Vec3.atBottomCenterOf(m.pos()).distanceToSqr(at);
            if (d < bd) {
                bd = d;
                best = m;
            }
        }
        return best;
    }

    private void play(SoundEvent sound, float volume, float pitch) {
        level().playSound(null, getX(), getY() + 0.3, getZ(), sound, SoundSource.HOSTILE, volume, pitch);
    }

    private void playAt(Vec3 at, SoundEvent sound, float volume, float pitch) {
        level().playSound(null, at.x, at.y, at.z, sound, SoundSource.HOSTILE, volume, pitch);
    }

    // ================================================================================================ interaction
    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (level().isClientSide) return InteractionResult.SUCCESS;
        if (player instanceof ServerPlayer sp && com.hitori.game.Ritual.interactDoll(sp, hand, this)) return InteractionResult.CONSUME;
        return InteractionResult.PASS;
    }

    // ================================================================================================ vanilla overrides
    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return super.hurt(source, amount);
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    protected void doPush(Entity entity) {
    }

    @Override
    public boolean isPickable() {
        return true;
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    @Override
    public boolean canChangeDimensions() {
        return false;
    }

    @Override
    public boolean causeFallDamage(float distance, float multiplier, DamageSource source) {
        return false;
    }

    @Override
    public boolean isInWall() {
        return false;
    }

    @Override
    @Nullable
    protected SoundEvent getAmbientSound() {
        return null;
    }

    @Override
    @Nullable
    protected SoundEvent getHurtSound(DamageSource source) {
        return null;
    }

    @Override
    @Nullable
    protected SoundEvent getDeathSound() {
        return null;
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
    }

    @Override
    public boolean canBeLeashed(Player player) {
        return false;
    }

    @Override
    public boolean isOnFire() {
        return false;
    }

    @Override
    public AABB getBoundingBoxForCulling() {
        return getBoundingBox().inflate(0.6);
    }

    /** Debug: a short description of what it is doing. */
    public String describe() {
        return String.format(java.util.Locale.ROOT, "%s pose=%d susp=%.2f eyes=%s cam=%s route=%d tub=%d at %.1f %.1f %.1f",
                state, pose(), suspicion, seenByEyes, seenByCamera, routeIndex, tubTicks, getX(), getY(), getZ());
    }

    /** Unused direction helper kept for the ending scenes. */
    public static float yawTo(Vec3 from, Vec3 to) {
        return (float) (Mth.atan2(to.z - from.z, to.x - from.x) * Mth.RAD_TO_DEG) - 90.0f;
    }

    public static Direction dirOf(float yaw) {
        return Direction.fromYRot(yaw);
    }
}
