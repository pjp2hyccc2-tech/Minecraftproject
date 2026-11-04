package jp.satsujinki.client;

import jp.satsujinki.SatsujinkiConfig;
import jp.satsujinki.network.GoreFxPacket;
import jp.satsujinki.registry.ModParticles;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

/** Client-side blood effects for GoreFxPacket. */
public final class ClientFx {
    private ClientFx() {
    }

    public static void handle(GoreFxPacket p) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) return;
        double mult = SatsujinkiConfig.goreParticles();
        RandomSource r = level.random;
        double x = p.x, y = p.y, z = p.z;
        switch (p.kind) {
            case GoreFxPacket.BLEED -> {
                drops(level, r, x, y, z, n(8 * p.intensity, mult), 0.22D, 0.25D, 0.1D);
                mist(level, r, x, y, z, n(1 + p.intensity, mult), 0.05D);
            }
            case GoreFxPacket.DEATH_GENERIC -> {
                drops(level, r, x, y, z, n(70, mult), 0.4D, 0.45D, 0.15D);
                mist(level, r, x, y, z, n(8, mult), 0.1D);
                bits(level, r, x, y, z, n(14, mult), 0.3D);
                screen(mc, p, 4.0D, 1);
            }
            case GoreFxPacket.DEATH_MIXER -> {
                for (int i = 0; i < n(170, mult); i++) {
                    double a = r.nextDouble() * Math.PI * 2.0D;
                    double sp = 0.15D + r.nextDouble() * 0.35D;
                    add(level, ModParticles.BLOOD_DROP.get(), x + r.nextGaussian() * 0.4D, y + r.nextDouble() * 0.6D, z + r.nextGaussian() * 0.4D,
                            Math.cos(a) * sp, 0.55D + r.nextDouble() * 0.75D, Math.sin(a) * sp);
                }
                mist(level, r, x, y + 1.2D, z, n(24, mult), 0.25D);
                bits(level, r, x, y + 0.5D, z, n(40, mult), 0.45D);
                screen(mc, p, 5.5D, 2);
                ClientState.shake(0.35F, 12);
            }
            case GoreFxPacket.DEATH_PRESS -> {
                for (int i = 0; i < n(160, mult); i++) {
                    double a = r.nextDouble() * Math.PI * 2.0D;
                    double sp = 0.35D + r.nextDouble() * 0.7D;
                    add(level, ModParticles.BLOOD_DROP.get(), x + Math.cos(a) * 0.5D, y + 0.1D, z + Math.sin(a) * 0.5D,
                            Math.cos(a) * sp, 0.05D + r.nextDouble() * 0.3D, Math.sin(a) * sp);
                }
                for (int i = 0; i < n(26, mult); i++) {
                    add(level, ModParticles.BLOOD_SPLAT.get(), x + r.nextGaussian() * 0.9D, y + 0.02D, z + r.nextGaussian() * 0.9D, 0, 0, 0);
                }
                bits(level, r, x, y + 0.2D, z, n(30, mult), 0.5D);
                mist(level, r, x, y + 0.2D, z, n(16, mult), 0.2D);
                screen(mc, p, 6.0D, 2);
            }
            case GoreFxPacket.PRESS_IMPACT -> {
                LocalPlayer pl = mc.player;
                if (pl != null) {
                    double d = Math.sqrt(pl.distanceToSqr(x, y, z));
                    if (d < 20.0D) ClientState.shake((float) (Math.min(1.6D, 0.6D + 0.25D * p.intensity) * (1.0D - d / 20.0D)), 18);
                }
                for (int i = 0; i < 40; i++) {
                    double a = i / 40.0D * Math.PI * 2.0D;
                    level.addParticle(ParticleTypes.POOF, x + Math.cos(a) * 1.6D, y + 0.1D, z + Math.sin(a) * 1.6D, Math.cos(a) * 0.25D, 0.02D, Math.sin(a) * 0.25D);
                }
            }
            case GoreFxPacket.DEATH_SAW -> {
                for (int i = 0; i < n(130, mult); i++) {
                    double a = r.nextDouble() * Math.PI * 2.0D;
                    double sp = 0.1D + r.nextDouble() * 0.45D;
                    add(level, ModParticles.BLOOD_DROP.get(), x, y, z, Math.cos(a) * sp, 0.25D + r.nextDouble() * 0.6D, Math.sin(a) * sp);
                }
                bits(level, r, x, y, z, n(24, mult), 0.4D);
                mist(level, r, x, y, z, n(14, mult), 0.2D);
                screen(mc, p, 4.5D, 2);
            }
            case GoreFxPacket.DEATH_GUILLOTINE -> {
                for (int i = 0; i < n(90, mult); i++) {
                    double sp = 0.25D + r.nextDouble() * 0.45D;
                    add(level, ModParticles.BLOOD_DROP.get(), x, y, z, p.dirX * sp + r.nextGaussian() * 0.08D, 0.15D + r.nextDouble() * 0.35D,
                            p.dirZ * sp + r.nextGaussian() * 0.08D);
                }
                for (int i = 0; i < n(50, mult); i++) {
                    add(level, ModParticles.BLOOD_DROP.get(), x, y, z, r.nextGaussian() * 0.06D, 0.4D + r.nextDouble() * 0.5D, r.nextGaussian() * 0.06D);
                }
                mist(level, r, x, y, z, n(10, mult), 0.15D);
                bits(level, r, x, y, z, n(8, mult), 0.2D);
                screen(mc, p, 4.0D, 1);
            }
            case GoreFxPacket.DEATH_SPIKES -> {
                drops(level, r, x, y, z, n(60, mult), 0.3D, 0.4D, 0.1D);
                bits(level, r, x, y, z, n(10, mult), 0.25D);
                for (int i = 0; i < n(8, mult); i++) {
                    add(level, ModParticles.BLOOD_SPLAT.get(), x + r.nextGaussian() * 0.5D, y - 0.3D, z + r.nextGaussian() * 0.5D, 0, 0, 0);
                }
                screen(mc, p, 3.5D, 1);
            }
            case GoreFxPacket.MIXER_GRIND -> {
                for (int i = 0; i < n(24, mult); i++) {
                    double a = r.nextDouble() * Math.PI * 2.0D;
                    add(level, ModParticles.BLOOD_DROP.get(), x, y, z, Math.cos(a) * 0.35D, 0.3D + r.nextDouble() * 0.5D, Math.sin(a) * 0.35D);
                }
                bits(level, r, x, y, z, n(4, mult), 0.3D);
                mist(level, r, x, y, z, n(2, mult), 0.1D);
            }
            case GoreFxPacket.SAW_CUT -> {
                for (int i = 0; i < n(16, mult); i++) {
                    add(level, ModParticles.BLOOD_DROP.get(), x, y, z, r.nextGaussian() * 0.25D, 0.25D + r.nextDouble() * 0.45D, r.nextGaussian() * 0.25D);
                }
                bits(level, r, x, y, z, n(3, mult), 0.25D);
                level.addParticle(ParticleTypes.CRIT, x, y, z, r.nextGaussian() * 0.3D, 0.3D, r.nextGaussian() * 0.3D);
            }
            case GoreFxPacket.SPAWN_POOF -> {
                for (int i = 0; i < 24; i++) {
                    level.addParticle(i % 2 == 0 ? ParticleTypes.LARGE_SMOKE : ParticleTypes.POOF, x + r.nextGaussian() * 0.3D, y + r.nextGaussian() * 0.5D,
                            z + r.nextGaussian() * 0.3D, r.nextGaussian() * 0.04D, 0.05D, r.nextGaussian() * 0.04D);
                }
            }
            default -> {
            }
        }
    }

    private static int n(double base, double mult) {
        return Math.max(0, Mth.floor(base * mult));
    }

    private static void add(ClientLevel level, ParticleOptions type, double x, double y, double z, double xd, double yd, double zd) {
        level.addParticle(type, true, x, y, z, xd, yd, zd);
    }

    private static void drops(ClientLevel level, RandomSource r, double x, double y, double z, int count, double spread, double up, double jitter) {
        for (int i = 0; i < count; i++) {
            add(level, ModParticles.BLOOD_DROP.get(), x + r.nextGaussian() * jitter, y + r.nextGaussian() * jitter, z + r.nextGaussian() * jitter,
                    r.nextGaussian() * spread * 0.5D, up * (0.4D + r.nextDouble()), r.nextGaussian() * spread * 0.5D);
        }
    }

    private static void mist(ClientLevel level, RandomSource r, double x, double y, double z, int count, double spread) {
        for (int i = 0; i < count; i++) {
            add(level, ModParticles.BLOOD_MIST.get(), x + r.nextGaussian() * spread, y + r.nextGaussian() * spread, z + r.nextGaussian() * spread,
                    r.nextGaussian() * 0.03D, 0.02D + r.nextDouble() * 0.03D, r.nextGaussian() * 0.03D);
        }
    }

    private static void bits(ClientLevel level, RandomSource r, double x, double y, double z, int count, double speed) {
        for (int i = 0; i < count; i++) {
            add(level, ModParticles.GORE_BIT.get(), x, y, z, r.nextGaussian() * speed * 0.6D, 0.2D + r.nextDouble() * speed, r.nextGaussian() * speed * 0.6D);
        }
    }

    private static void screen(Minecraft mc, GoreFxPacket p, double radius, int strength) {
        LocalPlayer pl = mc.player;
        if (pl == null) return;
        double d = Math.sqrt(pl.distanceToSqr(p.x, p.y, p.z));
        if (d < radius) ClientState.splashScreen(strength, (float) (1.0D - d / radius));
    }
}
