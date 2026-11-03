package com.hitori.client.sound;

import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;

/** A positional loop that can be moved (the nearest stream) and eases toward a target volume. */
public class PositionLoopSound extends AbstractTickableSoundInstance {
    public float target;
    public double tx, ty, tz;

    public PositionLoopSound(SoundEvent event, SoundSource source, double x, double y, double z) {
        super(event, source, RandomSource.create());
        this.looping = true;
        this.delay = 0;
        this.volume = 0.001f;
        this.x = this.tx = x;
        this.y = this.ty = y;
        this.z = this.tz = z;
    }

    public void finish() {
        stop();
    }

    @Override
    public void tick() {
        x += (tx - x) * 0.1;
        y += (ty - y) * 0.1;
        z += (tz - z) * 0.1;
        volume += (target - volume) * 0.03f;
        if (volume < 0.001f) volume = 0.001f;
    }

    @Override
    public boolean canStartSilent() {
        return true;
    }
}
