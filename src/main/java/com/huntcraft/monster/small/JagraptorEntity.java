package com.huntcraft.monster.small;

import com.huntcraft.core.Element;
import com.huntcraft.entity.monster.HuntMonster;
import com.huntcraft.entity.monster.Hitzone;
import com.huntcraft.entity.monster.MonsterProfile;
import com.huntcraft.entity.monster.MonsterRegistry;
import com.huntcraft.entity.monster.PartDef;
import com.huntcraft.entity.monster.RewardTable;
import com.huntcraft.entity.monster.attack.MonsterAttack;
import com.huntcraft.entity.monster.attack.TimelineAttack;
import com.huntcraft.registry.HCItems;
import com.huntcraft.registry.HCSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * ジャグラプトル - blue bipedal raptor with orange stripes and a crest. Pack hunter: bites, leaping kicks and
 * a yipping call that rallies nearby raptors (and, when alone, summons a couple of packmates).
 */
public class JagraptorEntity extends SmallHuntMonster {
    static final Hitzone HZ_HEAD = Hitzone.of(85, 85, 75, 25, 15, 20, 15, 10);
    static final Hitzone HZ_BODY = Hitzone.of(65, 60, 60, 20, 10, 15, 10, 5);
    static final Hitzone HZ_TAIL = Hitzone.of(60, 50, 55, 20, 10, 15, 10, 5);

    public static final MonsterProfile PROFILE = MonsterProfile.builder("jagraptor", false)
            .health(20, 34).highRank(1.35F, 0.85F)
            .scale(0.92F, 1.08F)
            .weakness(Element.FIRE, 3).weakness(Element.THUNDER, 2).weakness(Element.ICE, 1).weakness(Element.WATER, 1)
            .part(new PartDef("head", 0, 1.05F, 0.75F, 0.55F, 0.5F, HZ_HEAD).ko())
            .part(PartDef.body(HZ_BODY))
            .part(new PartDef("tail", 0, 0.7F, -1.0F, 0.45F, 0.35F, HZ_TAIL))
            .rageThreshold(99F).flinchThreshold(0.12F)
            .carves(new RewardTable()
                    .low(HCItems.JAGRAPTOR_HIDE, 40).low(HCItems.JAGRAPTOR_FANG, 20).low(HCItems.MONSTER_BONE_S, 20).low(HCItems.RAW_MEAT, 20)
                    .high(HCItems.JAGRAPTOR_HIDE, 36, 1, 2).high(HCItems.JAGRAPTOR_FANG, 26).high(HCItems.MONSTER_BONE_S, 20).high(HCItems.RAW_MEAT, 18))
            .carveCount(1)
            .questReward(300, 8)
            .build();

    public static final String[] ANIMS = {"flinch", "bite", "leap_kick", "pack_call", "look", "preen"};

    protected boolean calledPack;
    private int circleDir;
    private int circleTimer;

    public JagraptorEntity(EntityType<? extends JagraptorEntity> type, Level level) {
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
        return raptorAttacks(Element.NONE);
    }

    /** Attacks shared with the Blizraptor (bite element differs). */
    protected List<MonsterAttack> raptorAttacks(Element biteElement) {
        List<MonsterAttack> list = new ArrayList<>();
        list.add(TimelineAttack.builder("bite", 18).range(0, 2.2F).angle(45).weight(14).cooldown(14)
                .track(0, 6, 18).move(5, 8, 0.28, 0)
                .hit(8, 0, 0.95, 1.05, 0.85F, 4, biteElement, 0.5F)
                .sound(7, HCSounds.BITE, 1.0F, 1.5F)
                .at(14, (m, t, i) -> {
                    if (m.getRandom().nextFloat() < 0.35F) m.moveLocal(-0.35, 0.28); // hop back
                })
                .build());
        list.add(TimelineAttack.builder("leap_kick", 28).range(2.2F, 6.5F).angle(30).weight(9).cooldown(50)
                .track(0, 7, 14)
                .at(8, (m, t, i) -> m.moveLocal(0.52, 0.42))
                .hitSpan(10, 18, 0, 0.45, 0.6, 0.9F, 5, Element.NONE, 1.0F)
                .sound(8, () -> SoundEvents.GOAT_LONG_JUMP, 1.0F, 1.4F)
                .sound(9, () -> SoundEvents.FOX_AGGRO, 1.0F, 0.9F)
                .build());
        list.add(TimelineAttack.builder("pack_call", 30).range(1.5F, 18).angle(180).weight(5).cooldown(500)
                .track(0, 8, 12)
                .at(9, (m, target, i) -> {
                    if (m instanceof JagraptorEntity j) j.callPack(target);
                })
                .sound(9, () -> SoundEvents.FOX_SCREECH, 1.6F, 1.25F)
                .sound(18, () -> SoundEvents.FOX_SCREECH, 1.4F, 1.4F)
                .particles(9, ParticleTypes.NOTE, 0, 1.6, 0.7, 2, 0.2)
                .build());
        return list;
    }

