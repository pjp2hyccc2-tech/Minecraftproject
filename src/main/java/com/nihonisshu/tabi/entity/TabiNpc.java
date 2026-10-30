package com.nihonisshu.tabi.entity;

import com.google.gson.JsonObject;
import com.nihonisshu.tabi.data.TabiData;
import com.nihonisshu.tabi.dialogue.DialogueManager;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/** Villagers, family, shop staff and tourists. Talk with right click; ambient lines appear above their heads. */
public class TabiNpc extends PathfinderMob {
    private static final EntityDataAccessor<String> PROFILE = SynchedEntityData.defineId(TabiNpc.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<String> SKIN = SynchedEntityData.defineId(TabiNpc.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<String> DISPLAY = SynchedEntityData.defineId(TabiNpc.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<String> BUBBLES = SynchedEntityData.defineId(TabiNpc.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Integer> MODE = SynchedEntityData.defineId(TabiNpc.class, EntityDataSerializers.INT);

    public static final int STAND = 0, WANDER = 1, SIT = 2;

    private BlockPos home = BlockPos.ZERO;
    private float homeYaw;

    public TabiNpc(EntityType<? extends TabiNpc> type, Level level) {
        super(type, level);
        setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0)
                .add(Attributes.MOVEMENT_SPEED, 0.22)
                .add(Attributes.FOLLOW_RANGE, 16.0);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(PROFILE, "");
        entityData.define(SKIN, "villager_man");
        entityData.define(DISPLAY, "");
        entityData.define(BUBBLES, "");
        entityData.define(MODE, STAND);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.55) {
            @Override
            public boolean canUse() {
                return getMode() == WANDER && super.canUse();
            }
        });
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 6.0f));
        goalSelector.addGoal(7, new RandomLookAroundGoal(this) {
            @Override
            public boolean canUse() {
                return getMode() != SIT && super.canUse();
            }
        });
    }

    public void setupFromJson(JsonObject o) {
        entityData.set(PROFILE, TabiData.str(o, "profile", ""));
        entityData.set(SKIN, TabiData.str(o, "skin", "villager_man"));
        entityData.set(DISPLAY, TabiData.str(o, "name", ""));
        entityData.set(BUBBLES, TabiData.str(o, "bubbles", ""));
        String mode = TabiData.str(o, "mode", "stand");
        entityData.set(MODE, mode.equals("wander") ? WANDER : mode.equals("sit") ? SIT : STAND);
        String hold = TabiData.str(o, "hold", "");
        if (!hold.isEmpty()) {
            net.minecraft.world.item.Item it = net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(new net.minecraft.resources.ResourceLocation(hold));
            if (it != null) setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, new net.minecraft.world.item.ItemStack(it));
        }
        double x = o.get("x").getAsDouble(), y = o.get("y").getAsDouble(), z = o.get("z").getAsDouble();
        float yaw = (float) TabiData.dbl(o, "yaw", 0);
        moveTo(x, y, z, yaw, 0);
        setYHeadRot(yaw);
        setYBodyRot(yaw);
        home = BlockPos.containing(x, y, z);
        homeYaw = yaw;
        if (getMode() == WANDER) restrictTo(home, TabiData.integer(o, "radius", 6));
    }

    public String getProfile() { return entityData.get(PROFILE); }
    public String getSkin() { return entityData.get(SKIN); }
    public String getDisplay() { return entityData.get(DISPLAY); }
    public String getBubblePool() { return entityData.get(BUBBLES); }
    public int getMode() { return entityData.get(MODE); }

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide && getMode() != WANDER && tickCount % 40 == 0) {
            // stay at the post (shop counter, station gate, bench)
            if (home != BlockPos.ZERO && distanceToSqr(home.getX() + 0.5, home.getY(), home.getZ() + 0.5) > 2.0) {
                moveTo(home.getX() + 0.5, home.getY(), home.getZ() + 0.5, homeYaw, 0);
            }
        }
    }

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
        if (!level().isClientSide && player instanceof ServerPlayer sp) {
            getLookControl().setLookAt(player, 30f, 30f);
            DialogueManager.talk(sp, this);
        }
        return InteractionResult.sidedSuccess(level().isClientSide);
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (source.getEntity() instanceof Player p && p.isCreative() && p.isShiftKeyDown()) {
            discard();
            return true;
        }
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    protected void doPush(Entity entity) {
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    @Override
    public boolean canBeLeashed(Player player) {
        return false;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putString("profile", getProfile());
        tag.putString("skin", getSkin());
        tag.putString("display", getDisplay());
        tag.putString("bubbles", getBubblePool());
        tag.putInt("mode", getMode());
        tag.putLong("home", home.asLong());
        tag.putFloat("homeYaw", homeYaw);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        entityData.set(PROFILE, tag.getString("profile"));
        entityData.set(SKIN, tag.getString("skin"));
        entityData.set(DISPLAY, tag.getString("display"));
        entityData.set(BUBBLES, tag.getString("bubbles"));
        entityData.set(MODE, tag.getInt("mode"));
        home = BlockPos.of(tag.getLong("home"));
        homeYaw = tag.getFloat("homeYaw");
        if (getMode() == WANDER) restrictTo(home, 6);
    }
}
