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
import com.huntcraft.entity.monster.StatusThresholds;
import com.huntcraft.entity.monster.attack.MonsterAttack;
import com.huntcraft.entity.monster.attack.ProjectileSpec;
import com.huntcraft.entity.monster.attack.TimelineAttack;
import com.huntcraft.registry.HCItems;
import com.huntcraft.registry.HCSounds;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 冥炎龍アズラゴス - elder dragon of the netherflame (dragon element), final boss.
 * Ground: bite, double claw swipe, tail blade sweep, sweeping dragon beam, homing orb volley, wing gust.
 * Air: orb volley, dive, meteor rain. Enraged: SUPERNOVA (2 s charge, then a blast and a ring of dragon pillars).
 */
public class AzragosEntity extends LargeMonster {
    private static final Hitzone HZ_HEAD = Hitzone.of(55, 60, 45, 5, 10, 15, 15, 30);
    private static final Hitzone HZ_NECK = Hitzone.of(40, 35, 35, 0, 5, 10, 10, 20);
    private static final Hitzone HZ_BODY = Hitzone.of(30, 30, 25, 0, 5, 10, 10, 20);
    private static final Hitzone HZ_WING = Hitzone.of(40, 35, 45, 0, 5, 15, 15, 25);
    private static final Hitzone HZ_FORELEG = Hitzone.of(40, 40, 30, 0, 5, 10, 10, 20);
    private static final Hitzone HZ_LEG = Hitzone.of(35, 35, 25, 0, 5, 10, 10, 15);
    private static final Hitzone HZ_TAIL = Hitzone.of(45, 35, 40, 0, 5, 15, 15, 25);
    /** Elder dragons resist statuses more than wyverns. */
    private static final StatusThresholds ELDER_STATUS = new StatusThresholds(260, 220, 220, 180, 220, 300, 1.6F, 3.0F);

