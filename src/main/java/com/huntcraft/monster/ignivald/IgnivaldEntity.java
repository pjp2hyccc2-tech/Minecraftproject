package com.huntcraft.monster.ignivald;

import com.huntcraft.core.Element;
import com.huntcraft.entity.misc.MonsterProjectile;
import com.huntcraft.entity.monster.Hitzone;
import com.huntcraft.entity.monster.LargeMonster;
import com.huntcraft.entity.monster.MonsterDamage;
import com.huntcraft.entity.monster.MonsterProfile;
import com.huntcraft.entity.monster.PartDef;
import com.huntcraft.entity.monster.RewardTable;
import com.huntcraft.entity.monster.attack.MonsterAttack;
import com.huntcraft.entity.monster.attack.ProjectileSpec;
import com.huntcraft.entity.monster.attack.TimelineAttack;
import com.huntcraft.registry.HCItems;
import com.huntcraft.registry.HCSounds;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 炎翼竜イグニヴァルド - crimson flying wyvern (fire). Reference implementation of a large monster.
 * Ground: bite, tail spin, charge, fireball(s), sweeping flame breath. Air: fireball, talon dive, wing gust.
 */
public class IgnivaldEntity extends LargeMonster {
    private static final Hitzone HZ_HEAD = Hitzone.of(70, 75, 55, 0, 10, 25, 15, 30);
    private static final Hitzone HZ_NECK = Hitzone.of(40, 40, 40, 0, 5, 15, 10, 20);
    private static final Hitzone HZ_BODY = Hitzone.of(35, 35, 30, 0, 5, 15, 10, 20);
    private static final Hitzone HZ_WING = Hitzone.of(45, 40, 50, 0, 10, 20, 10, 25);
    private static final Hitzone HZ_TAIL = Hitzone.of(50, 35, 40, 0, 10, 20, 15, 25);
    private static final Hitzone HZ_LEG = Hitzone.of(30, 35, 25, 0, 5, 10, 10, 15);

    public static final MonsterProfile PROFILE = MonsterProfile.builder("ignivald", true)
            .category("flying_wyvern")
            .health(700, 1024).highRank(1.6F, 0.7F)
            .scale(0.9F, 1.25F)
            .element(Element.FIRE)
            .weakness(Element.DRAGON, 3).weakness(Element.THUNDER, 2).weakness(Element.ICE, 1).weakness(Element.WATER, 1)
            .part(new PartDef("head", 0, 4.05F, 4.1F, 1.7F, 1.5F, HZ_HEAD).breakable(160, "horn_tip_l", "horn_tip_r", "cheek_l", "cheek_r").ko())
            .part(new PartDef("neck", 0, 2.9F, 2.3F, 1.5F, 1.4F, HZ_NECK))
            .part(PartDef.body(HZ_BODY))
            .part(new PartDef("wing_l", -1.5F, 2.8F, 0.2F, 1.7F, 1.1F, HZ_WING).breakable(130, "mem_f3_l"))
            .part(new PartDef("wing_r", 1.5F, 2.8F, 0.2F, 1.7F, 1.1F, HZ_WING).breakable(130, "mem_f3_r"))
            .part(new PartDef("tail", 0, 2.2F, -4.0F, 1.9F, 1.3F, HZ_TAIL).severable(200, "tail4", "tail5", "tail_club"))
            .part(new PartDef("leg_l", -0.9F, 0, -0.3F, 1.2F, 1.9F, HZ_LEG))
            .part(new PartDef("leg_r", 0.9F, 0, -0.3F, 1.2F, 1.9F, HZ_LEG))
            .carves(new RewardTable()
                    .low(HCItems.IGNIVALD.scale, 30, 1, 2).low(HCItems.IGNIVALD.shell, 25).low(HCItems.IGNIVALD.webbing, 15)
                    .low(HCItems.IGNIVALD.claw, 15).low(HCItems.MONSTER_BONE_M, 8).low(HCItems.FLAME_SAC, 5).low(HCItems.IGNIVALD.gem, 2)
                    .high(HCItems.IGNIVALD.scalePlus, 28, 1, 2).high(HCItems.IGNIVALD.shellPlus, 22).high(HCItems.IGNIVALD.specialPlus, 14)
                    .high(HCItems.IGNIVALD.clawPlus, 14).high(HCItems.MONSTER_BONE_L, 6).high(HCItems.FLAME_SAC, 8).high(HCItems.IGNIVALD.gemPlus, 2))
            .tailCarves(new RewardTable()
                    .low(HCItems.IGNIVALD.tail, 60).low(HCItems.IGNIVALD.scale, 25).low(HCItems.IGNIVALD.gem, 3)
                    .high(HCItems.IGNIVALD.tail, 55).high(HCItems.IGNIVALD.scalePlus, 30).high(HCItems.IGNIVALD.gemPlus, 3))
            .captureRewards(new RewardTable()
                    .low(HCItems.IGNIVALD.scale, 30).low(HCItems.IGNIVALD.shell, 25).low(HCItems.IGNIVALD.webbing, 20).low(HCItems.IGNIVALD.gem, 4)
                    .high(HCItems.IGNIVALD.scalePlus, 30).high(HCItems.IGNIVALD.shellPlus, 25).high(HCItems.IGNIVALD.specialPlus, 20).high(HCItems.IGNIVALD.gemPlus, 4))
            .breakReward("head", new RewardTable().low(HCItems.IGNIVALD.shell, 60).low(HCItems.IGNIVALD.gem, 5).high(HCItems.IGNIVALD.shellPlus, 60).high(HCItems.IGNIVALD.gemPlus, 5))
            .breakReward("wing_l", new RewardTable().low(HCItems.IGNIVALD.webbing, 80).high(HCItems.IGNIVALD.specialPlus, 80))
            .breakReward("wing_r", new RewardTable().low(HCItems.IGNIVALD.webbing, 80).high(HCItems.IGNIVALD.specialPlus, 80))
            .questReward(2400, 90)
            .build();

