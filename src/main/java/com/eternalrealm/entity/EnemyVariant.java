package com.eternalrealm.entity;

import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;

import java.util.Locale;

/**
 * Behaviour of every enemy variant: ranged or melee, the element of its bolts, immunities, ambient particles and
 * sounds. Looks (parts, texture, scale) come from the generated {@link RigData} and client tables; the
 * {@code entity} GameTests check that both lists name the same variants.
 */
public enum EnemyVariant {
    // humanoid
    BANDIT(EnemyRig.HUMANOID, null, null, 0.0F),
    GHOUL(EnemyRig.HUMANOID, null, null, 0.0F),
    MUMMY(EnemyRig.HUMANOID, null, null, 0.0F),
    CULTIST(EnemyRig.HUMANOID, BoltKind.CURSE, null, 0.0F),
    PRISONER(EnemyRig.HUMANOID, null, null, 0.0F),
    JAILER(EnemyRig.HUMANOID, null, null, 0.0F),
    ICE_KNIGHT(EnemyRig.HUMANOID, null, ParticleTypes.SNOWFLAKE, 0.04F),
    FROST_MAGE(EnemyRig.HUMANOID, BoltKind.FROST, ParticleTypes.SNOWFLAKE, 0.12F),
    FIRE_IMP(EnemyRig.HUMANOID, BoltKind.FIRE, ParticleTypes.FLAME, 0.15F),
    DARK_KNIGHT(EnemyRig.HUMANOID, null, ParticleTypes.SMOKE, 0.08F),
    VOID_MAGE(EnemyRig.HUMANOID, BoltKind.VOID, ParticleTypes.PORTAL, 0.3F),
    SHADE(EnemyRig.HUMANOID, null, ParticleTypes.SMOKE, 0.25F),
    CROSSBOWMAN(EnemyRig.HUMANOID, BoltKind.ARROW, null, 0.0F),
    DRYAD(EnemyRig.HUMANOID, BoltKind.NATURE, ParticleTypes.SPORE_BLOSSOM_AIR, 0.05F),
    // beast
    WOLF(EnemyRig.BEAST, null, null, 0.0F),
    CAVE_RAT(EnemyRig.BEAST, null, null, 0.0F),
    FROST_HOUND(EnemyRig.BEAST, null, ParticleTypes.SNOWFLAKE, 0.12F),
    SALAMANDER(EnemyRig.BEAST, null, ParticleTypes.SMALL_FLAME, 0.1F),
    SCORPION(EnemyRig.BEAST, null, null, 0.0F),
    ABYSS_HOUND(EnemyRig.BEAST, null, ParticleTypes.SMOKE, 0.2F),
    SNOW_WOLF(EnemyRig.BEAST, null, null, 0.0F),
    // golem
    TREANT(EnemyRig.GOLEM, null, null, 0.0F),
    SAND_GOLEM(EnemyRig.GOLEM, null, ParticleTypes.WHITE_ASH, 0.3F),
    ICE_GOLEM(EnemyRig.GOLEM, null, ParticleTypes.SNOWFLAKE, 0.15F),
    LAVA_GOLEM(EnemyRig.GOLEM, null, ParticleTypes.LAVA, 0.05F),
    RUIN_CONSTRUCT(EnemyRig.GOLEM, null, ParticleTypes.ENCHANT, 0.25F),
    // flyer
    HARPY(EnemyRig.FLYER, null, null, 0.0F),
    ICE_WRAITH(EnemyRig.FLYER, BoltKind.FROST, ParticleTypes.SNOWFLAKE, 0.25F),
    STORM_SPIRIT(EnemyRig.FLYER, BoltKind.STORM, ParticleTypes.ELECTRIC_SPARK, 0.3F),
    SKY_SENTINEL(EnemyRig.FLYER, null, ParticleTypes.END_ROD, 0.05F),
    FIRE_SPIRIT(EnemyRig.FLYER, BoltKind.FIRE, ParticleTypes.FLAME, 0.35F);

    private final EnemyRig rig;
    private final BoltKind bolt;
    private final ParticleOptions particle;
    private final float particleChance;

    EnemyVariant(EnemyRig rig, BoltKind bolt, ParticleOptions particle, float particleChance) {
        this.rig = rig;
        this.bolt = bolt;
        this.particle = particle;
        this.particleChance = particleChance;
    }

    /** Variant id as used by {@link ERMonster#setVariant(String)}, e.g. "ice_knight". */
    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public EnemyRig rig() {
        return rig;
    }

    /** Keeps its distance and fires {@link EnemyBolt}s instead of fighting in melee. */
    public boolean ranged() {
        return bolt != null;
    }

    /** Element of its bolts (ranged variants), {@link BoltKind#ARROW} for melee variants. */
    public BoltKind bolt() {
        return bolt != null ? bolt : BoltKind.ARROW;
    }

    /** Client-side ambient particle, or {@code null}. */
    public ParticleOptions particle() {
        return particle;
    }

    /** Chance per client tick of one ambient particle. */
    public float particleChance() {
        return particleChance;
    }