    public static final MonsterProfile PROFILE = MonsterProfile.builder("azragos", true)
            .category("elder_dragon")
            .health(1000, 1024).highRank(1.7F, 0.6F)
            .scale(0.95F, 1.15F)
            .element(Element.DRAGON)
            .weakness(Element.DRAGON, 2).weakness(Element.ICE, 1).weakness(Element.THUNDER, 1)
            .status(ELDER_STATUS)
            .rageThreshold(0.11F).flinchThreshold(0.06F).captureThreshold(0.0F)
            .part(new PartDef("head", 0, 4.0F, 4.1F, 1.6F, 1.4F, HZ_HEAD).breakable(240, "horn_tip_l", "horn_tip_r", "horn2_tip_l", "horn2_tip_r").ko())
            .part(new PartDef("neck", 0, 3.1F, 2.5F, 1.5F, 1.4F, HZ_NECK))
            .part(PartDef.body(HZ_BODY))
            .part(new PartDef("wing_l", -1.7F, 3.3F, 0.2F, 1.8F, 1.0F, HZ_WING).breakable(180, "mem_f3_l"))
            .part(new PartDef("wing_r", 1.7F, 3.3F, 0.2F, 1.8F, 1.0F, HZ_WING).breakable(180, "mem_f3_r"))
            .part(new PartDef("foreleg_l", -1.0F, 0, 1.4F, 1.1F, 2.3F, HZ_FORELEG).breakable(170, "elbow_spike_l", "fore_plate_l"))
            .part(new PartDef("foreleg_r", 1.0F, 0, 1.4F, 1.1F, 2.3F, HZ_FORELEG).breakable(170, "elbow_spike_r", "fore_plate_r"))
            .part(new PartDef("hindleg_l", -1.0F, 0, -1.3F, 1.2F, 2.3F, HZ_LEG))
            .part(new PartDef("hindleg_r", 1.0F, 0, -1.3F, 1.2F, 2.3F, HZ_LEG))
            .part(new PartDef("tail_base", 0, 1.9F, -2.9F, 1.5F, 1.3F, HZ_TAIL))
            .part(new PartDef("tail", 0, 1.3F, -5.0F, 1.6F, 1.2F, HZ_TAIL).severable(300, "tail4", "tail5", "tail6", "tail_blade"))
            .carves(new RewardTable()
                    .low(HCItems.AZRAGOS.scale, 28, 1, 2).low(HCItems.AZRAGOS.shell, 25).low(HCItems.AZRAGOS.special, 12)
                    .low(HCItems.AZRAGOS.claw, 16).low(HCItems.MONSTER_BONE_L, 8).low(HCItems.AZRAGOS.gem, 3)
                    .high(HCItems.AZRAGOS.scalePlus, 26, 1, 2).high(HCItems.AZRAGOS.shellPlus, 23).high(HCItems.AZRAGOS.specialPlus, 12)
                    .high(HCItems.AZRAGOS.clawPlus, 16).high(HCItems.MONSTER_BONE_L, 8).high(HCItems.AZRAGOS.gemPlus, 3))
            .tailCarves(new RewardTable()
                    .low(HCItems.AZRAGOS.tail, 60).low(HCItems.AZRAGOS.scale, 25).low(HCItems.AZRAGOS.gem, 4)
                    .high(HCItems.AZRAGOS.tail, 55).high(HCItems.AZRAGOS.scalePlus, 30).high(HCItems.AZRAGOS.gemPlus, 4))
            .captureRewards(new RewardTable()
                    .low(HCItems.AZRAGOS.scale, 30).low(HCItems.AZRAGOS.shell, 25).low(HCItems.AZRAGOS.special, 18).low(HCItems.AZRAGOS.gem, 5)
                    .high(HCItems.AZRAGOS.scalePlus, 30).high(HCItems.AZRAGOS.shellPlus, 25).high(HCItems.AZRAGOS.specialPlus, 18).high(HCItems.AZRAGOS.gemPlus, 5))
            .breakReward("head", new RewardTable().low(HCItems.AZRAGOS.special, 70).low(HCItems.AZRAGOS.gem, 8).high(HCItems.AZRAGOS.specialPlus, 70).high(HCItems.AZRAGOS.gemPlus, 8))
            .breakReward("wing_l", new RewardTable().low(HCItems.AZRAGOS.shell, 60).low(HCItems.AZRAGOS.scale, 30).high(HCItems.AZRAGOS.shellPlus, 60).high(HCItems.AZRAGOS.scalePlus, 30))
            .breakReward("wing_r", new RewardTable().low(HCItems.AZRAGOS.shell, 60).low(HCItems.AZRAGOS.scale, 30).high(HCItems.AZRAGOS.shellPlus, 60).high(HCItems.AZRAGOS.scalePlus, 30))
            .breakReward("foreleg_l", new RewardTable().low(HCItems.AZRAGOS.claw, 80).high(HCItems.AZRAGOS.clawPlus, 80))
            .breakReward("foreleg_r", new RewardTable().low(HCItems.AZRAGOS.claw, 80).high(HCItems.AZRAGOS.clawPlus, 80))
            .questReward(5000, 200)
            .build();

    public static final String[] ANIMS = {
            "roar", "flinch", "bite", "claw_swipe", "tail_sweep", "breath_beam", "orb_volley", "wing_gust", "supernova",
            "takeoff", "land", "fly_orb", "dive", "meteor_rain"
    };

    // geometry (blocks at scale 1, measured from the model)
    private static final double MOUTH_UP = 4.25, MOUTH_FWD = 4.9;
    private static final double BEAM_PIVOT_UP = 3.6, BEAM_PIVOT_FWD = 2.2, BEAM_REACH = 2.7;
    private static final double CORE_UP = 2.7, CORE_FWD = 1.6;

    // assigned in createAttacks() (runs inside the super constructor): no field initializers here
    private MonsterAttack takeoff;
    private MonsterAttack land;
    private int airTime;
    private int airAttacks;
    private int flightCooldown = 360;

