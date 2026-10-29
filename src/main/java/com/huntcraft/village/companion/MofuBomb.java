package com.huntcraft.village.companion;

import com.huntcraft.core.DamageKind;
import com.huntcraft.core.Element;
import com.huntcraft.entity.monster.HuntMonster;
import com.huntcraft.entity.monster.MonsterPart;
import com.huntcraft.registry.HCItems;
import com.huntcraft.registry.HCSounds;
import com.huntcraft.village.VillageRegistry;
import com.huntcraft.village.npc.TrainingDummy;
import com.huntcraft.village.npc.VillageNpc;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Small barrel bomb thrown by bomber mofus. Explodes on impact; only hurts monsters (no terrain damage). The FLASH_BARREL
 * skill turns one bomb into a flash barrel that blinds monsters.
 */
public class MofuBomb extends ThrowableItemProjectile {
    private float damage = 8.0F;
    private double radius = 2.6D;
    private Element element = Element.NONE;
    private boolean flash;

    public MofuBomb(EntityType<? extends MofuBomb> type, Level level) {
        super(type, level);
    }

    public MofuBomb(Level level, LivingEntity thrower) {
        super(VillageRegistry.MOFU_BOMB.get(), thrower, level);
    }

    public void setDamage(float damage) {
        this.damage = damage;
    }

    public void setRadius(double radius) {
        this.radius = radius;
    }

    public void setElement(Element element) {
        this.element = element;
    }

    public void setFlash(boolean flash) {
        this.flash = flash;
    }

    @Override
    protected Item getDefaultItem() {
        return HCItems.BARREL_BOMB.get();
    }

    @Override
    protected float getGravity() {
        return 0.05F;
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide && this.tickCount % 2 == 0) {
            this.level().addParticle(ParticleTypes.SMOKE, getX(), getY() + 0.25, getZ(), 0, 0.02, 0);
        }
        if (!this.level().isClientSide && this.tickCount > 100) explode();
    }

    @Override
    protected void onHit(HitResult result) {
        super.onHit(result);
        if (!this.level().isClientSide) explode();
    }

    private void explode() {
        if (this.isRemoved()) return;
        Vec3 c = this.position();
        if (this.level() instanceof ServerLevel sl) {
            if (flash) {
                sl.sendParticles(ParticleTypes.FLASH, c.x, c.y + 0.5, c.z, 1, 0, 0, 0, 0);
                sl.sendParticles(ParticleTypes.END_ROD, c.x, c.y + 0.5, c.z, 30, 0.6, 0.6, 0.6, 0.15);
            } else {
                sl.sendParticles(ParticleTypes.EXPLOSION, c.x, c.y + 0.3, c.z, 2, 0.3, 0.3, 0.3, 0);
                sl.sendParticles(ParticleTypes.FLAME, c.x, c.y + 0.3, c.z, 14, 0.5, 0.4, 0.5, 0.06);
                sl.sendParticles(ParticleTypes.SMOKE, c.x, c.y + 0.5, c.z, 10, 0.4, 0.4, 0.4, 0.03);
            }
        }
        if (flash) {
            this.level().playSound(null, c.x, c.y, c.z, HCSounds.FLASH.get(), SoundSource.NEUTRAL, 1.0F, 1.2F);
        } else {
            this.level().playSound(null, c.x, c.y, c.z, VillageRegistry.MOFU_BOMB_SOUND.get(), SoundSource.NEUTRAL, 1.0F, 1.1F + this.random.nextFloat() * 0.2F);
        }
        Entity thrower = this.getOwner();
        MofuEntity mofu = thrower instanceof MofuEntity m ? m : null;
        double r = flash ? Math.max(radius, 6.0) : radius;
        AABB box = new AABB(c, c).inflate(r + 2.0);
        for (LivingEntity e : this.level().getEntitiesOfClass(LivingEntity.class, box)) {
            if (!validVictim(e)) continue;
            double d = HuntMonster.boxDistSqr(e.getBoundingBox(), c);
            if (e instanceof HuntMonster m) {
                for (MonsterPart part : m.getMonsterParts()) d = Math.min(d, HuntMonster.boxDistSqr(part.getBoundingBox(), c));
            }
            if (d > r * r) continue;
            if (flash) {
                if (e instanceof HuntMonster m) m.blind(m.profile().large ? 160 : 100);
                continue;
            }
            DamageSource src = mofu != null ? mofu.blastSource(this) : this.damageSources().explosion(this, thrower);
            e.invulnerableTime = 0;
            // explosion damage is not reduced by the hunt formula (HunterDamage), so pre-scale it like other mofu damage
            float dealt = e instanceof HuntMonster ? damage * 0.6F : damage;
            if (e.hurt(src, dealt) && mofu != null) mofu.onDealtDamage(e, damage, DamageKind.BLUNT, element == Element.NONE ? Element.FIRE : element);
        }
        this.discard();
    }

    private boolean validVictim(LivingEntity e) {
        if (!e.isAlive() || e instanceof Player || e instanceof MofuEntity || e instanceof VillageNpc) return false;
        if (e instanceof HuntMonster m) return !m.isCarcass();
        return e instanceof Enemy || e instanceof TrainingDummy;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putFloat("BombDamage", damage);
        tag.putDouble("BombRadius", radius);
        tag.putString("BombElement", element.name());
        tag.putBoolean("BombFlash", flash);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        damage = tag.getFloat("BombDamage");
        radius = tag.contains("BombRadius") ? tag.getDouble("BombRadius") : 2.6D;
        element = Element.byName(tag.getString("BombElement"));
        flash = tag.getBoolean("BombFlash");
    }
}
