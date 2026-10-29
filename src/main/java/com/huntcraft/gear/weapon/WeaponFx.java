package com.huntcraft.gear.weapon;

import com.huntcraft.core.CameraShake;
import com.huntcraft.core.Element;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/** Server side visual effects of weapon moves (particles are broadcast with sendParticles). */
public final class WeaponFx {
    private WeaponFx() {
    }

    public static DustParticleOptions dust(int rgb, float size) {
        return new DustParticleOptions(new Vector3f(((rgb >> 16) & 255) / 255F, ((rgb >> 8) & 255) / 255F, (rgb & 255) / 255F), size);
    }

    /** Charge level colours: white, yellow, red. */
    public static int chargeColor(int level) {
        return switch (level) {
            case 1 -> 0xFFFFFF;
            case 2 -> 0xFFD83A;
            default -> 0xFF3020;
        };
    }

    public static Vec3 hand(Player p) {
        Vec3 f = WeaponHits.flatLook(p);
        Vec3 right = new Vec3(-f.z, 0, f.x);
        return p.position().add(f.x * 0.5 + right.x * 0.35, 1.2, f.z * 0.5 + right.z * 0.35);
    }

    public static void send(Player p, ParticleOptions type, Vec3 pos, int count, double dx, double dy, double dz, double speed) {
        if (p.level() instanceof ServerLevel sl) sl.sendParticles(type, pos.x, pos.y, pos.z, count, dx, dy, dz, speed);
    }

    /** Burst when a new charge level is reached (visual cue). */
    public static void chargeBurst(Player p, int level) {
        Vec3 h = hand(p);
        send(p, dust(chargeColor(level), 1.4F), h, 10 + level * 6, 0.35, 0.35, 0.35, 0.02);
        if (level >= 3) {
            send(p, ParticleTypes.FLAME, h, 8, 0.25, 0.25, 0.25, 0.03);
            send(p, ParticleTypes.ENCHANTED_HIT, h, 10, 0.3, 0.3, 0.3, 0.2);
        } else {
            send(p, ParticleTypes.CRIT, h, 6, 0.2, 0.2, 0.2, 0.1);
        }
    }

    /** Small aura while holding a charge. */
    public static void chargeAura(Player p, int level) {
        Vec3 h = hand(p);
        send(p, dust(chargeColor(level), 0.9F), h, 2 + level, 0.25, 0.3, 0.25, 0.0);
    }

    /** Ground impact: dust ring with block particles, explosion puff and camera shake. */
    public static void slam(Player p, Vec3 pos, float power) {
        if (!(p.level() instanceof ServerLevel sl)) return;
        BlockPos below = BlockPos.containing(pos.x, pos.y - 0.5, pos.z);
        BlockState bs = sl.getBlockState(below);
        if (!bs.isAir()) {
            sl.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, bs), pos.x, pos.y + 0.1, pos.z, (int) (20 * power), 0.8 * power, 0.1, 0.8 * power, 0.15);
        }
        sl.sendParticles(ParticleTypes.EXPLOSION, pos.x, pos.y + 0.3, pos.z, Math.max(1, (int) power), 0.3, 0.1, 0.3, 0.0);
        sl.sendParticles(ParticleTypes.CLOUD, pos.x, pos.y + 0.2, pos.z, (int) (10 * power), 0.7 * power, 0.05, 0.7 * power, 0.04);
        CameraShake.shake(sl, pos, 10.0 + 4.0 * power, 0.35F * power, 8 + (int) (4 * power));
    }

    /** Sweep arc in front of the player. */
    public static void sweep(Player p, double dist, int count) {
        Vec3 f = WeaponHits.flatLook(p);
        Vec3 c = p.position().add(f.x * dist, 1.1, f.z * dist);
        send(p, ParticleTypes.SWEEP_ATTACK, c, count, 0.4, 0.1, 0.4, 0.0);
    }

    /** Element flavour particles at a position. */
    public static void element(Player p, Element el, Vec3 pos, int count) {
        ParticleOptions o = switch (el) {
            case FIRE -> ParticleTypes.FLAME;
            case ICE -> ParticleTypes.SNOWFLAKE;
            case THUNDER -> ParticleTypes.ELECTRIC_SPARK;
            case WATER -> ParticleTypes.SPLASH;
            case DRAGON -> ParticleTypes.DRAGON_BREATH;
            case POISON -> ParticleTypes.WITCH;
            case PARALYSIS -> ParticleTypes.WAX_ON;
            case SLEEP -> ParticleTypes.NOTE;
            default -> null;
        };
        if (o != null) send(p, o, pos, count, 0.3, 0.3, 0.3, 0.05);
    }
}
