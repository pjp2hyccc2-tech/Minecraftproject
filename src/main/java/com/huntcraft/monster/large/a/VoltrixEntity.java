package com.huntcraft.monster.large.a;

import com.huntcraft.core.CameraShake;
import com.huntcraft.core.Element;
import com.huntcraft.entity.misc.HazardEntity;
import com.huntcraft.entity.misc.MonsterProjectile;
import com.huntcraft.entity.monster.Hitzone;
import com.huntcraft.entity.monster.LargeMonster;
import com.huntcraft.entity.monster.MonsterProfile;
import com.huntcraft.entity.monster.PartDef;
import com.huntcraft.entity.monster.RewardTable;
import com.huntcraft.entity.monster.attack.MonsterAttack;
import com.huntcraft.entity.monster.attack.ProjectileSpec;
import com.huntcraft.entity.monster.attack.TimelineAttack;
import com.huntcraft.registry.HCItems;
import com.huntcraft.registry.HCSounds;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 雷鱗竜ヴォルトリクス - long serpentine leviathan (thunder). The electric organs along its back glow and become
 * "supercharged" (pulsing brighter, sparking) while enraged.
 * Lunge bite, wide tail whip, coil spin, serpentine dash, lightning strikes around the target, homing thunder orbs;
 * enraged: electric discharge around itself.
 */
public class VoltrixEntity extends LargeMonster {
    private static final Hitzone HZ_HEAD = Hitzone.of(70, 65, 60, 5, 20, 0, 30, 10);
    private static final Hitzone HZ_NECK = Hitzone.of(45, 40, 40, 5, 15, 0, 20, 5);
    private static final Hitzone HZ_BODY = Hitzone.of(35, 35, 30, 5, 10, 0, 15, 5);
    private static final Hitzone HZ_BACK = Hitzone.of(50, 40, 50, 5, 20, 0, 25, 10);
    private static final Hitzone HZ_TAIL = Hitzone.of(45, 40, 40, 5, 15, 0, 20, 5);
    private static final Hitzone HZ_LEG = Hitzone.of(30, 30, 25, 5, 10, 0, 15, 5);

