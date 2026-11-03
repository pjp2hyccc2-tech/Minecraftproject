package com.reisatsu.entity;

import com.reisatsu.game.Director;
import com.reisatsu.game.GameData;
import com.reisatsu.game.Spirits;
import com.reisatsu.registry.ModSounds;
import com.reisatsu.world.Fixtures;
import com.reisatsu.world.Layout;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * 来店者 三浦健司: every few minutes the door opens for him (the first time at two o'clock, after the crash far away).
 * He limps in from the road, dripping, sits at table 7, presses the call button and waits; then he is gone, the
 * chair left pulled out, the wet footprints drying. Invisible to the eye. Noticed: the doors lock, he LUNGEs (short).
 * Photo windows: walking in, and seated — his profile shows through the south window from the parking lot.
 */
public class Visitor extends Spirit {
    public static final int WALK = 0, SIT = 1, STAND = 2, PRESS = 3, NOTICE = 4, LUNGE = 5;
    private static final int S_ARRIVE = 0, S_SEATED = 1, S_LEAVE = 2;
    private int stage = S_ARRIVE;
    private int pathIdx;
    private int stageTicks;
    private int stayTicks;
    private int nextPress;
    private boolean outerOpened, innerOpened;
    private double printAccum;
    private Vec3 lastPos;
    private boolean printSide;
    private Vec3 lungeFrom;

    public Visitor(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
    }

    @Override
    public SpiritKind kind() {
        return SpiritKind.VISITOR;
    }

    /** Arrivals: the first at "two o'clock" after the crash cue, then every 3–5 minutes. */
    public static void schedule(ServerLevel level, GameData data) {
        if (data.isCaptured(SpiritKind.VISITOR)) return;
        if (Spirits.get(level, SpiritKind.VISITOR) != null) return;
        if (data.visitorNext == -1) {
            data.visitorNext = data.playTicks + 20 * 100;
            data.setDirty();
        }
        if (data.visitorNext == -2) { // he was here when the world was saved
            data.visitorNext = data.playTicks + 20 * 20;
            data.setDirty();
        }
        long until = data.visitorNext - data.playTicks;
        if (data.visitorArrivals == 0 && until <= 20 * 13 && data.once("crash_cue")) Director.crashCue(level);
        if (until <= 0) {
            Layout.Marker m = Layout.marker("visitor_spawn");
            Spirit s = Spirits.spawnAt(level, SpiritKind.VISITOR, m.center(), m.yaw());
            if (s != null) {
                s.extra = false;
                data.visitorArrivals++;
                data.visitorNext = -2;
                data.setDirty();
            }
        }
    }

    @Override
    public float coneHalfAngle() {
        return 50.0f;
    }

    @Override
    public float rangeMul() {
        return 0.8f;
    }

    @Override
    public float hearingMul() {
        return 0.6f;
    }

    @Override
    protected boolean investigates() {
        return false;
    }

    @Override
    public boolean photogenic() {
        int p = pose();
        return (stage == S_ARRIVE && p == WALK) || (stage == S_SEATED && (p == SIT || p == PRESS));
    }

    @Override
    protected double huntSpeed() {
        return 0.3;
    }

    @Override
    protected double walkSpeed() {
        return 0.05;
    }

    @Override
    protected boolean mayHunt(ServerPlayer p) {
        Vec3 from = lungeFrom != null ? lungeFrom : position();
        return p.position().distanceTo(from) < 8.0 && modeTicks < 20 * 8;
    }

    @Override
    protected int poseWalk() {
        return WALK;
    }

    @Override
    protected int poseStand() {
        return stage == S_SEATED ? SIT : STAND;
    }

    @Override
    protected int poseNotice() {
        return NOTICE;
    }

    @Override
    protected int poseHunt() {
        return LUNGE;
    }

    @Override
    protected Vec3 home() {
        return stage == S_ARRIVE ? Layout.marker("visitor_path").center() : Layout.marker("seat_7").center();
    }

    @Override
    protected void onNotice(ServerPlayer p) {
        super.onNotice(p);
        play(ModSounds.VISITOR_VISOR.get(), 1.0f, 1.0f);
        if (level() instanceof ServerLevel sl) Fixtures.setAutoDoorsLocked(sl, true);
        lungeFrom = position();
    }

    @Override
    protected void onHuntEnd(boolean caught) {
        if (level() instanceof ServerLevel sl) Fixtures.setAutoDoorsLocked(sl, false);
        stage = S_SEATED;
        stayTicks = Math.max(stayTicks, 20 * 40);
    }

    @Override
    protected void resumeRoutine() {
        if (stage == S_SEATED) {
            Layout.Marker seat = Layout.marker("seat_7");
            moveTo(seat.center().x, seat.center().y, seat.center().z, seat.yaw(), 0.0f);
            faceYaw(seat.yaw());
            setAnimPose(SIT);
        }
    }

    @Override
    protected void routine(ServerLevel level) {
        stageTicks++;
        switch (stage) {
            case S_ARRIVE -> arrive(level);
            case S_SEATED -> seated(level);
            case S_LEAVE -> {
                if (stageTicks > 30) leave(level);
            }
            default -> {
            }
        }
    }

    private void arrive(ServerLevel level) {
        List<Layout.Marker> path = Layout.markers("visitor_path");
        setAnimPose(WALK);
        if (pathIdx >= path.size()) {
            stage = S_SEATED;
            stageTicks = 0;
            Layout.Marker seat = Layout.marker("seat_7");
            moveTo(seat.center().x, seat.center().y, seat.center().z, seat.yaw(), 0.0f);
            faceYaw(seat.yaw());
            setAnimPose(SIT);
            Fixtures.pullOutChair(level, 7, true);
            stayTicks = 20 * (120 + random.nextInt(60));
            nextPress = 20 * (8 + random.nextInt(12));
            return;
        }
        Vec3 target = path.get(pathIdx).center();
        if (walkTo(target, walkSpeed())) pathIdx++;
        // the doors open for him, with their chime
        if (!outerOpened && position().distanceToSqr(Layout.marker("door_outer").center()) < 6.5) {
            outerOpened = true;
            Fixtures.openAutoDoor(level, "outer", 50);
        }
        if (!innerOpened && position().distanceToSqr(Layout.marker("door_inner").center()) < 6.5) {
            innerOpened = true;
            Fixtures.openAutoDoor(level, "inner", 50);
        }
        // wet footprints, one foot dragging
        if (lastPos != null) printAccum += lastPos.distanceTo(position());
        lastPos = position();
        if (printAccum > 0.85) {
            printAccum = 0;
            printSide = !printSide;
            float yaw = getYRot();
            double side = printSide ? 0.16 : -0.16;
            double rx = Mth.cos(yaw * Mth.DEG_TO_RAD) * side, rz = Mth.sin(yaw * Mth.DEG_TO_RAD) * side;
            Fixtures.wetFootprint(level, position().add(rx, 0.02, rz), yaw);
            play(ModSounds.VISITOR_STEP.get(), 0.55f, 0.9f + random.nextFloat() * 0.15f);
        }
    }

    private void seated(ServerLevel level) {
        if (pose() == PRESS && poseTicks() > 30) setAnimPose(SIT);
        if (pose() != SIT && pose() != PRESS) setAnimPose(SIT);
        if (--nextPress <= 0) {
            setAnimPose(PRESS);
            Fixtures.ringCallButton(level, 7);
            nextPress = 20 * (30 + random.nextInt(30));
        }
        if (--stayTicks <= 0) {
            stage = S_LEAVE;
            stageTicks = 0;
            setAnimPose(STAND);
        }
    }

    private void leave(ServerLevel level) {
        play(ModSounds.SPIRIT_VANISH.get(), 0.6f, 1.0f);
        GameData data = GameData.get(level.getServer());
        data.visitorNext = data.playTicks + 20 * (180 + random.nextInt(120));
        data.setDirty();
        discard();
    }

    @Override
    protected void ambient(ServerLevel level) {
        if (tickCount % 61 == 0) play(ModSounds.VISITOR_BREATH.get(), 0.45f, 0.95f + random.nextFloat() * 0.1f);
        if (tickCount % 47 == 0 && random.nextBoolean()) play(ModSounds.VISITOR_DRIP.get(), 0.35f, 1.0f);
        if (tickCount % 6 == 0) {
            level.sendParticles(com.reisatsu.registry.ModParticles.WATER_DROP.get(), getX() + (random.nextDouble() - 0.5) * 0.5,
                    getY() + 0.4 + random.nextDouble(), getZ() + (random.nextDouble() - 0.5) * 0.5, 1, 0.0, 0.0, 0.0, 0.0);
        }
        if (stage == S_SEATED && random.nextInt(900) == 0) {
            play(ModSounds.VISITOR_WHISPER.get(), 0.5f, 1.0f);
            ServerPlayer p = nearestPlayer(level, 7.0);
            if (p != null) com.reisatsu.game.Story.say(p, "visitor", 60, "dialogue.reisatsu.visitor.whisper");
        }
    }
}
