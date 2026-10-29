package com.huntcraft.monster.large.b;

import com.huntcraft.core.CameraShake;
import com.huntcraft.core.Element;
import com.huntcraft.entity.misc.HazardEntity;
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
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 岩槌竜グラヴァドン - rock-hammer brute wyvern (water / earth).
 * Slow and heavy: headbutt charge, overhead head slam (tremor + rockfall), 180-degree tail club swing,
 * body slam jump, water jets from the throat sac, dug-up rock lobs; enraged: double slam raising geysers.
 */
public class GravadonEntity extends LargeMonster {
    private static final Hitzone HZ_HEAD = Hitzone.of(45, 55, 40, 5, 0, 30, 15, 15);
    private static final Hitzone HZ_NECK = Hitzone.of(42, 40, 35, 5, 0, 22, 12, 10);
    private static final Hitzone HZ_BODY = Hitzone.of(30, 30, 25, 5, 0, 15, 10, 10);
    private static final Hitzone HZ_BACK = Hitzone.of(22, 28, 18, 0, 0, 10, 5, 5);
    private static final Hitzone HZ_ARMS = Hitzone.of(60, 50, 55, 10, 0, 25, 15, 15);
    private static final Hitzone HZ_TAIL = Hitzone.of(45, 40, 35, 5, 0, 20, 10, 10);
    private static final Hitzone HZ_LEG = Hitzone.of(40, 40, 30, 5, 0, 15, 10, 10);

    public static final MonsterProfile PROFILE = MonsterProfile.builder("gravadon", true)
            .category("brute_wyvern")
            .health(800, 1024).highRank(1.6F, 0.7F)
            .scale(0.92F, 1.22F)
            .element(Element.WATER)
            .weakness(Element.THUNDER, 3).weakness(Element.ICE, 1).weakness(Element.DRAGON, 1)
            .rageThreshold(0.14F).flinchThreshold(0.07F)
            .part(new PartDef("head", 0, 2.25F, 3.3F, 2.6F, 2.45F, HZ_HEAD).breakable(220, "crest_l", "crest_r", "crest_front").ko())
            .part(new PartDef("neck", 0, 2.35F, 2.0F, 1.7F, 1.5F, HZ_NECK))
            .part(PartDef.body(HZ_BODY))
            .part(new PartDef("back", 0, 3.25F, -0.25F, 2.4F, 1.15F, HZ_BACK).breakable(200, "back_rocks"))
            .part(new PartDef("arms", 0, 1.15F, 1.75F, 1.5F, 1.3F, HZ_ARMS).breakable(90, "arm_claws_l", "arm_claws_r", "arm_spur_l", "arm_spur_r"))
            .part(new PartDef("tail_base", 0, 2.1F, -2.5F, 1.7F, 1.6F, HZ_TAIL))
            .part(new PartDef("tail", 0, 1.95F, -4.6F, 1.9F, 1.55F, HZ_TAIL).severable(260, "tail3", "tail4", "tail_club"))
            .part(new PartDef("leg_l", -1.15F, 0, -0.4F, 1.2F, 2.0F, HZ_LEG))
            .part(new PartDef("leg_r", 1.15F, 0, -0.4F, 1.2F, 2.0F, HZ_LEG))
            .carves(new RewardTable()
                    .low(HCItems.GRAVADON.scale, 28, 1, 2).low(HCItems.GRAVADON.shell, 25).low(HCItems.GRAVADON.special, 12)
                    .low(HCItems.GRAVADON.claw, 15).low(HCItems.MONSTER_BONE_M, 10).low(HCItems.AQUA_SAC, 8).low(HCItems.GRAVADON.gem, 2)
                    .high(HCItems.GRAVADON.scalePlus, 26, 1, 2).high(HCItems.GRAVADON.shellPlus, 22).high(HCItems.GRAVADON.specialPlus, 12)
                    .high(HCItems.GRAVADON.clawPlus, 14).high(HCItems.MONSTER_BONE_L, 8).high(HCItems.AQUA_SAC, 10).high(HCItems.GRAVADON.gemPlus, 2))
            .tailCarves(new RewardTable()
                    .low(HCItems.GRAVADON.tail, 60).low(HCItems.GRAVADON.scale, 22).low(HCItems.MONSTER_BONE_M, 10).low(HCItems.GRAVADON.gem, 3)
                    .high(HCItems.GRAVADON.tail, 55).high(HCItems.GRAVADON.scalePlus, 28).high(HCItems.MONSTER_BONE_L, 8).high(HCItems.GRAVADON.gemPlus, 3))
            .captureRewards(new RewardTable()
                    .low(HCItems.GRAVADON.scale, 28).low(HCItems.GRAVADON.shell, 24).low(HCItems.GRAVADON.special, 18).low(HCItems.AQUA_SAC, 10).low(HCItems.GRAVADON.gem, 4)
                    .high(HCItems.GRAVADON.scalePlus, 28).high(HCItems.GRAVADON.shellPlus, 24).high(HCItems.GRAVADON.specialPlus, 18).high(HCItems.AQUA_SAC, 10).high(HCItems.GRAVADON.gemPlus, 4))
            .breakReward("head", new RewardTable().low(HCItems.GRAVADON.special, 55).low(HCItems.GRAVADON.claw, 35).low(HCItems.GRAVADON.gem, 6)
                    .high(HCItems.GRAVADON.specialPlus, 55).high(HCItems.GRAVADON.clawPlus, 35).high(HCItems.GRAVADON.gemPlus, 6))
            .breakReward("back", new RewardTable().low(HCItems.GRAVADON.scale, 70).low(HCItems.GRAVADON.gem, 4).high(HCItems.GRAVADON.scalePlus, 70).high(HCItems.GRAVADON.gemPlus, 4))
            .breakReward("arms", new RewardTable().low(HCItems.GRAVADON.shell, 65).low(HCItems.GRAVADON.scale, 30).high(HCItems.GRAVADON.shellPlus, 65).high(HCItems.GRAVADON.scalePlus, 30))
            .questReward(2800, 100)
            .build();

