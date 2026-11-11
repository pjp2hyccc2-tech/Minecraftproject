package com.eternalrealm.guild.house.forge;

import com.eternalrealm.network.forge.DamageNumberPacket;
import com.eternalrealm.network.forge.ERNetwork;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * The training dummy (練習用の案山子) of the guild houses: a straw figure on a post that stands where the house's
 * blueprint put it. Hit it with anything - a sword, a skill, an arrow - and it wobbles, thumps and shows the damage the
 * hit would have done (the number floats over it like any hit), and the attacker's action bar keeps a running
 * tally: hits, total and damage per second over the current bout (a bout ends after five quiet seconds). It never
 * takes damage, never moves and gives no ranking credit: it refuses every hit, so the combat code counts nothing.
 * {@code Look} (0 straw, 1 armoured, 2 painted target) picks its texture. Only {@code /kill} removes it.
 */
public class TrainingDummy extends LivingEntity {
    private static final EntityDataAccessor<Byte> LOOK = SynchedEntityData.defineId(TrainingDummy.class,
            EntityDataSerializers.BYTE);
    /** Entity event: the wobble of a hit (as an armour stand's). */
    private static final byte HIT = 32;
    private static final long BOUT_GAP_TICKS = 100;

    /** The current bout of each attacker (server side only, not saved). */
    private final Map<UUID, Bout> bouts = new HashMap<>();
    /** Client: game time of the last hit, for the wobble. */
    public long lastHit = Long.MIN_VALUE / 2;

    private static final class Bout {
        long start;
        long last;
        int hits;
        double total;
    }

    public TrainingDummy(EntityType<? extends TrainingDummy> type, Level level) {
        super(type, level);
    }

    public int look() {
        return entityData.get(LOOK);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(LOOK, (byte) 0);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        entityData.set(LOOK, (byte) Math.max(0, Math.min(2, tag.getByte("Look"))));
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putByte("Look", entityData.get(LOOK));
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (level().isClientSide || isRemoved()) {
            return false;
        }
        if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return super.hurt(source, amount); // /kill and the void
        }
        Entity attacker = source.getEntity();
        if (!(attacker instanceof ServerPlayer player) || !(amount > 0.0F)) {
            return false;
        }
        record(player, amount);
        return false;
    }

    private void record(ServerPlayer player, float amount) {
        long now = level().getGameTime();
        Bout bout = bouts.computeIfAbsent(player.getUUID(), id -> new Bout());
        if (bout.hits == 0 || now - bout.last > BOUT_GAP_TICKS) {
            bout.start = now;
            bout.hits = 0;
            bout.total = 0.0;
        }
        bout.last = now;
        bout.hits++;
        bout.total += amount;
        double seconds = Math.max(1.0, (now - bout.start) / 20.0);
        ERNetwork.sendToPlayer(player, new DamageNumberPacket(getId(), (float) getX(),
                (float) (getY() + getBbHeight() + 0.25), (float) getZ(), amount, 0));
        player.displayClientMessage(Component.translatable("message.eternalrealm.guildhouse.dummy",
                String.format(Locale.ROOT, "%.1f", amount), bout.hits, String.format(Locale.ROOT, "%.1f", bout.total),
                String.format(Locale.ROOT, "%.1f", bout.hits < 2 ? amount : bout.total / seconds))
                .withStyle(ChatFormatting.GOLD), true);
        level().broadcastEntityEvent(this, HIT);
        level().playSound(null, getX(), getY() + 1.0, getZ(), SoundEvents.ARMOR_STAND_HIT, SoundSource.NEUTRAL,
                0.7F, 0.8F + random.nextFloat() * 0.3F);
        bouts.values().removeIf(b -> now - b.last > BOUT_GAP_TICKS * 6);
    }

    @Override
    public void tick() {
        super.tick();
        float yaw = getYRot();                       // it never turns: body and head keep the placed facing
        yBodyRot = yBodyRotO = yHeadRot = yHeadRotO = yaw;
    }

    @Override
    public void handleEntityEvent(byte id) {
        if (id == HIT) {
            lastHit = level().getGameTime();
        } else {
            super.handleEntityEvent(id);
        }
    }

    // ------------------------------------------------------------------ a dummy: no pushing, no effects, no gear

    @Override
    public void knockback(double strength, double x, double z) {
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    protected void doPush(Entity entity) {
    }

    @Override
    public boolean isPushedByFluid() {
        return false;
    }

    @Override
    public boolean canBeAffected(MobEffectInstance effect) {
        return false;
    }

    @Override
    public boolean isAffectedByPotions() {
        return false;
    }

    @Override
    public boolean shouldShowName() {
        return false;
    }

    @Override
    public Iterable<ItemStack> getArmorSlots() {
        return List.of();
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
}
