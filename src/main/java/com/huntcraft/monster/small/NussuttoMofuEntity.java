package com.huntcraft.monster.small;

import com.huntcraft.core.Element;
import com.huntcraft.core.IHunterWeapon;
import com.huntcraft.data.HunterData;
import com.huntcraft.entity.monster.Hitzone;
import com.huntcraft.entity.monster.MonsterProfile;
import com.huntcraft.entity.monster.PartDef;
import com.huntcraft.entity.monster.RewardTable;
import com.huntcraft.entity.monster.attack.MonsterAttack;
import com.huntcraft.entity.monster.attack.TimelineAttack;
import com.huntcraft.item.GuildPassItem;
import com.huntcraft.item.HunterNotesItem;
import com.huntcraft.registry.HCItems;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Equipable;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.minecraft.world.item.TieredItem;
import net.minecraft.world.item.TridentItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

/**
 * ぬすっとモフ - mischievous cat-folk thief with a karakusa bandana and a bundle on its back. It tiptoes up to a
 * hunter, snatches ONE random item stack from the inventory (never the held item, equipment or the guild pass),
 * cheers, and runs off with the loot balanced on its head. About 40 s later it burrows away with it - unless it is
 * defeated first, in which case it drops the stolen item and a thief coin.
 */
public class NussuttoMofuEntity extends SmallHuntMonster {
    public static final int POSE_BURROW = 1;
    public static final int VARIANTS = 3;
    private static final EntityDataAccessor<ItemStack> DATA_LOOT = SynchedEntityData.defineId(NussuttoMofuEntity.class, EntityDataSerializers.ITEM_STACK);
    private static final EntityDataAccessor<Integer> DATA_VARIANT = SynchedEntityData.defineId(NussuttoMofuEntity.class, EntityDataSerializers.INT);

    static final Hitzone HZ_HEAD = Hitzone.of(100, 100, 100, 30, 30, 30, 30, 30);
    static final Hitzone HZ_BODY = Hitzone.of(90, 90, 90, 25, 25, 25, 25, 25);

    public static final MonsterProfile PROFILE = MonsterProfile.builder("nussutto_mofu", false)
            .health(14, 22).highRank(1.3F, 0.9F)
            .scale(0.92F, 1.08F)
            .weakness(Element.FIRE, 2).weakness(Element.WATER, 2).weakness(Element.THUNDER, 2).weakness(Element.ICE, 2)
            .part(new PartDef("head", 0, 0.55F, 0.05F, 0.5F, 0.42F, HZ_HEAD).ko())
            .part(PartDef.body(HZ_BODY))
            .rageThreshold(99F).flinchThreshold(0.2F)
            .carves(new RewardTable().low(HCItems.MOFU_COIN, 100).high(HCItems.MOFU_COIN, 100, 1, 2))
            .carveCount(1)
            .questReward(200, 5)
            .build();

    public static final String[] ANIMS = {"flinch", "steal", "swipe", "cheer", "look", "count"};

    /** Ticks a thief keeps its loot before burrowing away (~40 s). */
    public static final int FLEE_TIME = 800;
    private static final int BURROW_TIME = 30;

    private int fleeTicks;
    private int burrowTicks;

    public NussuttoMofuEntity(EntityType<? extends NussuttoMofuEntity> type, Level level) {
        super(type, level);
    }

    @Override
    public MonsterProfile profile() {
        return PROFILE;
    }

    @Override
    public String[] animationNames() {
        return ANIMS;
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(DATA_LOOT, ItemStack.EMPTY);
        this.entityData.define(DATA_VARIANT, 0);
    }

    public ItemStack getLoot() {
        return this.entityData.get(DATA_LOOT);
    }

    public boolean hasLoot() {
        return !getLoot().isEmpty();
    }

    public void setLoot(ItemStack stack) {
        this.entityData.set(DATA_LOOT, stack.copy());
    }

    public int getVariant() {
        return this.entityData.get(DATA_VARIANT);
    }

    public void setVariant(int v) {
        this.entityData.set(DATA_VARIANT, Math.floorMod(v, VARIANTS));
    }

    public boolean isBurrowing() {
        return getSpecialPose() == POSE_BURROW;
    }

    @Override
    protected void registerGoals() {
        super.registerGoals();
        this.goalSelector.addGoal(0, new FleeWithLootGoal(this));
    }

