package com.eternalrealm.combat.forge;

import com.eternalrealm.combat.Telegraph;
import com.eternalrealm.sound.ERSounds;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;

/**
 * Particles and sounds that go with a ground {@link Telegraph}, themed by its style: a soft shimmer over the area
 * while it charges and a burst over the whole area when it hits. Used by the dungeon traps and the big moves of strong
 * monsters (bosses have their own effects). Server thread only.
 */
public final class TelegraphFx {
    private TelegraphFx() {
    }

    /** The warning sound at the marker (a short chime, higher than a boss's). */
    public static void warnSound(ServerLevel level, Telegraph telegraph, float volume) {
        level.playSound(null, telegraph.x(), telegraph.y(), telegraph.z(), ERSounds.BOSS_TELEGRAPH.get(),
                SoundSource.HOSTILE, volume, 1.35F);
    }

    /** The impact sound of the marker's style. */
    public static void impactSound(ServerLevel level, Telegraph telegraph, float volume) {
        SoundEvent sound = switch (telegraph.style()) {
            case FIRE -> ERSounds.SKILL_FIRE_EXPLOSION.get();
            case FROST -> ERSounds.SKILL_ICE_SHATTER.get();
            case MAGIC -> ERSounds.SKILL_THUNDER.get();
            case HOLY -> ERSounds.SKILL_HOLY_CHIME.get();
            case DARK -> ERSounds.SKILL_SHADOW_WHOOSH.get();
            case DANGER, SAFE, ALLY -> ERSounds.SKILL_HEAVY_SLASH.get();
        };
        level.playSound(null, telegraph.x(), telegraph.y(), telegraph.z(), sound, SoundSource.HOSTILE, volume,
                0.9F + level.getRandom().nextFloat() * 0.2F);
    }

    /** A few particles rising inside the area while it charges (call every few ticks). */
    public static void shimmer(ServerLevel level, Telegraph telegraph, int count) {
        scatter(level, telegraph, count, secondary(telegraph.style()), 0.05, 0.01);
    }

    /** The hit: particles over the whole area. */
    public static void burst(ServerLevel level, Telegraph telegraph) {
        double area = Math.max(1.0, telegraph.boundingRadius() * telegraph.boundingRadius());
        int count = (int) Math.min(70, 10 + area * 2.0);
        scatter(level, telegraph, count, primary(telegraph.style()), 0.2, 0.05);
        scatter(level, telegraph, count / 3, secondary(telegraph.style()), 0.35, 0.08);
    }

    /** Spreads {@code count} particles over points inside the marker (rejection sampling in its bounding circle). */
    public static void scatter(ServerLevel level, Telegraph telegraph, int count, ParticleOptions particle,
                               double spreadY, double speed) {
        RandomSource random = level.getRandom();
        double radius = telegraph.boundingRadius();
        int placed = 0;
        for (int attempt = 0; attempt < count * 8 && placed < count; attempt++) {
            double x = telegraph.x() + (random.nextDouble() * 2.0 - 1.0) * radius;
            double z = telegraph.z() + (random.nextDouble() * 2.0 - 1.0) * radius;
            if (!telegraph.contains(x, telegraph.y(), z)) {
                continue;
            }
            level.sendParticles(particle, x, telegraph.y() + 0.15, z, 1, 0.05, spreadY, 0.05, speed);
            placed++;
        }
    }

    /** Main impact particle of a style. */
    public static ParticleOptions primary(Telegraph.Style style) {
        return switch (style) {
            case DANGER -> ParticleTypes.POOF;
            case MAGIC -> ParticleTypes.ELECTRIC_SPARK;
            case FIRE -> ParticleTypes.FLAME;
            case FROST -> ParticleTypes.SNOWFLAKE;
            case HOLY -> ParticleTypes.END_ROD;
            case DARK -> ParticleTypes.PORTAL;
            case SAFE -> ParticleTypes.HAPPY_VILLAGER;
            case ALLY -> ParticleTypes.GLOW;
        };
    }

    /** Accent particle of a style (also the charging shimmer). */
    public static ParticleOptions secondary(Telegraph.Style style) {
        return switch (style) {
            case DANGER -> ParticleTypes.CRIT;
            case MAGIC -> ERParticles.RUNE.get();
            case FIRE -> ParticleTypes.LAVA;
            case FROST -> ERParticles.FROST.get();
            case HOLY -> ERParticles.HOLY.get();
            case DARK -> ERParticles.SHADOW.get();
            case SAFE -> ParticleTypes.HAPPY_VILLAGER;
            case ALLY -> ParticleTypes.END_ROD;
        };
    }
}