    public static final MonsterProfile PROFILE = MonsterProfile.builder("voltrix", true)
            .category("leviathan")
            .health(620, 1024).highRank(1.6F, 0.7F)
            .scale(0.9F, 1.2F)
            .element(Element.THUNDER)
            .weakness(Element.ICE, 3).weakness(Element.WATER, 2).weakness(Element.DRAGON, 1)
            .part(new PartDef("head", 0, 2.3F, 3.8F, 1.5F, 1.4F, HZ_HEAD).breakable(150, "horn_tip_l", "horn_tip_r", "crest").ko())
            .part(new PartDef("neck", 0, 1.5F, 2.2F, 1.3F, 1.7F, HZ_NECK))
            .part(PartDef.body(HZ_BODY))
            .part(new PartDef("back", 0, 1.9F, -0.9F, 1.6F, 1.0F, HZ_BACK).breakable(140, "elec1", "elec2", "elec3"))
            .part(new PartDef("tail_base", 0, 0.7F, -3.0F, 1.3F, 1.5F, HZ_TAIL))
            .part(new PartDef("tail", 0, 0.5F, -4.8F, 1.6F, 1.5F, HZ_TAIL).severable(170, "tail3", "tail4", "tail5", "tail_fin"))
            .part(new PartDef("foreleg_l", -0.75F, 0, 0.9F, 0.9F, 1.3F, HZ_LEG))
            .part(new PartDef("foreleg_r", 0.75F, 0, 0.9F, 0.9F, 1.3F, HZ_LEG))
            .part(new PartDef("hindleg_l", -0.72F, 0, -1.7F, 0.9F, 1.3F, HZ_LEG))
            .part(new PartDef("hindleg_r", 0.72F, 0, -1.7F, 0.9F, 1.3F, HZ_LEG))
            .carves(new RewardTable()
                    .low(HCItems.VOLTRIX.scale, 30, 1, 2).low(HCItems.VOLTRIX.shell, 25).low(HCItems.VOLTRIX.special, 14)
                    .low(HCItems.VOLTRIX.claw, 15).low(HCItems.MONSTER_BONE_M, 8).low(HCItems.THUNDER_SAC, 6).low(HCItems.VOLTRIX.gem, 2)
                    .high(HCItems.VOLTRIX.scalePlus, 28, 1, 2).high(HCItems.VOLTRIX.shellPlus, 22).high(HCItems.VOLTRIX.specialPlus, 14)
                    .high(HCItems.VOLTRIX.clawPlus, 14).high(HCItems.MONSTER_BONE_L, 6).high(HCItems.THUNDER_SAC, 8).high(HCItems.VOLTRIX.gemPlus, 2))
            .tailCarves(new RewardTable()
                    .low(HCItems.VOLTRIX.tail, 60).low(HCItems.VOLTRIX.scale, 25).low(HCItems.VOLTRIX.gem, 3)
                    .high(HCItems.VOLTRIX.tail, 55).high(HCItems.VOLTRIX.scalePlus, 30).high(HCItems.VOLTRIX.gemPlus, 3))
            .captureRewards(new RewardTable()
                    .low(HCItems.VOLTRIX.scale, 30).low(HCItems.VOLTRIX.shell, 25).low(HCItems.VOLTRIX.special, 20)
                    .low(HCItems.VOLTRIX.claw, 12).low(HCItems.THUNDER_SAC, 8).low(HCItems.VOLTRIX.gem, 4)
                    .high(HCItems.VOLTRIX.scalePlus, 30).high(HCItems.VOLTRIX.shellPlus, 25).high(HCItems.VOLTRIX.specialPlus, 20)
                    .high(HCItems.VOLTRIX.clawPlus, 12).high(HCItems.THUNDER_SAC, 8).high(HCItems.VOLTRIX.gemPlus, 4))
            .breakReward("head", new RewardTable().low(HCItems.VOLTRIX.shell, 55).low(HCItems.VOLTRIX.claw, 40).low(HCItems.VOLTRIX.gem, 5)
                    .high(HCItems.VOLTRIX.shellPlus, 55).high(HCItems.VOLTRIX.clawPlus, 40).high(HCItems.VOLTRIX.gemPlus, 5))
            .breakReward("back", new RewardTable().low(HCItems.VOLTRIX.special, 70).low(HCItems.THUNDER_SAC, 25).low(HCItems.VOLTRIX.gem, 5)
                    .high(HCItems.VOLTRIX.specialPlus, 70).high(HCItems.THUNDER_SAC, 25).high(HCItems.VOLTRIX.gemPlus, 5))
            .questReward(2300, 88)
            .build();

    public static final String[] ANIMS = {
            "roar", "flinch", "bite", "tail_whip", "coil_spin", "dash", "lightning", "thunder_orbs", "discharge"
    };

    public VoltrixEntity(EntityType<? extends VoltrixEntity> type, Level level) {
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
        return LargeMonstersA.VOLTRIX_ROAR.get();
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return LargeMonstersA.VOLTRIX_GROWL.get();
    }

    @Override
    public double getRunSpeedModifier() {
        return 1.45;
    }

