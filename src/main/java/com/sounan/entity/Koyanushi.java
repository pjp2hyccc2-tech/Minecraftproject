package com.sounan.entity;

import com.sounan.game.GameManager;
import com.sounan.game.NoiseSystem;
import com.sounan.registry.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;

/**
 * 小屋主: sleeps in the futon at the back of an empty hut, breathing wetly. Noise, light in its face, or walking upright
 * right beside it wakes it — it sits up, slowly, joints cracking — and it chases you out of its house. Lose it and it
 * goes home to sleep again.
 */
public class Koyanushi extends SounanMonster {
    public static final int SLEEP = 0, WAKE = 1, CHASE = 2, FACE_WALL = 3, STAND = 4;
    private static final int S_SLEEP = 0, S_WAKE = 1, S_CHASE = 2, S_HOME = 3;
    private int state = S_SLEEP;
    @Nullable private BlockPos home;
    private Direction homeFacing = Direction.EAST;
    private int lit;
    private int close;
    private int lost;
    private int mumbleIn = 200;

    public Koyanushi(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
        temporary = false;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return baseAttributes(0.25, 40);
    }

    public void setHome(BlockPos pos, Direction facing) {
        home = pos;
        homeFacing = facing;
        lieDown();
    }

    @Nullable
    public BlockPos home() {
        return home;
    }

    private void lieDown() {
        state = S_SLEEP;
        stateTicks = 0;
        setAnimPose(SLEEP);
        getNavigation().stop();
        if (home != null) {
            moveTo(home.getX() + 0.5, home.getY() + 0.05, home.getZ() + 0.5, homeFacing.toYRot(), 0.0f);
            setYHeadRot(homeFacing.toYRot());
            yBodyRot = homeFacing.toYRot();
        }
    }

    public boolean asleep() {
        return state == S_SLEEP;
    }

    @Override
    public boolean hostileNow() {
        return state == S_WAKE || state == S_CHASE;
    }

    public void wake(@Nullable ServerPlayer by) {
        if (state != S_SLEEP && state != S_HOME) return;
        state = S_WAKE;
        stateTicks = 0;
        setAnimPose(WAKE);
        play(ModSounds.KOYANUSHI_WAKE.get(), 1.2f, 1.0f);
        if (by != null) {
            setTarget(by);
            GameManager.event(by, GameManager.EV_STINGER, 8, "");
            GameManager.addFear(by, 25.0f);
        }
    }

    public void repel() {
        if (state == S_CHASE || state == S_WAKE) {
            state = S_HOME;
            stateTicks = 0;
            setAnimPose(STAND);
        }
    }

    @Override
    protected void serverTick(@Nullable ServerPlayer p) {
        ServerLevel level = (ServerLevel) level();
        switch (state) {
            case S_SLEEP -> {
                if (home != null && distanceToSqr(Vec3.atBottomCenterOf(home)) > 1.0) lieDown();
                setDeltaMovement(Vec3.ZERO);
                if (--mumbleIn <= 0) {
                    play(ModSounds.KOYANUSHI_MUMBLE.get(), 0.6f, 0.95f + random.nextFloat() * 0.1f);
                    mumbleIn = 300 + random.nextInt(500);
                }
                ServerPlayer near = null;
                double best = 14.0;
                for (ServerPlayer sp : level.players()) {
                    if (!GameManager.huntable(sp)) continue;
                    double d = sp.distanceTo(this);
                    if (d < best) {
                        best = d;
                        near = sp;
                    }
                }
                if (near == null) return;
                GameManager.reportThreat(near, best < 8 ? 0.35f : 0.18f);
                if (best < 9 && com.sounan.game.GameData.get(level.getServer()).once("sleeper_" + (home == null ? 0 : home.asLong()))) {
                    GameManager.thought(near, "thought.sounan.sleeper");
                }
                NoiseSystem.Noise n = life > 60 ? NoiseSystem.loudest(level, position(), 0.6) : null;
                if (n != null) {
                    wake(near);
                    return;
                }
                if (GameManager.inBeam(near, this, 16.0)) lit++;
                else lit = Math.max(0, lit - 1);
                if (best < 3.0 && !near.isShiftKeyDown()) close++;
                else close = Math.max(0, close - 1);
                if (lit > 40 || close > 20) wake(near);
            }
            case S_WAKE -> {
                getNavigation().stop();
                if (p != null) {
                    faceTowards(p.position());
                    GameManager.reportThreat(p, 0.9f);
                }
                if (stateTicks == 20) play(ModSounds.KOYANUSHI_SCREAM.get(), 1.5f, 1.0f);
                if (stateTicks > 40) {
                    state = S_CHASE;
                    stateTicks = 0;
                    setAnimPose(CHASE);
                }
            }
            case S_CHASE -> {
                if (p == null || !GameManager.huntable(p)) {
                    state = S_HOME;
                    stateTicks = 0;
                    return;
                }
                GameManager.reportThreat(p, 1.0f);
                chase(p, 0.31);
                if (tickCount % 7 == 0) play(ModSounds.KOYANUSHI_STEP.get(), 1.0f, 0.9f + random.nextFloat() * 0.2f);
                boolean sees = hasLineOfSight(p);
                lost = sees ? 0 : lost + 1;
                double fromHome = home == null ? 0 : Math.sqrt(home.distToCenterSqr(p.position()));
                if (touching(p, 1.3)) {
                    GameManager.beginCapture(p, GameManager.KIND_KOYANUSHI, this);
                    state = S_HOME;
                    stateTicks = 0;
                } else if (lost > 80 || fromHome > 34 || stateTicks > 20 * 22) {
                    state = S_HOME;
                    stateTicks = 0;
                    setAnimPose(STAND);
                }
            }
            case S_HOME -> {
                setAnimPose(STAND);
                if (home == null) {
                    if (stateTicks > 100) vanish();
                    return;
                }
                if (tickCount % 20 == 0) getNavigation().moveTo(home.getX() + 0.5, home.getY(), home.getZ() + 0.5, mod(0.2));
                if (distanceToSqr(Vec3.atBottomCenterOf(home)) < 2.5 || stateTicks > 20 * 40) lieDown();
            }
            default -> {
            }
        }
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (home != null) tag.put("Home", NbtUtils.writeBlockPos(home));
        tag.putInt("HomeFacing", homeFacing.get2DDataValue());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("Home")) home = NbtUtils.readBlockPos(tag.getCompound("Home"));
        homeFacing = Direction.from2DDataValue(tag.getInt("HomeFacing"));
        temporary = false;
        state = S_SLEEP;
    }
}
