package com.meikon.client.sound;

import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;

/** A non-positional looping bed whose volume eases toward {@link #target}. */
public class LoopSound extends AbstractTickableSoundInstance {
    public float target;
    private final float ease;

    public LoopSound(SoundEvent event, SoundSource source, float ease) {
        super(event, source, RandomSource.create());
        this.looping = true;
        this.delay = 0;
        this.volume = 0.001f;
        this.relative = true;
        this.attenuation = SoundInstance.Attenuation.NONE;
        this.x = 0;
        this.y = 0;
        this.z = 0;
        this.ease = ease;
    }

    public void finish() {
        stop();
    }

    @Override
    public void tick() {
        volume += (target - volume) * ease;
        if (volume < 0.001f) volume = 0.001f;
    }

    @Override
    public boolean canStartSilent() {
        return true;
    }
}
