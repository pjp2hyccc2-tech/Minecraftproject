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
    FINAL(BossRig.KING),
    // the field bosses (boss.FieldBosses): calamities, then wanderers
    C_MEADOW(BossRig.QUEEN, Theme.SHADOW),
    C_MOON_FOREST(BossRig.TREANT, Theme.MOON),
    C_IRON_MINE(BossRig.COLOSSUS, Theme.FORGE),
    C_SACRED_FALLS(BossRig.QUEEN, Theme.WATER),
    C_ANCIENT_RUINS(BossRig.DRAGON, Theme.FIRE),
    C_SUMMIT(BossRig.COLOSSUS, Theme.STONE),
    C_END_FOREST(BossRig.KING, Theme.VOID),
    C_FARM(BossRig.TREANT, Theme.BLIGHT),
    C_LAKESIDE(BossRig.DRAGON, Theme.WATER),
    C_HARBOUR(BossRig.KING, Theme.STORM),
    W_BRAMBLE_KNIGHT(BossRig.KING, Theme.BLIGHT),
    W_MOONMOTH_WITCH(BossRig.QUEEN, Theme.MOON),
    W_SLAG_TITAN(BossRig.COLOSSUS, Theme.FORGE),
    W_CASCADE_WYVERN(BossRig.DRAGON, Theme.WATER),
    W_MIST_HERMIT(BossRig.QUEEN, Theme.MOON),
    W_WALKING_GLYPH(BossRig.COLOSSUS, Theme.STONE),
    W_ASH_HERALD(BossRig.KING, Theme.FIRE),
    W_THUNDER_ROC(BossRig.DRAGON, Theme.STORM),
    W_GLACIER_BEARER(BossRig.COLOSSUS, Theme.ICE),
    W_HOLLOW_WALKER(BossRig.KING, Theme.VOID),
    W_GRAVEBLOOM(BossRig.TREANT, Theme.SHADOW),
    W_SCARECROW_KING(BossRig.KING, Theme.BLIGHT),
    W_REED_SERPENT(BossRig.DRAGON, Theme.WATER),
    W_SUNKEN_CAPTAIN(BossRig.KING, Theme.WATER),
    W_TIDE_SINGER(BossRig.QUEEN, Theme.WATER);

    /** Particles and sounds of the field bosses, by what they are made of. */
    enum Theme {
        SHADOW(ParticleTypes.SQUID_INK, SoundEvents.EVOKER_AMBIENT, SoundEvents.VEX_HURT, SoundEvents.EVOKER_DEATH,
                SoundEvents.WARDEN_SONIC_CHARGE, SoundEvents.PLAYER_ATTACK_STRONG),
        MOON(ParticleTypes.END_ROD, SoundEvents.AMETHYST_BLOCK_CHIME, SoundEvents.AMETHYST_BLOCK_HIT,
                SoundEvents.AMETHYST_BLOCK_BREAK, SoundEvents.EVOKER_PREPARE_WOLOLO, SoundEvents.GLASS_BREAK),
        FORGE(ParticleTypes.LAVA, SoundEvents.IRON_GOLEM_STEP, SoundEvents.IRON_GOLEM_HURT, SoundEvents.IRON_GOLEM_DEATH,
                SoundEvents.RAVAGER_ROAR, SoundEvents.ANVIL_LAND),
        WATER(ParticleTypes.DRIPPING_WATER, SoundEvents.ELDER_GUARDIAN_AMBIENT, SoundEvents.ELDER_GUARDIAN_HURT,
                SoundEvents.ELDER_GUARDIAN_DEATH, SoundEvents.ELDER_GUARDIAN_CURSE, SoundEvents.GENERIC_SPLASH),
        FIRE(ParticleTypes.FLAME, SoundEvents.BLAZE_AMBIENT, SoundEvents.BLAZE_HURT, SoundEvents.BLAZE_DEATH,
                SoundEvents.ENDER_DRAGON_GROWL, SoundEvents.GENERIC_EXPLODE),
        STONE(ParticleTypes.WHITE_ASH, SoundEvents.RAVAGER_AMBIENT, SoundEvents.IRON_GOLEM_HURT,
                SoundEvents.IRON_GOLEM_DEATH, SoundEvents.RAVAGER_ROAR, SoundEvents.IRON_GOLEM_ATTACK),
        VOID(ParticleTypes.REVERSE_PORTAL, SoundEvents.WARDEN_AMBIENT, SoundEvents.WARDEN_HURT, SoundEvents.WARDEN_DEATH,
                SoundEvents.WARDEN_ROAR, SoundEvents.WARDEN_ATTACK_IMPACT),
        BLIGHT(ParticleTypes.SPORE_BLOSSOM_AIR, null, SoundEvents.ZOMBIE_ATTACK_WOODEN_DOOR,
                SoundEvents.ZOMBIE_BREAK_WOODEN_DOOR, SoundEvents.RAVAGER_ROAR, SoundEvents.PLAYER_ATTACK_STRONG),
        STORM(ParticleTypes.ELECTRIC_SPARK, SoundEvents.EVOKER_AMBIENT, SoundEvents.EVOKER_HURT,
                SoundEvents.EVOKER_DEATH, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundEvents.LIGHTNING_BOLT_IMPACT),
        ICE(ParticleTypes.SNOWFLAKE, null, SoundEvents.GLASS_HIT, SoundEvents.GLASS_BREAK, SoundEvents.RAVAGER_ROAR,
                SoundEvents.ANVIL_LAND);

        final ParticleOptions particle;
        final SoundEvent[] sounds;

        Theme(ParticleOptions particle, SoundEvent ambient, SoundEvent hurt, SoundEvent death, SoundEvent roar,
              SoundEvent hit) {
            this.particle = particle;
            this.sounds = new SoundEvent[]{ambient, hurt, death, roar, hit};
        }
    }

    private final BossRig rig;
    /** The field bosses' theme (null for the dungeon and raid bosses, which have their own particles and sounds). */
    private final Theme theme;

    BossKind(BossRig rig) {
        this(rig, null);
    }

    BossKind(BossRig rig, Theme theme) {
        this.rig = rig;
        this.theme = theme;
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
        return this == B6 || this == FINAL || this.theme == Theme.FIRE || this.theme == Theme.FORGE;
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
            default -> this.theme.particle;
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
            default -> this.theme.sounds.clone();
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
