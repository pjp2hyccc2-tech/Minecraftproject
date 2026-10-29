package com.huntcraft.village.npc;

import com.huntcraft.core.HCDimensions;
import com.huntcraft.village.HcAnimated;
import com.huntcraft.village.VillageActions;
import com.huntcraft.village.VillageRegistry;
import com.huntcraft.village.world.VillageSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AnimationState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

/**
 * Stationary, invulnerable village NPC (receptionist, blacksmith, merchant, mofu chef, mofu elder).
 * Right-click opens the matching screen (see {@link VillageActions#npcInteract}).
 */
public class VillageNpc extends PathfinderMob implements HcAnimated {
    private static final EntityDataAccessor<Integer> DATA_ROLE = SynchedEntityData.defineId(VillageNpc.class, EntityDataSerializers.INT);
    public static final String[] ACTIONS = {"wave", "stir", "nod", "cheer"};
    private static final Set<String> CHEF_PARTS = Set.of("hat_chef", "apron", "ladle");
    private static final Set<String> ELDER_PARTS = Set.of("kasa", "beard", "staff", "haori");

    public final AnimationState idleAnim = new AnimationState();
    public final AnimationState actionAnim = new AnimationState();
    @Nullable
    private String actionName;
    @Nullable
    private BlockPos home;
    private float homeYaw;
    private int idleTimer;