    @Override
    protected List<MonsterAttack> createAttacks() {
        MonsterAttack steal = TimelineAttack.builder("steal", 24).range(0, 3.0F).angle(45).weight(16).cooldown(40)
                .track(0, 6, 20)
                .lunge(6, 11, 0.42)
                .at(11, (m, target, t) -> {
                    if (m instanceof NussuttoMofuEntity mofu) mofu.trySteal(target);
                })
                .sound(6, () -> SoundEvents.CAT_AMBIENT, 1.0F, 1.6F)
                .build();
        MonsterAttack swipe = TimelineAttack.builder("swipe", 16).range(0, 1.9F).angle(45).weight(8).cooldown(20)
                .track(0, 5, 18).move(4, 6, 0.15, 0)
                .hit(7, 0, 0.45, 0.6, 0.6F, 2, Element.NONE, 0.4F)
                .sound(7, () -> SoundEvents.CAT_HISS, 0.8F, 1.4F)
                .build();
        return List.of(steal, swipe);
    }

    // ------------------------------------------------------------------ stealing
    /** Items a thief will never take. */
    public static boolean canSteal(ItemStack stack) {
        if (stack.isEmpty()) return false;
        Item it = stack.getItem();
        if (it instanceof IHunterWeapon || it instanceof Equipable || it instanceof GuildPassItem || it instanceof HunterNotesItem) return false;
        return !(it instanceof TieredItem || it instanceof ProjectileWeaponItem || it instanceof TridentItem);
    }

    public void trySteal(@Nullable LivingEntity target) {
        if (target == null || hasLoot()) return;
        Vec3 grab = localPos(0, 0.4, 0.8);
        double reach = 1.0 * getMonsterScale();
        if (boxDistSqr(target.getBoundingBox(), grab) > reach * reach) return; // missed
        if (!(target instanceof Player p) || p.isCreative() || p.isSpectator()) {
            hitEntity(target, 1, Element.NONE, 0.2F, 0);
            return;
        }
        if (HunterData.get(p).isInvulnerable()) return; // dodged
        Inventory inv = p.getInventory();
        List<Integer> slots = new ArrayList<>();
        for (int i = 0; i < inv.items.size(); i++) {
            if (i == inv.selected) continue;
            if (canSteal(inv.items.get(i))) slots.add(i);
        }
        if (slots.isEmpty()) {
            hitEntity(p, 2, Element.NONE, 0.3F, 0); // nothing worth taking: a cheeky scratch instead
            return;
        }
        int slot = slots.get(getRandom().nextInt(slots.size()));
        ItemStack stolen = inv.items.get(slot).copy();
        inv.items.set(slot, ItemStack.EMPTY);
        inv.setChanged();
        setLoot(stolen);
        fleeTicks = 0;
        hitEntity(p, 1, Element.NONE, 0.25F, 0);
        p.displayClientMessage(Component.translatable("message.huntcraft.mofu_stole", stolen.getHoverName()).withStyle(ChatFormatting.GOLD), true);
        playSound(SoundEvents.ITEM_PICKUP, 1.0F, 1.3F);
        playSound(SoundEvents.CAT_PURREOW, 1.2F, 1.5F);
        burstParticles(ParticleTypes.HAPPY_VILLAGER, position().add(0, 1.0, 0), 6, 0.3);
        setTarget(null);
        playAction("cheer", 16);
    }

    @Override
    public void setTarget(@Nullable LivingEntity target) {
        // a thief with loot only wants to get away
        if (target != null && (hasLoot() || isBurrowing())) return;
        super.setTarget(target);
    }

    @Override
    public boolean brainTick(LivingEntity target) {
        return hasLoot() || isBurrowing();
    }

    @Override
    public boolean isLocked() {
        return super.isLocked() || isBurrowing();
    }

    @Override
    @Nullable
    protected String specialStatePose() {
        return isBurrowing() ? "burrow" : null;
    }

