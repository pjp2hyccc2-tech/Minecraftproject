package com.birdiecraft.client;

import com.birdiecraft.golf.engine.Club;
import com.birdiecraft.golf.engine.ClubFamily;
import com.birdiecraft.golf.engine.Lie;
import com.birdiecraft.golf.engine.Trajectory;
import com.birdiecraft.network.S2CBallFlightPacket;
import com.birdiecraft.registry.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

/** Client-side replay of a server-simulated ball flight. */
public final class BallFlight {
    public final int participantId;
    public final boolean practice;
    public final boolean mine;
    public final int color;
    public final Club club;
    public final boolean perfect;
    public final float[] xs, ys, zs;
    public final byte[] events;
    public final Trajectory.Result result;
    public final Lie restLie;
    public final float carry;
    public final float total;
    private int delay;
    private int age = -1;
    private int linger;
    private boolean landed;

    public BallFlight(S2CBallFlightPacket p, boolean mine, int delayTicks) {
        this.participantId = p.participantId;
        this.practice = p.practice;
        this.mine = mine;
        this.color = p.color;
        this.club = Club.byId(p.club);
        this.perfect = p.perfect;
        this.xs = p.xs;
        this.ys = p.ys;
        this.zs = p.zs;
        this.events = p.events;
        this.result = Trajectory.Result.values()[Math.max(0, Math.min(Trajectory.Result.values().length - 1, p.result))];
        this.restLie = Lie.byId(p.restLie);
        this.carry = p.carry;
        this.total = p.total;
        this.delay = delayTicks;
    }

    public boolean started() {
        return age >= 0;
    }

    public boolean finished() {
        return age >= xs.length - 1;
    }

    /** True once the flight ended and its result display time ran out. */
    public boolean expired() {
        return finished() && linger > 50;
    }

    public boolean hasLanded() {
        return landed;
    }

    public int age() {
        return age;
    }

    public int length() {
        return xs.length;
    }

    public void tick() {
        if (delay > 0) {
            delay--;
            return;
        }
        if (age < 0) {
            age = 0;
            onLaunch();
            return;
        }
        if (finished()) {
            linger++;
            return;
        }
        age++;
        if (age < events.length && events[age] != 0) onEvents(events[age], age);
    }

    public Vec3 pos(float partial) {
        if (age < 0) return new Vec3(xs[0], ys[0], zs[0]);
        int i = Math.min(age, xs.length - 1);
        int j = Math.min(i + 1, xs.length - 1);
        float f = finished() ? 1f : partial;
        return new Vec3(xs[i] + (xs[j] - xs[i]) * f, ys[i] + (ys[j] - ys[i]) * f, zs[i] + (zs[j] - zs[i]) * f);
    }

    /** Horizontal distance from the start so far, in blocks. */
    public double distanceSoFar(float partial) {
        Vec3 p = pos(partial);
        double dx = p.x - xs[0];
        double dz = p.z - zs[0];
        return Math.sqrt(dx * dx + dz * dz);
    }

    public Vec3 horizontalDirection() {
        int i = Math.min(Math.max(age, 1), xs.length - 1);
        double dx = xs[i] - xs[0];
        double dz = zs[i] - zs[0];
        double len = Math.sqrt(dx * dx + dz * dz);
        if (len < 1.0e-4) {
            int k = Math.min(xs.length - 1, 5);
            dx = xs[k] - xs[0];
            dz = zs[k] - zs[0];
            len = Math.max(1.0e-4, Math.sqrt(dx * dx + dz * dz));
        }
        return new Vec3(dx / len, 0, dz / len);
    }

    private void onLaunch() {
        SoundEvent hit;
        if (club.isPutter()) hit = ModSounds.HIT_PUTT.get();
        else if (club.family == ClubFamily.DRIVER || club.family == ClubFamily.WOOD) hit = ModSounds.HIT_DRIVER.get();
        else hit = ModSounds.HIT_IRON.get();
        play(hit, xs[0], ys[0], zs[0], 1.0f, perfect ? 1.1f : 1.0f);
        if (!club.isPutter()) play(ModSounds.WHOOSH.get(), xs[0], ys[0], zs[0], 0.6f, 1.0f);
        ClientLevel level = Minecraft.getInstance().level;
        if (level != null && !club.isPutter()) {
            for (int i = 0; i < 6; i++) {
                level.addParticle(ParticleTypes.CLOUD, xs[0], ys[0] + 0.05, zs[0],
                        (Math.random() - 0.5) * 0.05, 0.02, (Math.random() - 0.5) * 0.05);
            }
        }
    }

    private void onEvents(byte ev, int i) {
        ClientLevel level = Minecraft.getInstance().level;
        float x = xs[i], y = ys[i], z = zs[i];
        if ((ev & Trajectory.EV_LAND) != 0) landed = true;
        if ((ev & Trajectory.EV_BOUNCE) != 0) {
            float speed = i > 0 ? (float) Math.abs(ys[i - 1] - ys[i]) : 0.1f;
            play(ModSounds.BALL_BOUNCE.get(), x, y, z, Math.min(1f, 0.3f + speed * 2f), 0.9f + (float) Math.random() * 0.2f);
            if (level != null) {
                for (int k = 0; k < 3; k++) {
                    level.addParticle(ParticleTypes.CLOUD, x, y + 0.02, z, (Math.random() - 0.5) * 0.03, 0.01, (Math.random() - 0.5) * 0.03);
                }
            }
        }
        if ((ev & Trajectory.EV_WALL) != 0) {
            play(ModSounds.BALL_BOUNCE.get(), x, y, z, 0.6f, 1.3f);
        }
        if ((ev & Trajectory.EV_TREE) != 0) {
            play(SoundEvents.AZALEA_LEAVES_BREAK, x, y, z, 1.0f, 1.0f);
            if (level != null) {
                for (int k = 0; k < 10; k++) {
                    level.addParticle(ParticleTypes.COMPOSTER, x + (Math.random() - 0.5), y + (Math.random() - 0.5), z + (Math.random() - 0.5), 0, -0.02, 0);
                }
            }
        }
        if ((ev & Trajectory.EV_LIP) != 0) {
            play(ModSounds.BALL_BOUNCE.get(), x, y, z, 0.8f, 1.6f);
        }
        if ((ev & Trajectory.EV_CUP) != 0) {
            play(ModSounds.CUP_IN.get(), x, y, z, 1.0f, 1.0f);
            if (level != null) {
                for (int k = 0; k < 24; k++) {
                    double a = Math.random() * Math.PI * 2;
                    level.addParticle(ParticleTypes.FIREWORK, x, y + 0.3, z, Math.cos(a) * 0.15, 0.25 + Math.random() * 0.15, Math.sin(a) * 0.15);
                }
            }
        }
        if ((ev & Trajectory.EV_SPLASH) != 0) {
            play(SoundEvents.GENERIC_SPLASH, x, y, z, 1.0f, 1.2f);
            if (level != null) {
                for (int k = 0; k < 20; k++) {
                    level.addParticle(ParticleTypes.SPLASH, x + (Math.random() - 0.5) * 0.6, y + 0.8, z + (Math.random() - 0.5) * 0.6, 0, 0.2, 0);
                }
            }
        }
    }

    private static void play(SoundEvent sound, double x, double y, double z, float vol, float pitch) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level != null) level.playLocalSound(x, y, z, sound, SoundSource.PLAYERS, vol, pitch, false);
    }
}