    public AzragosEntity(EntityType<? extends AzragosEntity> type, Level level) {
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
        return LargeMonstersB.AZRAGOS_ROAR.get();
    }

    @Override
    public float getTurnSpeed() {
        return 5.0F;
    }

    @Override
    public double getRunSpeedModifier() {
        return 1.3;
    }

    @Override
    protected List<MonsterAttack> createAttacks() {
        ProjectileSpec orb = ProjectileSpec.of(MonsterProjectile.Kind.DRAGON_ORB, 12, Element.DRAGON).speed(0.75F).size(1.1F).explode(1.8F).lifetime(90).homing().count(5, 22F);
        ProjectileSpec orb3 = ProjectileSpec.of(MonsterProjectile.Kind.DRAGON_ORB, 12, Element.DRAGON).speed(0.85F).size(1.0F).explode(1.6F).lifetime(90).homing().count(3, 26F);
        ProjectileSpec airOrb = ProjectileSpec.of(MonsterProjectile.Kind.DRAGON_ORB, 13, Element.DRAGON).speed(0.9F).size(1.2F).explode(2.0F).lifetime(90).homing().count(3, 18F);

        MonsterAttack bite = TimelineAttack.builder("bite", 26).range(0, 6).angle(35).weight(10).cooldown(30)
                .track(0, 8, 6).move(9, 12, 0.25, 0)
                .hit(12, 0, 3.4, 4.7, 1.8F, 17, Element.NONE, 1.4F)
                .sound(11, HCSounds.BITE, 2.2F, 0.75F)
                .build();
        // double claw swipe (right then left)
        MonsterAttack claws = TimelineAttack.builder("claw_swipe", 44).range(0, 6.5F).angle(60).weight(12).cooldown(40)
                .track(0, 10, 6)
                .sweep(12, 16, 0.5F, 6.2F, 75, -45, 3.0F, 16, Element.NONE, 1.8F)
                .sound(12, HCSounds.TAIL_SWING, 2.2F, 1.1F)
                .at(19, (m, tg, t) -> clearHits(m))
                .track(18, 22, 5)
                .sweep(24, 28, 0.5F, 6.2F, -75, 45, 3.0F, 16, Element.NONE, 1.8F)
                .sound(24, HCSounds.TAIL_SWING, 2.2F, 1.0F)
                .build();
        // tail blade sweep from the right side, through the back, to the left
        MonsterAttack tailSweep = TimelineAttack.builder("tail_sweep", 40).range(0, 10).angle(180).weight(9).cooldown(70)
                .sweep(14, 24, 1.5F, 10.0F, 80, 280, 3.0F, 18, Element.NONE, 2.6F)
                .sound(14, HCSounds.TAIL_SWING, 2.8F, 0.6F).sound(19, HCSounds.TAIL_SWING, 2.6F, 0.7F)
                .build();
        // sweeping dragon beam (left to right across the target)
        MonsterAttack beam = TimelineAttack.builder("breath_beam", 70).range(3, 22).angle(60).weight(9).cooldown(160).notWhenTired()
                .track(0, 18, 5)
                .sound(4, LargeMonstersB.AZRAGOS_CHARGE, 2.5F, 1.4F)
                .span(4, 21, (m, tg, t) -> beamCharge(m, t))
                .span(22, 52, (m, tg, t) -> beamTick(m, tg, t - 22, -50F + 100F * (t - 22) / 30F))
                .sound(22, HCSounds.DRAGON_BEAM, 3.0F, 0.6F).sound(32, HCSounds.DRAGON_BEAM, 3.0F, 0.65F).sound(42, HCSounds.DRAGON_BEAM, 3.0F, 0.7F)
                .build();
        // homing dragon orb volley (5, then 3 more)
        MonsterAttack volley = TimelineAttack.builder("orb_volley", 50).range(6, 30).angle(50).weight(10).cooldown(100).notWhenTired()
                .track(0, 16, 5)
                .span(4, 17, (m, tg, t) -> beamCharge(m, t))
                .projectile(18, 0, MOUTH_UP, MOUTH_FWD, orb)
                .sound(18, HCSounds.DRAGON_BEAM, 2.5F, 1.5F)
                .track(19, 28, 5)
                .projectile(30, 0, MOUTH_UP, MOUTH_FWD, orb3)
                .sound(30, HCSounds.DRAGON_BEAM, 2.5F, 1.7F)
                .build();
        // ground wing gust: two forward beats that trip and push hunters
        MonsterAttack gust = TimelineAttack.builder("wing_gust", 40).range(0, 12).angle(70).weight(8).cooldown(120)
                .track(0, 10, 4)
                .wind(16, 11).wind(26, 11)
                .hitFlags(17, 0, 1.4, 4.2, 4.5F, 10, Element.NONE, 2.2F, MonsterDamage.FLAG_TRIP)
                .particles(16, ParticleTypes.CLOUD, 0, 0.6, 5.0, 30, 1.6)
                .particles(26, ParticleTypes.CLOUD, 0, 0.6, 5.0, 30, 1.6)
                .sound(16, HCSounds.WING_FLAP, 3.0F, 0.6F).sound(26, HCSounds.WING_FLAP, 3.0F, 0.65F)
                .build();
        // enraged: SUPERNOVA - rears up and gathers dragon energy (~2 s), then detonates
        MonsterAttack supernova = TimelineAttack.builder("supernova", 90).range(0, 14).angle(180).weight(14).cooldown(400).enragedOnly()
                .sound(4, LargeMonstersB.AZRAGOS_ROAR, 3.0F, 0.7F)
                .sound(12, LargeMonstersB.AZRAGOS_CHARGE, 3.5F, 1.0F)
                .sound(32, LargeMonstersB.AZRAGOS_CHARGE, 3.5F, 1.3F)
                .span(8, 51, (m, tg, t) -> novaCharge(m, t))
                .at(52, (m, tg, t) -> novaBlast(m))
                .build();

        // ---- flight
        takeoff = TimelineAttack.builder("takeoff", 32).range(0, 999).angle(180).weight(0).cooldown(0).anywhere()
                .wind(8, 8).flying(12, true)
                .span(12, 31, (m, t, i) -> m.setDeltaMovement(m.getDeltaMovement().x * 0.7, 0.26, m.getDeltaMovement().z * 0.7))
                .sound(8, HCSounds.WING_FLAP, 3.0F, 0.7F).sound(18, HCSounds.WING_FLAP, 3.0F, 0.7F).sound(26, HCSounds.WING_FLAP, 3.0F, 0.75F)
                .build();
        land = TimelineAttack.builder("land", 24).range(0, 999).angle(180).weight(0).cooldown(0).airOnly()
                .span(0, 20, (m, t, i) -> m.setDeltaMovement(m.getDeltaMovement().x * 0.8, m.onGround() ? 0 : -0.34, m.getDeltaMovement().z * 0.8))
                .wind(4, 8).sound(4, HCSounds.WING_FLAP, 3.0F, 0.7F)
                .flying(20, false).tremor(21, 7)
                .build();
        MonsterAttack flyOrb = TimelineAttack.builder("fly_orb", 36).range(0, 32).angle(60).weight(10).cooldown(50).airOnly().notWhenTired()
                .track(0, 14, 7)
                .span(4, 15, (m, tg, t) -> beamCharge(m, t))
                .projectile(16, 0, 3.4, 3.6, airOrb)
                .sound(16, HCSounds.DRAGON_BEAM, 2.5F, 1.4F)
                .span(0, 35, (m, t, i) -> m.setDeltaMovement(m.getDeltaMovement().scale(0.6)))
                .build();
        MonsterAttack dive = TimelineAttack.builder("dive", 40).range(5, 30).angle(45).weight(9).cooldown(160).airOnly()
                .track(0, 12, 8)
                .sound(10, LargeMonstersB.AZRAGOS_ROAR, 2.5F, 1.2F)
                .span(14, 30, (m, target, t) -> {
                    if (target != null) {
                        Vec3 d = target.position().subtract(m.position());
                        double len = Math.max(0.1, d.length());
                        m.setDeltaMovement(d.scale(Math.min(1.1, len * 0.25) / len));
                    }
                })
                .hitSpan(14, 32, 0, 1.0, 2.0, 2.8F, 20, Element.DRAGON, 2.8F)
                .flying(33, false).tremor(34, 7)
                .span(34, 34, LBUtil.sphere(0, 0.5, 1.0, 3.6F, 14, Element.DRAGON, 2.0F, MonsterDamage.FLAG_TRIP))
                .at(34, (m, tg, t) -> {
                    LBUtil.impact(m, m.position(), 3.0F);
                    m.burstParticles(ParticleTypes.DRAGON_BREATH, m.position().add(0, 0.5, 0), 60, 2.0);
                })
                .build();
        MonsterAttack meteors = TimelineAttack.builder("meteor_rain", 70).range(0, 40).angle(180).weight(8).cooldown(300).airOnly()
                .span(0, 12, (m, t, i) -> m.setDeltaMovement(m.getDeltaMovement().x * 0.7, 0.14, m.getDeltaMovement().z * 0.7))
                .span(13, 69, (m, t, i) -> m.setDeltaMovement(m.getDeltaMovement().scale(0.5)))
                .sound(10, LargeMonstersB.AZRAGOS_ROAR, 3.0F, 0.8F)
                .at(12, (m, tg, t) -> {
                    m.burstParticles(ParticleTypes.DRAGON_BREATH, m.position().add(0, 2.5, 0), 80, 2.5);
                    m.burstParticles(ParticleTypes.END_ROD, m.position().add(0, 3.0, 0), 40, 2.0);
                })
                .span(16, 52, (m, tg, t) -> {
                    if ((t - 16) % 4 == 0) meteor(m, tg, (t - 16) / 4);
                })
                .build();
        return List.of(bite, claws, tailSweep, beam, volley, gust, supernova, takeoff, land, flyOrb, dive, meteors);
    }