    @Override
    protected void smallTick() {
        if (isBurrowing()) {
            burrowTicks--;
            if (level() instanceof ServerLevel sl && this.tickCount % 2 == 0) {
                BlockState below = level().getBlockState(blockPosition().below());
                if (!below.isAir()) {
                    sl.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, below), getX(), getY() + 0.1, getZ(), 6, 0.3, 0.05, 0.3, 0.05);
                }
            }
            if (this.tickCount % 6 == 0) playSound(SoundEvents.ROOTED_DIRT_BREAK, 0.8F, 1.2F);
            if (burrowTicks <= 0) {
                // gone, loot and all
                if (level() instanceof ServerLevel sl) sl.sendParticles(ParticleTypes.POOF, getX(), getY() + 0.3, getZ(), 12, 0.3, 0.2, 0.3, 0.02);
                setLoot(ItemStack.EMPTY);
                discard();
            }
            return;
        }
        if (hasLoot()) {
            fleeTicks++;
            if (fleeTicks >= FLEE_TIME && !super.isLocked() && !isActing()) startBurrow();
        }
    }

    private void startBurrow() {
        stopAction();
        setSpecialPose(POSE_BURROW);
        burrowTicks = BURROW_TIME;
        this.getNavigation().stop();
        playSound(SoundEvents.CAT_AMBIENT, 1.0F, 1.8F);
    }

    @Override
    protected void dropCustomDeathLoot(DamageSource source, int looting, boolean recentlyHit) {
        super.dropCustomDeathLoot(source, looting, recentlyHit);
        if (hasLoot()) {
            this.spawnAtLocation(getLoot().copy());
            setLoot(ItemStack.EMPTY);
        }
    }

    @Override
    public void combatMove(LivingEntity target, double dist, float angle, boolean waiting) {
        // tiptoe closer, never charge
        float s = getMonsterScale();
        if (dist > 1.4 * s) {
            if (this.tickCount % 5 == 0 || this.getNavigation().isDone()) {
                boolean hurry = dist > 12 && !waiting;
                this.getNavigation().moveTo(target, hurry ? 1.2 : (waiting ? 0.55 : 0.75));
            }
        } else {
            this.getNavigation().stop();
            if (Math.abs(angle) > 10) turnTowards(target.position(), getTurnSpeed());
        }
    }

    @Override
    @Nullable
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, MobSpawnType reason,
                                        @Nullable SpawnGroupData data, @Nullable CompoundTag tag) {
        setVariant(getRandom().nextInt(VARIANTS));
        return super.finalizeSpawn(level, difficulty, reason, data, tag);
    }

    @Override
    public int getAttackGap() {
        return 20;
    }

    @Override
    protected double runThreshold() {
        return 0.13;
    }

    @Override
    protected IdleAction[] idleActions() {
        // with loot the arms are busy holding it up (MofuModel): only glance around nervously
        return hasLoot() ? new IdleAction[]{new IdleAction("look", 40, 1)}
                : new IdleAction[]{new IdleAction("look", 40, 3), new IdleAction("count", 40, 1)};
    }

    @Override
    protected boolean wantsIdleActions() {
        return super.wantsIdleActions() && !isBurrowing();
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return hasLoot() ? SoundEvents.CAT_PURREOW : SoundEvents.CAT_STRAY_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.CAT_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.CAT_DEATH;
    }

    @Override
    public float getVoicePitch() {
        return 1.35F + (this.getRandom().nextFloat() - 0.5F) * 0.2F;
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
        // light paws
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (hasLoot()) tag.put("HCLoot", getLoot().save(new CompoundTag()));
        tag.putInt("HCFlee", fleeTicks);
        tag.putInt("HCBurrow", burrowTicks);
        tag.putInt("HCVariant", getVariant());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        setLoot(tag.contains("HCLoot") ? ItemStack.of(tag.getCompound("HCLoot")) : ItemStack.EMPTY);
        fleeTicks = tag.getInt("HCFlee");
        burrowTicks = tag.getInt("HCBurrow");
        if (isBurrowing() && burrowTicks <= 0) burrowTicks = 1;
        setVariant(tag.getInt("HCVariant"));
    }

    /** Runs away from nearby hunters while carrying loot. */
    static class FleeWithLootGoal extends Goal {
        private final NussuttoMofuEntity mofu;
        @Nullable
        private Player from;
        private int repath;

        FleeWithLootGoal(NussuttoMofuEntity mofu) {
            this.mofu = mofu;
            this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            if (!mofu.hasLoot() || mofu.isLocked() || mofu.isActing()) return false;
            from = mofu.level().getNearestPlayer(mofu, 16.0);
            return from != null && !from.isSpectator();
        }

        @Override
        public boolean canContinueToUse() {
            return mofu.hasLoot() && !mofu.isLocked() && from != null && from.isAlive() && mofu.distanceToSqr(from) < 26 * 26;
        }

        @Override
        public void start() {
            repath = 0;
        }

        @Override
        public void tick() {
            if (from == null) return;
            if (--repath <= 0) {
                repath = 12;
                Vec3 away = DefaultRandomPos.getPosAway(mofu, 14, 6, from.position());
                if (away != null) mofu.getNavigation().moveTo(away.x, away.y, away.z, 1.75);
            }
        }

        @Override
        public void stop() {
            from = null;
            mofu.getNavigation().stop();
        }
    }
}