    @Override
    protected List<MonsterAttack> createAttacks() {
        ProjectileSpec orb = ProjectileSpec.of(MonsterProjectile.Kind.THUNDER_ORB, 11, Element.THUNDER)
                .speed(0.75F).homing().size(1.2F).explode(1.7F).lifetime(100);

        MonsterAttack bite = TimelineAttack.builder("bite", 30).range(0, 6).angle(40).weight(14).cooldown(30)
                .track(0, 9, 8).move(10, 13, 0.42, 0)
                .hit(12, 0, 1.0, 4.9, 1.9F, 14, Element.NONE, 1.2F)
                .sound(11, HCSounds.BITE, 2.0F, 0.8F)
                .build();
        MonsterAttack tailWhip = TimelineAttack.builder("tail_whip", 36).range(0, 9.5F).angle(180).weight(10).cooldown(70)
                .track(0, 6, 6)
                .sweep(13, 20, 1.0F, 9.0F, 60, 300, 3.2F, 15, Element.NONE, 2.4F)
                .sound(13, HCSounds.TAIL_SWING, 2.5F, 0.65F).sound(17, HCSounds.TAIL_SWING, 2.2F, 0.75F)
                .build();
        MonsterAttack coilSpin = TimelineAttack.builder("coil_spin", 44).range(0, 7.5F).angle(180).weight(8).cooldown(110)
                .track(0, 8, 6)
                .sweep(15, 27, 0.5F, 7.0F, 0, 360, 3.0F, 16, Element.NONE, 2.2F)
                .particlesSpan(15, 27, ParticleTypes.ELECTRIC_SPARK, 0, 1.2, -0.5, 4, 2.0)
                .sound(14, HCSounds.TAIL_SWING, 2.5F, 0.6F).sound(20, HCSounds.TAIL_SWING, 2.5F, 0.7F)
                .sound(15, LargeMonstersA.VOLTRIX_ZAP, 1.5F, 1.0F)
                .build();
        MonsterAttack dash = TimelineAttack.builder("dash", 56).range(6, 22).angle(30).weight(10).cooldown(120)
                .track(0, 12, 7).track(13, 32, 1.6F)
                .sound(11, LargeMonstersA.VOLTRIX_ROAR, 1.6F, 1.3F)
                .move(13, 40, 0.5, 0).move(41, 44, 0.25, 0)
                .hitSpan(13, 42, 0, 1.0, 2.6, 2.4F, 16, Element.NONE, 2.6F)
                .particlesSpan(13, 40, ParticleTypes.ELECTRIC_SPARK, 0, 1.6, -0.6, 2, 1.2)
                .sound(16, HCSounds.STOMP, 1.8F, 0.9F).sound(22, HCSounds.STOMP, 1.8F, 0.9F)
                .sound(28, HCSounds.STOMP, 1.8F, 0.9F).sound(34, HCSounds.STOMP, 1.8F, 0.9F)
                .build();
        MonsterAttack lightning = TimelineAttack.builder("lightning", 56).range(4, 26).angle(70).weight(9).cooldown(180).notWhenTired()
                .track(0, 14, 5)
                .sound(4, LargeMonstersA.VOLTRIX_CHARGE, 2.5F, 0.8F)
                .particlesSpan(4, 20, ParticleTypes.ELECTRIC_SPARK, 0, 2.6, -0.6, 3, 1.0)
                .at(20, (m, t, i) -> callLightning(t))
                .sound(20, LargeMonstersA.VOLTRIX_ROAR, 2.5F, 1.1F)
                .build();
        MonsterAttack orbs = TimelineAttack.builder("thunder_orbs", 46).range(5, 28).angle(45).weight(10).cooldown(100).notWhenTired()
                .track(0, 12, 6).track(13, 30, 3)
                .particlesSpan(4, 13, ParticleTypes.ELECTRIC_SPARK, 0, 2.4, 4.4, 2, 0.3)
                .projectile(14, 0.6, 2.4, 4.4, orb).projectile(22, -0.6, 2.4, 4.4, orb).projectile(30, 0, 2.4, 4.4, orb)
                .sound(14, LargeMonstersA.VOLTRIX_ZAP, 2.0F, 1.0F).sound(22, LargeMonstersA.VOLTRIX_ZAP, 2.0F, 1.1F)
                .sound(30, LargeMonstersA.VOLTRIX_ZAP, 2.0F, 1.2F)
                .build();
        MonsterAttack discharge = TimelineAttack.builder("discharge", 60).range(0, 9).angle(180).weight(12).cooldown(260).enragedOnly().notWhenTired()
                .sound(8, LargeMonstersA.VOLTRIX_CHARGE, 3.0F, 0.6F).sound(20, LargeMonstersA.VOLTRIX_CHARGE, 3.0F, 0.9F)
                .span(10, 31, (m, t, i) -> chargeSparks(i - 10))
                .at(32, (m, t, i) -> discharge())
                .build();

        return List.of(bite,
                new LargeAUtil.Weighted(tailWhip, LargeAUtil.rear(70, 2)),
                lightning, coilSpin, dash, orbs, discharge);
    }

