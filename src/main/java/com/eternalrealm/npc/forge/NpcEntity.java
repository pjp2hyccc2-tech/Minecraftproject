package com.eternalrealm.npc.forge;

import com.eternalrealm.npc.NpcBodies;
import com.eternalrealm.npc.Townsfolk;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AnimationState;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.BlockPathTypes;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.EnumSet;
import java.util.List;

/**
 * The body of every town NPC: service NPCs, story characters, quest givers, townsfolk and guards. One entity type; the
 * synced appearance id ({@link NpcBodies}) selects texture, model parts, scale and animations on the client.
 *
 * <p>NPCs cannot be hurt (only {@code /kill} removes them), are never pushed, never despawn and never fight. They look at
 * players who come close and look around now and then. What they do depends on their {@link Mode}: service NPCs and
 * characters hold their post, townsfolk stroll within {@link Townsfolk#STROLL_RADIUS} of their spot (no water, no doors,
 * and no pathfinding at all while no player is within {@link #ACTIVE_RANGE}), guards stand watch facing their post.
 * Talking to an NPC plays its talk gesture on every client ({@link #startTalking}).</p>
 *
 * <p>Whoever places NPCs ({@link Npcs#ensure}, {@link Crowds}) stamps them ({@link #markEnsured}); an NPC that finds
 * another body of the same person nearby (a duplicate created while its chunk's entities were still loading, or the
 * villager it wore before) keeps the most recently placed one and removes the rest.</p>
 */
public class NpcEntity extends PathfinderMob {
    private static final EntityDataAccessor<String> DATA_APPEARANCE =
            SynchedEntityData.defineId(NpcEntity.class, EntityDataSerializers.STRING);
    /** Entity event: a player talks to the NPC (plays the talk gesture). */
    public static final byte TALK_EVENT = 101;
    /** Townsfolk only walk while a player is this close (blocks). */
    public static final double ACTIVE_RANGE = 48.0;
    private static final double STROLL_SPEED = 0.42;
    private static final int DUPLICATE_CHECK_TICKS = 600;

    /** What the NPC does when nobody talks to it. */
    public enum Mode {
        /** Service NPCs, characters and quest givers: stand at the post, face its direction. */
        POST,
        /** Townsfolk: stroll near the spot, pause, look at passers-by. */
        STROLL,
        /** Guards: stand at the post facing its direction; the head turns now and then. */
        WATCH
    }

    public final AnimationState idleAnimation = new AnimationState();
    public final AnimationState workAnimation = new AnimationState();
    public final AnimationState talkAnimation = new AnimationState();

    private Mode mode = Mode.POST;
    @Nullable
    private BlockPos home;
    private float homeYaw;
    private long ensuredAt;
    private int talkTicks;
    @Nullable
    private Player talkTarget;
    private boolean playerNear;
    private int nearCheck;
    private int duplicateCheck;

    private int clientTalkTicks;
    private float workWeight = 1.0F;
    private float workWeightO = 1.0F;