    public VillageNpc(EntityType<? extends VillageNpc> type, Level level) {
        super(type, level);
        this.setPersistenceRequired();
        this.setInvulnerable(true);
        this.idleTimer = 40 + this.random.nextInt(80);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 40.0D).add(Attributes.MOVEMENT_SPEED, 0.25D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0D).add(Attributes.FOLLOW_RANGE, 16.0D).add(Attributes.ATTACK_DAMAGE, 1.0D);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(DATA_ROLE, 0);
    }

    public NpcRole getRole() {
        return NpcRole.byId(this.entityData.get(DATA_ROLE));
    }

    public void setRole(NpcRole role) {
        this.entityData.set(DATA_ROLE, role.ordinal());
        this.setCustomName(role.displayName());
        this.setCustomNameVisible(false);
        ItemStack held = switch (role) {
            case BLACKSMITH -> new ItemStack(Items.IRON_AXE);
            case RECEPTIONIST -> new ItemStack(Items.WRITABLE_BOOK);
            case MERCHANT -> new ItemStack(Items.PAPER);
            default -> ItemStack.EMPTY;
        };
        this.setItemSlot(EquipmentSlot.MAINHAND, held);
        this.setDropChance(EquipmentSlot.MAINHAND, 0.0F);
    }

    public void setHome(BlockPos pos, float yaw) {
        this.home = pos;
        this.homeYaw = yaw;
        this.setYRot(yaw);
        this.yHeadRot = yaw;
        this.yBodyRot = yaw;
    }

    @Nullable
    public BlockPos getHome() {
        return home;
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new ReturnHomeGoal(this));
        this.goalSelector.addGoal(2, new LookAtPlayerGoal(this, Player.class, 8.0F, 1.0F));
        this.goalSelector.addGoal(3, new RandomLookAroundGoal(this));
    }

    // ------------------------------------------------------------------ invulnerable & immovable
    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return super.hurt(source, amount);
        return false;
    }

    @Override
    public boolean isInvulnerableTo(DamageSource source) {
        return !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY);
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    protected void doPush(Entity entity) {
    }

    @Override
    public void knockback(double strength, double x, double z) {
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
    public boolean requiresCustomPersistence() {
        return true;
    }

    @Override
    protected boolean shouldDespawnInPeaceful() {
        return false;
    }

    // ------------------------------------------------------------------ interaction
    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
        if (!this.level().isClientSide && player instanceof ServerPlayer sp) {
            this.getLookControl().setLookAt(player, 30.0F, 30.0F);
            VillageActions.npcInteract(sp, this);
            if (getRole().mofu) playAction(getRole() == NpcRole.CHEF ? 3 : 2);
            else this.swing(InteractionHand.MAIN_HAND);
        }
        return InteractionResult.sidedSuccess(this.level().isClientSide);
    }

    // ------------------------------------------------------------------ ticking
    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide) {
            this.idleAnim.startIfStopped(this.tickCount);
            return;
        }
        if (--idleTimer <= 0) {
            idleTimer = 70 + this.random.nextInt(110);
            doIdleAction();
        }
        if (this.tickCount % 100 == 13) validateRecord();
    }

    private void doIdleAction() {
        Player near = this.level().getNearestPlayer(this, 14.0D);
        switch (getRole()) {
            case BLACKSMITH -> {
                this.swing(InteractionHand.MAIN_HAND);
                this.level().playSound(null, getX(), getY(), getZ(), VillageRegistry.ANVIL_TAP.get(), SoundSource.NEUTRAL, 0.45F,
                        0.9F + this.random.nextFloat() * 0.2F);
                if (this.level() instanceof ServerLevel sl) {
                    Vec3 front = this.position().add(Vec3.directionFromRotation(0, this.yBodyRot).scale(0.9)).add(0, 0.9, 0);
                    sl.sendParticles(ParticleTypes.LAVA, front.x, front.y, front.z, 2, 0.1, 0.05, 0.1, 0.0);
                    sl.sendParticles(ParticleTypes.CRIT, front.x, front.y, front.z, 4, 0.15, 0.1, 0.15, 0.2);
                }
                idleTimer = 24 + this.random.nextInt(36);
            }
            case CHEF -> {
                playAction(1);
                if (this.level() instanceof ServerLevel sl) {
                    Vec3 front = this.position().add(Vec3.directionFromRotation(0, this.yBodyRot).scale(1.2)).add(0, 1.0, 0);
                    sl.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, front.x, front.y, front.z, 1, 0.1, 0.1, 0.1, 0.01);
                }
            }
            case ELDER -> {
                if (near != null) playAction(2);
            }
            default -> {
                if (near != null && this.random.nextFloat() < 0.35F) this.swing(InteractionHand.MAIN_HAND);
            }
        }
    }

    /** Plays one of {@link #ACTIONS} on clients. */
    public void playAction(int idx) {
        if (idx >= 0 && idx < ACTIONS.length) this.level().broadcastEntityEvent(this, (byte) (100 + idx));
    }

    @Override
    public void handleEntityEvent(byte id) {
        if (id >= 100 && id < 100 + ACTIONS.length) {
            this.actionName = ACTIONS[id - 100];
            this.actionAnim.start(this.tickCount);
        } else {
            super.handleEntityEvent(id);
        }
    }

    /** Duplicate NPCs (e.g. an old copy loaded from disk after a respawn) remove themselves. */
    private void validateRecord() {
        if (!(this.level() instanceof ServerLevel sl) || !HCDimensions.isVillage(sl)) return;
        UUID expected = VillageSavedData.get(sl).npcUuid(getRole().id);
        if (expected != null && !expected.equals(this.getUUID())) this.discard();
    }

    // ------------------------------------------------------------------ hcmodel
    @Override
    public AnimationState hcIdle() {
        return idleAnim;
    }

    @Override
    public AnimationState hcAction() {
        return actionAnim;
    }

    @Override
    @Nullable
    public String hcActionName() {
        return actionName;
    }

    @Override
    public Set<String> hcShown() {
        return switch (getRole()) {
            case CHEF -> CHEF_PARTS;
            case ELDER -> ELDER_PARTS;
            default -> Set.of();
        };
    }

    // ------------------------------------------------------------------ save
    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putString("Role", getRole().id);
        if (home != null) {
            tag.putInt("HomeX", home.getX());
            tag.putInt("HomeY", home.getY());
            tag.putInt("HomeZ", home.getZ());
        }
        tag.putFloat("HomeYaw", homeYaw);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        setRole(NpcRole.byName(tag.getString("Role")));
        if (tag.contains("HomeX")) home = new BlockPos(tag.getInt("HomeX"), tag.getInt("HomeY"), tag.getInt("HomeZ"));
        homeYaw = tag.getFloat("HomeYaw");
        this.setInvulnerable(true);
    }

    /** Walks back to the home spot when displaced. */
    static class ReturnHomeGoal extends Goal {
        private final VillageNpc npc;
        private int ticks;

        ReturnHomeGoal(VillageNpc npc) {
            this.npc = npc;
            this.setFlags(EnumSet.of(Goal.Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            return npc.home != null && npc.home.distToCenterSqr(npc.position()) > 2.25;
        }

        @Override
        public boolean canContinueToUse() {
            return npc.home != null && ticks < 200 && npc.home.distToCenterSqr(npc.position()) > 0.5;
        }

        @Override
        public void start() {
            ticks = 0;
            BlockPos h = npc.home;
            if (h != null) npc.getNavigation().moveTo(h.getX() + 0.5, h.getY(), h.getZ() + 0.5, 0.7);
        }

        @Override
        public void tick() {
            ticks++;
            BlockPos h = npc.home;
            if (h == null) return;
            if (ticks % 20 == 0) npc.getNavigation().moveTo(h.getX() + 0.5, h.getY(), h.getZ() + 0.5, 0.7);
            if (ticks > 160 || h.distToCenterSqr(npc.position()) > 400) {
                npc.moveTo(h.getX() + 0.5, h.getY(), h.getZ() + 0.5, npc.homeYaw, 0);
                npc.getNavigation().stop();
            }
        }

        @Override
        public void stop() {
            npc.getNavigation().stop();
            npc.setYRot(npc.homeYaw);
            npc.yBodyRot = npc.homeYaw;
        }
    }
}
