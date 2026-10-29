package com.huntcraft.monster.small;

import com.huntcraft.core.Element;
import com.huntcraft.entity.misc.MonsterProjectile;
import com.huntcraft.entity.monster.Hitzone;
import com.huntcraft.entity.monster.MonsterProfile;
import com.huntcraft.entity.monster.PartDef;
import com.huntcraft.entity.monster.RewardTable;
import com.huntcraft.entity.monster.attack.MonsterAttack;
import com.huntcraft.entity.monster.attack.ProjectileSpec;
import com.huntcraft.entity.monster.attack.TimelineAttack;
import com.huntcraft.registry.HCItems;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * ブリザラプトル - snow-white raptor of the frozen peaks with a frosty crest. Ice bite and a small ice-shard spit.
 * Shares the Jagraptor's skeleton and pack behaviour (mixed packs answer each other's calls).
 */
public class BlizraptorEntity extends JagraptorEntity {
    static final Hitzone HZ_HEAD_B = Hitzone.of(80, 80, 70, 35, 10, 20, 0, 10);
    static final Hitzone HZ_BODY_B = Hitzone.of(60, 55, 55, 30, 5, 15, 0, 5);
    static final Hitzone HZ_TAIL_B = Hitzone.of(55, 50, 50, 30, 5, 15, 0, 5);

    public static final MonsterProfile PROFILE = MonsterProfile.builder("blizraptor", false)
            .health(26, 42).highRank(1.35F, 0.85F)
            .scale(0.92F, 1.08F)
            .element(Element.ICE)
            .weakness(Element.FIRE, 3).weakness(Element.THUNDER, 2).weakness(Element.DRAGON, 1)
            .part(new PartDef("head", 0, 1.05F, 0.75F, 0.55F, 0.55F, HZ_HEAD_B).ko())
            .part(PartDef.body(HZ_BODY_B))
            .part(new PartDef("tail", 0, 0.7F, -1.0F, 0.45F, 0.35F, HZ_TAIL_B))
            .rageThreshold(99F).flinchThreshold(0.12F)
            .carves(new RewardTable()
                    .low(HCItems.BLIZRAPTOR_HIDE, 45).low(HCItems.BLIZRAPTOR_CREST, 20).low(HCItems.MONSTER_BONE_S, 15).low(HCItems.RAW_MEAT, 12).low(HCItems.FROST_SAC, 8)
                    .high(HCItems.BLIZRAPTOR_HIDE, 40, 1, 2).high(HCItems.BLIZRAPTOR_CREST, 26).high(HCItems.MONSTER_BONE_S, 14).high(HCItems.RAW_MEAT, 10).high(HCItems.FROST_SAC, 10))
            .carveCount(1)
            .questReward(400, 10)
            .build();

    public static final String[] ANIMS_B = {"flinch", "bite", "leap_kick", "pack_call", "ice_spit", "look", "preen"};

    public BlizraptorEntity(EntityType<? extends BlizraptorEntity> type, Level level) {
        super(type, level);
    }

    @Override
    public MonsterProfile profile() {
        return PROFILE;
    }

    @Override
    public String[] animationNames() {
        return ANIMS_B;
    }

    @Override
    protected List<MonsterAttack> createAttacks() {
        List<MonsterAttack> list = raptorAttacks(Element.ICE);
        ProjectileSpec shard = ProjectileSpec.of(MonsterProjectile.Kind.ICE_SHARD, 4, Element.ICE).speed(1.05F).size(0.55F).lifetime(40);
        list.add(TimelineAttack.builder("ice_spit", 26).range(3.0F, 13).angle(35).weight(10).cooldown(70)
                .track(0, 10, 12)
                .particlesSpan(4, 10, ParticleTypes.SNOWFLAKE, 0, 1.25, 0.8, 1, 0.1)
                .projectile(11, 0, 1.2, 0.85, shard)
                .sound(10, () -> SoundEvents.SNOW_GOLEM_SHOOT, 1.2F, 1.3F)
                .sound(11, () -> SoundEvents.FOX_SPIT, 1.0F, 0.8F)
                .build());
        return list;
    }
}
