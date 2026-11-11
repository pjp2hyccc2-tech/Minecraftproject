package com.eternalrealm.mount.forge;

import com.eternalrealm.mount.MountBodies;
import com.eternalrealm.mount.MountIndividual;
import com.eternalrealm.mount.MountKind;
import com.eternalrealm.mount.MountLook;
import com.eternalrealm.mount.MountLooks;
import com.eternalrealm.mount.MountMotion;
import com.eternalrealm.mount.MountRules;
import com.eternalrealm.mount.MountTraits;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.BooleanSupplier;

/**
 * A summoned mount ({@link MountKind}): it exists only while ridden. The rider's client moves it (like vanilla
 * horses) with {@link MountMotion}; the server keeps it invulnerable, untargetable and unpushable, lets a griffin's
 * rider get off only on the ground, and sends it away when its rider is gone or moves it impossibly fast or high.
 * Not saved with the world (its entity type is {@code noSave}).
 */
public class MountEntity extends Mob implements OwnableEntity {
    private static final Logger LOG = LogUtils.getLogger();
    /** The rider's jump key on this client (set by the client setup; the server never needs it). */
    public static volatile BooleanSupplier clientJumpHeld = () -> false;
    /** Ticks per sample of the server's watch on speed and height. */
    private static final int WATCH_WINDOW = 20;
    /** The individual's look ({@link MountLook#encode()}): the coat (texture) and the mane or wings. */
    private static final EntityDataAccessor<String> LOOK = SynchedEntityData.defineId(MountEntity.class,
            EntityDataSerializers.STRING);
    /** The individual's values packed as speed | acceleration << 8 | lift << 16: the rider's client moves by them. */
    private static final EntityDataAccessor<Integer> TRAITS = SynchedEntityData.defineId(MountEntity.class,
            EntityDataSerializers.INT);

    private final MountKind kind;
    /** Which kept mount this is (server side; the stable's id). */
    @Nullable
    private UUID individualId;
    private MountKind.MountStats stats;
    private int statsFrom = Integer.MIN_VALUE;
    private MountLook look;
    private String lookFrom;
    private MountMotion.Velocity motion = MountMotion.Velocity.ZERO;
    @Nullable
    private UUID owner;
    private int riderlessTicks;
    // the server's watch (WATCH_WINDOW ticks per sample; the rider's client moves the mount between ticks)
    private Vec3 watchedFrom;
    private int speedingWindows;
    private int hoveringHigh;
    private double lastWatchedY = Double.NaN;
    /** Sneak was held when the griffin landed: the rider gets off only after letting go and sneaking again. */
    private boolean landingSneak;
    private double fixedCeiling = Double.NaN;

    // what the model shows (client side, measured from the movement every tick)
    private float distance;
    private float distanceO;
    private float speed;
    private float climb;
    private float lean;
    private float leanO;
    private float wheel;
    private float wheelO;
    private float wings;
    private float wingsO;
    private float flap;
    private float flapO;
    private float beat;
    private int airTicks;
    private int groundTicks;
    private float lastYaw = Float.NaN;

