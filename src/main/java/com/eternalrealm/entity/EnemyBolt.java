package com.eternalrealm.entity;

import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Spell bolt of ranged enemies ({@code eternalrealm:enemy_bolt}). Flies straight, deals the shooter's
 * ATTACK_DAMAGE to the first non-enemy it hits plus its element's effect ({@link BoltKind}). Never breaks or ignites
 * blocks and is never saved. Rendered as the element's item sprite with a particle trail.
 */
public class EnemyBolt extends ThrowableItemProjectile {
    private static final int MAX_AGE_TICKS = 100;

    private BoltKind kind = BoltKind.FIRE;
    private float damage = 4.0F;

    public EnemyBolt(EntityType<? extends EnemyBolt> type, Level level) {
        super(type, level);
    }

    public EnemyBolt(Level level, LivingEntity shooter, BoltKind kind, float damage) {
        super(EREntityTypes.ENEMY_BOLT.get(), shooter, level);
        this.kind = kind;
        this.damage = damage;
        setItem(new ItemStack(kind.item()));
    }

    public BoltKind kind() {
        return this.kind;
    }

    public float damage() {
        return this.damage;
    }

    @Override
    protected Item getDefaultItem() {
        return BoltKind.FIRE.item();
    }

    @Override
    protected float getGravity() {
        return 0.0F;
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            BoltKind visual = BoltKind.byItem(getItem().getItem());
            if (visual != null) {
                ParticleOptions particle = visual.particle();
                Vec3 motion = getDeltaMovement();
                for (int i = 0; i < 2; i++) {
                    level().addParticle(particle, getX() - motion.x * 0.5 * i, getY() + 0.1 - motion.y * 0.5 * i,
                            getZ() - motion.z * 0.5 * i, 0.0, 0.0, 0.0);
                }
            }
        } else if (this.tickCount > MAX_AGE_TICKS) {
            discard();
        }
    }

    @Override
    protected boolean canHitEntity(Entity entity) {
        return super.canHitEntity(entity) && !(entity instanceof ERMonster) && !(entity instanceof ERBoss)
                && !(entity instanceof EnemyBolt);
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        super.onHitEntity(result);
        if (level().isClientSide) {
            return;
        }
        Entity hit = result.getEntity();
        Entity owner = getOwner();
        DamageSource source = damageSources().mobProjectile(this, owner instanceof LivingEntity living ? living : null);
        if (hit.hurt(source, this.damage) && hit instanceof LivingEntity living) {
            this.kind.onHit(living, owner);
        }
    }

    @Override
    protected void onHit(HitResult result) {
        super.onHit(result);
        if (!level().isClientSide && !isRemoved()) {
            if (level() instanceof ServerLevel server) {
                Vec3 at = result.getLocation();
                server.sendParticles(this.kind.particle(), at.x, at.y, at.z, 10, 0.2, 0.2, 0.2, 0.03);
            }
            discard();
        }
    }
}
