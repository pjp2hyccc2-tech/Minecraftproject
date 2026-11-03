package com.reisatsu.entity;

import com.reisatsu.game.GameManager;
import com.reisatsu.game.PlayerState;
import com.reisatsu.registry.ModSounds;
import com.reisatsu.world.Fixtures;
import com.reisatsu.world.Layout;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * 鏡の女 白石玲子: on the other side of the mirror over the women's sinks, and never leaves it. The taps run while she
 * leans over the dead sink, face in her hands, crying (she cannot see you, but she hears breathing within 4 blocks);
 * then the taps stop by themselves, she straightens and STAREs through the glass, presses her palms on it, and bows
 * again. Noticed: she SCREAMs, the mirror cracks, the lights burst — leave the restroom within 3 s or be taken.
 * Seen only through the mirror, faintly. Photo window: BOWED, through the mirror.
 */
public class MirrorWoman extends Spirit {
    public static final int BOWED = 0, STARE = 1, PRESS = 2, SCREAM = 3, STAND = 4;
    private static final int S_BOWED = 0, S_STARE = 1, S_PRESS = 2, S_SCREAM = 3;
    private int sub = S_BOWED;
    private int subTicks;
    private int bowedFor = 20 * 40;
    private int stareFor = 20 * 10;
    private boolean tapsOn;
    @Nullable private UUID screamAt;

    public MirrorWoman(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
    }

    @Override
    public SpiritKind kind() {
        return SpiritKind.MIRROR_WOMAN;
    }

    @Override
    public float coneHalfAngle() {
        return sub == S_STARE || sub == S_PRESS ? 70.0f : 0.0f;
    }

    @Override
    public float rangeMul() {
        return 1.1f;
    }

    @Override
    public float breathRange() {
        return sub == S_BOWED ? 4.0f : 0.0f;
    }

    @Override
    public float hearingMul() {
        return 0.4f;
    }

    @Override
    protected boolean investigates() {
        return false;
    }

    @Override
    public boolean photogenic() {
        return pose() == BOWED;
    }

    @Override
    protected boolean mayHunt(ServerPlayer p) {
        return false;
    }

    @Override
    protected int poseWalk() {
        return STAND;
    }

    @Override
    protected int poseStand() {
        return BOWED;
    }

    @Override
    protected int poseNotice() {
        return SCREAM;
    }

    @Override
    protected int poseHunt() {
        return SCREAM;
    }

    @Override
    protected Vec3 home() {
        return Layout.marker("mirror_woman").center();
    }

    @Override
    protected double floorY() {
        return Layout.marker("mirror_woman").pos().getY();
    }

    /** Noticed: the scream; leave the women's restroom within 3 s. */
    @Override
    protected void onAwarenessFull(ServerPlayer p) {
        if (!(level() instanceof ServerLevel level)) return;
        interrupted();
        sub = S_SCREAM;
        subTicks = 0;
        screamAt = p.getUUID();
        setAnimPose(SCREAM);
        play(ModSounds.MIRROR_SCREAM.get(), 1.8f, 1.0f);
        Fixtures.crackMirror(level, true);
        for (BlockPos l : Fixtures.lightsIn(level, "womens")) Fixtures.burstLight(level, l);
        GameManager.event(p, GameManager.EV_SCREAM, 60, "");
        GameManager.onNoticed(p, this);
    }

    /** Standing in the mirror's wall, palms on the pane (the pane is the mirror block's far 1 px, toward the women's room). */
    private static Vec3 pressSpot() {
        Layout.Marker home = Layout.marker("mirror_woman");
        if (!Layout.has("mirror")) return home.center();
        net.minecraft.core.Direction f = home.facing();
        BlockPos m = Layout.marker("mirror").pos();
        double pane = (f.getAxis() == net.minecraft.core.Direction.Axis.Z ? m.getZ() : m.getX()) + 0.5
                + (f.getAxisDirection() == net.minecraft.core.Direction.AxisDirection.POSITIVE ? 1 : -1) * (0.5 - 1.0 / 16.0);
        Vec3 c = home.center();
        double along = pane - (f.getAxisDirection() == net.minecraft.core.Direction.AxisDirection.POSITIVE ? 1 : -1) * 0.45;
        return f.getAxis() == net.minecraft.core.Direction.Axis.Z ? new Vec3(c.x, c.y, along) : new Vec3(along, c.y, c.z);
    }

    @Override
    protected void interrupted() {
        setTaps(false);
    }