    public boolean fireImmune() {
        return this == FIRE_IMP || this == SALAMANDER || this == LAVA_GOLEM || this == FIRE_SPIRIT;
    }

    /** Rendered with a translucent render type (ghosts). */
    public boolean translucent() {
        return this == SHADE || this == ICE_WRAITH;
    }

    /** Floats instead of walking: no fall damage, ignores gravity (flyers and the shade). */
    public boolean floats() {
        return rig == EnemyRig.FLYER;
    }

    /** {ambient, hurt, death} sounds. */
    public SoundEvent[] sounds() {
        return switch (this) {
            case BANDIT, CROSSBOWMAN -> new SoundEvent[]{SoundEvents.PILLAGER_AMBIENT, SoundEvents.PILLAGER_HURT, SoundEvents.PILLAGER_DEATH};
            case GHOUL -> new SoundEvent[]{SoundEvents.ZOMBIE_AMBIENT, SoundEvents.ZOMBIE_HURT, SoundEvents.ZOMBIE_DEATH};
            case MUMMY -> new SoundEvent[]{SoundEvents.HUSK_AMBIENT, SoundEvents.HUSK_HURT, SoundEvents.HUSK_DEATH};
            case CULTIST -> new SoundEvent[]{SoundEvents.EVOKER_AMBIENT, SoundEvents.EVOKER_HURT, SoundEvents.EVOKER_DEATH};
            case PRISONER -> new SoundEvent[]{SoundEvents.ZOMBIE_VILLAGER_AMBIENT, SoundEvents.ZOMBIE_VILLAGER_HURT, SoundEvents.ZOMBIE_VILLAGER_DEATH};
            case JAILER -> new SoundEvent[]{SoundEvents.VINDICATOR_AMBIENT, SoundEvents.VINDICATOR_HURT, SoundEvents.VINDICATOR_DEATH};
            case ICE_KNIGHT -> new SoundEvent[]{SoundEvents.STRAY_AMBIENT, SoundEvents.STRAY_HURT, SoundEvents.STRAY_DEATH};
            case FROST_MAGE -> new SoundEvent[]{SoundEvents.ILLUSIONER_AMBIENT, SoundEvents.ILLUSIONER_HURT, SoundEvents.ILLUSIONER_DEATH};
            case FIRE_IMP, FIRE_SPIRIT, STORM_SPIRIT -> new SoundEvent[]{SoundEvents.BLAZE_AMBIENT, SoundEvents.BLAZE_HURT, SoundEvents.BLAZE_DEATH};
            case DARK_KNIGHT -> new SoundEvent[]{SoundEvents.WITHER_SKELETON_AMBIENT, SoundEvents.WITHER_SKELETON_HURT, SoundEvents.WITHER_SKELETON_DEATH};
            case VOID_MAGE -> new SoundEvent[]{SoundEvents.ENDERMAN_AMBIENT, SoundEvents.ENDERMAN_HURT, SoundEvents.ENDERMAN_DEATH};
            case SHADE, ICE_WRAITH -> new SoundEvent[]{SoundEvents.VEX_AMBIENT, SoundEvents.VEX_HURT, SoundEvents.VEX_DEATH};
            case DRYAD -> new SoundEvent[]{SoundEvents.ALLAY_AMBIENT_WITHOUT_ITEM, SoundEvents.ALLAY_HURT, SoundEvents.ALLAY_DEATH};
            case WOLF, FROST_HOUND, ABYSS_HOUND, SNOW_WOLF -> new SoundEvent[]{SoundEvents.WOLF_GROWL, SoundEvents.WOLF_HURT, SoundEvents.WOLF_DEATH};
            case CAVE_RAT -> new SoundEvent[]{SoundEvents.SILVERFISH_AMBIENT, SoundEvents.SILVERFISH_HURT, SoundEvents.SILVERFISH_DEATH};
            case SALAMANDER -> new SoundEvent[]{SoundEvents.STRIDER_AMBIENT, SoundEvents.STRIDER_HURT, SoundEvents.STRIDER_DEATH};
            case SCORPION -> new SoundEvent[]{SoundEvents.SPIDER_AMBIENT, SoundEvents.SPIDER_HURT, SoundEvents.SPIDER_DEATH};
            case TREANT, SAND_GOLEM, ICE_GOLEM, LAVA_GOLEM, RUIN_CONSTRUCT, SKY_SENTINEL ->
                    new SoundEvent[]{null, SoundEvents.IRON_GOLEM_HURT, SoundEvents.IRON_GOLEM_DEATH};
            case HARPY -> new SoundEvent[]{SoundEvents.PHANTOM_AMBIENT, SoundEvents.PHANTOM_HURT, SoundEvents.PHANTOM_DEATH};
        };
    }

    /** The behaviour of {@code rig/id}; unknown ids fall back to the rig's first variant. */
    public static EnemyVariant of(EnemyRig rig, String id) {
        for (EnemyVariant variant : values()) {
            if (variant.rig == rig && variant.id().equals(id)) {
                return variant;
            }
        }
        for (EnemyVariant variant : values()) {
            if (variant.rig == rig) {
                return variant;
            }
        }
        throw new IllegalStateException("no variants for " + rig);
    }
}
