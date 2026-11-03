package com.reisatsu.entity;

import com.reisatsu.game.GameManager;
import com.reisatsu.game.Spirits;
import com.reisatsu.game.Story;
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

import java.util.List;

/**
 * かくれんぼの子 春日ゆうか: still playing hide-and-seek. 「もういいかい」 from nowhere when you first enter the
 * customer area; then she runs (small feet, a chair scrapes) to one of her hiding places and crouches with her hands
 * over her eyes. Every 6–10 s she PEEKs: if she sees you, 「みーつけた」 — not a capture: she giggles, runs to a new
 * place and calls the nearest other spirit to where you are. Every few minutes she counts at the pillar.
 * She hears breathing within 3 blocks. Invisible to the eye. Photo window: hiding, hands over her eyes.
 */
public class Child extends Spirit {
    public static final int RUN = 0, COUNT = 1, HIDE = 2, PEEK = 3, FOUND = 4, STAND = 5, GIGGLE = 6;
    private static final int S_WAIT = 0, S_RUN = 1, S_HIDE = 2, S_FOUND = 3, S_TO_COUNT = 4, S_COUNT = 5;
    private int sub = S_WAIT;
    private int subTicks;
    private int hideIdx = -1;
    private int nextPeek;
    private int peekTicks;
    /** Seconds until she goes back to count at the pillar. */
    private int nextCount = 150;
    private boolean started;

    public Child(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
    }

    @Override
    public SpiritKind kind() {
        return SpiritKind.CHILD;
    }

    /** 「もういいかい」: the game begins. */
    public void startGame(ServerPlayer p) {
        started = true;
        runToNewHide();
    }

    @Override
    public float coneHalfAngle() {
        return pose() == PEEK ? 70.0f : 0.0f;
    }

    @Override
    public float fixedRange() {
        return 9.0f;
    }

    @Override
    public float breathRange() {
        return 3.0f;
    }

    @Override
    public float hearingMul() {
        return 0.5f;
    }

    @Override
    protected boolean investigates() {
        return false;
    }

    @Override
    public boolean photogenic() {
        return pose() == HIDE && sub == S_HIDE;
    }

    @Override
    public float faceAngle() {
        return 130.0f;
    }

    @Override
    protected double walkSpeed() {
        return 0.16;
    }

    @Override
    protected int poseWalk() {
        return RUN;
    }

    @Override
    protected int poseStand() {
        return STAND;
    }

    @Override
    protected int poseNotice() {
        return FOUND;
    }

    @Override
    protected int poseHunt() {
        return RUN;
    }

    @Override
    protected Vec3 home() {
        List<Layout.Marker> hides = Layout.markers("child_hides");
        return hideIdx >= 0 && hideIdx < hides.size() ? hides.get(hideIdx).center() : Layout.marker("child_count").center();
    }

    /** She never hunts: being seen means 「みーつけた」. */
    @Override
    protected void onAwarenessFull(ServerPlayer p) {
        found(p);
    }

    private void found(ServerPlayer p) {
        sub = S_FOUND;
        subTicks = 0;
        setAnimPose(FOUND);
        faceTowards(p.getEyePosition());
        play(ModSounds.CHILD_MITSUKETA.get(), 1.1f, 1.0f);
        Story.say(p, "child", 60, "dialogue.reisatsu.child.mitsuketa");
        GameManager.addFear(p, 0.22f);
        GameManager.event(p, GameManager.EV_STINGER, 1, "");
        // she calls the nearest other spirit to where you are
        if (level() instanceof ServerLevel sl) {
            Spirit best = null;
            double bd = Double.MAX_VALUE;
            for (Spirit s : Spirits.all(sl)) {
                if (s == this || s.mode() == Mode.BOUND || s.mode() == Mode.EXORCISM || s.mode() == Mode.FROZEN) continue;
                if (s.kind() == SpiritKind.MIRROR_WOMAN || s.kind() == SpiritKind.VISITOR) continue;
                double d = s.distanceToSqr(p);
                if (d < bd) {
                    bd = d;
                    best = s;
                }
            }
            if (best != null) {
                best.raise(p, 0.5f);
                if (best.mode() == Mode.ROUTINE) best.startInvestigate(p.position());
            }
        }
        setAwareness(p, 0.0f);
    }

    private void runToNewHide() {
        List<Layout.Marker> hides = Layout.markers("child_hides");
        int n = hides.size();
        int next = random.nextInt(n);
        if (n > 1 && next == hideIdx) next = (next + 1 + random.nextInt(n - 1)) % n;
        hideIdx = next;
        sub = S_RUN;
        subTicks = 0;
        setAnimPose(RUN);
    }

