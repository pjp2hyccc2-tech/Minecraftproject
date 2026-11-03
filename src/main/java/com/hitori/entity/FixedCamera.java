package com.hitori.entity;

import com.hitori.game.Cameras;
import net.minecraft.core.NonNullList;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * 定点カメラ (docs/DESIGN.md §3.1, §5): a small action cam on a mini tripod with a red REC LED. It looks along its own
 * yaw / pitch; the camera app on the laptop or the phone views the apartment through it
 * ({@code Minecraft.setCameraEntity}). A {@link LivingEntity} on purpose: shader loaders read the night-vision and
 * blindness channels from the camera entity's effects (the IR look of the camera app, §9), so the client puts a hidden
 * night vision effect on the camera it is viewing through.
 * <p>
 * Never saved with the chunk: the game keeps the cameras in its own data ({@code game/Cameras}) and spawns them.
 * A client-only instance (never added to a level) is the "virtual camera" of the endings and the peek eye.
 */
public class FixedCamera extends LivingEntity implements Animated {
    private static final EntityDataAccessor<Integer> DATA_NUMBER = SynchedEntityData.defineId(FixedCamera.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_POSE = SynchedEntityData.defineId(FixedCamera.class, EntityDataSerializers.INT);
    public static final int POSE_IDLE = 0, POSE_FALLEN = 1;
    private static final NonNullList<ItemStack> NO_ARMOR = NonNullList.withSize(4, ItemStack.EMPTY);

    /** Client-only stand-in (virtual camera / peek eye): not in any level, moved by hand. */
    public boolean clientOnly;
    /** Tick the current pose began (client: the model's clock). */
    private int poseStart = -1000;

    public FixedCamera(EntityType<? extends FixedCamera> type, Level level) {
        super(type, level);
        setNoGravity(true);
        setInvulnerable(true);
        setSilent(true);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return LivingEntity.createLivingAttributes().add(Attributes.MAX_HEALTH, 1.0).add(Attributes.KNOCKBACK_RESISTANCE, 1.0);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(DATA_NUMBER, 0);
        entityData.define(DATA_POSE, POSE_IDLE);
    }

    /** CAM n (1..4). */
    public int number() {
        return entityData.get(DATA_NUMBER);
    }

    public void setNumber(int n) {
        entityData.set(DATA_NUMBER, n);
    }

    public int camPose() {
        return entityData.get(DATA_POSE);
    }

    public void setCamPose(int p) {
        entityData.set(DATA_POSE, p);
    }

    public boolean fallen() {
        return camPose() == POSE_FALLEN;
    }

    // ---- Animated (the model: IDLE, FALLEN tipping over in ~5 ticks with a bounce)
    @Override
    public int pose() {
        return camPose();
    }

    @Override
    public float poseTime(float partialTick) {
        return tickCount - poseStart + partialTick;
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        // a camera that arrives already fallen (rejoining) lies there; one knocked over now tips over
        if (DATA_POSE.equals(key)) poseStart = tickCount <= 1 ? -1000 : tickCount;
    }

    /** The feed's roll while the camera falls and after (degrees): 83 on its side, ramping in with the fall. */
    public float feedRoll(float partialTick) {
        if (!fallen()) return 0.0f;
        float pt = Math.max(0.0f, poseTime(partialTick));
        float c = net.minecraft.util.Mth.clamp(pt / 5.0f, 0.0f, 1.0f);
        float fall = c * c * c;
        float bounce = pt > 5.0f ? (float) Math.exp(-0.5f * (pt - 5.0f)) * net.minecraft.util.Mth.sin((pt - 5.0f) * 1.5f) : 0.0f;
        return 83.0f * fall - 8.0f * bounce;
    }

    /** Points it (body, head and view) in one go, without interpolation. */
    public void aim(float yaw, float pitch) {
        setYRot(yaw);
        yRotO = yaw;
        setXRot(pitch);
        xRotO = pitch;
        yBodyRot = yaw;
        yBodyRotO = yaw;
        yHeadRot = yaw;
        yHeadRotO = yaw;
    }

    /** Moves a client-only instance (keeps old = new so the view does not slide). */
    public void place(Vec3 eyeAt, float yaw, float pitch) {
        double y = eyeAt.y - getEyeHeight();
        setPos(eyeAt.x, y, eyeAt.z);
        xo = xOld = eyeAt.x;
        yo = yOld = y;
        zo = zOld = eyeAt.z;
        aim(yaw, pitch);
    }

    /** The direction the lens looks in. */
    public Vec3 lookDir() {
        return Vec3.directionFromRotation(getXRot(), getYRot());
    }

    @Override
    public void tick() {
        setDeltaMovement(Vec3.ZERO);
        super.tick();
        // hold the aim: nothing turns a camera on a tripod
        yBodyRot = getYRot();
        yHeadRot = getYRot();
        setDeltaMovement(Vec3.ZERO);
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        if (level().isClientSide) return player.isShiftKeyDown() ? InteractionResult.SUCCESS : InteractionResult.PASS;
        if (player instanceof ServerPlayer sp && player.isShiftKeyDown() && Cameras.pickUp(sp, this)) return InteractionResult.CONSUME;
        return InteractionResult.PASS;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return super.hurt(source, amount);
        return false;
    }

    @Override
    public boolean isPickable() {
        return !clientOnly;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    protected void doPush(Entity entity) {
    }

    @Override
    public boolean canBeCollidedWith() {
        return false;
    }

    @Override
    public boolean attackable() {
        return false;
    }

    @Override
    public boolean shouldBeSaved() {
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
    public boolean shouldShowName() {
        return false;
    }

    @Override
    public Iterable<ItemStack> getArmorSlots() {
        return NO_ARMOR;
    }

    @Override
    public ItemStack getItemBySlot(EquipmentSlot slot) {
        return ItemStack.EMPTY;
    }

    @Override
    public void setItemSlot(EquipmentSlot slot, ItemStack stack) {
    }

    @Override
    public HumanoidArm getMainArm() {
        return HumanoidArm.RIGHT;
    }

    @Override
    public AABB getBoundingBoxForCulling() {
        return getBoundingBox().inflate(0.5);
    }
}
