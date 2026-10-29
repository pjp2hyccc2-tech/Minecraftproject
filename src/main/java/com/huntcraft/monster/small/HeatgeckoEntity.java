package com.huntcraft.monster.small;

import com.huntcraft.core.Element;
import com.huntcraft.entity.misc.MonsterProjectile;
import com.huntcraft.entity.monster.HuntMonster;
import com.huntcraft.entity.monster.Hitzone;
import com.huntcraft.entity.monster.MonsterProfile;
import com.huntcraft.entity.monster.PartDef;
import com.huntcraft.entity.monster.RewardTable;
import com.huntcraft.entity.monster.attack.MonsterAttack;
import com.huntcraft.entity.monster.attack.ProjectileSpec;
import com.huntcraft.entity.monster.attack.TimelineAttack;
import com.huntcraft.registry.HCItems;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * ヒートゲッコー - lava lizard whose scales are dotted with glowing magma spots. Spits embers and arcing lava
 * blobs that leave burning puddles, and flicks its hot tail at anyone sneaking up behind it.
 */
public class HeatgeckoEntity extends SmallHuntMonster {
    static final Hitzone HZ_HEAD = Hitzone.of(80, 80, 75, 0, 35, 15, 25, 10);
    static final Hitzone HZ_BODY = Hitzone.of(60, 60, 55, 0, 30, 10, 20, 5);
    static final Hitzone HZ_TAIL = Hitzone.of(70, 55, 60, 0, 30, 10, 20, 5);

    public static final MonsterProfile PROFILE = MonsterProfile.builder("heatgecko", false)
            .health(18, 30).highRank(1.35F, 0.85F)
            .scale(0.9F, 1.1F)
            .element(Element.FIRE)
            .weakness(Element.WATER, 3).weakness(Element.ICE, 2)
            .part(new PartDef("head", 0, 0.15F, 0.7F, 0.45F, 0.35F, HZ_HEAD).ko())
            .part(PartDef.body(HZ_BODY))
            .part(new PartDef("tail", 0, 0.05F, -0.95F, 0.4F, 0.3F, HZ_TAIL))
            .rageThreshold(99F).flinchThreshold(0.15F)
            .carves(new RewardTable()
                    .low(HCItems.HEATGECKO_SCALE, 65).low(HCItems.FLAME_SAC, 35)
                    .high(HCItems.HEATGECKO_SCALE, 62, 1, 2).high(HCItems.FLAME_SAC, 38))
            .carveCount(1)
            .questReward(350, 8)
            .build();

    public static final String[] ANIMS = {"flinch", "fire_spit", "lava_spit", "tail_flick", "lick", "bask"};

    private int backoffTimer;

    public HeatgeckoEntity(EntityType<? extends HeatgeckoEntity> type, Level level) {
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
    protected List<MonsterAttack> createAttacks() {
        ProjectileSpec ember = ProjectileSpec.of(MonsterProjectile.Kind.FIREBALL, 3, Element.FIRE).speed(0.95F).size(0.5F).lifetime(40);
        ProjectileSpec lava = ProjectileSpec.of(MonsterProjectile.Kind.LAVA_BLOB, 4, Element.FIRE).speed(0.75F).gravity(0.035F)
                .size(0.6F).explode(1.2F).linger(50).lifetime(70);
        MonsterAttack spit = TimelineAttack.builder("fire_spit", 24).range(2.5F, 12).angle(35).weight(12).cooldown(40)
                .track(0, 9, 12)
                .particlesSpan(3, 9, ParticleTypes.SMALL_FLAME, 0, 0.3, 0.75, 1, 0.08)
                .projectile(10, 0, 0.3, 0.8, ember)
                .sound(10, () -> SoundEvents.BLAZE_SHOOT, 0.9F, 1.6F)
                .build();
        MonsterAttack blob = TimelineAttack.builder("lava_spit", 32).range(3.5F, 12).angle(30).weight(7).cooldown(140)
                .track(0, 13, 10)
                .particlesSpan(4, 13, ParticleTypes.LAVA, 0, 0.35, 0.7, 1, 0.1)
                .at(14, (m, target, t) -> lob(m, target, lava))
                .sound(14, () -> SoundEvents.FIRECHARGE_USE, 1.0F, 1.3F)
                .build();
        MonsterAttack flick = TimelineAttack.builder("tail_flick", 22).range(0, 2.2F).angle(180).weight(10).cooldown(30)
                .track(0, 4, 6)
                .sweep(8, 13, 0.3F, 2.3F, 110, 250, 1.0F, 3, Element.FIRE, 0.9F)
                .particlesSpan(8, 13, ParticleTypes.SMALL_FLAME, 0, 0.15, -1.0, 2, 0.2)
                .sound(8, () -> SoundEvents.PLAYER_ATTACK_SWEEP, 0.8F, 1.5F)
                .build();
        return List.of(spit, blob, flick);
    }

    /** Lobbed lava blob aimed above the target so the arc lands on it. */
    private static void lob(HuntMonster m, @Nullable LivingEntity target, ProjectileSpec spec) {
        Vec3 from = m.localPos(0, 0.35, 0.8);
        Vec3 dir;
        if (target != null) {
            Vec3 to = target.position().subtract(from);
            double flat = Math.sqrt(to.x * to.x + to.z * to.z);
            double time = flat / Math.max(0.1, spec.speed);
            dir = new Vec3(to.x, to.y + 0.5 * spec.gravity * time * time, to.z).normalize();
        } else {
            float yaw = m.getYRot() * ((float) Math.PI / 180F);
            dir = new Vec3(-Math.sin(yaw), 0.35, Math.cos(yaw)).normalize();
        }
        MonsterProjectile p = new MonsterProjectile(m.level(), m, spec, dir, target);
        p.setPos(from.x, from.y, from.z);
        m.level().addFreshEntity(p);
    }

    @Override
    public void combatMove(LivingEntity target, double dist, float angle, boolean waiting) {
        float s = getMonsterScale();
        if (waiting && dist < 3.5 * s && --backoffTimer <= 0) {
            // scuttle back to spitting range
            backoffTimer = 15;
            Vec3 away = position().subtract(target.position()).normalize().scale(4.5).add(position());
            this.getNavigation().moveTo(away.x, away.y, away.z, getRunSpeedModifier());
            return;
        }
        if (dist < 4.5 * s && !waiting) {
            this.getNavigation().stop();
            if (Math.abs(angle) > 10) turnTowards(target.position(), getTurnSpeed());
            return;
        }
        super.combatMove(target, dist, angle, waiting);
    }

    @Override
    protected void smallTick() {
        if (this.tickCount % 12 == 0 && getRandom().nextInt(3) == 0) {
            burstParticles(ParticleTypes.SMALL_FLAME, position().add(0, 0.45, 0), 1, 0.25);
        }
    }

    @Override
    public int getAttackGap() {
        return isTired() ? 36 : 22;
    }

    @Override
    protected IdleAction[] idleActions() {
        return new IdleAction[]{new IdleAction("lick", 26, 3), new IdleAction("bask", 60, 2)};
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.STRIDER_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.STRIDER_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.STRIDER_DEATH;
    }

    @Override
    public float getVoicePitch() {
        return 1.4F + (this.getRandom().nextFloat() - 0.5F) * 0.15F;
    }
}
