package com.huntcraft.gear.weapon;

import com.huntcraft.core.DamageKind;
import com.huntcraft.core.HunterDamage;
import com.huntcraft.entity.monster.HuntMonster;
import com.huntcraft.entity.monster.MonsterPart;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

/**
 * Bow arrow. Hits go through {@link HunterDamage} with the bow stack as weapon (kind SHOT), with a critical distance
 * bonus, coatings and piercing (each monster part can be struck once by a piercing arrow).
 */
public class HunterArrow extends AbstractArrow {
    private static final EntityDataAccessor<Byte> DATA_COAT = SynchedEntityData.defineId(HunterArrow.class, EntityDataSerializers.BYTE);

    private ItemStack bow = ItemStack.EMPTY;
    private float motionValue = 0.15F;
    private float elementMult = 1.0F;
    private float critRange = 10.0F;
    private int hitsLeft = 1;
    @Nullable
    private Vec3 origin;
    private final Set<Integer> hitIds = new HashSet<>();
    private int groundTicks;

    public HunterArrow(EntityType<? extends HunterArrow> type, Level level) {
        super(type, level);
        this.pickup = Pickup.DISALLOWED;
    }

    public HunterArrow(Level level, LivingEntity shooter) {
        super(WeaponsModule.HUNTER_ARROW.get(), shooter, level);
        this.pickup = Pickup.DISALLOWED;
        this.origin = shooter.getEyePosition();
    }

    public void setup(ItemStack bow, float mv, float elementMult, int coat, float critRange, int hits) {
        this.bow = bow;
        this.motionValue = mv;
        this.elementMult = elementMult;
        this.critRange = critRange;
        this.hitsLeft = Math.max(1, hits);
        if (hits > 1) this.setPierceLevel((byte) Math.min(100, hits));
        this.entityData.set(DATA_COAT, (byte) coat);
    }

    public int getCoat() {
        return this.entityData.get(DATA_COAT);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(DATA_COAT, (byte) 0);
    }

    @Override
    protected ItemStack getPickupItem() {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    protected boolean canHitEntity(Entity e) {
        if (hitIds.contains(e.getId())) return false;
        if (e instanceof Player && e == getOwner()) return false;
        return super.canHitEntity(e);
    }

    /** Also checks monster part hitboxes near the path (parts can stick out far from the main body box). */
    @Nullable
    @Override
    protected EntityHitResult findHitEntity(Vec3 start, Vec3 end) {
        EntityHitResult best = super.findHitEntity(start, end);
        double bestDist = best != null ? start.distanceToSqr(best.getLocation()) : Double.MAX_VALUE;
        AABB sweep = new AABB(start, end).inflate(8.0);
        for (HuntMonster m : level().getEntitiesOfClass(HuntMonster.class, sweep, m -> m.isAlive() && !m.isCarcass())) {
            for (MonsterPart part : m.getMonsterParts()) {
                if (!canHitEntity(part)) continue;
                Optional<Vec3> hit = part.getBoundingBox().inflate(0.3).clip(start, end);
                if (hit.isPresent()) {
                    double d = start.distanceToSqr(hit.get());
                    if (d < bestDist) {
                        bestDist = d;
                        best = new EntityHitResult(part, hit.get());
                    }
                }
            }
        }
        return best;
    }

    private float distanceMultiplier() {
        if (origin == null) return 1.0F;
        float d = (float) position().distanceTo(origin);
        if (d < 2.0F) return 0.8F;
        if (d <= critRange) return 1.15F;
        return Math.max(0.6F, 1.15F - (d - critRange) * 0.04F);
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        Entity target = result.getEntity();
        hitIds.add(target.getId());
        if (target instanceof MonsterPart mp) hitIds.add(mp.getParent().getId());
        if (!level().isClientSide && getOwner() instanceof Player player && !bow.isEmpty()) {
            int coat = getCoat();
            float mv = motionValue * distanceMultiplier() * (coat == HunterBowItem.COAT_POWER ? 1.35F : 1.0F);
            boolean hit = HunterDamage.hit(player, target, mv, ctx -> {
                ctx.weapon = bow;
                ctx.kind = DamageKind.SHOT;
                ctx.useSharpness = false;
                ctx.elementMult = elementMult;
                ctx.statusMult = elementMult;
            });
            if (hit) applyCoating(player, target, coat);
            this.playSound(SoundEvents.ARROW_HIT, 0.8F, 1.2F / (this.random.nextFloat() * 0.2F + 0.9F));
        }
        if (--hitsLeft <= 0) {
            this.discard();
        }
    }

    private void applyCoating(Player player, Entity target, int coat) {
        if (coat != HunterBowItem.COAT_POISON && coat != HunterBowItem.COAT_PARA) return;
        HuntMonster m = target instanceof HuntMonster hm ? hm : target instanceof MonsterPart mp ? mp.getParent() : null;
        if (m != null) {
            m.addStatus(coat == HunterBowItem.COAT_POISON ? 0 : 1, (coat == HunterBowItem.COAT_POISON ? 9.0F : 7.0F) * Math.max(0.5F, elementMult), player);
        } else if (target instanceof LivingEntity le) {
            if (coat == HunterBowItem.COAT_POISON) le.addEffect(new MobEffectInstance(MobEffects.POISON, 80, 0));
            else le.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 3));
        }
    }

    @Override
    protected void tickDespawn() {
        if (++groundTicks > 40) this.discard();
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide && !this.inGround && this.tickCount > 1) {
            int coat = getCoat();
            if (coat != HunterBowItem.COAT_NONE) {
                int rgb = coat == HunterBowItem.COAT_POWER ? 0xFF4A3A : coat == HunterBowItem.COAT_POISON ? 0xA14BD8 : 0xF2D21B;
                level().addParticle(WeaponFx.dust(rgb, 0.8F), getX(), getY(), getZ(), 0, 0, 0);
            } else if (this.tickCount % 2 == 0) {
                level().addParticle(ParticleTypes.CRIT, getX(), getY(), getZ(), 0, 0, 0);
            }
        }
        if (!level().isClientSide && this.tickCount > 100) this.discard();
    }
}
