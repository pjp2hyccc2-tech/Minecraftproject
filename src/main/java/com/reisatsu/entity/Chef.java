package com.reisatsu.entity;

import com.reisatsu.game.Director;
import com.reisatsu.game.GameManager;
import com.reisatsu.registry.ModParticles;
import com.reisatsu.registry.ModSounds;
import com.reisatsu.world.Fixtures;
import com.reisatsu.world.Layout;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * 料理長 権藤剛: chops at his board, back to the pass window; every few seconds the chopping stops and his head turns
 * slowly over his shoulder. Tends the stove (the flames roar: he hears nothing), plates at the pass with his head
 * bowed and rings the bell. Burnt black, embers in the cracks; visible to the eye. Noticed: he roars, slams the
 * cleaver and charges — inside the kitchen and the corridor only.
 * Photo windows: through the pass window while he plates, or from the side at the stove.
 */
public class Chef extends Spirit {
    public static final int CHOP = 0, STOVE = 1, PLATE = 2, LOOK = 3, WALK = 4, CHASE = 5, IDLE = 6;
    private static final int S_CHOP = 0, S_TO_STOVE = 1, S_STOVE = 2, S_TO_PASS = 3, S_PLATE = 4, S_TO_BOARD = 5;
    private int sub = S_TO_BOARD;
    private int subTicks;
    private int chopFor = 20 * 30;
    private float lookSide = 1.0f;
    private int nextLook = 20 * 8;
    private int lookTicks;

    public Chef(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
    }

    @Override
    public SpiritKind kind() {
        return SpiritKind.CHEF;
    }

    @Override
    public float coneHalfAngle() {
        return switch (pose()) {
            case STOVE -> 20.0f;
            case PLATE -> 30.0f;
            case CHOP -> 35.0f;
            default -> 60.0f;
        };
    }

    @Override
    public float lookDownTilt() {
        return switch (pose()) {
            case PLATE -> 40.0f;
            case CHOP -> 35.0f;
            default -> 0.0f;
        };
    }

    @Override
    public float fixedRange() {
        return pose() == PLATE ? 3.0f : 0.0f;
    }

    @Override
    public float hearingMulAt(Vec3 pos) {
        if (pose() == STOVE) return 0.0f; // the flames roar
        Layoutish r = Layoutish.of(pos);
        return r == Layoutish.KITCHEN ? 1.5f : 0.7f;
    }

    private enum Layoutish {
        KITCHEN, OTHER;

        static Layoutish of(Vec3 pos) {
            Layout.Room r = Layout.roomAt(net.minecraft.core.BlockPos.containing(pos));
            return r != null && (r.key().equals("kitchen") || r.key().equals("fridge")) ? KITCHEN : OTHER;
        }
    }

    @Override
    public boolean photogenic() {
        int p = pose();
        return p == PLATE || p == STOVE || p == IDLE || p == WALK;
    }

    @Override
    protected double huntSpeed() {
        return 0.27;
    }

    @Override
    protected double walkSpeed() {
        return 0.07;
    }

    @Override
    protected boolean mayHunt(ServerPlayer p) {
        String r = GameManager.state(p).room;
        return r.equals("kitchen") || r.equals("fridge") || r.equals("corridor") || distanceTo(p) < 2.5;
    }

    @Override
    protected int poseWalk() {
        return WALK;
    }

    @Override
    protected int poseStand() {
        return IDLE;
    }

    @Override
    protected int poseNotice() {
        return LOOK;
    }

    @Override
    protected int poseHunt() {
        return CHASE;
    }

    @Override
    protected int poseLook() {
        return IDLE;
    }

    @Override
    protected Vec3 home() {
        return Layout.marker("chef_board").center();
    }

    @Override
    protected void onNotice(ServerPlayer p) {
        super.onNotice(p);
        play(ModSounds.CHEF_ROAR.get(), 1.6f, 1.0f);
        play(ModSounds.CHEF_SCRAPE.get(), 1.0f, 0.9f);
    }

    @Override
    protected int noticeTicks() {
        return 26;
    }

    @Override
    protected void interrupted() {
        if (level() instanceof ServerLevel sl) Fixtures.setDevice(sl, "gas_range", false);
    }

    @Override
    protected void resumeRoutine() {
        sub = S_TO_BOARD;
        subTicks = 0;
    }

