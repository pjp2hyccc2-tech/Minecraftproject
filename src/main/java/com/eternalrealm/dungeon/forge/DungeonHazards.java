package com.eternalrealm.dungeon.forge;

import com.eternalrealm.boss.Difficulty;
import com.eternalrealm.combat.Telegraph;
import com.eternalrealm.combat.forge.TelegraphFx;
import com.eternalrealm.combat.forge.Telegraphs;
import com.eternalrealm.core.ModInfo;
import com.eternalrealm.dungeon.DungeonLayout;
import com.eternalrealm.dungeon.hazard.HazardEvent;
import com.eternalrealm.dungeon.hazard.HazardField;
import com.eternalrealm.dungeon.hazard.HazardKind;
import com.eternalrealm.dungeon.hazard.HazardPlanner;
import com.eternalrealm.dungeon.hazard.HazardSite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Runs the traps of one dungeon instance ({@link HazardPlanner}, {@link HazardField}) in the world: shows each warning
 * as a ground telegraph with themed particles and sounds, then hits who is inside it when it fires. Traps never change
 * a block. Damage is a share of the victim's maximum health (damage type {@code eternalrealm:trap}); it is not reduced
 * by VIT (traps have no attacker) and cannot be guarded, but defense buffs apply. Server thread only.
 */
public final class DungeonHazards {
    /** {@code data/eternalrealm/damage_type/trap.json}. */
    public static final ResourceKey<DamageType> TRAP_DAMAGE =
            ResourceKey.create(Registries.DAMAGE_TYPE, ResourceLocation.fromNamespaceAndPath(ModInfo.MOD_ID, "trap"));

    private final ServerLevel level;
    private final HazardField field;
    private final double damageScale;
    /** Telegraph id of each warning trap. */
    private final Map<Integer, Integer> markers = new HashMap<>();

    public DungeonHazards(ServerLevel level, HazardField field, double damageScale) {
        this.level = level;
        this.field = field;
        this.damageScale = damageScale;
    }

    /** The traps of a built instance. */
    static DungeonHazards forInstance(ServerLevel level, DungeonLayout layout, BlockPos origin, Difficulty difficulty) {
        List<HazardSite> sites = HazardPlanner.plan(layout);
        return new DungeonHazards(level, new HazardField(sites, origin.getX(), origin.getY(), origin.getZ(),
                layout.seed()), damageScale(difficulty));
    }

    /** Harder difficulties hit harder (the warnings stay the same). */
    public static double damageScale(Difficulty difficulty) {
        return switch (difficulty) {
            case NORMAL -> 1.0;
            case HARD -> 1.3;
            case EX -> 1.6;
        };
    }

    public HazardField field() {
        return field;
    }

    /** Advances the traps; {@code victims} are who can be hit (the members inside the instance). */
    public void tick(long now, List<? extends LivingEntity> victims) {
        List<HazardField.Victim> positions = new ArrayList<>(victims.size());
        Map<UUID, LivingEntity> byId = new HashMap<>();
        for (LivingEntity victim : victims) {
            if (victim.isAlive() && !victim.isSpectator()) {
                positions.add(new HazardField.Victim(victim.getUUID(), victim.getX(), victim.getY(), victim.getZ()));
                byId.put(victim.getUUID(), victim);
            }
        }
        for (HazardEvent event : field.tick(now, positions)) {
            if (event instanceof HazardEvent.Warn warn) {
                warn(warn);
            } else if (event instanceof HazardEvent.Strike strike) {
                strike(strike, byId);
            }
        }
        if (now % 4 == 0 && !markers.isEmpty()) {
            for (HazardSite site : field.sites()) {
                Telegraph pending = field.pending(site.index());
                if (pending != null) {
                    charging(site.kind(), pending);
                }
            }
        }
    }

    /** The instance ends: every marker is called off. */
    public void stop() {
        for (int id : markers.values()) {
            Telegraphs.cancel(level, id);
        }
        markers.clear();
        field.reset();
    }

    private void warn(HazardEvent.Warn warn) {
        Telegraph telegraph = warn.telegraph();
        Integer previous = markers.put(warn.site().index(), Telegraphs.show(level, telegraph));
        if (previous != null) {
            Telegraphs.cancel(level, previous);
        }
        TelegraphFx.warnSound(level, telegraph, 0.6F);
    }

    private void strike(HazardEvent.Strike strike, Map<UUID, LivingEntity> byId) {
        Telegraph telegraph = strike.telegraph();
        Integer marker = markers.remove(strike.site().index());
        if (marker != null) {
            Telegraphs.fire(level, marker);
        }
        HazardKind kind = strike.site().kind();
        TelegraphFx.burst(level, telegraph);
        ParticleOptions accent = accent(kind);
        if (accent != null) {
            TelegraphFx.scatter(level, telegraph, 18, accent, 0.4, 0.1);
        }
        TelegraphFx.impactSound(level, telegraph, 0.8F);
        SoundEvent extra = sound(kind);
        if (extra != null) {
            level.playSound(null, telegraph.x(), telegraph.y(), telegraph.z(), extra, SoundSource.HOSTILE, 0.8F, 1.0F);
        }
        DamageSource source = trapDamage(level);
        for (UUID id : strike.hit()) {
            LivingEntity victim = byId.get(id);
            if (victim == null || !victim.isAlive()) {
                continue;
            }
            float amount = (float) Math.max(1.0, victim.getMaxHealth() * kind.damageFraction() * damageScale);
            if (victim.hurt(source, amount)) {
                affect(victim, kind, telegraph);
            }
        }
    }