    public static final String[] ANIMS = {
            "roar", "flinch", "bite", "tail_spin", "charge", "fireball", "fireball_triple", "breath_sweep",
            "takeoff", "land", "fly_fireball", "dive", "wing_gust"
    };

    private MonsterAttack takeoff;
    private MonsterAttack land;
    private int airTime;
    private int airAttacks;
    private int flightCooldown = 300;

    public IgnivaldEntity(EntityType<? extends IgnivaldEntity> type, Level level) {
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
    protected SoundEvent roarSound() {
        return HCSounds.ROAR_DRAGON.get();
    }

    @Override
    protected List<MonsterAttack> createAttacks() {
        ProjectileSpec fireball = ProjectileSpec.of(MonsterProjectile.Kind.FIREBALL, 14, Element.FIRE).speed(1.15F).explode(2.4F).size(1.3F).linger(50);
        ProjectileSpec smallFireball = ProjectileSpec.of(MonsterProjectile.Kind.FIREBALL, 11, Element.FIRE).speed(1.25F).explode(2.0F).size(1.0F).linger(30);

        MonsterAttack bite = TimelineAttack.builder("bite", 24).range(0, 5.5F).angle(40).weight(14).cooldown(30)
                .track(0, 6, 9).move(7, 10, 0.32, 0)
                .hit(10, 0, 4.1, 4.6, 1.9F, 13, Element.NONE, 1.2F)
                .sound(9, HCSounds.BITE, 2.0F, 0.85F)
                .build();
        MonsterAttack tailSpin = TimelineAttack.builder("tail_spin", 32).range(0, 7.5F).angle(180).weight(12).cooldown(70)
                .track(0, 6, 6)
                .sweep(10, 20, 0.5F, 7.8F, 0, 360, 4.0F, 15, Element.NONE, 2.2F)
                .sound(10, HCSounds.TAIL_SWING, 2.5F, 0.7F)
                .sound(15, HCSounds.TAIL_SWING, 2.0F, 0.8F)
                .build();
        MonsterAttack charge = TimelineAttack.builder("charge", 48).range(6, 22).angle(30).weight(10).cooldown(120)
                .track(0, 9, 12).track(10, 26, 2.0F)
                .move(9, 40, 0.42, 0)
                .hitSpan(9, 40, 0, 1.4, 2.6, 2.3F, 16, Element.NONE, 2.6F)
                .sound(8, HCSounds.ROAR_LARGE, 1.5F, 1.3F)
                .sound(12, HCSounds.STOMP, 2.0F, 0.8F).sound(20, HCSounds.STOMP, 2.0F, 0.8F)
                .sound(28, HCSounds.STOMP, 2.0F, 0.8F).sound(36, HCSounds.STOMP, 2.0F, 0.8F)
                .build();
        MonsterAttack fireballA = TimelineAttack.builder("fireball", 30).range(5, 26).angle(35).weight(12).cooldown(60).notWhenTired()
                .track(0, 14, 6)
                .projectile(15, 0, 4.2, 4.8, fireball)
                .sound(14, HCSounds.FIRE_BREATH, 3.0F, 0.9F)
                .particlesSpan(6, 14, ParticleTypes.FLAME, 0, 4.3, 4.7, 3, 0.2)
                .build();
        MonsterAttack triple = TimelineAttack.builder("fireball_triple", 44)
                .range(7, 28).angle(55).weight(6).cooldown(140).notWhenTired()
                .track(0, 10, 6).track(13, 20, 4).track(23, 30, 4)
                .projectile(12, 0, 4.2, 4.8, smallFireball).projectile(22, 0, 4.2, 4.8, smallFireball).projectile(32, 0, 4.2, 4.8, smallFireball)
                .sound(12, HCSounds.FIRE_BREATH, 2.5F, 1.0F).sound(22, HCSounds.FIRE_BREATH, 2.5F, 1.05F).sound(32, HCSounds.FIRE_BREATH, 2.5F, 1.1F)
                .build();
        MonsterAttack sweep = TimelineAttack.builder("breath_sweep", 56).range(2, 12).angle(80).weight(9).cooldown(200).enragedOnly().notWhenTired()
                .track(0, 10, 6)
                .breath(18, 42, 0, 4.0, 5.0, 9.0F, 34, 3.5F, Element.FIRE, ParticleTypes.FLAME)
                .sound(18, HCSounds.FIRE_BREATH, 3.0F, 0.7F).sound(28, HCSounds.FIRE_BREATH, 3.0F, 0.75F)
                .build();
        takeoff = TimelineAttack.builder("takeoff", 32).range(0, 999).angle(180).weight(0).cooldown(0).anywhere()
                .wind(8, 7).flying(12, true)
                .span(12, 31, (m, t, i) -> m.setDeltaMovement(m.getDeltaMovement().x * 0.7, 0.24, m.getDeltaMovement().z * 0.7))
                .sound(8, HCSounds.WING_FLAP, 2.5F, 0.8F).sound(18, HCSounds.WING_FLAP, 2.5F, 0.8F).sound(26, HCSounds.WING_FLAP, 2.5F, 0.85F)
                .build();
        land = TimelineAttack.builder("land", 24).range(0, 999).angle(180).weight(0).cooldown(0).airOnly()
                .span(0, 20, (m, t, i) -> m.setDeltaMovement(m.getDeltaMovement().x * 0.8, m.onGround() ? 0 : -0.32, m.getDeltaMovement().z * 0.8))
                .wind(4, 7).sound(4, HCSounds.WING_FLAP, 2.5F, 0.8F)
                .flying(20, false).tremor(21, 6)
                .build();
        MonsterAttack flyFire = TimelineAttack.builder("fly_fireball", 28).range(0, 32).angle(60).weight(12).cooldown(40).airOnly().notWhenTired()
                .track(0, 14, 8)
                .projectile(15, 0, 3.6, 4.2, fireball)
                .sound(14, HCSounds.FIRE_BREATH, 3.0F, 0.85F)
                .span(0, 27, (m, t, i) -> m.setDeltaMovement(m.getDeltaMovement().scale(0.6)))
                .build();
        MonsterAttack dive = TimelineAttack.builder("dive", 40).range(5, 28).angle(40).weight(10).cooldown(160).airOnly()
                .track(0, 12, 8)
                .span(14, 30, (m, target, t) -> {
                    if (target != null) {
                        Vec3 d = target.position().subtract(m.position());
                        double len = Math.max(0.1, d.length());
                        m.setDeltaMovement(d.scale(Math.min(1.05, len * 0.25) / len));
                    }
                })
                .hitSpan(14, 32, 0, 0.8, 1.5, 2.6F, 18, Element.FIRE, 2.6F)
                .sound(12, HCSounds.ROAR_DRAGON, 2.0F, 1.2F)
                .flying(33, false).tremor(34, 5)
                .build();
        MonsterAttack gust = TimelineAttack.builder("wing_gust", 32).range(0, 12).angle(180).weight(7).cooldown(120).airOnly()
                .wind(8, 9).wind(20, 9)
                .hitFlags(8, 0, 0, 2, 4.0F, 6, Element.NONE, 1.8F, MonsterDamage.FLAG_TRIP)
                .span(0, 31, (m, t, i) -> m.setDeltaMovement(m.getDeltaMovement().scale(0.5)))
                .build();
        return List.of(bite, tailSpin, charge, fireballA, triple, sweep, takeoff, land, flyFire, dive, gust);
    }

    // ------------------------------------------------------------------ flight behaviour
    @Override
    public boolean brainTick(LivingEntity target) {
        if (!isFlying()) {
            if (flightCooldown > 0) {
                flightCooldown--;
                return false;
            }
            int chance = isEnraged() ? 70 : 150;
            if (getHealth() < getMaxHealth() * 0.6F) chance -= 30;
            if (getRandom().nextInt(Math.max(10, chance)) == 0 && !isTired()) {
                startAttack(takeoff, target);
                return true;
            }
        } else {
            airTime++;
            if (airTime > 300 || airAttacks >= 3 || isTired()) {
                startAttack(land, target);
                return true;
            }
        }
        return false;
    }

    @Override
    public void startAttack(MonsterAttack attack, @Nullable LivingEntity target) {
        super.startAttack(attack, target);
        if (attack.airOnly && attack != land) airAttacks++;
    }

    @Override
    public void setFlying(boolean flying) {
        super.setFlying(flying);
        if (!flying) {
            airTime = 0;
            airAttacks = 0;
            flightCooldown = 400 + getRandom().nextInt(300);
        }
    }

    @Override
    protected void customServerTick() {
        // embers drift from the mouth while enraged
        if (isEnraged() && tickCount % 6 == 0 && !isLocked()) {
            Vec3 m = mouthPos();
            burstParticles(ParticleTypes.FLAME, m, 2, 0.15);
        }
        // never stay airborne without the flying flag
        if (isFlying() && isLocked()) setFlying(false);
    }
}
