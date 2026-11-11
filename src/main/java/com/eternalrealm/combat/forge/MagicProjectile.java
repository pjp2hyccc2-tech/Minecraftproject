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
    /** Enemy a locked skill's projectile curves into (D-LOCKON), or -1. */
    private int homingTarget = -1;

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

    /**
     * The glowing sprite of a spell kind (vanilla item textures; the trail is derived from the item on the client, so
     * every kind maps to an item that tells its element apart).
     */
    static Item visualFor(String kind) {
        return switch (kind) {
            case "fireball", "phoenix" -> Items.FIRE_CHARGE;
            case "ice_lance", "frost_orb", "hail" -> Items.PRISMARINE_SHARD;
            case "holy_light", "smite", "holy_lance", "light_wave", "star_arrow" -> Items.NETHER_STAR;
            case "mana_wave", "shadow_bolt", "rift" -> Items.ECHO_SHARD;
            case "meteor", "magma" -> Items.MAGMA_BLOCK;
            case "magic_orb", "spark", "thunder", "thunder_orb" -> Items.HEART_OF_THE_SEA;
            case "poison_dart", "poison_flask" -> Items.SLIME_BALL;
            case "knife" -> Items.IRON_NUGGET;
            case "crescent" -> Items.AMETHYST_CLUSTER;
            case com.eternalrealm.combat.WeaponProfiles.STAR_SIGIL -> Items.END_CRYSTAL;
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
        if (item == Items.SLIME_BALL) {
            return net.minecraft.core.particles.ParticleTypes.ITEM_SLIME;
        }
        if (item == Items.IRON_NUGGET) {
            return net.minecraft.core.particles.ParticleTypes.CRIT;
        }
        if (item == Items.END_CRYSTAL) {
            return net.minecraft.core.particles.ParticleTypes.END_ROD;
        }
        return ERParticles.RUNE.get();
    }

    @Override
    protected Item getDefaultItem() {
        return Items.AMETHYST_SHARD;
    }

    /** Makes the projectile curve into {@code target} while it flies (a skill locked on to an enemy). */
    void homeOn(net.minecraft.world.entity.LivingEntity target) {
        homingTarget = target.getId();
    }

    @Override
    public void tick() {
        if (!level().isClientSide) {
            homingTarget = Homing.steer(this, homingTarget);
        }
        super.tick();
        if (level().isClientSide) {
            ParticleOptions particle = trailFor(getItem().getItem());
            Vec3 motion = getDeltaMovement();
            for (int i = 0; i < 2; i++) {
                level().addParticle(particle, getX() - motion.x * 0.5 * i, getY() + 0.15 - motion.y * 0.5 * i,
                        getZ() - motion.z * 0.5 * i, 0.0, 0.0, 0.0);
            }
            if (getItem().getItem() == Items.END_CRYSTAL) {
                // the star sigil bolt: a thick trail of runes and sparks
                for (int i = 0; i < 3; i++) {
                    level().addParticle(ERParticles.RUNE.get(), getX() - motion.x * 0.33 * i + (random.nextDouble() - 0.5) * 0.3,
                            getY() + 0.15 - motion.y * 0.33 * i + (random.nextDouble() - 0.5) * 0.3,
                            getZ() - motion.z * 0.33 * i + (random.nextDouble() - 0.5) * 0.3, 0.0, 0.0, 0.0);
                }
                level().addParticle(net.minecraft.core.particles.ParticleTypes.WITCH, getX(), getY() + 0.15, getZ(),
                        0.0, 0.0, 0.0);
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
            return !(entity instanceof net.minecraft.world.entity.player.Player); // the server decides; never stop on a player
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
        if (target.getId() == homingTarget) {
            homingTarget = -1; // hit: a piercing shot flies on straight
        }
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
            if (com.eternalrealm.combat.WeaponProfiles.STAR_SIGIL.equals(payload.spec().kind())) {
                WeaponSkillFx.sigilBurst(owner, level, at, payload.spec().explosionRadius());
            }
        }
    }
}