    /** Alerts every raptor nearby; a lone raptor hunting a hunter summons up to two packmates. */
    public void callPack(@Nullable LivingEntity target) {
        if (!(target instanceof Player) || !(level() instanceof ServerLevel sl)) return;
        List<JagraptorEntity> pack = sl.getEntitiesOfClass(JagraptorEntity.class, getBoundingBox().inflate(22), e -> e != this && e.isAlive());
        for (JagraptorEntity e : pack) {
            if (e.getTarget() == null && !e.isLocked()) e.setTarget(target);
        }
        if (calledPack || pack.size() >= 2) return;
        calledPack = true;
        int want = 1 + getRandom().nextInt(2);
        for (int k = 0; k < 6 && want > 0; k++) {
            double ang = getRandom().nextDouble() * Math.PI * 2;
            double r = 9 + getRandom().nextDouble() * 4;
            int x = Mth.floor(getX() + Math.cos(ang) * r), z = Mth.floor(getZ() + Math.sin(ang) * r);
            int y = sl.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            if (Math.abs(y - getY()) > 6) continue;
            HuntMonster m = MonsterRegistry.spawn(sl, profile().id, new BlockPos(x, y, z), getRank(), getQuestId());
            if (m instanceof JagraptorEntity j) {
                j.calledPack = true;
                if (getHomePos() != null) j.setHomePos(getHomePos());
                j.setTarget(target);
                sl.sendParticles(ParticleTypes.POOF, j.getX(), j.getY() + 0.5, j.getZ(), 8, 0.3, 0.3, 0.3, 0.02);
                want--;
            }
        }
    }

    @Override
    public void combatMove(LivingEntity target, double dist, float angle, boolean waiting) {
        float s = getMonsterScale();
        if (waiting && dist < 3.0 * s) {
            // circle around the prey between attacks, like a pack hunter
            if (--circleTimer <= 0) {
                circleDir = getRandom().nextBoolean() ? 1 : -1;
                circleTimer = 20 + getRandom().nextInt(30);
                circleTarget(target, 3.2 * s, getWalkSpeedModifier(), circleDir);
            }
            if (Math.abs(angle) > 25) turnTowards(target.position(), getTurnSpeed() * 0.5F);
            return;
        }
        if (dist > 1.5 * s) {
            boolean run = dist > 4.5 * s && !waiting;
            if (this.tickCount % 4 == 0 || this.getNavigation().isDone()) {
                this.getNavigation().moveTo(target, run ? getRunSpeedModifier() : getWalkSpeedModifier() * (waiting ? 0.7 : 1.0));
            }
        } else {
            this.getNavigation().stop();
            if (Math.abs(angle) > 10) turnTowards(target.position(), getTurnSpeed());
        }
    }

    @Override
    public double getRunSpeedModifier() {
        return 1.35;
    }

    @Override
    public int getAttackGap() {
        return isTired() ? 30 : 16;
    }

    @Override
    protected IdleAction[] idleActions() {
        return new IdleAction[]{new IdleAction("look", 40, 3), new IdleAction("preen", 36, 2)};
    }

    @Override
    protected void onIdleAction(String anim) {
        if ("look".equals(anim)) playSound(SoundEvents.FOX_SNIFF, 0.8F, 1.1F);
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.FOX_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.FOX_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.FOX_DEATH;
    }

    @Override
    public float getVoicePitch() {
        return 0.8F + (this.getRandom().nextFloat() - 0.5F) * 0.15F;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean("HCCalledPack", calledPack);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        calledPack = tag.getBoolean("HCCalledPack");
    }
}