    public MountEntity(EntityType<? extends MountEntity> type, Level level, MountKind kind) {
        super(type, level);
        this.kind = kind;
        setMaxUpStep(1.1F);
        setNoGravity(kind.flies());
        setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 40.0).add(Attributes.MOVEMENT_SPEED, 0.3)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0);
    }

    public MountKind kind() {
        return kind;
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(LOOK, "");
        this.entityData.define(TRAITS, pack(MountTraits.STANDARD));
    }

    /** Becomes {@code individual}: its look for everyone, its values for the rider's client (set before spawning). */
    public void become(MountIndividual individual) {
        this.individualId = individual.id();
        this.entityData.set(LOOK, individual.look().encode());
        this.entityData.set(TRAITS, pack(individual.traits()));
    }

    /** The stable id of the kept mount this is (server side), if it was called from a stable. */
    public java.util.Optional<UUID> individualId() {
        return java.util.Optional.ofNullable(individualId);
    }

    /** Its look (the classic one until the server says otherwise). */
    public MountLook look() {
        String encoded = this.entityData.get(LOOK);
        if (look == null || !encoded.equals(lookFrom)) {
            lookFrom = encoded;
            look = MountLooks.orClassic(kind, MountLook.decode(encoded));
        }
        return look;
    }

    /** Its values (standard until the server says otherwise). */
    public MountTraits traits() {
        return unpack(this.entityData.get(TRAITS));
    }

    /** How it moves: its kind's numbers scaled by its values. */
    public MountKind.MountStats stats() {
        int packed = this.entityData.get(TRAITS);
        if (stats == null || packed != statsFrom) {
            statsFrom = packed;
            stats = MountIndividual.scaled(kind, unpack(packed));
        }
        return stats;
    }

    /** Client side, for the stable's preview: shows {@code look} without a server. */
    public void preview(MountLook look) {
        this.entityData.set(LOOK, look.encode());
    }

    /** Client side, for the stable's preview: a griffin shows its wings spread as in a glide. */
    public void previewSpread() {
        this.wings = 1.0F;
        this.wingsO = 1.0F;
        this.airTicks = 30;
    }

    private static int pack(MountTraits traits) {
        return traits.speed() | traits.acceleration() << 8 | traits.lift() << 16;
    }

    private static MountTraits unpack(int packed) {
        return new MountTraits(MountTraits.clamp(packed & 0xFF), MountTraits.clamp(packed >> 8 & 0xFF),
                MountTraits.clamp(packed >> 16 & 0xFF));
    }

    void setOwner(UUID owner) {
        this.owner = owner;
    }

    @Override
    @Nullable
    public UUID getOwnerUUID() {
        return owner;
    }

    // ------------------------------------------------------------------ riding

    @Override
    @Nullable
    public LivingEntity getControllingPassenger() {
        return getFirstPassenger() instanceof Player player ? player : null;
    }

    @Override
    protected boolean canAddPassenger(Entity passenger) {
        return getPassengers().isEmpty() && passenger instanceof Player;
    }

    @Override
    protected void tickRidden(Player rider, Vec3 travel) {
        super.tickRidden(rider, travel);
        float yaw = rider.getYRot();
        setRot(yaw, 0.0F);
        this.yRotO = yaw;
        this.yBodyRot = yaw;
        this.yHeadRot = yaw;
    }

    @Override
    protected Vec3 getRiddenInput(Player rider, Vec3 travel) {
        return new Vec3(rider.xxa, 0.0, rider.zza);
    }

    @Override
    public void travel(Vec3 travel) {
        if (isAlive() && getControllingPassenger() instanceof Player rider && isControlledByLocalInstance()) {
            boolean jump = rider.level().isClientSide && clientJumpHeld.getAsBoolean();
            drive(new MountMotion.Input(travel.z, travel.x, jump, rider.isShiftKeyDown()));
            return;
        }
        if (getControllingPassenger() == null) {
            super.travel(travel);
        }
    }

    /**
     * One tick of ridden movement: the velocity from {@link MountMotion}, then the move with collisions. The rider's
     * client calls it every tick; GameTests call it on the server.
     */
    public void drive(MountMotion.Input input) {
        boolean water = isInWater() || isInLava();
        double gap = kind.flies() ? flightCeiling() - getY() : Double.MAX_VALUE;
        MountMotion.Velocity next = MountMotion.step(kind, stats(), input, getYRot(), motion,
                new MountMotion.Surroundings(onGround(), water, gap));
        Vec3 before = position();
        move(MoverType.SELF, new Vec3(next.x(), next.y(), next.z()));
        Vec3 moved = position().subtract(before);
        double vx = horizontalCollision && Math.abs(moved.x) < Math.abs(next.x()) ? moved.x : next.x();
        double vz = horizontalCollision && Math.abs(moved.z) < Math.abs(next.z()) ? moved.z : next.z();
        double vy = verticalCollision ? Math.min(0.0, next.y()) : next.y();
        if (verticalCollision && next.y() > 0.0) {
            vy = 0.0; // bumped a ceiling
        }
        motion = new MountMotion.Velocity(vx, vy, vz);
        setDeltaMovement(vx, vy, vz);
        resetFallDistance();
        calculateEntityAnimation(kind.flies() && !onGround());
    }

    /** The current velocity of the ride (blocks per tick). */
    public MountMotion.Velocity motion() {
        return motion;
    }

    /** The highest this mount may fly here ({@link #terrainCeiling()}, or the ceiling a GameTest fixed). */
    public double flightCeiling() {
        return Double.isNaN(fixedCeiling) ? terrainCeiling() : fixedCeiling;
    }

    /** GameTests: flies under this absolute height instead of the terrain's ceiling (the test world's sky varies). */
    public void fixCeilingForTests(double y) {
        this.fixedCeiling = y;
    }

    /** The ceiling from the terrain: above the highest ground around it, below the top of the world. */
    public double terrainCeiling() {
        List<Integer> heights = new ArrayList<>(9);
        int r = MountRules.CEILING_SAMPLE_RADIUS;
        int x = Mth.floor(getX());
        int z = Mth.floor(getZ());
        for (int dx = -r; dx <= r; dx += r) {
            for (int dz = -r; dz <= r; dz += r) {
                heights.add(level().getHeight(Heightmap.Types.MOTION_BLOCKING, x + dx, z + dz));
            }
        }
        return MountRules.flightCeiling(heights, level().getMaxBuildHeight());
    }

    @Override
    protected void positionRider(Entity passenger, Entity.MoveFunction move) {
        if (!hasPassenger(passenger)) {
            return;
        }
        float yaw = this.yBodyRot * Mth.DEG_TO_RAD;
        double forward = MountBodies.seatForward(kind);
        double x = getX() - Mth.sin(yaw) * forward;
        double z = getZ() + Mth.cos(yaw) * forward;
        move.accept(passenger, x, getY() + getPassengersRidingOffset() + passenger.getMyRidingOffset(), z);
    }

    @Override
    public double getPassengersRidingOffset() {
        return MountBodies.seatHeight(kind) - 0.35;
    }

    @Override
    public boolean shouldRiderSit() {
        return true;
    }

    // ------------------------------------------------------------------ the server's watch

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            tickLooks();
            return;
        }
        Entity rider = getFirstPassenger();
        if (rider == null || !rider.isAlive()) {
            if (rider != null) {
                ejectPassengers();
            }
            if (++riderlessTicks >= 2) {
                vanish();
            }
            return;
        }
        riderlessTicks = 0;
        if (rider instanceof ServerPlayer) {
            // the rider's client moves it: a move without a vertical part leaves onGround false, which the other
            // players would see as flying (the griffin's spread wings)
            setOnGround(grounded());
        }
        if (kind.flies() && rider instanceof Player player) {
            if (!player.isShiftKeyDown()) {
                landingSneak = false;
            } else if (landingSneak || !grounded()) {
                // in the air, sneak only descends; after a landing, sneak again to get off
                landingSneak = true;
                player.setShiftKeyDown(false);
            }
        }
        if (tickCount % 20 == 0) {
            playMovementSound();
        }
        if (rider instanceof ServerPlayer player) {
            watch(player);
        }
    }

    /**
     * Sends the mount away if it keeps moving much faster, or keeps hovering much higher, than it can (a modified
     * client): measured over {@link #WATCH_WINDOW}-tick windows, so a lag burst of moves is not mistaken for speed.
     */
    private void watch(ServerPlayer rider) {
        if (watchedFrom == null) {
            watchedFrom = position();
        }
        if (tickCount % WATCH_WINDOW != 0) {
            return;
        }
        double dx = getX() - watchedFrom.x;
        double dz = getZ() - watchedFrom.z;
        double perTick = Math.sqrt(dx * dx + dz * dz) / WATCH_WINDOW;
        watchedFrom = position();
        speedingWindows = perTick > stats().topSpeed() * 1.35 + 0.25 ? speedingWindows + 1 : 0;
        if (kind.flies()) {
            // above the ceiling the ride sinks by itself (MountMotion.ABOVE_CEILING_SINK): only hovering up there counts
            boolean high = getY() > flightCeiling() + 6.0;
            boolean sinking = !Double.isNaN(lastWatchedY) && getY() < lastWatchedY - 0.5;
            hoveringHigh = high && !sinking ? hoveringHigh + 1 : 0;
            lastWatchedY = getY();
        }
        if (speedingWindows >= 3 || hoveringHigh >= 3) {
            LOG.warn("{} rode a {} too {} ({} blocks/tick at y {}); the mount was sent away",
                    rider.getGameProfile().getName(), kind.id(), speedingWindows >= 3 ? "fast" : "high",
                    String.format(java.util.Locale.ROOT, "%.2f", perTick), Math.round(getY()));
            MountService.dismiss(rider, false);
        }
    }

    /**
     * Standing on something. On the server the rider's client moves the mount, and a move without a vertical part
     * does not set {@link #onGround()}, so the blocks under it are checked as well.
     */
    public boolean grounded() {
        return onGround() || !level().noCollision(this, getBoundingBox().move(0.0, -0.15, 0.0));
    }

    /** Leaves in a puff. */
    void vanish() {
        if (level() instanceof ServerLevel server) {
            server.sendParticles(ParticleTypes.CLOUD, getX(), getY() + getBbHeight() * 0.5, getZ(), 24,
                    getBbWidth() * 0.4, getBbHeight() * 0.3, getBbWidth() * 0.4, 0.02);
            server.sendParticles(ParticleTypes.END_ROD, getX(), getY() + getBbHeight() * 0.5, getZ(), 10,
                    getBbWidth() * 0.4, getBbHeight() * 0.3, getBbWidth() * 0.4, 0.05);
        }
        discard();
    }

    private void playMovementSound() {
        double speedNow = motion.horizontal() > 0.0 ? motion.horizontal()
                : Math.sqrt((getX() - xo) * (getX() - xo) + (getZ() - zo) * (getZ() - zo));
        if (kind == MountKind.BIKE && speedNow > 0.05) {
            level().playSound(null, getX(), getY(), getZ(), SoundEvents.BEACON_AMBIENT, SoundSource.NEUTRAL, 0.35F,
                    (float) (0.8 + speedNow));
        }
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
        switch (kind) {
            case HORSE -> playSound(motion.horizontal() > 0.3 || getDeltaMovement().horizontalDistanceSqr() > 0.09
                    ? SoundEvents.HORSE_GALLOP : SoundEvents.HORSE_STEP, 0.35F, 1.0F);
            case GRIFFIN -> playSound(SoundEvents.HORSE_STEP_WOOD, 0.25F, 1.4F);
            case BIKE -> {
                // rolls silently: the hum is its sound
            }
        }
    }

    // ------------------------------------------------------------------ untouchable

    @Override
    public boolean hurt(DamageSource source, float amount) {
        return source.is(DamageTypeTags.BYPASSES_INVULNERABILITY) && super.hurt(source, amount);
    }

    @Override
    public boolean isInvulnerableTo(DamageSource source) {
        return !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY);
    }

    @Override
    public boolean causeFallDamage(float distance, float multiplier, DamageSource source) {
        return false;
    }

    @Override
    public boolean isAttackable() {
        return false;
    }

    @Override
    public boolean canBeSeenAsEnemy() {
        return false;
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean ignoreExplosion() {
        return true;
    }

    @Override
    public boolean isPushedByFluid() {
        return false;
    }

    @Override
    protected void doPush(Entity entity) {
    }

    @Override
    public boolean canBeLeashed(Player player) {
        return false;
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    @Override
    protected void pushEntities() {
    }

    // ------------------------------------------------------------------ the looks (client)

    private void tickLooks() {
        distanceO = distance;
        leanO = lean;
        wheelO = wheel;
        wingsO = wings;
        flapO = flap;
        double dx = getX() - xo;
        double dy = getY() - yo;
        double dz = getZ() - zo;
        float horizontal = (float) Math.sqrt(dx * dx + dz * dz);
        speed += (horizontal - speed) * 0.35F;
        climb += ((float) dy - climb) * 0.3F;
        distance += horizontal;
        double radius = MountBodies.wheelRadius(kind);
        if (radius > 0.0) {
            // rolling forward turns the wheels backwards around their axle, as seen from the side
            wheel += (float) (horizontal / radius) * forwardSign(dx, dz);
        }
        float yaw = getYRot();
        float turn = Float.isNaN(lastYaw) ? 0.0F : Mth.wrapDegrees(yaw - lastYaw);
        lastYaw = yaw;
        // turning right (the yaw grows) leans the bike to the right
        float leanTarget = Mth.clamp(turn * 2.2F * Math.min(1.0F, speed * 3.0F), -28.0F, 28.0F);
        lean += (leanTarget - lean) * 0.25F;
        if (onGround()) {
            groundTicks++;
            airTicks = 0;
        } else {
            airTicks++;
            groundTicks = 0;
        }
        if (kind.flies()) {
            boolean airborne = airTicks > 2;
            float spreadTarget = airborne ? 1.0F : 0.0F;
            wings += (spreadTarget - wings) * 0.2F;
            // beats: fast when climbing or taking off, a slow lazy beat when cruising, none while gliding down
            float rate = !airborne ? 0.0F : climb > 0.04F || airTicks < 20 ? 0.11F : climb < -0.02F ? 0.0F : 0.04F;
            flap += rate;
            beat += ((rate > 0.0F ? Math.min(1.0F, rate * 12.0F) : 0.0F) - beat) * 0.15F;
        }
    }

    private float forwardSign(double dx, double dz) {
        float yaw = getYRot() * Mth.DEG_TO_RAD;
        double along = -Mth.sin(yaw) * dx + Mth.cos(yaw) * dz;
        return along >= 0.0 ? 1.0F : -1.0F;
    }

    /** Blocks travelled so far (the phase of the walk and run cycles). */
    public float distance(float partialTick) {
        return Mth.lerp(partialTick, distanceO, distance);
    }

    /** Smoothed horizontal speed (blocks per tick). */
    public float speed() {
        return speed;
    }

    /** Smoothed vertical speed (blocks per tick). */
    public float climb() {
        return climb;
    }

    /** The bike's lean into a turn (degrees, positive to the right). */
    public float lean(float partialTick) {
        return Mth.lerp(partialTick, leanO, lean);
    }

    /** The bike's wheel angle (radians). */
    public float wheel(float partialTick) {
        return Mth.lerp(partialTick, wheelO, wheel);
    }

    /** A flier's wing spread: 0 folded, 1 spread. */
    public float wings(float partialTick) {
        return Mth.lerp(partialTick, wingsO, wings);
    }

    /** Wing beats so far (the flap animation's phase). */
    public float flap(float partialTick) {
        return Mth.lerp(partialTick, flapO, flap);
    }

    /** How hard the wings beat: 0 gliding, 1 climbing (smoothed). */
    public float beat() {
        return beat;
    }

    /** Ticks since it left the ground (0 on the ground). */
    public int airTicks() {
        return airTicks;
    }

    /** Ticks since it touched the ground (0 in the air). */
    public int groundTicks() {
        return groundTicks;
    }
}