    @Override
    protected void resumeRoutine() {
        sub = S_BOWED;
        subTicks = 0;
    }

    private void setTaps(boolean on) {
        if (tapsOn == on || !(level() instanceof ServerLevel level)) return;
        tapsOn = on;
        if (!Layout.has("sink_w")) return;
        for (Layout.Marker m : Layout.markers("sink_w")) Fixtures.setTap(level, m.pos(), on);
    }

    @Override
    protected void routine(ServerLevel level) {
        Layout.Marker home = Layout.marker("mirror_woman");
        // at home in the cavity; for PRESS she rushes to the glass (her model's palms land ~0.45 ahead of her)
        Vec3 want = sub == S_PRESS ? pressSpot() : home.center();
        double d = horizontalDistance(want);
        if (d > 0.02) {
            double step = sub == S_PRESS ? 0.35 : d > 3.0 ? d : 0.08;
            Vec3 c = position();
            Vec3 to = d <= step ? want : c.add(new Vec3(want.x - c.x, 0.0, want.z - c.z).normalize().scale(step));
            moveTo(to.x, want.y, to.z, home.yaw(), getXRot());
        }
        subTicks++;
        switch (sub) {
            case S_BOWED -> {
                faceYaw(home.yaw());
                setXRot(30.0f);
                setAnimPose(BOWED);
                if (subTicks == 1) {
                    setTaps(true);
                    bowedFor = 20 * (30 + random.nextInt(21));
                }
                if (subTicks % 70 == 10) play(ModSounds.MIRROR_SOB.get(), 0.7f, 0.95f + random.nextFloat() * 0.1f);
                if (subTicks > bowedFor) {
                    setTaps(false);
                    sub = S_STARE;
                    subTicks = 0;
                    stareFor = 20 * (8 + random.nextInt(5));
                }
            }
            case S_STARE -> {
                setAnimPose(STARE);
                setXRot(0.0f);
                ServerPlayer p = nearestPlayer(level, 10.0);
                if (p != null && subTicks > 30) lookHead(p.getEyePosition());
                else faceYaw(home.yaw());
                if (subTicks == 40 && random.nextInt(3) == 0) play(ModSounds.MIRROR_KNOCK.get(), 0.8f, 1.0f);
                if (subTicks > stareFor) {
                    sub = S_PRESS;
                    subTicks = 0;
                    setAnimPose(PRESS);
                    play(ModSounds.MIRROR_SQUEAK.get(), 0.9f, 1.0f);
                    Fixtures.handPrints(level);
                }
            }
            case S_PRESS -> {
                if (subTicks > 60) {
                    sub = S_BOWED;
                    subTicks = 0;
                }
            }
            case S_SCREAM -> tickScream(level);
            default -> {
            }
        }
    }

    @Override
    protected void ambient(ServerLevel level) {
        if (tickCount % 9 == 0) {
            level.sendParticles(com.reisatsu.registry.ModParticles.WATER_DROP.get(), getX() + (random.nextDouble() - 0.5) * 0.5,
                    getY() + 0.9, getZ() + (random.nextDouble() - 0.5) * 0.5, 1, 0.02, 0.0, 0.02, 0.0);
        }
        // someone stands at her sink with their back to the mirror
        if (sub == S_STARE && subTicks == 60) {
            for (ServerPlayer p : GameManager.players(level)) {
                if (!GameManager.huntable(p) || !GameManager.state(p).room.equals("womens") || p.distanceTo(this) > 7) continue;
                double away = p.getViewVector(1.0f).dot(position().subtract(p.position()).normalize());
                if (away < -0.3) {
                    play(ModSounds.MIRROR_WHISPER.get(), 0.8f, 1.0f);
                    com.reisatsu.game.Story.say(p, "mirror_woman", 50, "dialogue.reisatsu.mirror.whisper");
                }
            }
        }
    }

    private void tickScream(ServerLevel level) {
        if (sub != S_SCREAM) return;
        if (subTicks >= 60) {
            ServerPlayer p = GameManager.player(level, screamAt);
            if (p != null && GameManager.huntable(p)) {
                PlayerState ps = GameManager.state(p);
                if (ps.room.equals("womens")) GameManager.caught(p, this);
            }
            screamAt = null;
            waryTicks = 20 * 30;
            sub = S_BOWED;
            subTicks = 0;
            setAnimPose(BOWED);
            for (ServerPlayer pl : GameManager.players(level)) if (awarenessOf(pl) > 0) setAwareness(pl, 0.4f);
        }
    }
}