    @Override
    protected void resumeRoutine() {
        if (started) runToNewHide();
    }

    @Override
    protected void routine(ServerLevel level) {
        subTicks++;
        switch (sub) {
            case S_WAIT -> {
                Layout.Marker c = Layout.marker("child_count");
                setAnimPose(COUNT);
                if (horizontalDistance(c.center()) > 0.3) {
                    Vec3 cc = c.center();
                    moveTo(cc.x, cc.y, cc.z, c.yaw(), 0.0f);
                }
                faceYaw(c.yaw());
                if (started) runToNewHide();
            }
            case S_RUN -> {
                Vec3 to = home();
                setAnimPose(RUN);
                if (subTicks % 6 == 0) play(ModSounds.CHILD_RUN.get(), 0.6f, 1.0f + random.nextFloat() * 0.1f);
                if (walkTo(to, walkSpeed()) || subTicks > 20 * 20) {
                    List<Layout.Marker> hides = Layout.markers("child_hides");
                    Layout.Marker h = hides.get(hideIdx);
                    Vec3 hc = h.center();
                    moveTo(hc.x, hc.y, hc.z, h.yaw(), 0.0f);
                    faceYaw(h.yaw());
                    sub = S_HIDE;
                    subTicks = 0;
                    setAnimPose(HIDE);
                    nextPeek = 20 * (6 + random.nextInt(5));
                    if (random.nextInt(2) == 0) {
                        List<BlockPos> chairs = Fixtures.chairsNear(level, blockPosition(), 3);
                        if (!chairs.isEmpty()) Fixtures.knockChair(level, chairs.get(random.nextInt(chairs.size())), false);
                    }
                }
            }
            case S_HIDE -> {
                if (peekTicks > 0) {
                    peekTicks--;
                    setAnimPose(PEEK);
                    if (peekTicks == 0) {
                        setAnimPose(HIDE);
                        nextPeek = 20 * (6 + random.nextInt(5));
                    }
                } else if (--nextPeek <= 0) {
                    peekTicks = 40;
                    setAnimPose(PEEK);
                } else {
                    setAnimPose(HIDE);
                }
                if (random.nextInt(500) == 0) play(ModSounds.CHILD_GIGGLE.get(), 0.6f, 1.0f);
                if (random.nextInt(1400) == 0) play(ModSounds.CHILD_HUM.get(), 0.5f, 1.0f);
                if (started && level.getGameTime() % 20 == 0 && --nextCount <= 0) {
                    nextCount = 150 + random.nextInt(90);
                    sub = S_TO_COUNT;
                    subTicks = 0;
                }
            }
            case S_FOUND -> {
                if (subTicks == 30) {
                    setAnimPose(GIGGLE);
                    play(ModSounds.CHILD_GIGGLE.get(), 0.9f, 1.05f);
                }
                if (subTicks > 55) runToNewHide();
            }
            case S_TO_COUNT -> {
                Layout.Marker c = Layout.marker("child_count");
                setAnimPose(RUN);
                if (subTicks % 6 == 0) play(ModSounds.CHILD_RUN.get(), 0.5f, 1.0f);
                if (walkTo(c.center(), walkSpeed()) || subTicks > 20 * 20) {
                    faceYaw(c.yaw());
                    sub = S_COUNT;
                    subTicks = 0;
                    setAnimPose(COUNT);
                    play(ModSounds.CHILD_COUNT.get(), 1.0f, 1.0f);
                    ServerPlayer near = nearestPlayer(level, 24.0);
                    if (near != null) Story.say(near, "child", 160, "dialogue.reisatsu.child.count");
                }
            }
            case S_COUNT -> {
                setAnimPose(COUNT);
                if (subTicks == 20 * 8 + 10) {
                    play(ModSounds.CHILD_MOUI.get(), 0.9f, 1.0f);
                    ServerPlayer near = nearestPlayer(level, 24.0);
                    if (near != null) Story.say(near, "child", 60, "dialogue.reisatsu.child.moui");
                }
                if (subTicks > 20 * 10) runToNewHide();
            }
            default -> {
            }
        }
    }

    @Override
    protected void ambient(ServerLevel level) {
        if (sub == S_HIDE && random.nextInt(2400) == 0) {
            ServerPlayer near = nearestPlayer(level, 6.0);
            if (near != null) {
                play(ModSounds.CHILD_WHISPER.get(), 0.6f, 1.0f);
                Story.say(near, "child", 50, "dialogue.reisatsu.child.whisper");
            }
        }
    }
}