    // ------------------------------------------------------------------ attack effects
    private static void clearHits(@Nullable Object m) {
        if (m instanceof AzragosEntity a) a.hitThisAction.clear();
    }

    /** Glowing energy gathering in the jaws. */
    private static void beamCharge(Object o, int t) {
        if (!(o instanceof AzragosEntity m) || !(m.level() instanceof ServerLevel sl)) return;
        Vec3 mouth = m.localPos(0, MOUTH_UP, MOUTH_FWD);
        LBUtil.converge(sl, mouth, 1.8 * m.getMonsterScale(), ParticleTypes.DRAGON_BREATH, 3, m.getRandom(), 0.09);
        if (t % 2 == 0) LBUtil.converge(sl, mouth, 2.4 * m.getMonsterScale(), ParticleTypes.END_ROD, 1, m.getRandom(), 0.12);
    }

    /** One tick of the sweeping beam. step = ticks since the beam started; sweepDeg relative to facing (+ = right). */
    private static void beamTick(Object o, @Nullable LivingEntity target, int step, float sweepDeg) {
        if (!(o instanceof AzragosEntity m) || !(m.level() instanceof ServerLevel sl)) return;
        float s = m.getMonsterScale();
        float yaw = (m.getYRot() + sweepDeg) * Mth.DEG_TO_RAD;
        Vec3 flat = new Vec3(-Mth.sin(yaw), 0, Mth.cos(yaw));
        Vec3 origin = m.localPos(0, BEAM_PIVOT_UP, BEAM_PIVOT_FWD).add(flat.scale(BEAM_REACH * s));
        double len = 17.0 * s;
        double aimDist = len * 0.75;
        double aimY = m.getY() + 0.4;
        if (target != null) {
            double dx = target.getX() - origin.x, dz = target.getZ() - origin.z;
            aimDist = Mth.clamp(Math.sqrt(dx * dx + dz * dz), 4.0, len);
            aimY = target.getY() + target.getBbHeight() * 0.4;
        }
        Vec3 dir = flat.scale(aimDist).add(0, aimY - origin.y, 0).normalize();
        Vec3 end = origin.add(dir.scale(len));
        BlockHitResult bhr = m.level().clip(new ClipContext(origin, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, m));
        if (bhr.getType() == HitResult.Type.BLOCK) end = bhr.getLocation();
        double dist = end.distanceTo(origin);
        int k = 0;
        for (double d = 0; d < dist; d += 0.7, k++) {
            Vec3 p = origin.add(dir.scale(d));
            sl.sendParticles(ParticleTypes.DRAGON_BREATH, p.x, p.y, p.z, 1, 0.1, 0.1, 0.1, 0.004);
            if ((k + step) % 3 == 0) sl.sendParticles(ParticleTypes.END_ROD, p.x, p.y, p.z, 1, 0.04, 0.04, 0.04, 0.01);
        }
        sl.sendParticles(ParticleTypes.DRAGON_BREATH, end.x, end.y + 0.2, end.z, 8, 0.6, 0.2, 0.6, 0.03);
        if (step % 4 == 0) sl.sendParticles(ParticleTypes.EXPLOSION, end.x, end.y + 0.3, end.z, 1, 0.2, 0.1, 0.2, 0);
        if (step % 3 == 0) {
            m.hitThisAction.clear(); // the beam hits repeatedly
            AABB box = new AABB(origin, end).inflate(1.8 * s);
            for (LivingEntity e : m.level().getEntitiesOfClass(LivingEntity.class, box, m::isValidHitTarget)) {
                Vec3 c = e.position().add(0, e.getBbHeight() * 0.5, 0);
                double along = Mth.clamp(c.subtract(origin).dot(dir), 0.0, dist);
                Vec3 closest = origin.add(dir.scale(along));
                if (closest.distanceTo(c) <= 1.3 * s + e.getBbWidth() * 0.5) m.hitEntity(e, 7, Element.DRAGON, 0.4F, 0);
            }
        }
    }

