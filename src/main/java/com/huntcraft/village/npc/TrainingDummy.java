package com.huntcraft.village.npc;

import com.huntcraft.core.Element;
import com.huntcraft.core.HunterDamage;
import com.huntcraft.core.IHunterWeapon;
import com.huntcraft.village.HcAnimated;
import com.huntcraft.village.VillageRegistry;
import com.huntcraft.village.companion.MofuEntity;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AnimationState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/**
 * Straw training dummy. Hunter weapon hits are processed by the core damage formula (HunterDamage) which also shows
 * the damage numbers; other hits show a plain number here. The name plate shows last hit / total / DPS.
 */
public class TrainingDummy extends LivingEntity implements HcAnimated {
    private final NonNullList<ItemStack> armor = NonNullList.withSize(4, ItemStack.EMPTY);
    public final AnimationState idleAnim = new AnimationState();
    public final AnimationState hitAnim = new AnimationState();
    private float total;
    private int hits;
    private long firstHit;
    private long lastHit = -1;

    public TrainingDummy(EntityType<? extends TrainingDummy> type, Level level) {
        super(type, level);
        this.setCustomName(Component.translatable("entity.huntcraft.training_dummy"));
        this.setCustomNameVisible(true);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return LivingEntity.createLivingAttributes().add(Attributes.MAX_HEALTH, 1000.0D).add(Attributes.KNOCKBACK_RESISTANCE, 1.0D);
    }

    @Override
    public Iterable<ItemStack> getArmorSlots() {
        return armor;
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
    public boolean hurt(DamageSource source, float amount) {
        if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return super.hurt(source, amount);
        if (!(source.getEntity() instanceof LivingEntity)) return false;
        this.invulnerableTime = 0;
        return super.hurt(source, amount);
    }

    @Override
    protected void actuallyHurt(DamageSource source, float amount) {
        float before = this.getHealth();
        super.actuallyHurt(source, amount);
        float dealt = before - this.getHealth();
        if (!source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) this.setHealth(this.getMaxHealth());
        if (dealt > 0 && !this.level().isClientSide) onDummyHit(source, dealt);
    }

    @Override
    public void die(DamageSource source) {
        if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            super.die(source);
            return;
        }
        this.setHealth(this.getMaxHealth());
    }

    private void onDummyHit(DamageSource source, float dealt) {
        long now = this.level().getGameTime();
        if (lastHit < 0 || now - lastHit > 100) {
            total = 0;
            hits = 0;
            firstHit = now;
        }
        total += dealt;
        hits++;
        lastHit = now;
        float secs = Math.max(1.0F, (now - firstHit) / 20.0F);
        this.setCustomName(Component.translatable("entity.huntcraft.training_dummy.stats", fmt(dealt), fmt(total), fmt(total / secs), hits));
        this.level().broadcastEntityEvent(this, (byte) 100);
        this.level().playSound(null, getX(), getY(), getZ(), VillageRegistry.DUMMY_HIT.get(), SoundSource.NEUTRAL, 0.6F,
                0.85F + this.random.nextFloat() * 0.3F);
        Entity attacker = source.getEntity();
        Vec3 pos = this.position().add(0, 1.7, 0);
        if (attacker instanceof ServerPlayer sp) {
            boolean coreShown = source.getDirectEntity() == sp && sp.getMainHandItem().getItem() instanceof IHunterWeapon;
            if (!coreShown) HunterDamage.sendDamageNumber(sp, pos, dealt, 0xFFFFFF, false, Element.NONE);
        } else if (attacker instanceof MofuEntity mofu && mofu.getOwner() != null) {
            HunterDamage.sendDamageNumber(mofu.getOwner(), pos, dealt, 0xFFC864, false, Element.NONE);
        }
    }

    private static String fmt(float f) {
        return f >= 100 ? String.valueOf(Math.round(f)) : String.format(Locale.ROOT, "%.1f", f);
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide) {
            this.idleAnim.startIfStopped(this.tickCount);
            return;
        }
        if (this.tickCount % 100 == 37 && this.level() instanceof net.minecraft.server.level.ServerLevel sl
                && com.huntcraft.core.HCDimensions.isVillage(sl)) {
            java.util.List<java.util.UUID> ids = com.huntcraft.village.world.VillageSavedData.get(sl).dummies;
            if (!ids.isEmpty() && !ids.contains(this.getUUID())) {
                this.discard();
                return;
            }
        }
        if (lastHit >= 0 && this.level().getGameTime() - lastHit > 160) {
            lastHit = -1;
            this.setCustomName(Component.translatable("entity.huntcraft.training_dummy"));
        }
        this.setDeltaMovement(0, Math.min(0, this.getDeltaMovement().y), 0);
    }

    @Override
    public void handleEntityEvent(byte id) {
        if (id == 100) this.hitAnim.start(this.tickCount);
        else super.handleEntityEvent(id);
    }

    @Override
    public boolean isAffectedByPotions() {
        return false;
    }

    @Override
    public AnimationState hcIdle() {
        return idleAnim;
    }

    @Override
    public AnimationState hcAction() {
        return hitAnim;
    }

    @Override
    @Nullable
    public String hcActionName() {
        return "hit";
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean("HcDummy", true);
    }
}
