package com.huntcraft.monster.large.a;

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
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 氷牙獣フロストガルム - huge sabre-toothed fanged beast (ice).
 * Claw swipes (paw on the hunter's side), lunge pounce, straight frost breath, fan of ice shards, belly slide,
 * tail slam that sends ice spikes racing forward; enraged: double pounce and a freezing field around itself.
 */
public class FrostgarmEntity extends LargeMonster {
    private static final Hitzone HZ_HEAD = Hitzone.of(65, 70, 55, 30, 5, 15, 0, 10);
    private static final Hitzone HZ_NECK = Hitzone.of(45, 45, 40, 20, 5, 10, 0, 5);
    private static final Hitzone HZ_BODY = Hitzone.of(35, 35, 30, 15, 5, 10, 0, 5);
    private static final Hitzone HZ_BACK = Hitzone.of(30, 40, 30, 25, 0, 5, 0, 10);
    private static final Hitzone HZ_FORELEG = Hitzone.of(45, 40, 40, 20, 5, 10, 0, 5);
    private static final Hitzone HZ_HINDLEG = Hitzone.of(35, 35, 30, 15, 5, 10, 0, 5);
    private static final Hitzone HZ_TAIL = Hitzone.of(50, 30, 45, 25, 5, 10, 0, 5);

    public static final MonsterProfile PROFILE = MonsterProfile.builder("frostgarm", true)
            .category("fanged_beast")
            .health(650, 1024).highRank(1.6F, 0.7F)
            .scale(0.9F, 1.22F)
            .element(Element.ICE)
            .weakness(Element.FIRE, 3).weakness(Element.THUNDER, 1)
            .part(new PartDef("head", 0, 2.2F, 4.2F, 1.8F, 1.9F, HZ_HEAD).breakable(150, "fang_tip_l", "fang_tip_r").ko())
            .part(new PartDef("neck", 0, 2.3F, 2.7F, 1.6F, 1.7F, HZ_NECK))
            .part(PartDef.body(HZ_BODY))
            .part(new PartDef("back", 0, 3.3F, 0.5F, 2.4F, 1.3F, HZ_BACK).breakable(160, "csh1_l", "csh2_l", "csh1_r", "csh2_r", "cbk1", "cbk2"))
            .part(new PartDef("foreleg_l", -0.75F, 0, 1.2F, 0.95F, 2.0F, HZ_FORELEG).breakable(120, "bracer_l"))
            .part(new PartDef("foreleg_r", 0.75F, 0, 1.2F, 0.95F, 2.0F, HZ_FORELEG).breakable(120, "bracer_r"))
            .part(new PartDef("hindleg_l", -0.7F, 0, -1.65F, 0.95F, 2.1F, HZ_HINDLEG))
            .part(new PartDef("hindleg_r", 0.7F, 0, -1.65F, 0.95F, 2.1F, HZ_HINDLEG))
            .part(new PartDef("tail", 0, 0.9F, -4.4F, 1.7F, 1.8F, HZ_TAIL).severable(180, "tail3", "tail4", "tail5", "tail_tip"))
            .carves(new RewardTable()
                    .low(HCItems.FROSTGARM.scale, 30, 1, 2).low(HCItems.FROSTGARM.shell, 25).low(HCItems.FROSTGARM.special, 12)
                    .low(HCItems.FROSTGARM.claw, 15).low(HCItems.MONSTER_BONE_M, 8).low(HCItems.FROST_SAC, 8).low(HCItems.FROSTGARM.gem, 2)
                    .high(HCItems.FROSTGARM.scalePlus, 28, 1, 2).high(HCItems.FROSTGARM.shellPlus, 22).high(HCItems.FROSTGARM.specialPlus, 12)
                    .high(HCItems.FROSTGARM.clawPlus, 14).high(HCItems.MONSTER_BONE_L, 6).high(HCItems.FROST_SAC, 10).high(HCItems.FROSTGARM.gemPlus, 2))
            .tailCarves(new RewardTable()
                    .low(HCItems.FROSTGARM.tail, 60).low(HCItems.FROSTGARM.scale, 25).low(HCItems.FROSTGARM.gem, 3)
                    .high(HCItems.FROSTGARM.tail, 55).high(HCItems.FROSTGARM.scalePlus, 30).high(HCItems.FROSTGARM.gemPlus, 3))
            .captureRewards(new RewardTable()
                    .low(HCItems.FROSTGARM.scale, 30).low(HCItems.FROSTGARM.shell, 25).low(HCItems.FROSTGARM.special, 18)
                    .low(HCItems.FROSTGARM.claw, 15).low(HCItems.FROST_SAC, 8).low(HCItems.FROSTGARM.gem, 4)
                    .high(HCItems.FROSTGARM.scalePlus, 30).high(HCItems.FROSTGARM.shellPlus, 25).high(HCItems.FROSTGARM.specialPlus, 18)
                    .high(HCItems.FROSTGARM.clawPlus, 15).high(HCItems.FROST_SAC, 8).high(HCItems.FROSTGARM.gemPlus, 4))
            .breakReward("head", new RewardTable().low(HCItems.FROSTGARM.special, 70).low(HCItems.FROSTGARM.shell, 25).low(HCItems.FROSTGARM.gem, 5)
                    .high(HCItems.FROSTGARM.specialPlus, 70).high(HCItems.FROSTGARM.shellPlus, 25).high(HCItems.FROSTGARM.gemPlus, 5))
            .breakReward("back", new RewardTable().low(HCItems.FROST_SAC, 45).low(HCItems.FROSTGARM.shell, 45).low(HCItems.FROSTGARM.gem, 10)
                    .high(HCItems.FROST_SAC, 45).high(HCItems.FROSTGARM.shellPlus, 45).high(HCItems.FROSTGARM.gemPlus, 10))
            .breakReward("foreleg_l", new RewardTable().low(HCItems.FROSTGARM.claw, 80).low(HCItems.FROSTGARM.scale, 20)
                    .high(HCItems.FROSTGARM.clawPlus, 80).high(HCItems.FROSTGARM.scalePlus, 20))
            .breakReward("foreleg_r", new RewardTable().low(HCItems.FROSTGARM.claw, 80).low(HCItems.FROSTGARM.scale, 20)
                    .high(HCItems.FROSTGARM.clawPlus, 80).high(HCItems.FROSTGARM.scalePlus, 20))
            .questReward(2200, 85)
            .build();

    public static final String[] ANIMS = {
            "roar", "flinch", "claw_l", "claw_r", "pounce", "ice_breath", "ice_shards", "belly_slide", "tail_slam",
            "double_pounce", "ice_field"
    };

    /** Horizontal speed of the current leap (blocks/tick). */
    private double leapSpeed;
    /** Ground positions of the ice spikes of the current tail slam (for the eruption particles). */
    private Vec3[] spikePos;

    public FrostgarmEntity(EntityType<? extends FrostgarmEntity> type, Level level) {
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
        return LargeMonstersA.FROSTGARM_ROAR.get();
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return LargeMonstersA.FROSTGARM_GROWL.get();
    }

    @Override
    public float getTurnSpeed() {
        return 7.0F;
    }

    @Override
    public double getRunSpeedModifier() {
        return 1.6;
    }

    @Override
    public int getAttackGap() {
        return Math.max(6, super.getAttackGap() - 3);
    }

    @Override
    protected List<MonsterAttack> createAttacks() {
        ProjectileSpec shard = ProjectileSpec.of(MonsterProjectile.Kind.ICE_SHARD, 9, Element.ICE).count(5, 13).speed(1.25F).size(0.85F).lifetime(40);

        MonsterAttack clawL = TimelineAttack.builder("claw_l", 28).range(0, 5.5F).angle(60).weight(13).cooldown(26)
                .track(0, 8, 8).move(9, 12, 0.22, 0)
                .sweep(10, 13, 0.5F, 5.6F, -80, 30, 3.5F, 12, Element.NONE, 1.4F)
                .sound(9, HCSounds.TAIL_SWING, 1.6F, 1.3F)
                .sound(4, LargeMonstersA.FROSTGARM_GROWL, 1.5F, 1.1F)
                .build();
        MonsterAttack clawR = TimelineAttack.builder("claw_r", 28).range(0, 5.5F).angle(60).weight(13).cooldown(26)
                .track(0, 8, 8).move(9, 12, 0.22, 0)
                .sweep(10, 13, 0.5F, 5.6F, 80, -30, 3.5F, 12, Element.NONE, 1.4F)
                .sound(9, HCSounds.TAIL_SWING, 1.6F, 1.3F)
                .sound(4, LargeMonstersA.FROSTGARM_GROWL, 1.5F, 1.1F)
                .build();
        MonsterAttack pounce = TimelineAttack.builder("pounce", 44).range(5, 17).angle(30).weight(11).cooldown(90)
                .track(0, 13, 7)
                .sound(12, LargeMonstersA.FROSTGARM_GROWL, 2.0F, 0.8F)
                .at(15, (m, t, i) -> leap(t, 0.62, 1.0))
                .span(16, 29, (m, t, i) -> glide(i - 15))
                .span(16, 31, (m, t, i) -> m.hitSphere(m.localPos(0, 1.4, 2.4), 2.4F * m.getMonsterScale(), 17, Element.NONE, 2.2F, MonsterDamage.FLAG_TRIP))
                .at(30, (m, t, i) -> land(13))
                .build();
        MonsterAttack breath = TimelineAttack.builder("ice_breath", 64).range(2, 12).angle(30).weight(9).cooldown(170).notWhenTired()
                .track(0, 16, 5)
                .particlesSpan(6, 20, ParticleTypes.SNOWFLAKE, 0, 2.7, 4.3, 2, 0.15)
                .sound(4, LargeMonstersA.FROSTGARM_CRYSTAL, 1.5F, 0.7F)
                .span(22, 46, (m, t, i) -> frostBreath(i - 22))
                .sound(22, HCSounds.ICE_BREATH, 3.0F, 0.6F).sound(30, HCSounds.ICE_BREATH, 3.0F, 0.65F).sound(38, HCSounds.ICE_BREATH, 3.0F, 0.6F)
                .build();
        MonsterAttack shards = TimelineAttack.builder("ice_shards", 36).range(4, 22).angle(35).weight(10).cooldown(80).notWhenTired()
                .track(0, 12, 6)
                .particlesSpan(4, 14, ParticleTypes.SNOWFLAKE, 0, 2.6, 4.2, 2, 0.2)
                .sound(8, LargeMonstersA.FROSTGARM_CRYSTAL, 1.5F, 1.2F)
                .projectile(15, 0, 2.0, 4.4, shard)
                .at(15, (m, t, i) -> LargeAUtil.blockBurst(m, m.localPos(0, 2.0, 4.4), Blocks.PACKED_ICE.defaultBlockState(), 20, 0.4))
                .sound(15, HCSounds.ICE_BREATH, 2.5F, 1.3F)
                .build();
        MonsterAttack slide = TimelineAttack.builder("belly_slide", 56).range(6, 20).angle(25).weight(9).cooldown(140)
                .track(0, 11, 6).track(13, 28, 1.5F)
                .sound(11, HCSounds.STOMP, 2.0F, 0.7F)
                .move(13, 34, 0.62, 0).move(35, 40, 0.34, 0)
                .hitSpan(13, 40, 0, 0.8, 2.2, 2.6F, 15, Element.NONE, 2.4F)
                .span(13, 40, (m, t, i) -> slideSpray())
                .sound(14, HCSounds.ICE_BREATH, 1.6F, 0.5F).sound(24, HCSounds.ICE_BREATH, 1.6F, 0.5F).sound(34, HCSounds.ICE_BREATH, 1.6F, 0.5F)
                .build();
        MonsterAttack tailSlam = TimelineAttack.builder("tail_slam", 46).range(3, 14).angle(30).weight(8).cooldown(120)
                .track(0, 10, 5)
                .sound(12, HCSounds.TAIL_SWING, 2.5F, 0.6F)
                .hit(17, 0, 0.5, -4.4, 2.6F, 14, Element.ICE, 1.8F)
                .tremor(17, 5)
                .at(17, (m, t, i) -> spawnSpikeLine())
                .at(24, (m, t, i) -> spikeBurst(0)).at(29, (m, t, i) -> spikeBurst(1))
                .at(34, (m, t, i) -> spikeBurst(2)).at(39, (m, t, i) -> spikeBurst(3))
                .build();
        MonsterAttack doublePounce = TimelineAttack.builder("double_pounce", 76).range(4, 18).angle(40).weight(12).cooldown(160).enragedOnly()
                .track(0, 10, 9)
                .sound(9, LargeMonstersA.FROSTGARM_GROWL, 2.0F, 0.75F)
                .at(12, (m, t, i) -> leap(t, 0.6, 1.0))
                .span(13, 26, (m, t, i) -> glide(i - 12))
                .span(13, 28, (m, t, i) -> m.hitSphere(m.localPos(0, 1.4, 2.4), 2.4F * m.getMonsterScale(), 16, Element.NONE, 2.0F, MonsterDamage.FLAG_TRIP))
                .at(27, (m, t, i) -> land(11))
                .at(30, (m, t, i) -> hitThisAction.clear())
                .track(28, 36, 14)
                .sound(35, LargeMonstersA.FROSTGARM_ROAR, 1.6F, 1.3F)
                .at(38, (m, t, i) -> leap(t, 0.66, 1.1))
                .span(39, 52, (m, t, i) -> glide(i - 38))
                .span(39, 54, (m, t, i) -> m.hitSphere(m.localPos(0, 1.4, 2.4), 2.5F * m.getMonsterScale(), 18, Element.ICE, 2.4F, MonsterDamage.FLAG_TRIP))
                .at(53, (m, t, i) -> land(14))
                .build();
        MonsterAttack iceField = TimelineAttack.builder("ice_field", 60).range(0, 9).angle(180).weight(10).cooldown(300).enragedOnly().notWhenTired()
                .sound(6, LargeMonstersA.FROSTGARM_HOWL, 3.5F, 1.0F)
                .particlesSpan(8, 20, ParticleTypes.SNOWFLAKE, 0, 3.6, 1.2, 4, 1.2)
                .at(22, (m, t, i) -> freezeGround())
                .tremor(22, 6)
                .build();

        return List.of(
                new LargeAUtil.Weighted(clawL, LargeAUtil.side(-1)),
                new LargeAUtil.Weighted(clawR, LargeAUtil.side(1)),
                shards, pounce, breath, slide, tailSlam, doublePounce, iceField);
    }

    // ------------------------------------------------------------------ attack helpers
    /** Starts a leap toward the target: vertical speed vy, horizontal speed chosen to land on the target. */
    private void leap(@Nullable LivingEntity target, double vy, double maxSpeed) {
        double speed = 0.55 * Math.sqrt(getMonsterScale());
        if (target != null) {
            double dx = target.getX() - getX(), dz = target.getZ() - getZ();
            double dist = Math.sqrt(dx * dx + dz * dz) - getBbWidth() * 0.4;
            double air = 2.0 * vy / 0.08;
            speed = Mth.clamp(dist / air, 0.25, maxSpeed);
        }
        leapSpeed = speed;
        float yaw = getYRot() * Mth.DEG_TO_RAD;
        setDeltaMovement(-Mth.sin(yaw) * speed, vy, Mth.cos(yaw) * speed);
        hasImpulse = true;
        playSound(HCSounds.STOMP.get(), 2.0F, 0.9F);
        burstParticles(ParticleTypes.SNOWFLAKE, position().add(0, 0.3, 0), 20, 1.2);
    }

    /** Keeps the leap's horizontal momentum while airborne. */
    private void glide(int sinceLaunch) {
        if (sinceLaunch > 3 && onGround()) return;
        float yaw = getYRot() * Mth.DEG_TO_RAD;
        Vec3 v = getDeltaMovement();
        setDeltaMovement(-Mth.sin(yaw) * leapSpeed, v.y, Mth.cos(yaw) * leapSpeed);
        hasImpulse = true;
    }

    /** Landing impact of a pounce. */
    private void land(float damage) {
        hitSphere(localPos(0, 0.8, 2.6), 2.6F * getMonsterScale(), damage, Element.NONE, 1.8F, MonsterDamage.FLAG_TRIP);
        playSound(HCSounds.STOMP.get(), 2.5F, 0.7F);
        Vec3 p = localPos(0, 0.2, 2.6);
        burstParticles(ParticleTypes.SNOWFLAKE, p, 30, 1.4);
        LargeAUtil.blockBurst(this, p, Blocks.SNOW_BLOCK.defaultBlockState(), 30, 1.2);
        setDeltaMovement(getDeltaMovement().multiply(0.2, 1.0, 0.2));
    }

    /** One tick of the straight frost breath (fixed direction along the facing, slightly downward). */
    private void frostBreath(int step) {
        if (!(level() instanceof ServerLevel sl)) return;
        float s = getMonsterScale();
        Vec3 origin = localPos(0, 2.1, 4.6);
        float yaw = getYRot() * Mth.DEG_TO_RAD;
        Vec3 dir = new Vec3(-Mth.sin(yaw), -0.22, Mth.cos(yaw)).normalize();
        double length = 11.0 * s;
        double half = Math.toRadians(16);
        RandomSource r = getRandom();
        for (int k = 0; k < 7; k++) {
            Vec3 v = dir.add((r.nextDouble() - 0.5) * 0.36, (r.nextDouble() - 0.5) * 0.2, (r.nextDouble() - 0.5) * 0.36).normalize()
                    .scale(0.55 + r.nextDouble() * 0.45);
            sl.sendParticles(k % 3 == 0 ? ParticleTypes.SNOWFLAKE : ParticleTypes.CLOUD, origin.x, origin.y, origin.z, 0, v.x, v.y, v.z, 1.0);
        }
        for (int k = 0; k < 4; k++) {
            double d = r.nextDouble() * length;
            double spread = Math.tan(half) * d;
            Vec3 p = origin.add(dir.scale(d)).add((r.nextDouble() - 0.5) * spread, (r.nextDouble() - 0.5) * spread * 0.5, (r.nextDouble() - 0.5) * spread);
            sl.sendParticles(ParticleTypes.SNOWFLAKE, p.x, p.y, p.z, 2, 0.2, 0.2, 0.2, 0.01);
        }
        if (step % 3 == 0) {
            hitThisAction.clear();
            double cos = Math.cos(half);
            AABB box = new AABB(origin, origin).inflate(length);
            for (LivingEntity e : level().getEntitiesOfClass(LivingEntity.class, box, this::isValidHitTarget)) {
                Vec3 to = e.position().add(0, e.getBbHeight() * 0.5, 0).subtract(origin);
                double dist = to.length();
                if (dist > length + e.getBbWidth()) continue;
                if (dist < 1.8 || to.normalize().dot(dir) >= cos - 0.04) hitEntity(e, 4.5F, Element.ICE, 0.25F, 0);
            }
        }
    }

    /** Snow spray thrown up while belly sliding. */
    private void slideSpray() {
        if (!(level() instanceof ServerLevel sl)) return;
        Vec3 front = localPos(0, 0.2, 2.6);
        sl.sendParticles(ParticleTypes.SNOWFLAKE, front.x, front.y, front.z, 6, 0.9, 0.2, 0.9, 0.05);
        sl.sendParticles(ParticleTypes.ITEM_SNOWBALL, front.x, front.y, front.z, 4, 0.8, 0.2, 0.8, 0.1);
        Vec3 side = localPos(0, 0.1, -0.5);
        sl.sendParticles(ParticleTypes.CLOUD, side.x, side.y, side.z, 2, 1.0, 0.1, 1.0, 0.02);
    }

    /** Tail slam: a line of ice spikes erupts in front of the beast, racing away from it. */
    private void spawnSpikeLine() {
        float s = getMonsterScale();
        spikePos = new Vec3[4];
        for (int k = 0; k < 4; k++) {
            Vec3 p = localPos(0, 0, 3.6 + k * 2.6);
            p = LargeAUtil.ground(level(), p.x, getY(), p.z);
            spikePos[k] = p;
            HazardEntity.spawn(this, p, HazardEntity.Kind.ICE_SPIKES, (1.6F + 0.12F * k) * s, 7 + k * 5, 6, 14 * damageMultiplier(), Element.ICE);
        }
        burstParticles(ParticleTypes.SNOWFLAKE, localPos(0, 0.3, -4.4), 30, 1.2);
        LargeAUtil.blockBurst(this, localPos(0, 0.3, -4.4), Blocks.PACKED_ICE.defaultBlockState(), 25, 1.0);
        playSound(LargeMonstersA.FROSTGARM_CRYSTAL.get(), 2.5F, 0.6F);
    }

    private void spikeBurst(int k) {
        if (spikePos == null || k >= spikePos.length || spikePos[k] == null) return;
        Vec3 p = spikePos[k].add(0, 0.6, 0);
        LargeAUtil.blockBurst(this, p, Blocks.PACKED_ICE.defaultBlockState(), 40, 0.8 + 0.1 * k);
        LargeAUtil.blockBurst(this, p, Blocks.BLUE_ICE.defaultBlockState(), 15, 0.6);
        if (level() instanceof ServerLevel sl) {
            CameraShake.shake(sl, p, 10, 0.35F, 6);
        }
    }

    /** Enraged: slams down and freezes the ground around itself. */
    private void freezeGround() {
        float s = getMonsterScale();
        Vec3 c = LargeAUtil.ground(level(), getX(), getY(), getZ());
        HazardEntity.spawn(this, c, HazardEntity.Kind.ICE_FIELD, 6.5F * s, 4, 120, 3.5F * damageMultiplier(), Element.ICE);
        hitSphere(localPos(0, 1.0, 1.5), 4.6F * s, 12, Element.ICE, 2.0F, 0);
        if (level() instanceof ServerLevel sl) {
            sl.sendParticles(ParticleTypes.SNOWFLAKE, c.x, c.y + 0.5, c.z, 120, 3.5 * s, 0.6, 3.5 * s, 0.05);
            sl.sendParticles(ParticleTypes.CLOUD, c.x, c.y + 0.3, c.z, 40, 3.0 * s, 0.2, 3.0 * s, 0.08);
        }
        LargeAUtil.blockBurst(this, localPos(0, 0.3, 2.5), Blocks.PACKED_ICE.defaultBlockState(), 50, 1.6);
        playSound(LargeMonstersA.FROSTGARM_CRYSTAL.get(), 3.0F, 0.5F);
        playSound(HCSounds.ICE_BREATH.get(), 3.0F, 0.5F);
    }

    // ------------------------------------------------------------------ ambience
    @Override
    protected void clientTick() {
        super.clientTick();
        if (isCarcass() || tickCount % (isEnraged() ? 3 : 12) != 0) return;
        RandomSource r = getRandom();
        // frost drifting off the shoulder crystals (heavier while enraged)
        double side = r.nextBoolean() ? 0.75 : -0.75;
        Vec3 p = localPos(side, 3.9, 1.2);
        level().addParticle(ParticleTypes.SNOWFLAKE, p.x + (r.nextDouble() - 0.5) * 0.6, p.y, p.z + (r.nextDouble() - 0.5) * 0.6,
                (r.nextDouble() - 0.5) * 0.02, 0.02, (r.nextDouble() - 0.5) * 0.02);
        if (isEnraged()) {
            Vec3 m = mouthPos();
            level().addParticle(ParticleTypes.SNOWFLAKE, m.x, m.y - 0.3, m.z, (r.nextDouble() - 0.5) * 0.05, -0.02, (r.nextDouble() - 0.5) * 0.05);
        }
    }
}