    public static final String[] ANIMS = {
            "roar", "flinch", "bite", "headbutt_charge", "head_slam", "tail_club", "body_slam", "mud_jet", "rock_throw", "double_slam"
    };

    public GravadonEntity(EntityType<? extends GravadonEntity> type, Level level) {
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
        return LargeMonstersB.GRAVADON_ROAR.get();
    }

    @Override
    public float getTurnSpeed() {
        return 3.8F;
    }

    @Override
    public double getRunSpeedModifier() {
        return 1.35;
    }

    @Override
    protected List<MonsterAttack> createAttacks() {
        ProjectileSpec jet = ProjectileSpec.of(MonsterProjectile.Kind.WATER_JET, 13, Element.WATER).speed(1.25F).size(1.1F).explode(1.5F).lifetime(50).count(3, 14F);
        ProjectileSpec bigJet = ProjectileSpec.of(MonsterProjectile.Kind.WATER_JET, 17, Element.WATER).speed(1.4F).size(1.6F).explode(2.2F).lifetime(50);
        ProjectileSpec rock = ProjectileSpec.of(MonsterProjectile.Kind.ROCK, 16, Element.NONE).speed(1.05F).gravity(0.04F).size(1.4F).explode(2.0F).lifetime(70);

        // quick tusk bite (close range filler)
        MonsterAttack bite = TimelineAttack.builder("bite", 28).range(0, 5.5F).angle(35).weight(12).cooldown(30)
                .track(0, 8, 5).move(9, 12, 0.22, 0)
                .hit(12, 0, 2.3, 4.3, 1.9F, 16, Element.NONE, 1.4F)
                .sound(11, HCSounds.BITE, 2.2F, 0.7F)
                .build();
        // headbutt charge: paws the ground, then rams with the hammer crest
        MonsterAttack charge = TimelineAttack.builder("headbutt_charge", 56).range(7, 24).angle(30).weight(10).cooldown(140)
                .track(0, 13, 4).track(14, 32, 1.4F)
                .sound(4, HCSounds.GROWL, 2.0F, 0.6F)
                .at(5, (m, tg, t) -> LBUtil.dig(m, m.localPos(-0.8, 0, 0.6), 12))
                .at(11, (m, tg, t) -> LBUtil.dig(m, m.localPos(-0.8, 0, 0.6), 12))
                .sound(14, LargeMonstersB.GRAVADON_ROAR, 2.5F, 1.25F)
                .move(14, 44, 0.46, 0)
                .span(14, 44, LBUtil.sphere(0, 1.8, 3.7, 2.3F, 22, Element.NONE, 3.0F, MonsterDamage.FLAG_LAUNCH))
                .span(14, 44, (m, tg, t) -> {
                    if (t % 6 == 2) {
                        m.playSound(HCSounds.STOMP.get(), 2.0F, 0.7F);
                        LBUtil.dig(m, m.position(), 8);
                    }
                })
                .span(45, 52, (m, tg, t) -> m.moveLocal(0.30 - 0.04 * (t - 45), 0))
                .at(45, (m, tg, t) -> LBUtil.dig(m, m.localPos(0, 0, 1.5), 30))
                .sound(45, HCSounds.STOMP, 2.5F, 0.5F)
                .build();
        // overhead head slam: tremor + falling rocks ahead (and on the target)
        MonsterAttack slam = TimelineAttack.builder("head_slam", 50).range(0, 8).angle(40).weight(11).cooldown(80)
                .track(0, 16, 4)
                .sound(8, LargeMonstersB.GRAVADON_ROAR, 1.6F, 1.4F)
                .span(24, 24, LBUtil.sphere(0, 0.6, 4.6, 2.6F, 26, Element.NONE, 2.0F, MonsterDamage.FLAG_STUN))
                .tremor(24, 7)
                .sound(24, LargeMonstersB.GRAVADON_SLAM, 3.0F, 0.8F)
                .at(24, (m, tg, t) -> {
                    LBUtil.impact(m, m.localPos(0, 0, 4.6), 2.6F);
                    LBUtil.hazard(m, HazardEntity.Kind.ROCKFALL, 0, 7.5, 1.6F, 16, 2, 16, Element.NONE);
                    LBUtil.hazard(m, HazardEntity.Kind.ROCKFALL, -2.6, 9.2, 1.6F, 20, 2, 16, Element.NONE);
                    LBUtil.hazard(m, HazardEntity.Kind.ROCKFALL, 2.6, 9.2, 1.6F, 24, 2, 16, Element.NONE);
                    if (tg != null && tg.distanceToSqr(m) < 18 * 18) LBUtil.hazardAt(m, HazardEntity.Kind.ROCKFALL, tg.position(), 1.8F, 26, 2, 16, Element.NONE);
                })
                .build();
        // tail club: winds up, then the whole body turns 180 degrees to the right, sweeping the club behind it
        MonsterAttack club = TimelineAttack.builder("tail_club", 44).range(0, 8.5F).angle(180).weight(9).cooldown(70)
                .sound(10, HCSounds.GROWL, 1.8F, 0.8F)
                .span(14, 25, (m, tg, t) -> LBUtil.spin(m, 15.0F))
                .span(14, 27, (m, tg, t) -> {
                    float s = m.getMonsterScale();
                    m.hitArc(1.5 * s, 7.0 * s, 100, 260, 3.6 * s, 22, Element.NONE, 3.2F, 0);
                })
                .sound(15, HCSounds.TAIL_SWING, 2.8F, 0.55F).sound(21, HCSounds.TAIL_SWING, 2.6F, 0.6F)
                .sound(27, HCSounds.STOMP, 2.2F, 0.7F)
                .at(27, (m, tg, t) -> LBUtil.dig(m, m.localPos(0, 0, -4.5), 16))
                .build();
        // body slam: crouch, leap forward, belly-flop shockwave
        MonsterAttack bodySlam = TimelineAttack.builder("body_slam", 52).range(0, 7).angle(180).weight(8).cooldown(120)
                .track(0, 12, 3)
                .sound(10, HCSounds.GROWL, 2.0F, 0.7F)
                .at(14, (m, tg, t) -> {
                    m.moveLocal(0.32, 0.62);
                    LBUtil.dig(m, m.position(), 20);
                })
                .span(15, 21, (m, tg, t) -> m.moveLocal(0.30, 0))
                .span(22, 26, (m, tg, t) -> {
                    if (!m.onGround()) m.setDeltaMovement(m.getDeltaMovement().x * 0.6, -0.9, m.getDeltaMovement().z * 0.6);
                })
                .span(27, 27, LBUtil.sphere(0, 0.6, 0.8, 4.4F, 20, Element.NONE, 2.6F, MonsterDamage.FLAG_TRIP))
                .tremor(27, 10)
                .sound(27, LargeMonstersB.GRAVADON_SLAM, 3.0F, 0.65F)
                .at(27, (m, tg, t) -> LBUtil.impact(m, m.position(), 4.0F))
                .build();
        // water jets from the swollen throat sac: a 3-way fan, then one heavy aimed jet
        MonsterAttack mudJet = TimelineAttack.builder("mud_jet", 48).range(6, 26).angle(35).weight(10).cooldown(80).notWhenTired()
                .track(0, 16, 5)
                .particlesSpan(6, 17, ParticleTypes.FALLING_WATER, 0, 2.3, 4.2, 2, 0.3)
                .sound(8, HCSounds.GROWL, 1.6F, 1.2F)
                .projectile(18, 0, 2.5, 4.4, jet)
                .sound(18, HCSounds.WATER_JET, 2.5F, 0.8F)
                .particles(18, ParticleTypes.SPLASH, 0, 2.5, 4.5, 30, 0.4)
                .track(19, 26, 4)
                .projectile(28, 0, 2.5, 4.4, bigJet)
                .sound(28, HCSounds.WATER_JET, 3.0F, 0.65F)
                .particles(28, ParticleTypes.SPLASH, 0, 2.5, 4.5, 40, 0.5)
                .build();
        // rock throw: digs the hammer into the ground and flings three boulders in an arc
        MonsterAttack rockThrow = TimelineAttack.builder("rock_throw", 56).range(8, 28).angle(35).weight(8).cooldown(120)
                .track(0, 10, 5)
                .span(10, 24, (m, tg, t) -> {
                    if (t % 3 == 0) LBUtil.dig(m, m.localPos(0, 0, 4.4), 10);
                })
                .sound(12, HCSounds.STOMP, 1.8F, 1.3F).sound(18, HCSounds.STOMP, 1.8F, 1.2F)
                .track(20, 27, 5)
                .at(28, (m, tg, t) -> {
                    Vec3 from = m.localPos(0, 3.0, 4.0);
                    LBUtil.lob(m, tg, rock, from, 0F);
                    LBUtil.lob(m, tg, rock, from, -16F);
                    LBUtil.lob(m, tg, rock, from, 16F);
                    LBUtil.dig(m, m.localPos(0, 0, 4.4), 40);
                })
                .sound(28, LargeMonstersB.GRAVADON_SLAM, 2.0F, 1.3F)
                .build();
        // enraged: two slams; the first raises a line of geysers, the second a ring (+ one under the target)
        MonsterAttack doubleSlam = TimelineAttack.builder("double_slam", 80).range(0, 10).angle(45).weight(12).cooldown(200).enragedOnly()
                .track(0, 14, 4)
                .span(22, 22, LBUtil.sphere(0, 0.6, 4.6, 2.6F, 24, Element.NONE, 2.0F, MonsterDamage.FLAG_STUN))
                .tremor(22, 7)
                .sound(22, LargeMonstersB.GRAVADON_SLAM, 3.0F, 0.8F)
                .at(22, (m, tg, t) -> {
                    LBUtil.impact(m, m.localPos(0, 0, 4.6), 2.6F);
                    for (int i = 0; i < 4; i++) {
                        LBUtil.hazard(m, HazardEntity.Kind.GEYSER, 0, 6.5 + i * 2.6, 1.7F, 6 + i * 4, 8, 18, Element.WATER);
                    }
                })
                .track(30, 44, 3)
                .sound(42, LargeMonstersB.GRAVADON_ROAR, 2.0F, 1.3F)
                .at(36, (m, tg, t) -> clearHits(m))
                .span(50, 50, LBUtil.sphere(0, 0.6, 4.6, 2.8F, 26, Element.NONE, 2.2F, MonsterDamage.FLAG_STUN))
                .tremor(50, 9)
                .sound(50, LargeMonstersB.GRAVADON_SLAM, 3.0F, 0.7F)
                .at(50, (m, tg, t) -> {
                    LBUtil.impact(m, m.localPos(0, 0, 4.6), 3.0F);
                    for (int i = 0; i < 8; i++) {
                        double a = i * Math.PI / 4;
                        LBUtil.hazard(m, HazardEntity.Kind.GEYSER, Math.sin(a) * 7.0, Math.cos(a) * 7.0, 1.7F, 12, 8, 18, Element.WATER);
                    }
                    if (tg != null && tg.distanceToSqr(m) < 24 * 24) LBUtil.hazardAt(m, HazardEntity.Kind.GEYSER, tg.position(), 2.0F, 20, 8, 20, Element.WATER);
                })
                .build();
        return List.of(bite, charge, slam, club, bodySlam, mudJet, rockThrow, doubleSlam);
    }

    private static void clearHits(@Nullable Object m) {
        if (m instanceof GravadonEntity g) g.hitThisAction.clear();
    }

    @Override
    protected void customServerTick() {
        // enraged: steam snorts and water drips from the jaws
        if (isEnraged() && tickCount % 7 == 0 && !isLocked()) {
            Vec3 m = localPos(0, 2.6, 4.5);
            burstParticles(ParticleTypes.CLOUD, m, 1, 0.1);
            burstParticles(ParticleTypes.FALLING_WATER, m.add(0, -0.6, 0), 2, 0.25);
        }
        // heavy footfalls while running
        if (isRunning() && onGround() && !isActing() && tickCount % 8 == 0) {
            playSound(HCSounds.STOMP.get(), 1.4F, 0.65F);
            if (level() instanceof ServerLevel sl) CameraShake.shake(sl, position(), 12, 0.15F, 4);
        }
    }
}
