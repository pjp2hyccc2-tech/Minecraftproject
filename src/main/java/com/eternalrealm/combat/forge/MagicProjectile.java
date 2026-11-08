package com.eternalrealm.combat.forge;

import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.HashSet;
import java.util.Set;

/**
 * Spell projectile (magic bolts, fireballs, ice lances, holy light...). Rendered as a glowing item sprite chosen by the
 * spell kind, with a particle trail derived from that item on the client. The damage was rolled at launch; the server
 * applies it on impact. Never saved.
 */
public final class MagicProjectile extends ThrowableItemProjectile {
    private ProjectilePayload payload;
    private int pierceLeft;
    private int age;
    private final Set<Integer> alreadyHit = new HashSet<>();

    public MagicProjectile(EntityType<? extends MagicProjectile> type, Level level) {
        super(type, level);
    }

    static MagicProjectile create(ServerLevel level, ServerPlayer owner, ProjectilePayload payload) {
        MagicProjectile projectile = new MagicProjectile(EREntities.MAGIC_PROJECTILE.get(), level);
        projectile.setOwner(owner);
        projectile.payload = payload;
        projectile.pierceLeft = payload.spec().pierce();
        projectile.setNoGravity(!payload.spec().gravity());
        projectile.setItem(new ItemStack(visualFor(payload.spec().kind())));
        return projectile;
    }

    static Item visualFor(String kind) {
        return switch (kind) {
            case "fireball" -> Items.FIRE_CHARGE;
            case "ice_lance" -> Items.PRISMARINE_SHARD;
            case "holy_light" -> Items.NETHER_STAR;
            case "mana_wave" -> Items.ECHO_SHARD;
            case "meteor" -> Items.MAGMA_BLOCK;
            case "magic_orb" -> Items.HEART_OF_THE_SEA;
            default -> Items.AMETHYST_SHARD;
        };
    }

    private static ParticleOptions trailFor(Item item) {
        if (item == Items.FIRE_CHARGE || item == Items.MAGMA_BLOCK) {
            return ERParticles.EMBER.get();
        }
        if (item == Items.PRISMARINE_SHARD) {
            return ERParticles.FROST.get();
        }
        if (item == Items.NETHER_STAR) {
            return ERParticles.HOLY.get();
        }
        if (item == Items.ECHO_SHARD) {
            return ERParticles.SHADOW.get();
        }
        if (item == Items.HEART_OF_THE_SEA) {
            return ERParticles.SPARK.get();
        }
        return ERParticles.RUNE.get();
    }

    @Override
    protected Item getDefaultItem() {
        return Items.AMETHYST_SHARD;
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            ParticleOptions particle = trailFor(getItem().getItem());
            Vec3 motion = getDeltaMovement();
            for (int i = 0; i < 2; i++) {
                level().addParticle(particle, getX() - motion.x * 0.5 * i, getY() + 0.15 - motion.y * 0.5 * i,
                        getZ() - motion.z * 0.5 * i, 0.0, 0.0, 0.0);
            }
            return;
        }
        if (isRemoved()) {
            return;
        }
        if (payload == null) {
            discard();
            return;
        }
        if (++age > payload.spec().lifetimeTicks()) {
            if (payload.spec().explosionRadius() > 0.0) {
                explode(position());
            }
            discard();
        }
    }

    @Override
    protected boolean canHitEntity(Entity entity) {
        if (!super.canHitEntity(entity) || alreadyHit.contains(entity.getId())) {
            return false;
        }
        if (level().isClientSide) {
            return true;
        }
        return entity instanceof LivingEntity living && getOwner() instanceof ServerPlayer owner
                && CombatHits.isValidTarget(owner, living);
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        if (level().isClientSide || isRemoved()) {
            return;
        }
        if (payload == null || !(getOwner() instanceof ServerPlayer owner)
                || !(result.getEntity() instanceof LivingEntity target)) {
            discard();
            return;
        }
        alreadyHit.add(target.getId());
        if (payload.spec().explosionRadius() > 0.0) {
            explode(result.getLocation());
            discard();
            return;
        }
        CombatHits.projectileHit(owner, target, payload);
        if (pierceLeft-- <= 0) {
            discard();
        }
    }

    @Override
    protected void onHitBlock(BlockHitResult result) {
        super.onHitBlock(result);
        if (level().isClientSide || isRemoved()) {
            return;
        }
        if (payload != null && payload.spec().explosionRadius() > 0.0) {
            explode(result.getLocation());
        } else if (level() instanceof ServerLevel level) {
            Vec3 at = result.getLocation();
            level.sendParticles(trailFor(getItem().getItem()), at.x, at.y, at.z, 8, 0.15, 0.15, 0.15, 0.02);
        }
        discard();
    }

    private void explode(Vec3 at) {
        if (getOwner() instanceof ServerPlayer owner && level() instanceof ServerLevel level && payload != null) {
            CombatHits.explosion(owner, level, at, payload);
        }
    }
}
