package com.meikon.entity;

import com.meikon.game.Closet;
import com.meikon.game.GameManager;
import com.meikon.game.NoiseSystem;
import com.meikon.game.PlayerState;
import com.meikon.registry.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.OpenDoorGoal;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.BlockPathTypes;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * 花嫁 — the bride. She cannot be hurt. She wanders the mansion humming かごめかごめ, listens for noise, and sees
 * far when you carry a light. When she sees you she screams and runs; lose her line of sight and she searches,
 * checking the closet she saw you slip into. Every bell you take makes her faster and sharper.
 */
public class Hanayome extends Monster {
    public enum State { WANDER, IDLE, INVESTIGATE, CHASE, SEARCH, CHECK_CLOSET, STUNNED, RETREAT, CAPTURE }

    private static final EntityDataAccessor<Integer> DATA_STATE = SynchedEntityData.defineId(Hanayome.class, EntityDataSerializers.INT);

    // ------------------------------------------------------------------ server-side brain
    private State state = State.WANDER;
    private int stateTicks;
    @Nullable private BlockPos waypoint;
    @Nullable private Vec3 goal;
    @Nullable private UUID targetId;
    @Nullable private Vec3 lastKnown;
    private int lostSight;
    private int screechCooldown;
    private int closetSeen = -1;
    private int closetWait;
    private int passive;
    private int stuck;
    private Vec3 stuckRef = Vec3.ZERO;
    private int idleTime;
    private int breathCooldown;
    private int repath;
    /** Client-only: eased 'duck under the lintel' amount for the renderer. */
    public float clientDuck;