    public NpcEntity(EntityType<? extends NpcEntity> type, Level level) {
        super(type, level);
        setPathfindingMalus(BlockPathTypes.WATER, -1.0F);
        setPathfindingMalus(BlockPathTypes.DOOR_OPEN, -1.0F);
        setPathfindingMalus(BlockPathTypes.DOOR_WOOD_CLOSED, -1.0F);
        setPathfindingMalus(BlockPathTypes.DOOR_IRON_CLOSED, -1.0F);
        setPathfindingMalus(BlockPathTypes.TRAPDOOR, -1.0F);
        setPathfindingMalus(BlockPathTypes.LAVA, -1.0F);
        setPathfindingMalus(BlockPathTypes.DANGER_FIRE, -1.0F);
        setPathfindingMalus(BlockPathTypes.DAMAGE_FIRE, -1.0F);
        setInvulnerable(true);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0)
                .add(Attributes.MOVEMENT_SPEED, 0.5)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
                .add(Attributes.FOLLOW_RANGE, 16.0);
    }

    // --------------------------------------------------------------------------------------------- appearance

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(DATA_APPEARANCE, NpcBodies.DEFAULT);
    }

    public String getAppearance() {
        return this.entityData.get(DATA_APPEARANCE);
    }

    /** Server side; synced to clients. Unknown ids become {@link NpcBodies#DEFAULT}. */
    public void setAppearance(String id) {
        this.entityData.set(DATA_APPEARANCE, NpcBodies.orDefault(id));
        refreshDimensions();
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (DATA_APPEARANCE.equals(key)) {
            refreshDimensions();
        }
    }

    @Override
    public EntityDimensions getDimensions(Pose pose) {
        return super.getDimensions(pose).scale(NpcBodies.scale(getAppearance()));
    }

    // ---------------------------------------------------------------------------------------- post and mode

    public Mode mode() {
        return this.mode;
    }

    @Nullable
    public BlockPos home() {
        return this.home;
    }

    public float homeYaw() {
        return this.homeYaw;
    }

    /** Stand at {@code pos} facing {@code yaw} (service NPCs and characters). Moves the NPC there. */
    public void setPost(BlockPos pos, float yaw) {
        setHome(Mode.POST, pos, yaw, true);
    }

    /** Stand watch at {@code pos} facing {@code yaw} (guards). Moves the NPC there. */
    public void setWatch(BlockPos pos, float yaw) {
        setHome(Mode.WATCH, pos, yaw, true);
    }

    /** Stroll near {@code pos}; moved there only if it is away from it. */
    public void setStroll(BlockPos pos, float yaw) {
        double r = Townsfolk.STROLL_RADIUS + 1.0;
        boolean away = distanceToSqr(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5) > r * r;
        setHome(Mode.STROLL, pos, yaw, away || this.tickCount == 0);
    }

    private void setHome(Mode mode, BlockPos pos, float yaw, boolean move) {
        this.mode = mode;
        this.home = pos.immutable();
        this.homeYaw = yaw;
        restrictTo(this.home, mode == Mode.STROLL ? Townsfolk.STROLL_RADIUS : 1);
        if (move) {
            getNavigation().stop();
            moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, yaw, 0.0F);
            setYHeadRot(yaw);
            setYBodyRot(yaw);
        }
    }

    /** Stamp of the last placement (game time); the newest body of a person wins over duplicates. */
    public void markEnsured(long gameTime) {
        this.ensuredAt = gameTime;
        this.duplicateCheck = 0;
    }

    boolean outranks(NpcEntity other) {
        if (this.ensuredAt != other.ensuredAt) {
            return this.ensuredAt > other.ensuredAt;
        }
        return getUUID().compareTo(other.getUUID()) > 0;
    }

    // ------------------------------------------------------------------------------------------------- talk

    /** A player talks to the NPC: it turns to them and plays its talk gesture (server side, synced). */
    public void startTalking(Player player) {
        if (level().isClientSide) {
            return;
        }
        this.talkTarget = player;
        this.talkTicks = Mth.ceil(NpcBodies.talkSeconds(getAppearance()) * 20.0F);
        getNavigation().stop();
        level().broadcastEntityEvent(this, TALK_EVENT);
    }

    public boolean isTalking() {
        return this.talkTicks > 0;
    }

    @Override
    public void handleEntityEvent(byte id) {
        if (id == TALK_EVENT) {
            this.talkAnimation.start(this.tickCount);
            this.clientTalkTicks = Mth.ceil(NpcBodies.talkSeconds(getAppearance()) * 20.0F) + 1;
            return;
        }
        super.handleEntityEvent(id);
    }

    /** Townsfolk and guards greet whoever talks to them (service NPCs are handled by {@link Npcs#onEntityInteract}). */
    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (hand == InteractionHand.MAIN_HAND && !level().isClientSide && player instanceof ServerPlayer serverPlayer
                && Npcs.roleOf(this).isEmpty()) {
            Crowds.greet(serverPlayer, this);
        }
        return InteractionResult.sidedSuccess(level().isClientSide);
    }

    // ---------------------------------------------------------------------------------------------------- AI

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new TalkGoal(this));
        this.goalSelector.addGoal(5, new StrollGoal(this));
        this.goalSelector.addGoal(6, new GlanceGoal(this, 8.0F, 0.025F, false));
        this.goalSelector.addGoal(6, new GlanceGoal(this, 5.0F, 0.012F, true));
        this.goalSelector.addGoal(7, new LookAroundGoal(this));
        this.goalSelector.addGoal(8, new FacePostGoal(this));
    }

    /** Whether a player is within {@link #ACTIVE_RANGE} (server side, refreshed every two seconds). */
    public boolean isPlayerNear() {
        return this.playerNear;
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            clientTick();
            return;
        }
        if (this.talkTicks > 0 && --this.talkTicks == 0) {
            this.talkTarget = null;
        }
        if (--this.nearCheck <= 0) {
            this.nearCheck = 40;
            this.playerNear = level().hasNearbyAlivePlayer(getX(), getY(), getZ(), ACTIVE_RANGE);
        }
        if (--this.duplicateCheck <= 0) {
            this.duplicateCheck = DUPLICATE_CHECK_TICKS + getRandom().nextInt(40);
            removeDuplicates();
        }
        if (this.home != null && (this.tickCount + getId()) % 20 == 0) {
            keepNearHome();
        }
    }

    /** Another body of the same person nearby: the most recently placed one stays (see {@link Npcs#identity}). */
    private void removeDuplicates() {
        String identity = Npcs.identity(this);
        if (identity == null || isRemoved()) {
            return;
        }
        List<Mob> twins = level().getEntitiesOfClass(Mob.class, getBoundingBox().inflate(ACTIVE_RANGE),
                mob -> mob != this && mob.isAlive() && identity.equals(Npcs.identity(mob)));
        for (Mob twin : twins) {
            if (twin instanceof NpcEntity other) {
                if (other.outranks(this)) {
                    discard();
                    return;
                }
                other.discard();
            } else {
                twin.discard(); // the villager this NPC used to be
            }
        }
    }

    private void keepNearHome() {
        BlockPos pos = this.home;
        if (pos == null) {
            return;
        }
        double dx = getX() - (pos.getX() + 0.5);
        double dz = getZ() - (pos.getZ() + 0.5);
        double dy = getY() - pos.getY();
        double distance = dx * dx + dz * dz;
        if (this.mode != Mode.STROLL) {
            if (distance > 2.25 || Math.abs(dy) > 2.0) {
                setHome(this.mode, pos, this.homeYaw, true);
            }
            return;
        }
        double radius = Townsfolk.STROLL_RADIUS;
        if (distance > (radius + 6.0) * (radius + 6.0) || Math.abs(dy) > 6.0 || isInWater()) {
            setHome(this.mode, pos, this.homeYaw, true);
        } else if (distance > (radius + 1.5) * (radius + 1.5) && this.playerNear && getNavigation().isDone()) {
            getNavigation().moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, STROLL_SPEED);
        }
    }

    // --------------------------------------------------------------------------------------------- client

    private void clientTick() {
        int phase = Math.floorMod(getId() * 37, 240);
        if (!this.idleAnimation.isStarted()) {
            this.idleAnimation.start(this.tickCount - phase);
        }
        if (!this.workAnimation.isStarted()) {
            this.workAnimation.start(this.tickCount - phase * 3);
        }
        if (this.clientTalkTicks > 0 && --this.clientTalkTicks == 0) {
            this.talkAnimation.stop();
        }
        // the work loop (hammering, writing...) pauses while someone stands close or the NPC talks
        boolean attending = this.talkAnimation.isStarted() || level().getNearestPlayer(this, 3.5) != null;
        this.workWeightO = this.workWeight;
        this.workWeight = attending ? Math.max(0.0F, this.workWeight - 0.08F) : Math.min(1.0F, this.workWeight + 0.08F);
    }

    /** Weight (0..1) of the work loop at {@code ageInTicks} (client). */
    public float workWeight(float ageInTicks) {
        float partial = Mth.clamp(ageInTicks - this.tickCount, 0.0F, 1.0F);
        return Mth.lerp(partial, this.workWeightO, this.workWeight);
    }

    // ------------------------------------------------------------------------------------------- properties

    @Override
    public boolean hurt(DamageSource source, float amount) {
        return source.is(DamageTypeTags.BYPASSES_INVULNERABILITY) && super.hurt(source, amount);
    }

    @Override
    public boolean isAttackable() {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean isPushedByFluid() {
        return false;
    }

    @Override
    public boolean canBeLeashed(Player player) {
        return false;
    }

    @Override
    public boolean canChangeDimensions() {
        return false;
    }

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return false;
    }

    @Override
    public boolean requiresCustomPersistence() {
        return true;
    }

    @Override
    protected boolean shouldDespawnInPeaceful() {
        return false;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putString("Appearance", getAppearance());
        tag.putString("Mode", this.mode.name());
        if (this.home != null) {
            tag.putIntArray("Home", new int[]{this.home.getX(), this.home.getY(), this.home.getZ()});
        }
        tag.putFloat("HomeYaw", this.homeYaw);
        tag.putLong("EnsuredAt", this.ensuredAt);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        setAppearance(tag.getString("Appearance"));
        Mode saved = Mode.POST;
        for (Mode candidate : Mode.values()) {
            if (candidate.name().equals(tag.getString("Mode"))) {
                saved = candidate;
            }
        }
        this.mode = saved;
        int[] pos = tag.getIntArray("Home");
        if (pos.length == 3) {
            setHome(saved, new BlockPos(pos[0], pos[1], pos[2]), tag.getFloat("HomeYaw"), false);
        }
        this.ensuredAt = tag.getLong("EnsuredAt");
        setInvulnerable(true);
    }

    // ------------------------------------------------------------------------------------------------- goals

    /** Turns to whoever talks to the NPC and stops walking. */
    private static final class TalkGoal extends Goal {
        private final NpcEntity npc;

        TalkGoal(NpcEntity npc) {
            this.npc = npc;
            setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            return this.npc.isTalking() && this.npc.talkTarget != null;
        }

        @Override
        public void start() {
            this.npc.getNavigation().stop();
        }

        @Override
        public void tick() {
            Player target = this.npc.talkTarget;
            if (target != null) {
                this.npc.getLookControl().setLookAt(target, 30.0F, 30.0F);
            }
        }
    }

    /** Townsfolk walk to a random dry spot near home, only while players are around to see it. */
    private static final class StrollGoal extends WaterAvoidingRandomStrollGoal {
        private final NpcEntity npc;

        StrollGoal(NpcEntity npc) {
            super(npc, STROLL_SPEED);
            this.npc = npc;
        }

        @Override
        public boolean canUse() {
            return this.npc.mode == Mode.STROLL && this.npc.playerNear && !this.npc.isTalking() && super.canUse();
        }

        @Override
        public boolean canContinueToUse() {
            return this.npc.playerNear && !this.npc.isTalking() && super.canContinueToUse();
        }

        @Nullable
        @Override
        protected Vec3 getPosition() {
            Vec3 target = super.getPosition();
            BlockPos home = this.npc.home;
            if (target == null || home == null) {
                return target;
            }
            double radius = Townsfolk.STROLL_RADIUS;
            if (target.distanceToSqr(Vec3.atBottomCenterOf(home)) > radius * radius) {
                return null;
            }
            BlockPos pos = BlockPos.containing(target);
            if (!this.npc.level().getFluidState(pos).isEmpty() || !this.npc.level().getFluidState(pos.below()).isEmpty()) {
                return null;
            }
            return target;
        }
    }

    /** Looks at a nearby player for a while (guards only at close range and less often). */
    private static final class GlanceGoal extends LookAtPlayerGoal {
        private final NpcEntity npc;
        private final boolean guards;

        GlanceGoal(NpcEntity npc, float range, float probability, boolean guards) {
            super(npc, Player.class, range, probability);
            this.npc = npc;
            this.guards = guards;
        }

        @Override
        public boolean canUse() {
            return (this.npc.mode == Mode.WATCH) == this.guards && !this.npc.isTalking() && super.canUse();
        }

        @Override
        public boolean canContinueToUse() {
            return !this.npc.isTalking() && super.canContinueToUse();
        }
    }

    /** Idle look around (not guards: their head scan is part of their animation). */
    private static final class LookAroundGoal extends RandomLookAroundGoal {
        private final NpcEntity npc;

        LookAroundGoal(NpcEntity npc) {
            super(npc);
            this.npc = npc;
        }

        @Override
        public boolean canUse() {
            return this.npc.mode != Mode.WATCH && !this.npc.isTalking() && super.canUse();
        }
    }

    /** NPCs at a post turn back to face its direction after looking elsewhere. */
    private static final class FacePostGoal extends Goal {
        private final NpcEntity npc;
        private int ticks;

        FacePostGoal(NpcEntity npc) {
            this.npc = npc;
            setFlags(EnumSet.of(Goal.Flag.LOOK));
        }

        private float offPost() {
            return Math.abs(Mth.wrapDegrees(this.npc.getYHeadRot() - this.npc.homeYaw));
        }

        @Override
        public boolean canUse() {
            return this.npc.mode != Mode.STROLL && this.npc.home != null && !this.npc.isTalking() && offPost() > 4.0F
                    && this.npc.getRandom().nextInt(this.npc.mode == Mode.WATCH ? 8 : 24) == 0;
        }

        @Override
        public void start() {
            this.ticks = 0;
        }

        @Override
        public boolean canContinueToUse() {
            return !this.npc.isTalking() && offPost() > 2.0F && this.ticks < 80;
        }

        @Override
        public void tick() {
            this.ticks++;
            double angle = Math.toRadians(this.npc.homeYaw);
            this.npc.getLookControl().setLookAt(this.npc.getX() - Math.sin(angle) * 4.0, this.npc.getEyeY(),
                    this.npc.getZ() + Math.cos(angle) * 4.0);
        }
    }
}