    @Override
    protected void routine(ServerLevel level) {
        subTicks++;
        switch (sub) {
            case S_TO_BOARD -> {
                Layout.Marker b = Layout.marker("chef_board");
                setAnimPose(WALK);
                if (walkTo(b.center(), walkSpeed())) {
                    faceYaw(b.yaw());
                    sub = S_CHOP;
                    subTicks = 0;
                    chopFor = 20 * (25 + random.nextInt(16));
                    nextLook = 20 * (6 + random.nextInt(7));
                    setAnimPose(CHOP);
                }
            }
            case S_CHOP -> {
                Layout.Marker b = Layout.marker("chef_board");
                if (lookTicks > 0) {
                    // the head turns slowly over the shoulder; the body stays at the board
                    lookTicks--;
                    setAnimPose(LOOK);
                    // his model turns the head ~160 degrees over the shoulder on its own, toward the side of netHeadYaw:
                    // the side where you are (and his sight cone goes with it)
                    setYHeadRot(b.yaw() + lookSide * 150.0f);
                    if (lookTicks == 0) {
                        setYHeadRot(b.yaw());
                        setAnimPose(CHOP);
                        nextLook = 20 * (6 + random.nextInt(7));
                    }
                } else {
                    setAnimPose(CHOP);
                    setYHeadRot(b.yaw());
                    // the chopping itself is a client loop in step with the cleaver (HorrorAudio.tickWork)
                    if (--nextLook <= 0) {
                        play(ModSounds.CHEF_CHOP_STOP.get(), 1.0f, 1.0f);
                        lookTicks = 40 + random.nextInt(21);
                        lookSide = sideToward(level);
                    }
                }
                if (subTicks > chopFor && lookTicks == 0) {
                    sub = S_TO_STOVE;
                    subTicks = 0;
                }
            }
            case S_TO_STOVE -> {
                Layout.Marker s = Layout.marker("chef_stove");
                setAnimPose(WALK);
                if (walkTo(s.center(), walkSpeed())) {
                    faceYaw(s.yaw());
                    sub = S_STOVE;
                    subTicks = 0;
                    setAnimPose(STOVE);
                    Fixtures.setDevice(level, "gas_range", true);
                    play(ModSounds.FIXTURE_GAS_IGNITE.get(), 1.0f, 1.0f);
                    Director.happen(level, "gas", 20 * 8, position().add(0, 1.2, 0));
                }
            }
            case S_STOVE -> {
                if (subTicks % 40 == 5) play(ModSounds.CHEF_SIZZLE.get(), 0.8f, 1.0f);
                if (subTicks > 20 * 8) {
                    Fixtures.setDevice(level, "gas_range", false);
                    sub = S_TO_PASS;
                    subTicks = 0;
                }
            }
            case S_TO_PASS -> {
                Layout.Marker pm = Layout.marker("chef_pass");
                setAnimPose(WALK);
                if (walkTo(pm.center(), walkSpeed())) {
                    faceYaw(pm.yaw());
                    sub = S_PLATE;
                    subTicks = 0;
                    setAnimPose(PLATE);
                    setXRot(35.0f);
                }
            }
            case S_PLATE -> {
                // his model taps the bell at pose tick 100
                if (pose() == PLATE && poseTicks() == 100) {
                    Vec3 bell = Layout.has("pass_bell") ? Layout.marker("pass_bell").center() : position();
                    playAt(bell, ModSounds.FIXTURE_PASS_BELL.get(), 1.0f, 1.0f);
                }
                if (subTicks > 20 * 6) {
                    setXRot(0.0f);
                    sub = S_TO_BOARD;
                    subTicks = 0;
                }
            }
            default -> {
            }
        }
    }

    /** +1 when the nearest player is on his right (positive head yaw), -1 on his left; random when nobody is near. */
    private float sideToward(ServerLevel level) {
        ServerPlayer p = nearestPlayer(level, 18.0);
        if (p == null) return random.nextBoolean() ? 1.0f : -1.0f;
        float toward = (float) (Mth.atan2(p.getZ() - getZ(), p.getX() - getX()) * Mth.RAD_TO_DEG) - 90.0f;
        return Mth.wrapDegrees(toward - getYRot()) >= 0.0f ? 1.0f : -1.0f;
    }

    @Override
    protected void ambient(ServerLevel level) {
        if (tickCount % 3 == 0 && random.nextInt(3) == 0) {
            double a = random.nextDouble() * Math.PI * 2;
            level.sendParticles(ModParticles.EMBER.get(), getX() + Math.cos(a) * 0.3, getY() + 1.6 + random.nextDouble() * 0.5,
                    getZ() + Math.sin(a) * 0.3, 1, 0.05, 0.05, 0.05, 0.01);
        }
        if (tickCount % 90 == 0) play(ModSounds.CHEF_BREATH.get(), 0.5f, 0.9f + random.nextFloat() * 0.15f);
        if ((pose() == WALK || pose() == CHASE) && tickCount % (pose() == CHASE ? 7 : 12) == 0) play(ModSounds.CHEF_STEP.get(), 0.7f, 1.0f);
    }
}
