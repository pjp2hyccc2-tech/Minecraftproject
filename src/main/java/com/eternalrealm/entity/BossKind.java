package com.eternalrealm.entity;

import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;

import java.util.Locale;
import java.util.Optional;

/**
 * One entry per boss of the boss module ({@code com.eternalrealm.boss.Bosses}): entity type
 * {@code eternalrealm:boss_<id>}, its rig, hitbox (from {@link RigData}), theme particle and sounds.
 */
public enum BossKind {
    B1(BossRig.KING),
    B2(BossRig.TREANT),
    B3(BossRig.COLOSSUS),
    B4(BossRig.QUEEN),
    B5(BossRig.QUEEN),
    B6(BossRig.DRAGON),
    B7(BossRig.COLOSSUS),
    B8(BossRig.KING),
    FINAL(BossRig.KING);

    private final BossRig rig;

    BossKind(BossRig rig) {
        this.rig = rig;
    }

    /** "b1".."b8" or "final", the ids of the boss module. */
    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public BossRig rig() {
        return this.rig;
    }

    /** Registry path of the entity type, e.g. "boss_b3". */
    public String typeName() {
        return "boss_" + id();
    }

    public float width() {
        return RigData.hitbox(this.rig.id(), id())[0];
    }

    public float height() {
        return RigData.hitbox(this.rig.id(), id())[1];
    }

    public boolean fireImmune() {
        return this == B6 || this == FINAL;
    }

    /** Client particle shown while telegraphing (and around the boss when enraged). */
    public ParticleOptions themeParticle() {
        return switch (this) {
            case B1 -> ParticleTypes.SMOKE;
            case B2 -> ParticleTypes.SPORE_BLOSSOM_AIR;
            case B3 -> ParticleTypes.WHITE_ASH;
            case B4, B5 -> ParticleTypes.SNOWFLAKE;
            case B6 -> ParticleTypes.FLAME;
            case B7 -> ParticleTypes.ELECTRIC_SPARK;
            case B8 -> ParticleTypes.SCULK_SOUL;
            case FINAL -> ParticleTypes.REVERSE_PORTAL;
        };
    }

    /** {ambient, hurt, death, roar, heavy hit}; ambient may be null. */
    public SoundEvent[] sounds() {
        return switch (this) {
            case B1 -> new SoundEvent[]{SoundEvents.VINDICATOR_AMBIENT, SoundEvents.VINDICATOR_HURT, SoundEvents.VINDICATOR_DEATH,
                    SoundEvents.RAVAGER_ROAR, SoundEvents.PLAYER_ATTACK_STRONG};
            case B2 -> new SoundEvent[]{null, SoundEvents.ZOMBIE_ATTACK_WOODEN_DOOR, SoundEvents.ZOMBIE_BREAK_WOODEN_DOOR,
                    SoundEvents.RAVAGER_ROAR, SoundEvents.IRON_GOLEM_ATTACK};
            case B3 -> new SoundEvent[]{SoundEvents.RAVAGER_AMBIENT, SoundEvents.RAVAGER_HURT, SoundEvents.RAVAGER_DEATH,
                    SoundEvents.RAVAGER_ROAR, SoundEvents.RAVAGER_ATTACK};
            case B4 -> new SoundEvent[]{SoundEvents.PIGLIN_BRUTE_AMBIENT, SoundEvents.PIGLIN_BRUTE_HURT, SoundEvents.PIGLIN_BRUTE_DEATH,
                    SoundEvents.PIGLIN_BRUTE_ANGRY, SoundEvents.ANVIL_LAND};
            case B5 -> new SoundEvent[]{SoundEvents.EVOKER_AMBIENT, SoundEvents.EVOKER_HURT, SoundEvents.EVOKER_DEATH,
                    SoundEvents.EVOKER_PREPARE_WOLOLO, SoundEvents.GLASS_BREAK};
            case B6 -> new SoundEvent[]{SoundEvents.ENDER_DRAGON_AMBIENT, SoundEvents.ENDER_DRAGON_HURT, SoundEvents.ENDER_DRAGON_DEATH,
                    SoundEvents.ENDER_DRAGON_GROWL, SoundEvents.GENERIC_EXPLODE};
            case B7 -> new SoundEvent[]{SoundEvents.BEACON_AMBIENT, SoundEvents.IRON_GOLEM_HURT, SoundEvents.IRON_GOLEM_DEATH,
                    SoundEvents.WARDEN_SONIC_CHARGE, SoundEvents.IRON_GOLEM_ATTACK};
            case B8 -> new SoundEvent[]{SoundEvents.WARDEN_AMBIENT, SoundEvents.WARDEN_HURT, SoundEvents.WARDEN_DEATH,
                    SoundEvents.WARDEN_ROAR, SoundEvents.WARDEN_ATTACK_IMPACT};
            case FINAL -> new SoundEvent[]{SoundEvents.WITHER_AMBIENT, SoundEvents.WITHER_HURT, SoundEvents.WITHER_DEATH,
                    SoundEvents.WITHER_SPAWN, SoundEvents.WITHER_BREAK_BLOCK};
        };
    }

    public static Optional<BossKind> byId(String id) {
        for (BossKind kind : values()) {
            if (kind.id().equals(id)) {
                return Optional.of(kind);
            }
        }
        return Optional.empty();
    }
}
