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
    FIRE_SPIRIT(EnemyRig.FLYER, BoltKind.FIRE, ParticleTypes.FLAME, 0.35F),
    // the bestiary (tools/art_entities/bestiary.py): rig, bolt, particle, chance, sounds like, fire immune, translucent
    // GENERATED BESTIARY VARIANTS BEGIN (tools/art_entities/bestiary_gen.py)
    // humanoid (bestiary)
    F1_BANDIT_SCOUT(EnemyRig.HUMANOID, null, null, 0.0F, BANDIT, false, false),
    F1_GOBLIN_PILFERER(EnemyRig.HUMANOID, null, null, 0.0F, BANDIT, false, false),
    F1_LIVING_SCARECROW(EnemyRig.HUMANOID, null, null, 0.0F, PRISONER, false, false),
    F1_BANDIT_CROSSBOWMAN(EnemyRig.HUMANOID, BoltKind.ARROW, null, 0.0F, BANDIT, false, false),
    F1_CAPLING(EnemyRig.HUMANOID, null, null, 0.0F, DRYAD, false, false),
    F1_FALLEN_SQUIRE(EnemyRig.HUMANOID, null, null, 0.0F, JAILER, false, false),
    LORD_GARM(EnemyRig.HUMANOID, null, null, 0.0F, JAILER, false, false),
    LORD_HOOK(EnemyRig.HUMANOID, BoltKind.ARROW, null, 0.0F, BANDIT, false, false),
    F2_THORN_DRYAD(EnemyRig.HUMANOID, BoltKind.NATURE, null, 0.0F, DRYAD, false, false),
    F2_NIGHT_STALKER(EnemyRig.HUMANOID, null, null, 0.0F, BANDIT, false, false),
    F2_MOONBOW_HUNTER(EnemyRig.HUMANOID, BoltKind.ARROW, null, 0.0F, BANDIT, false, false),
    LORD_NEBULA(EnemyRig.HUMANOID, BoltKind.NATURE, null, 0.0F, DRYAD, false, false),
    LORD_BELLADONNA(EnemyRig.HUMANOID, BoltKind.CURSE, null, 0.0F, CULTIST, false, false),
    F3_KOBOLD_MINER(EnemyRig.HUMANOID, null, null, 0.0F, PRISONER, false, false),
    F3_DUST_MUMMY(EnemyRig.HUMANOID, null, null, 0.0F, MUMMY, false, false),
    F3_SUN_CULTIST(EnemyRig.HUMANOID, BoltKind.FIRE, null, 0.0F, CULTIST, false, false),
    F3_RUST_GUARD(EnemyRig.HUMANOID, null, null, 0.0F, DARK_KNIGHT, false, false),
    LORD_RUSTBONE(EnemyRig.HUMANOID, BoltKind.ARROW, null, 0.0F, DARK_KNIGHT, false, false),
    F4_KAPPA(EnemyRig.HUMANOID, null, null, 0.0F, GHOUL, false, false),
    F4_LOST_PILGRIM(EnemyRig.HUMANOID, BoltKind.FROST, null, 0.0F, GHOUL, false, false),
    F4_FROST_MONK(EnemyRig.HUMANOID, BoltKind.FROST, null, 0.0F, FROST_MAGE, false, false),
    F5_EMBER_IMP(EnemyRig.HUMANOID, BoltKind.FIRE, ParticleTypes.FLAME, 0.15F, FIRE_IMP, true, false),
    F5_RELIC_THIEF(EnemyRig.HUMANOID, null, null, 0.0F, BANDIT, false, false),
    F5_FLAME_PRIEST(EnemyRig.HUMANOID, BoltKind.FIRE, ParticleTypes.FLAME, 0.05F, CULTIST, true, false),
    LORD_VALKA(EnemyRig.HUMANOID, null, null, 0.0F, DARK_KNIGHT, false, false),
    F6_SKY_KNIGHT(EnemyRig.HUMANOID, null, null, 0.0F, ICE_KNIGHT, false, false),
    F6_STORM_MAGE(EnemyRig.HUMANOID, BoltKind.STORM, null, 0.0F, FROST_MAGE, false, false),
    LORD_ALVIN(EnemyRig.HUMANOID, null, null, 0.0F, ICE_KNIGHT, false, false),
    LORD_PEAK_SNIPER(EnemyRig.HUMANOID, BoltKind.ARROW, null, 0.0F, BANDIT, false, false),
    F7_DOOM_KNIGHT(EnemyRig.HUMANOID, null, null, 0.0F, DARK_KNIGHT, false, false),
    F7_SHADOW_ASSASSIN(EnemyRig.HUMANOID, null, null, 0.0F, SHADE, false, false),
    F7_HEX_WITCH(EnemyRig.HUMANOID, BoltKind.CURSE, null, 0.0F, CULTIST, false, false),
    LORD_XENOS(EnemyRig.HUMANOID, BoltKind.VOID, null, 0.0F, VOID_MAGE, false, false),
    LORD_BLACK_KNIGHT(EnemyRig.HUMANOID, null, null, 0.0F, DARK_KNIGHT, false, false),
    F8_PUMPKIN_HEAD(EnemyRig.HUMANOID, null, null, 0.0F, PRISONER, false, false),
    F8_LOST_FARMHAND(EnemyRig.HUMANOID, null, null, 0.0F, GHOUL, false, false),
    F8_VINE_LURKER(EnemyRig.HUMANOID, BoltKind.NATURE, null, 0.0F, DRYAD, false, false),
    LORD_HARVEST_BOSS(EnemyRig.HUMANOID, null, null, 0.0F, BANDIT, false, false),
    LORD_STROM(EnemyRig.HUMANOID, BoltKind.FIRE, null, 0.0F, PRISONER, false, false),
    F9_BOG_GHOUL(EnemyRig.HUMANOID, null, null, 0.0F, GHOUL, false, false),
    F9_REED_WITCH(EnemyRig.HUMANOID, BoltKind.NATURE, null, 0.0F, CULTIST, false, false),
    F9_FISHER_GHOST(EnemyRig.HUMANOID, null, null, 0.0F, SHADE, false, true),
    F9_WATER_IMP(EnemyRig.HUMANOID, BoltKind.FROST, null, 0.0F, FIRE_IMP, false, false),
    LORD_MELUSINE(EnemyRig.HUMANOID, BoltKind.FROST, null, 0.0F, FROST_MAGE, false, false),
    LORD_DROWNED_FISHER(EnemyRig.HUMANOID, null, null, 0.0F, GHOUL, false, false),
    F10_DECK_PIRATE(EnemyRig.HUMANOID, null, null, 0.0F, BANDIT, false, false),
    F10_PIRATE_BRUTE(EnemyRig.HUMANOID, null, null, 0.0F, JAILER, false, false),
    F10_GHOST_SAILOR(EnemyRig.HUMANOID, null, null, 0.0F, SHADE, false, true),
    F10_SMUGGLER(EnemyRig.HUMANOID, BoltKind.ARROW, null, 0.0F, BANDIT, false, false),
    F10_SEA_WITCH(EnemyRig.HUMANOID, BoltKind.VOID, null, 0.0F, CULTIST, false, false),
    LORD_BARBAS(EnemyRig.HUMANOID, null, null, 0.0F, JAILER, false, false),
    LORD_DOCK_BRAWLER(EnemyRig.HUMANOID, null, null, 0.0F, JAILER, false, false),
    // beast (bestiary)
    F1_HORNED_HARE(EnemyRig.BEAST, null, null, 0.0F, CAVE_RAT, false, false),
    F1_WILD_BOAR(EnemyRig.BEAST, null, null, 0.0F, WOLF, false, false),
    F1_STRAY_SHEEPDOG(EnemyRig.BEAST, null, null, 0.0F, WOLF, false, false),
    LORD_DURN(EnemyRig.BEAST, null, null, 0.0F, WOLF, false, false),
    F2_MOONSHADE_WOLF(EnemyRig.BEAST, null, null, 0.0F, WOLF, false, false),
    F2_TWIN_TAILED_FOX(EnemyRig.BEAST, null, null, 0.0F, WOLF, false, false),
    F2_BARK_LIZARD(EnemyRig.BEAST, null, null, 0.0F, SALAMANDER, false, false),
    LORD_LUNAR(EnemyRig.BEAST, null, null, 0.0F, WOLF, false, false),
    F3_ROCK_CRAB(EnemyRig.BEAST, null, null, 0.0F, SCORPION, false, false),
    F3_GOLDEN_SCARAB(EnemyRig.BEAST, null, null, 0.0F, SCORPION, false, false),
    F3_TUNNEL_RAT(EnemyRig.BEAST, null, null, 0.0F, CAVE_RAT, false, false),
    LORD_VEIN_EATER(EnemyRig.BEAST, null, null, 0.0F, SCORPION, false, false),
    F4_FALLS_TOAD(EnemyRig.BEAST, null, null, 0.0F, SALAMANDER, false, false),
    F4_CRYSTAL_CRAB(EnemyRig.BEAST, null, null, 0.0F, SCORPION, false, false),
    LORD_MISTWEAVER(EnemyRig.BEAST, null, null, 0.0F, SCORPION, false, false),
    LORD_ORD(EnemyRig.BEAST, null, null, 0.0F, SALAMANDER, false, false),
    F5_ASH_HOUND(EnemyRig.BEAST, null, ParticleTypes.SMOKE, 0.1F, WOLF, false, false),
    F5_MAGMA_LIZARD(EnemyRig.BEAST, null, ParticleTypes.LAVA, 0.03F, SALAMANDER, true, false),
    F5_LAVA_TORTOISE(EnemyRig.BEAST, null, null, 0.0F, SALAMANDER, true, false),
    F6_PEAK_WOLF(EnemyRig.BEAST, null, null, 0.0F, WOLF, false, false),
    F6_SKY_RAM(EnemyRig.BEAST, null, null, 0.0F, WOLF, false, false),
    F7_VOID_HOUND(EnemyRig.BEAST, null, ParticleTypes.PORTAL, 0.12F, WOLF, false, false),
    F7_PLAGUE_RAT(EnemyRig.BEAST, null, ParticleTypes.CRIMSON_SPORE, 0.08F, CAVE_RAT, false, false),
    F7_END_SCORPION(EnemyRig.BEAST, null, null, 0.0F, SCORPION, false, false),
    LORD_NIHIL(EnemyRig.BEAST, null, null, 0.0F, SALAMANDER, false, false),
    F8_GIANT_MOLE(EnemyRig.BEAST, null, null, 0.0F, CAVE_RAT, false, false),
    F8_ROWDY_CALF(EnemyRig.BEAST, null, null, 0.0F, WOLF, false, false),
    LORD_BRUTUS(EnemyRig.BEAST, null, null, 0.0F, WOLF, false, false),
    F9_MARSH_FROG(EnemyRig.BEAST, null, null, 0.0F, SALAMANDER, false, false),
    F9_LAKE_SERPENT(EnemyRig.BEAST, null, null, 0.0F, SALAMANDER, false, false),
    F9_GREAT_CRAYFISH(EnemyRig.BEAST, null, null, 0.0F, SCORPION, false, false),
    LORD_NUMEL(EnemyRig.BEAST, null, null, 0.0F, SALAMANDER, false, false),
    F10_BEACH_CRAB(EnemyRig.BEAST, null, null, 0.0F, SCORPION, false, false),
    F10_SHORE_MONITOR(EnemyRig.BEAST, null, null, 0.0F, SALAMANDER, false, false),
    LORD_ANKARA(EnemyRig.BEAST, null, null, 0.0F, SCORPION, false, false),
    // golem (bestiary)
    F2_FUNGAL_BRUTE(EnemyRig.GOLEM, null, null, 0.0F, TREANT, false, false),
    F3_ORE_GOLEM(EnemyRig.GOLEM, null, null, 0.0F, SAND_GOLEM, false, false),
    LORD_DOLGA(EnemyRig.GOLEM, null, null, 0.0F, SAND_GOLEM, false, false),
    F4_MOSS_GUARDIAN(EnemyRig.GOLEM, null, null, 0.0F, RUIN_CONSTRUCT, false, false),
    F4_SNOW_APE(EnemyRig.GOLEM, null, null, 0.0F, ICE_GOLEM, false, false),
    LORD_GOURA(EnemyRig.GOLEM, null, null, 0.0F, ICE_GOLEM, false, false),
    F5_RUIN_SENTINEL(EnemyRig.GOLEM, null, null, 0.0F, RUIN_CONSTRUCT, false, false),
    F5_OBSIDIAN_GUARD(EnemyRig.GOLEM, null, null, 0.0F, LAVA_GOLEM, false, false),
    LORD_AGNIS(EnemyRig.GOLEM, null, null, 0.0F, RUIN_CONSTRUCT, true, false),
    F6_CLOUD_GIANT(EnemyRig.GOLEM, null, ParticleTypes.CLOUD, 0.08F, ICE_GOLEM, false, false),
    F6_FROST_TITANLING(EnemyRig.GOLEM, null, null, 0.0F, ICE_GOLEM, false, false),
    F7_BLIGHTED_TREANT(EnemyRig.GOLEM, null, null, 0.0F, TREANT, false, false),
    F7_AMETHYST_COLOSSUS(EnemyRig.GOLEM, null, null, 0.0F, ICE_GOLEM, false, false),
    F8_HAY_GOLEM(EnemyRig.GOLEM, null, null, 0.0F, TREANT, false, false),
    F9_MUD_GOLEM(EnemyRig.GOLEM, null, null, 0.0F, TREANT, false, false),
    F10_CORAL_GOLEM(EnemyRig.GOLEM, null, null, 0.0F, RUIN_CONSTRUCT, false, false),
    // flyer (bestiary)
    F1_MEADOW_WISP(EnemyRig.FLYER, BoltKind.NATURE, ParticleTypes.SPORE_BLOSSOM_AIR, 0.15F, ICE_WRAITH, false, false),
    F2_MOON_MOTH(EnemyRig.FLYER, BoltKind.NATURE, ParticleTypes.END_ROD, 0.05F, ICE_WRAITH, false, false),
    F2_OWL_WARDEN(EnemyRig.FLYER, null, null, 0.0F, HARPY, false, false),
    F2_WILL_O_WISP(EnemyRig.FLYER, BoltKind.CURSE, ParticleTypes.SOUL_FIRE_FLAME, 0.25F, FIRE_SPIRIT, true, false),
    F3_GIANT_CAVE_BAT(EnemyRig.FLYER, null, null, 0.0F, HARPY, false, false),
    F3_GEM_WRAITH(EnemyRig.FLYER, BoltKind.VOID, ParticleTypes.WITCH, 0.08F, ICE_WRAITH, false, true),
    F4_MIST_SPIRIT(EnemyRig.FLYER, BoltKind.FROST, ParticleTypes.CLOUD, 0.08F, ICE_WRAITH, false, false),
    F4_FROST_HERON(EnemyRig.FLYER, BoltKind.FROST, null, 0.0F, HARPY, false, false),
    F4_SPRAY_WRAITH(EnemyRig.FLYER, BoltKind.FROST, ParticleTypes.DRIPPING_WATER, 0.15F, ICE_WRAITH, false, true),
    F5_CINDER_WRAITH(EnemyRig.FLYER, BoltKind.FIRE, ParticleTypes.SMOKE, 0.2F, FIRE_SPIRIT, true, true),
    F5_SCORCH_VULTURE(EnemyRig.FLYER, null, null, 0.0F, HARPY, false, false),
    LORD_SCORCH_EYE(EnemyRig.FLYER, BoltKind.FIRE, ParticleTypes.FLAME, 0.3F, FIRE_SPIRIT, true, false),
    F6_THUNDER_HAWK(EnemyRig.FLYER, BoltKind.STORM, ParticleTypes.ELECTRIC_SPARK, 0.06F, HARPY, false, false),
    F6_GALE_SPIRIT(EnemyRig.FLYER, BoltKind.STORM, ParticleTypes.CLOUD, 0.1F, STORM_SPIRIT, false, false),
    F6_YOUNG_GRIFFIN(EnemyRig.FLYER, null, null, 0.0F, HARPY, false, false),
    F6_STAR_SENTINEL(EnemyRig.FLYER, BoltKind.STORM, ParticleTypes.END_ROD, 0.05F, SKY_SENTINEL, false, false),
    LORD_ZEPHYR(EnemyRig.FLYER, BoltKind.STORM, ParticleTypes.ELECTRIC_SPARK, 0.2F, HARPY, false, false),
    F7_HOLLOW_EYE(EnemyRig.FLYER, BoltKind.VOID, ParticleTypes.PORTAL, 0.2F, ICE_WRAITH, false, false),
    F7_WANDERING_GRUDGE(EnemyRig.FLYER, BoltKind.VOID, ParticleTypes.SOUL, 0.06F, SHADE, false, true),
    F8_FIELD_CROW(EnemyRig.FLYER, null, null, 0.0F, HARPY, false, false),
    F8_WILD_ROOSTER(EnemyRig.FLYER, null, null, 0.0F, HARPY, false, false),
    F8_GIANT_BEE(EnemyRig.FLYER, null, null, 0.0F, HARPY, false, false),
    F8_WINDMILL_SPRITE(EnemyRig.FLYER, BoltKind.STORM, ParticleTypes.CLOUD, 0.06F, STORM_SPIRIT, false, false),
    F9_EGRET_SPIRIT(EnemyRig.FLYER, BoltKind.FROST, null, 0.0F, HARPY, false, false),
    F9_GIANT_DRAGONFLY(EnemyRig.FLYER, null, null, 0.0F, HARPY, false, false),
    F10_SEA_HARPY(EnemyRig.FLYER, null, null, 0.0F, HARPY, false, false),
    F10_TIDE_SPIRIT(EnemyRig.FLYER, BoltKind.FROST, ParticleTypes.DRIPPING_WATER, 0.2F, STORM_SPIRIT, false, false);
    // GENERATED BESTIARY VARIANTS END

    private final EnemyRig rig;
    private final BoltKind bolt;
    private final ParticleOptions particle;
    private final float particleChance;
    /** The variant whose sounds this one borrows (the bestiary looks), {@code null} for the first looks. */
    private final EnemyVariant soundsLike;
    private final boolean fireImmune;
    private final boolean translucent;

    EnemyVariant(EnemyRig rig, BoltKind bolt, ParticleOptions particle, float particleChance) {
        this(rig, bolt, particle, particleChance, null, false, false);
    }

    EnemyVariant(EnemyRig rig, BoltKind bolt, ParticleOptions particle, float particleChance, EnemyVariant soundsLike,
                 boolean fireImmune, boolean translucent) {
        this.rig = rig;
        this.bolt = bolt;
        this.particle = particle;
        this.particleChance = particleChance;
        this.soundsLike = soundsLike;
        this.fireImmune = fireImmune;
        this.translucent = translucent;
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
        return this.fireImmune || this == FIRE_IMP || this == SALAMANDER || this == LAVA_GOLEM || this == FIRE_SPIRIT;
    }

    /** Rendered with a translucent render type (ghosts). */
    public boolean translucent() {
        return this.translucent || this == SHADE || this == ICE_WRAITH;
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
            // GENERATED BESTIARY SOUNDS BEGIN (tools/art_entities/bestiary_gen.py)
            case F1_HORNED_HARE, F1_WILD_BOAR, F1_BANDIT_SCOUT, F1_GOBLIN_PILFERER, F1_MEADOW_WISP,
                    F1_STRAY_SHEEPDOG, F1_LIVING_SCARECROW, F1_BANDIT_CROSSBOWMAN, F1_CAPLING, F1_FALLEN_SQUIRE,
                    LORD_GARM, LORD_HOOK, LORD_DURN, F2_MOONSHADE_WOLF, F2_TWIN_TAILED_FOX, F2_THORN_DRYAD,
                    F2_MOON_MOTH, F2_FUNGAL_BRUTE, F2_OWL_WARDEN, F2_NIGHT_STALKER, F2_BARK_LIZARD,
                    F2_MOONBOW_HUNTER, F2_WILL_O_WISP, LORD_NEBULA, LORD_BELLADONNA, LORD_LUNAR, F3_KOBOLD_MINER,
                    F3_ROCK_CRAB, F3_ORE_GOLEM, F3_DUST_MUMMY, F3_GIANT_CAVE_BAT, F3_SUN_CULTIST, F3_GOLDEN_SCARAB,
                    F3_TUNNEL_RAT, F3_RUST_GUARD, F3_GEM_WRAITH, LORD_DOLGA, LORD_RUSTBONE, LORD_VEIN_EATER,
                    F4_MIST_SPIRIT, F4_FALLS_TOAD, F4_KAPPA, F4_LOST_PILGRIM, F4_FROST_HERON, F4_CRYSTAL_CRAB,
                    F4_FROST_MONK, F4_MOSS_GUARDIAN, F4_SNOW_APE, F4_SPRAY_WRAITH, LORD_GOURA, LORD_MISTWEAVER,
                    LORD_ORD, F5_EMBER_IMP, F5_ASH_HOUND, F5_MAGMA_LIZARD, F5_RUIN_SENTINEL, F5_CINDER_WRAITH,
                    F5_RELIC_THIEF, F5_FLAME_PRIEST, F5_OBSIDIAN_GUARD, F5_SCORCH_VULTURE, F5_LAVA_TORTOISE,
                    LORD_VALKA, LORD_SCORCH_EYE, LORD_AGNIS, F6_THUNDER_HAWK, F6_SKY_KNIGHT, F6_CLOUD_GIANT,
                    F6_GALE_SPIRIT, F6_PEAK_WOLF, F6_STORM_MAGE, F6_YOUNG_GRIFFIN, F6_SKY_RAM, F6_STAR_SENTINEL,
                    F6_FROST_TITANLING, LORD_ALVIN, LORD_PEAK_SNIPER, LORD_ZEPHYR, F7_VOID_HOUND, F7_BLIGHTED_TREANT,
                    F7_DOOM_KNIGHT, F7_HOLLOW_EYE, F7_AMETHYST_COLOSSUS, F7_SHADOW_ASSASSIN, F7_PLAGUE_RAT,
                    F7_HEX_WITCH, F7_WANDERING_GRUDGE, F7_END_SCORPION, LORD_XENOS, LORD_BLACK_KNIGHT,
                    LORD_NIHIL, F8_FIELD_CROW, F8_GIANT_MOLE, F8_PUMPKIN_HEAD, F8_WILD_ROOSTER, F8_ROWDY_CALF,
                    F8_HAY_GOLEM, F8_LOST_FARMHAND, F8_GIANT_BEE, F8_VINE_LURKER, F8_WINDMILL_SPRITE,
                    LORD_BRUTUS, LORD_HARVEST_BOSS, LORD_STROM, F9_MARSH_FROG, F9_LAKE_SERPENT, F9_EGRET_SPIRIT,
                    F9_BOG_GHOUL, F9_REED_WITCH, F9_GREAT_CRAYFISH, F9_MUD_GOLEM, F9_FISHER_GHOST,
                    F9_GIANT_DRAGONFLY, F9_WATER_IMP, LORD_MELUSINE, LORD_DROWNED_FISHER, LORD_NUMEL,
                    F10_DECK_PIRATE, F10_PIRATE_BRUTE, F10_BEACH_CRAB, F10_SEA_HARPY, F10_CORAL_GOLEM,
                    F10_SHORE_MONITOR, F10_GHOST_SAILOR, F10_SMUGGLER, F10_TIDE_SPIRIT, F10_SEA_WITCH,
                    LORD_BARBAS, LORD_DOCK_BRAWLER, LORD_ANKARA -> this.soundsLike.sounds();
            // GENERATED BESTIARY SOUNDS END
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