    // ------------------------------------------------------------------ attack helpers
    /** Lightning strikes at the target and in a ring around it (more bolts while supercharged). */
    private void callLightning(@Nullable LivingEntity target) {
        float s = getMonsterScale();
        Vec3 c = target != null ? target.position() : localPos(0, 0, 8);
        c = LargeAUtil.ground(level(), c.x, c.y, c.z);
        float dmg = 15 * damageMultiplier();
        HazardEntity.spawn(this, c, HazardEntity.Kind.LIGHTNING, 2.2F * s, 24, 6, dmg, Element.THUNDER);
        int n = isEnraged() ? 6 : 4;
        double base = getRandom().nextDouble() * Math.PI * 2;
        double r = 4.2 * s;
        for (int k = 0; k < n; k++) {
            double a = base + k * Math.PI * 2 / n;
            Vec3 p = LargeAUtil.ground(level(), c.x + Math.cos(a) * r, c.y, c.z + Math.sin(a) * r);
            HazardEntity.spawn(this, p, HazardEntity.Kind.LIGHTNING, 1.9F * s, 30 + k * 4, 6, dmg * 0.9F, Element.THUNDER);
        }
        burstParticles(ParticleTypes.ELECTRIC_SPARK, mouthPos(), 30, 0.6);
    }

    /** Sparks gathering over the body while charging the discharge. */
    private void chargeSparks(int step) {
        if (!(level() instanceof ServerLevel sl)) return;
        float s = getMonsterScale();
        int n = 2 + step / 4;
        Vec3 c = localPos(0, 1.6, -0.6);
        sl.sendParticles(ParticleTypes.ELECTRIC_SPARK, c.x, c.y, c.z, n * 2, 1.4 * s, 0.8 * s, 2.4 * s, 0.25);
        if (step % 5 == 0) sl.sendParticles(ParticleTypes.END_ROD, c.x, c.y + 0.6, c.z, 4, 1.0 * s, 0.4, 1.8 * s, 0.05);
    }

    /** Enraged: massive electric discharge around the body. */
    private void discharge() {
        float s = getMonsterScale();
        Vec3 c = localPos(0, 1.2, -0.4);
        hitSphere(c, 7.5F * s, 24, Element.THUNDER, 2.6F, 0);
        if (level() instanceof ServerLevel sl) {
            sl.sendParticles(ParticleTypes.ELECTRIC_SPARK, c.x, c.y, c.z, 160, 3.0 * s, 1.2 * s, 3.0 * s, 0.6);
            sl.sendParticles(ParticleTypes.FLASH, c.x, c.y + 1.0, c.z, 1, 0, 0, 0, 0);
            sl.sendParticles(ParticleTypes.END_ROD, c.x, c.y, c.z, 50, 2.5 * s, 1.0 * s, 2.5 * s, 0.3);
            CameraShake.shake(sl, c, 24 * s, 0.9F, 14);
        }
        double base = getRandom().nextDouble() * Math.PI * 2;
        for (int k = 0; k < 5; k++) {
            double a = base + k * Math.PI * 2 / 5;
            Vec3 p = LargeAUtil.ground(level(), getX() + Math.cos(a) * 4.5 * s, getY(), getZ() + Math.sin(a) * 4.5 * s);
            LargeAUtil.visualBolt(this, p);
        }
        playSound(HCSounds.THUNDER_CRACK.get(), 4.0F, 0.8F);
    }

    // ------------------------------------------------------------------ ambience
    @Override
    protected void clientTick() {
        super.clientTick();
        if (isCarcass()) return;
        boolean charged = isEnraged() && !isTired();
        if (tickCount % (charged ? 2 : 14) != 0) return;
        RandomSource r = getRandom();
        // sparks crawling along the organ rows (constant crackle while supercharged)
        double fwd = 1.0 - r.nextDouble() * 4.0;
        double side = r.nextBoolean() ? 0.35 : -0.35;
        Vec3 p = localPos(side, 2.1 - Math.max(0, -fwd - 2.0) * 0.3, fwd);
        level().addParticle(ParticleTypes.ELECTRIC_SPARK, p.x, p.y, p.z, (r.nextDouble() - 0.5) * 0.2, 0.1 + r.nextDouble() * 0.1, (r.nextDouble() - 0.5) * 0.2);
        if (charged && tickCount % 6 == 0) {
            Vec3 m = mouthPos();
            level().addParticle(ParticleTypes.ELECTRIC_SPARK, m.x, m.y, m.z, (r.nextDouble() - 0.5) * 0.3, 0.05, (r.nextDouble() - 0.5) * 0.3);
        }
    }
}