    /** Supernova charge: energy streams into the chest, the pillar ring is telegraphed on the ground. */
    private static void novaCharge(Object o, int t) {
        if (!(o instanceof AzragosEntity m) || !(m.level() instanceof ServerLevel sl)) return;
        float s = m.getMonsterScale();
        double k = Mth.clamp((t - 8) / 43.0, 0.0, 1.0);
        RandomSource r = m.getRandom();
        Vec3 core = m.localPos(0, CORE_UP, CORE_FWD);
        LBUtil.converge(sl, core, 7.0 * s * (1.0 - 0.35 * k), ParticleTypes.END_ROD, 2 + (int) (k * 5), r, 0.32);
        LBUtil.converge(sl, core, 4.5 * s, ParticleTypes.DRAGON_BREATH, 4, r, 0.1);
        if (t % 6 == 0) {
            Vec3 g = LBUtil.ground(sl, m.position());
            LBUtil.ring(sl, g, 10.5 * s, ParticleTypes.DRAGON_BREATH, 40);
            LBUtil.ring(sl, g, 8.5 * s, ParticleTypes.WITCH, 28);
        }
        if (t % 10 == 0) CameraShake.shake(sl, m.position(), 32, 0.2F + (float) k * 0.5F, 10);
    }

    private static void novaBlast(Object o) {
        if (!(o instanceof AzragosEntity m) || !(m.level() instanceof ServerLevel sl)) return;
        float s = m.getMonsterScale();
        Vec3 c = m.localPos(0, CORE_UP, CORE_FWD);
        m.hitSphere(m.position().add(0, 1.5 * s, 0), 8.5 * s, 36, Element.DRAGON, 3.5F, MonsterDamage.FLAG_LAUNCH);
        sl.sendParticles(ParticleTypes.FLASH, c.x, c.y, c.z, 1, 0, 0, 0, 0);
        sl.sendParticles(ParticleTypes.EXPLOSION_EMITTER, c.x, c.y, c.z, 3, 2.0, 0.6, 2.0, 0);
        sl.sendParticles(ParticleTypes.DRAGON_BREATH, c.x, c.y, c.z, 260, 4.5 * s, 1.5, 4.5 * s, 0.08);
        sl.sendParticles(ParticleTypes.END_ROD, c.x, c.y, c.z, 120, 1.0, 1.0, 1.0, 0.6);
        m.playSound(LargeMonstersB.AZRAGOS_NOVA.get(), 4.0F, 0.8F);
        m.playSound(HCSounds.EXPLOSION_SMALL.get(), 3.0F, 0.5F);
        CameraShake.shake(sl, m.position(), 48, 1.3F, 30);
        for (int i = 0; i < 12; i++) {
            double a = i * Math.PI * 2 / 12;
            LBUtil.hazard(m, HazardEntity.Kind.DRAGON_PILLAR, Math.sin(a) * 10.5, Math.cos(a) * 10.5, 1.6F, 4 + (i % 3) * 4, 50, 14, Element.DRAGON);
        }
        for (int i = 0; i < 6; i++) {
            double a = i * Math.PI * 2 / 6 + 0.5;
            LBUtil.hazard(m, HazardEntity.Kind.DRAGON_PILLAR, Math.sin(a) * 5.5, Math.cos(a) * 5.5, 1.5F, 2, 30, 14, Element.DRAGON);
        }
    }

