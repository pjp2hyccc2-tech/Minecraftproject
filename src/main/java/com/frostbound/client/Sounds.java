package com.frostbound.client;

import com.frostbound.registry.ModRegistries;
import com.frostbound.world.Dims;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;

/** UI sounds and the ambient loops of the frozen city (wind, blizzard, generator hum, murmur of the city). */
public final class Sounds {
    private static Loop wind;
    private static Loop blizzard;
    private static Loop hum;
    private static Loop murmur;

    private Sounds() {
    }

    public static void play(String name, float volume, float pitch) {
        SoundEvent e = ModRegistries.sound(name);
        if (e != null) {
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(e, pitch, volume));
        }
    }

    public static void click() {
        play("ui_click", 0.45f, 1.0f);
    }

    /** Ambient loop whose volume follows a target set every tick; fades in and out. */
    private static final class Loop extends AbstractTickableSoundInstance {
        float target;
        boolean positional;

        Loop(SoundEvent event, boolean positional) {
            super(event, SoundSource.AMBIENT, RandomSource.create());
            this.looping = true;
            this.delay = 0;
            this.volume = 0.001f;
            this.positional = positional;
            if (!positional) {
                this.relative = true;
                this.attenuation = SoundInstance.Attenuation.NONE;
                this.x = 0;
                this.y = 0;
                this.z = 0;
            }
        }

        void at(double px, double py, double pz) {
            this.x = px;
            this.y = py;
            this.z = pz;
        }

        void finish() {
            stop();
        }

        @Override
        public void tick() {
            volume += (target - volume) * 0.05f;
            if (volume < 0.001f) {
                volume = 0.001f;
            }
        }

        @Override
        public boolean canStartSilent() {
            return true;
        }
    }

    private static Loop ensure(Loop loop, String name, boolean positional) {
        Minecraft mc = Minecraft.getInstance();
        if (loop != null && mc.getSoundManager().isActive(loop)) {
            return loop;
        }
        SoundEvent e = ModRegistries.sound(name);
        if (e == null) {
            return null;
        }
        Loop l = new Loop(e, positional);
        mc.getSoundManager().play(l);
        return l;
    }

    public static void stopAll() {
        for (Loop l : new Loop[] {wind, blizzard, hum, murmur}) {
            if (l != null) {
                l.finish();
            }
        }
        wind = null;
        blizzard = null;
        hum = null;
        murmur = null;
    }

    /** Called every client tick. */
    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || !mc.level.dimension().equals(Dims.FROZEN_WASTES)) {
            stopAll();
            return;
        }
        CompoundTag snap = ClientCity.snap();
        String weather = snap.getString("weather");
        boolean storm = weather.equals("BLIZZARD") || weather.equals("EXTREME_COLD") || weather.equals("GREAT_COLD_WAVE");
        boolean windy = storm || weather.equals("WIND");
        wind = ensure(wind, "wind_loop", false);
        if (wind != null) {
            wind.target = windy ? 0.32f : 0.16f;
        }
        if (storm) {
            blizzard = ensure(blizzard, "blizzard_loop", false);
        }
        if (blizzard != null) {
            blizzard.target = storm ? (weather.equals("GREAT_COLD_WAVE") ? 0.4f : 0.3f) : 0f;
        }
        if (ClientCity.hasCity()) {
            CompoundTag g = ClientCity.generator();
            boolean running = g.getInt("level") > 0 && !g.getBoolean("broken") && !g.getBoolean("fuelOut");
            hum = ensure(hum, "generator_hum", true);
            if (hum != null) {
                hum.at(g.getInt("x") + 0.5, g.getInt("y") + 3, g.getInt("z") + 0.5);
                hum.target = running ? 0.55f + 0.1f * g.getInt("level") : 0f;
            }
            int hour = snap.getInt("hour");
            boolean day = hour >= 7 && hour < 21;
            murmur = ensure(murmur, "city_murmur", true);
            if (murmur != null) {
                murmur.at(g.getInt("x") + 0.5, g.getInt("y") + 1, g.getInt("z") + 12.5);
                murmur.target = day ? 0.35f : 0.05f;
            }
        }
    }
}