    public Hanayome(EntityType<? extends Monster> type, Level level) {
        super(type, level);
        this.xpReward = 0;
        setPersistenceRequired();
        setMaxUpStep(1.0f);
        if (getNavigation() instanceof GroundPathNavigation nav) {
            nav.setCanOpenDoors(true);
        }
        setPathfindingMalus(BlockPathTypes.DOOR_WOOD_CLOSED, 0.0f);
        setPathfindingMalus(BlockPathTypes.WATER, 8.0f);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 200.0)
                .add(Attributes.MOVEMENT_SPEED, 0.30)
                .add(Attributes.FOLLOW_RANGE, 64.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
                .add(Attributes.ATTACK_DAMAGE, 0.0);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new OpenDoorGoal(this, false));
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(DATA_STATE, State.WANDER.ordinal());
    }

    public State clientState() {
        int i = entityData.get(DATA_STATE);
        return i >= 0 && i < State.values().length ? State.values()[i] : State.WANDER;
    }

    public State state() {
        return state;
    }

    // ------------------------------------------------------------------ invulnerable, silent, persistent
    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (source.is(DamageTypes.FELL_OUT_OF_WORLD) || source.is(DamageTypes.GENERIC_KILL)) return super.hurt(source, amount);
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    @Override
    protected boolean shouldDespawnInPeaceful() {
        return false;
    }

    @Nullable
    @Override
    protected SoundEvent getAmbientSound() {
        return null;
    }

    @Nullable
    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return null;
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState block) {
        float vol = state == State.CHASE ? 0.9f : state == State.WANDER || state == State.IDLE ? 0.32f : 0.55f;
        playSound(ModSounds.BRIDE_STEP.get(), vol, 0.9f + random.nextFloat() * 0.15f);
    }

    @Override
    public int getMaxHeadYRot() {
        return 85;
    }

    // ------------------------------------------------------------------ state machine
    private void setState(State next) {
        if (state != next) {
            state = next;
            stateTicks = 0;
            entityData.set(DATA_STATE, next.ordinal());
        }
    }

    /** Forces a state from outside (director, debug, items). */
    public void force(State next, @Nullable Vec3 at) {
        if (at != null) goal = at;
        setState(next);
        getNavigation().stop();
    }

    public void stun(int ticks) {
        setState(State.STUNNED);
        passive = ticks + 20 * 25;
        getNavigation().stop();
        playSound(ModSounds.BRIDE_SCREECH.get(), 1.6f, 0.62f);
        stateTicks = -ticks;
    }

    @Nullable
    public ServerPlayer target() {
        if (targetId == null || !(level() instanceof ServerLevel sl)) return null;
        return sl.getPlayerByUUID(targetId) instanceof ServerPlayer sp ? sp : null;
    }

    private double speed(double base) {
        return base * GameManager.brideSpeedFactor();
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        if (!(level() instanceof ServerLevel level)) return;
        stateTicks++;
        if (screechCooldown > 0) screechCooldown--;
        if (passive > 0) passive--;
        if (breathCooldown > 0) breathCooldown--;
        if (repath > 0) repath--;

        // purifying salt: she will not come near it
        if (state != State.CAPTURE && state != State.RETREAT && state != State.STUNNED && GameManager.nearSalt(level, position(), 2.6)) {
            getNavigation().stop();
            level.playSound(null, getX(), getY() + 1.6, getZ(), ModSounds.BRIDE_BREATH.get(), SoundSource.HOSTILE, 1.0f, 0.6f);
            goal = Vec3.atBottomCenterOf(GameManager.farWaypoint(level, blockPosition()));
            passive = Math.max(passive, 20 * 12);
            setState(State.RETREAT);
        }
        // she never sets foot in the room of lamplight
        if (GameManager.nearSafeRoom(position(), 0.8)) {
            getNavigation().stop();
            if (state != State.RETREAT && state != State.CAPTURE) {
                goal = Vec3.atBottomCenterOf(GameManager.farWaypoint(level, blockPosition()));
                setState(State.RETREAT);
            }
        }

        if (state == State.CAPTURE) {
            getNavigation().stop();
            ServerPlayer t = target();
            if (t != null) getLookControl().setLookAt(t, 90, 90);
            if (stateTicks > 60) setState(State.RETREAT);
            return;
        }

        // ---------------------------------------------------------------- senses (every 2 ticks)
        if (state != State.STUNNED && tickCount % 2 == 0 && !GameManager.debugPeaceful) {
            ServerPlayer seen = findVisiblePlayer(level);
            if (seen != null && (passive <= 0 || distanceTo(seen) < 4.0)) {
                if (state != State.CHASE) startChase(seen);
                targetId = seen.getUUID();
                lastKnown = seen.position();
                lostSight = 0;
                PlayerState ps = GameManager.state(seen);
                if (ps != null) ps.seenByBride = 5;
            } else if (state == State.CHASE) {
                lostSight += 2;
            }
            if (state != State.CHASE && state != State.CHECK_CLOSET && passive <= 0) {
                NoiseSystem.Noise heard = NoiseSystem.loudest(level, position(), GameManager.hearingFactor());
                if (heard != null && (state != State.INVESTIGATE || heard.strength() > 0.0)) {
                    if (goal == null || state != State.INVESTIGATE || heard.pos().distanceToSqr(goal) > 4.0) {
                        goal = heard.pos();
                        if (state != State.INVESTIGATE && state != State.SEARCH) {
                            playSound(ModSounds.BRIDE_RUSTLE.get(), 0.7f, 0.9f);
                        }
                        setState(State.INVESTIGATE);
                    }
                }
            }
            // she hears you breathing inside the closet
            if (state != State.CHASE && state != State.CHECK_CLOSET) {
                for (ServerPlayer p : GameManager.huntable(level)) {
                    PlayerState ps = GameManager.state(p);
                    if (ps == null || ps.closet < 0) continue;
                    if (ps.breathAudible() && distanceToSqr(p) < 4.5 * 4.5) {
                        closetSeen = ps.closet;
                        targetId = p.getUUID();
                        setState(State.CHECK_CLOSET);
                        break;
                    }
                }
            }
        }

        // ---------------------------------------------------------------- behaviour
        switch (state) {
            case WANDER -> tickWander(level);
            case IDLE -> tickIdle(level);
            case INVESTIGATE -> tickInvestigate(level);
            case CHASE -> tickChase(level);
            case SEARCH -> tickSearch(level);
            case CHECK_CLOSET -> tickCheckCloset(level);
            case STUNNED -> {
                getNavigation().stop();
                if (stateTicks >= 0) {
                    goal = Vec3.atBottomCenterOf(GameManager.farWaypoint(level, blockPosition()));
                    setState(State.RETREAT);
                }
            }
            case RETREAT -> {
                if (goal == null) goal = Vec3.atBottomCenterOf(GameManager.farWaypoint(level, blockPosition()));
                moveTowards(goal, 0.95);
                if (position().distanceToSqr(goal) < 4.0 || stateTicks > 20 * 30) {
                    goal = null;
                    setState(State.WANDER);
                }
            }
            default -> {
            }
        }
        tickStuck(level);
        tickBreathing(level);
    }

    private void startChase(ServerPlayer p) {
        setState(State.CHASE);
        if (screechCooldown <= 0) {
            level().playSound(null, getX(), getY() + 1.6, getZ(), ModSounds.BRIDE_SCREECH.get(), SoundSource.HOSTILE, 2.2f,
                    0.95f + random.nextFloat() * 0.1f);
            screechCooldown = 20 * 25;
            GameManager.onBrideSpotted(p, this);
        }
        closetSeen = -1;
    }

    private void tickWander(ServerLevel level) {
        if (GameManager.relentless() && tickCount % 160 == 0) {
            ServerPlayer near = GameManager.nearestHuntable(level, position(), 200);
            if (near != null) {
                goal = near.position().add(random.nextGaussian() * 5, 0, random.nextGaussian() * 5);
                setState(State.INVESTIGATE);
                return;
            }
        }
        if (waypoint == null || getNavigation().isDone()) {
            if (waypoint != null && position().distanceToSqr(Vec3.atBottomCenterOf(waypoint)) < 6.0) {
                waypoint = null;
                idleTime = 50 + random.nextInt(110);
                setState(State.IDLE);
                return;
            }
            if (waypoint == null) waypoint = GameManager.nextWaypoint(level, this);
            if (waypoint != null) {
                moveTowards(Vec3.atBottomCenterOf(waypoint), 0.52);
                if (getNavigation().isDone() && stateTicks > 40) waypoint = null;
            }
        }
    }

    private void tickIdle(ServerLevel level) {
        getNavigation().stop();
        if (stateTicks % 35 == 1) {
            float yaw = getYHeadRot() + (random.nextFloat() - 0.5f) * 160.0f;
            Vec3 dir = Vec3.directionFromRotation(random.nextFloat() * 20.0f - 5.0f, yaw);
            getLookControl().setLookAt(getX() + dir.x * 4, getEyeY() + dir.y * 4, getZ() + dir.z * 4, 4, 10);
        }
        if (stateTicks > idleTime) setState(State.WANDER);
    }

    private void tickInvestigate(ServerLevel level) {
        if (goal == null) {
            setState(State.WANDER);
            return;
        }
        moveTowards(goal, 0.85);
        if (position().distanceToSqr(goal) < 3.0 || stateTicks > 20 * 18) {
            lastKnown = goal;
            goal = null;
            setState(State.SEARCH);
        }
    }

    private void tickChase(ServerLevel level) {
        ServerPlayer t = target();
        PlayerState ps = t == null ? null : GameManager.state(t);
        if (t == null || ps == null || !t.isAlive() || t.level() != level || ps.captured) {
            setState(State.SEARCH);
            return;
        }
        if (ps.closet >= 0 && lostSight < 30) {
            // she saw you go in
            closetSeen = ps.closet;
            setState(State.CHECK_CLOSET);
            return;
        }
        if (ps.inSafeRoom) {
            lastKnown = t.position();
            setState(State.SEARCH);
            return;
        }
        getLookControl().setLookAt(t, 40, 40);
        if (repath <= 0 || getNavigation().isDone()) {
            Vec3 aim = lostSight > 0 && lastKnown != null ? lastKnown : t.position();
            getNavigation().moveTo(aim.x, aim.y, aim.z, speed(1.13));
            repath = 5;
        }
        double horiz = Math.hypot(t.getX() - getX(), t.getZ() - getZ());
        if (horiz < 1.35 && Math.abs(t.getY() - getY()) < 1.6 && lostSight < 6 && hasLineOfSight(t)) {
            capture(t);
            return;
        }
        if (lostSight > 50) {
            setState(State.SEARCH);
        }
    }

    private void tickSearch(ServerLevel level) {
        if (closetSeen >= 0) {
            setState(State.CHECK_CLOSET);
            return;
        }
        if (stateTicks == 1) {
            if (lastKnown != null) moveTowards(lastKnown, 0.8);
        }
        if (getNavigation().isDone() && stateTicks % 50 == 0) {
            Vec3 base = lastKnown != null ? lastKnown : position();
            Vec3 p = base.add(random.nextGaussian() * 6, 0, random.nextGaussian() * 6);
            moveTowards(p, 0.7);
            float yaw = getYHeadRot() + (random.nextFloat() - 0.5f) * 180.0f;
            Vec3 dir = Vec3.directionFromRotation(0, yaw);
            getLookControl().setLookAt(getX() + dir.x * 4, getEyeY(), getZ() + dir.z * 4, 6, 10);
        }
        if (stateTicks > 20 * 16) {
            lastKnown = null;
            setState(State.WANDER);
        }
    }

    private void tickCheckCloset(ServerLevel level) {
        Closet c = GameManager.closet(closetSeen);
        if (c == null) {
            closetSeen = -1;
            setState(State.SEARCH);
            return;
        }
        Vec3 front = Vec3.atBottomCenterOf(c.front());
        if (position().distanceToSqr(front) > 2.2) {
            moveTowards(front, stateTicks < 10 ? 1.0 : 0.75);
            closetWait = 0;
            if (stateTicks > 20 * 15) {
                closetSeen = -1;
                setState(State.SEARCH);
            }
            return;
        }
        // standing right outside the slats. waiting. listening.
        getNavigation().stop();
        getLookControl().setLookAt(Vec3.atCenterOf(c.inside()));
        closetWait++;
        if (closetWait == 1) {
            level.playSound(null, getX(), getY() + 1.5, getZ(), ModSounds.BRIDE_BREATH.get(), SoundSource.HOSTILE, 0.9f, 0.85f);
        }
        if (closetWait >= 40 + random.nextInt(3)) {
            ServerPlayer hidden = GameManager.playerInCloset(level, closetSeen);
            boolean knows = hidden != null && (GameManager.state(hidden) != null
                    && (GameManager.state(hidden).breathAudible() || targetId != null && targetId.equals(hidden.getUUID()) && lostSight < 200));
            if (hidden != null && knows) {
                openDoor(level, c);
                capture(hidden);
            } else {
                if (random.nextInt(3) == 0) openDoor(level, c);
                closetSeen = -1;
                lastKnown = null;
                setState(State.WANDER);
            }
        }
    }

    private void openDoor(ServerLevel level, Closet c) {
        BlockState s = level.getBlockState(c.front());
        if (s.getBlock() instanceof DoorBlock door) {
            door.setOpen(this, level, s, c.front(), true);
        }
        level.playSound(null, c.front(), ModSounds.BRIDE_DOOR.get(), SoundSource.HOSTILE, 1.4f, 1.0f);
    }

    private void capture(ServerPlayer t) {
        targetId = t.getUUID();
        setState(State.CAPTURE);
        getNavigation().stop();
        GameManager.beginCapture(t, this);
    }

    /** Called when an ofuda burns or salt hits her during a capture. */
    public void repelled() {
        stun(50);
    }

    private void moveTowards(Vec3 p, double mul) {
        if (repath > 0 && !getNavigation().isDone()) return;
        Path path = getNavigation().createPath(p.x, p.y, p.z, 1);
        if (path != null) getNavigation().moveTo(path, speed(mul));
        repath = 10;
    }

    @Nullable
    private ServerPlayer findVisiblePlayer(ServerLevel level) {
        ServerPlayer best = null;
        double bestD = Double.MAX_VALUE;
        for (ServerPlayer p : GameManager.huntable(level)) {
            double d = distanceToSqr(p);
            if (d < bestD && canSee(p)) {
                best = p;
                bestD = d;
            }
        }
        return best;
    }

    public boolean canSee(ServerPlayer p) {
        PlayerState ps = GameManager.state(p);
        if (ps == null || ps.closet >= 0 || ps.inSafeRoom || ps.captured) return false;
        double dist = distanceTo(p);
        if (dist > GameManager.sightRange(p, ps)) return false;
        if (dist > 2.8) {
            Vec3 look = Vec3.directionFromRotation(0, getYHeadRot());
            double dx = p.getX() - getX(), dz = p.getZ() - getZ();
            double len = Math.sqrt(dx * dx + dz * dz);
            if (len > 1.0e-4 && (look.x * dx + look.z * dz) / len < 0.26) return false;
        }
        return hasLineOfSight(p);
    }

    private void tickStuck(ServerLevel level) {
        if (state == State.IDLE || state == State.CHECK_CLOSET || state == State.STUNNED || getNavigation().isDone()) {
            stuck = 0;
            stuckRef = position();
            return;
        }
        if (++stuck >= 60) {
            if (position().distanceToSqr(stuckRef) < 0.5) {
                getNavigation().stop();
                waypoint = null;
                repath = 0;
                if (state == State.WANDER || state == State.RETREAT || state == State.INVESTIGATE) {
                    BlockPos w = GameManager.nextWaypoint(level, this);
                    if (w != null && !GameManager.anyoneSees(level, this)) {
                        teleportTo(w.getX() + 0.5, w.getY(), w.getZ() + 0.5);
                    }
                    goal = null;
                    setState(State.WANDER);
                }
            }
            stuck = 0;
            stuckRef = position();
        }
    }

    private void tickBreathing(ServerLevel level) {
        if (breathCooldown > 0) return;
        if (state == State.SEARCH || state == State.CHECK_CLOSET || state == State.INVESTIGATE) {
            ServerPlayer near = GameManager.nearestHuntable(level, position(), 9.0);
            if (near != null) {
                level.playSound(null, getX(), getY() + 1.7, getZ(), ModSounds.BRIDE_BREATH.get(), SoundSource.HOSTILE, 0.8f,
                        0.85f + random.nextFloat() * 0.2f);
                breathCooldown = 60 + random.nextInt(60);
                return;
            }
        }
        if ((state == State.WANDER || state == State.IDLE) && random.nextInt(260) == 0) {
            playSound(ModSounds.BRIDE_RUSTLE.get(), 0.5f, 0.9f + random.nextFloat() * 0.2f);
        }
        breathCooldown = 20;
    }

    // ------------------------------------------------------------------ persistence
    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("BrideState", state == State.CAPTURE ? State.WANDER.ordinal() : state.ordinal());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        int s = tag.getInt("BrideState");
        if (s >= 0 && s < State.values().length) setState(State.values()[s]);
    }

    /** Head bob / stride phase for the renderer. */
    public float strideSpeed() {
        return Mth.clamp((float) getDeltaMovement().horizontalDistance() * 6.0f, 0.0f, 1.0f);
    }
}