    /** Meteor rain: the first one on the target, the rest scattered around it. */
    private static void meteor(Object o, @Nullable LivingEntity target, int index) {
        if (!(o instanceof AzragosEntity m)) return;
        Vec3 c = target != null ? target.position() : m.localPos(0, 0, 8);
        Vec3 p = c;
        if (index > 0) {
            double a = m.getRandom().nextDouble() * Math.PI * 2, r = 2.0 + m.getRandom().nextDouble() * 6.0;
            p = c.add(Math.cos(a) * r, 0, Math.sin(a) * r);
        }
        LBUtil.hazardAt(m, HazardEntity.Kind.METEOR, p, 2.2F, 26, 2, 22, Element.DRAGON);
    }

    // ------------------------------------------------------------------ flight behaviour
    @Override
    public boolean brainTick(LivingEntity target) {
        if (!isFlying()) {
            if (flightCooldown > 0) {
                flightCooldown--;
                return false;
            }
            int chance = isEnraged() ? 80 : 160;
            if (getHealth() < getMaxHealth() * 0.5F) chance -= 30;
            if (getRandom().nextInt(Math.max(10, chance)) == 0 && !isTired()) {
                startAttack(takeoff, target);
                return true;
            }
        } else {
            airTime++;
            if (airTime > 320 || airAttacks >= 3 || isTired()) {
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
            flightCooldown = 420 + getRandom().nextInt(300);
        }
    }

    @Override
    protected void customServerTick() {
        if (isFlying() && isLocked()) setFlying(false);
        if (isEnraged() && tickCount % 5 == 0) {
            RandomSource r = getRandom();
            Vec3 p = position().add((r.nextDouble() - 0.5) * getBbWidth() * 1.4, r.nextDouble() * getBbHeight(), (r.nextDouble() - 0.5) * getBbWidth() * 1.4);
            burstParticles(ParticleTypes.DRAGON_BREATH, p, 2, 0.3);
            if (!isLocked()) burstParticles(ParticleTypes.DRAGON_BREATH, mouthPos(), 2, 0.12);
        }
    }
}