    /** The trap damage source (plain magic damage if the damage type is missing from the data). */
    public static DamageSource trapDamage(ServerLevel level) {
        return level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolder(TRAP_DAMAGE)
                .map(DamageSource::new).orElseGet(() -> level.damageSources().magic());
    }

    private static void affect(LivingEntity victim, HazardKind kind, Telegraph telegraph) {
        switch (kind.effect()) {
            case SLOW -> victim.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 1));
            case ROOT -> victim.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 4));
            case POISON -> victim.addEffect(new MobEffectInstance(MobEffects.POISON, 80, 0));
            case BURN -> victim.setSecondsOnFire(3);
            case FREEZE -> {
                victim.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 50, 2));
                victim.setTicksFrozen(Math.max(victim.getTicksFrozen(), 100));
            }
            case LAUNCH -> {
                Vec3 motion = victim.getDeltaMovement();
                victim.setDeltaMovement(motion.x, 0.85, motion.z);
                victim.hurtMarked = true;
            }
            case SHOCK -> victim.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 30, 3));
            case PUSH -> {
                double radians = Math.toRadians(telegraph.yaw());
                victim.push(-Math.sin(radians) * 1.1, 0.2, Math.cos(radians) * 1.1);
                victim.hurtMarked = true;
            }
            case WITHER -> victim.addEffect(new MobEffectInstance(MobEffects.WITHER, 60, 0));
            case NONE -> {
                // the damage is all
            }
        }
        if (victim instanceof Player player) {
            player.displayClientMessage(net.minecraft.network.chat.Component.translatable(kind.translationKey())
                    .withStyle(net.minecraft.ChatFormatting.RED), true);
        }
    }

    /** Charging look: the style's shimmer, and debris falling from the ceiling for traps that come from above. */
    private void charging(HazardKind kind, Telegraph telegraph) {
        TelegraphFx.shimmer(level, telegraph, 3);
        ParticleOptions falling = switch (kind) {
            case ROCKFALL -> new BlockParticleOption(ParticleTypes.FALLING_DUST, Blocks.COBBLESTONE.defaultBlockState());
            case ICICLE_FALL, ICE_SPIKES -> new BlockParticleOption(ParticleTypes.FALLING_DUST,
                    Blocks.PACKED_ICE.defaultBlockState());
            case SAND_GEYSER, QUICKSAND -> new BlockParticleOption(ParticleTypes.FALLING_DUST,
                    Blocks.SAND.defaultBlockState());
            case EMBER_RAIN, LAVA_GEYSER -> ParticleTypes.FALLING_LAVA;
            default -> null;
        };
        if (falling != null) {
            for (int i = 0; i < 3; i++) {
                double x = telegraph.x() + (level.getRandom().nextDouble() - 0.5) * telegraph.boundingRadius();
                double z = telegraph.z() + (level.getRandom().nextDouble() - 0.5) * telegraph.boundingRadius();
                level.sendParticles(falling, x, telegraph.y() + 3.5, z, 1, 0.1, 0.1, 0.1, 0.0);
            }
        }
    }

    /** Extra impact particle of a trap. */
    private static ParticleOptions accent(HazardKind kind) {
        return switch (kind) {
            case ROCKFALL, CRUMBLING_FLOOR -> new BlockParticleOption(ParticleTypes.BLOCK,
                    Blocks.COBBLESTONE.defaultBlockState());
            case SPORE_BLOOM -> ParticleTypes.SNEEZE;
            case THORN_LASH, ROOT_SNARE -> new BlockParticleOption(ParticleTypes.BLOCK,
                    Blocks.ROOTED_DIRT.defaultBlockState());
            case SAND_GEYSER, QUICKSAND -> new BlockParticleOption(ParticleTypes.BLOCK, Blocks.SAND.defaultBlockState());
            case ICE_SPIKES, ICICLE_FALL, FROST_GRATE, FROST_RUNE -> new BlockParticleOption(ParticleTypes.BLOCK,
                    Blocks.PACKED_ICE.defaultBlockState());
            case LAVA_GEYSER, FLAME_JET, EMBER_RAIN -> ParticleTypes.LAVA;
            case GALE -> ParticleTypes.CLOUD;
            case VOID_ERUPTION, SHADOW_RING, VOID_LANCE -> ParticleTypes.REVERSE_PORTAL;
            case DART_VOLLEY, SUN_BEAM, LIGHTNING_RUNE -> null;
        };
    }

    /** Extra vanilla impact sound of a trap. */
    private static SoundEvent sound(HazardKind kind) {
        return switch (kind) {
            case ROCKFALL, CRUMBLING_FLOOR -> SoundEvents.STONE_BREAK;
            case DART_VOLLEY -> SoundEvents.ARROW_SHOOT;
            case SPORE_BLOOM -> SoundEvents.PUFFER_FISH_BLOW_UP;
            case SAND_GEYSER, QUICKSAND -> SoundEvents.SAND_BREAK;
            case ICE_SPIKES, ICICLE_FALL -> SoundEvents.GLASS_BREAK;
            case LAVA_GEYSER, EMBER_RAIN -> SoundEvents.LAVA_POP;
            case FLAME_JET -> SoundEvents.BLAZE_SHOOT;
            case LIGHTNING_RUNE -> SoundEvents.LIGHTNING_BOLT_IMPACT;
            case VOID_ERUPTION, VOID_LANCE -> SoundEvents.ENDERMAN_TELEPORT;
            default -> null;
        };
    }
}
