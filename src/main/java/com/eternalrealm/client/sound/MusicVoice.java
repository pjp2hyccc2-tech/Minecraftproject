package com.eternalrealm.client.sound;

import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;

/**
 * One playing music track or jingle whose gain the {@link MusicDirector} changes every client tick (fades, ducking).
 * The sound engine applies the MUSIC volume slider on top and re-reads {@link #getVolume()} every tick.
 *
 * <p>A looping voice has delay 0, so the engine plays the stream through a looping audio stream: the OGG files are
 * written as seamless loops, so there is no gap or fade at the loop point.
 */
final class MusicVoice extends AbstractTickableSoundInstance {
    private final String trackId;
    private float gain;
    private float fadeFrom;
    private float fadeTo;
    private int fadeTicks;
    private int fadeAge;
    private int age;

    MusicVoice(SoundEvent event, String trackId, boolean loop, float initialGain) {
        super(event, SoundSource.MUSIC, SoundInstance.createUnseededRandom());
        this.trackId = trackId;
        this.looping = loop;
        this.delay = 0;
        this.relative = true;
        this.attenuation = SoundInstance.Attenuation.NONE;
        this.x = 0.0D;
        this.y = 0.0D;
        this.z = 0.0D;
        this.gain = initialGain;
        this.fadeFrom = initialGain;
        this.fadeTo = initialGain;
        this.volume = initialGain;
    }

    String trackId() {
        return trackId;
    }

    /** Fades from the current gain to {@code target} over {@code ticks} client ticks (0 = at once). */
    void fadeTo(float target, int ticks) {
        fadeFrom = gain;
        fadeTo = target;
        fadeTicks = Math.max(0, ticks);
        fadeAge = 0;
        if (fadeTicks == 0) {
            gain = target;
        }
    }

    float target() {
        return fadeTo;
    }

    int age() {
        return age;
    }

    /** True once a fade to silence has finished. */
    boolean faded() {
        return fadeAge >= fadeTicks && fadeTo <= 0.0F;
    }

    /** Advances the fade by one tick; {@code multiplier} is the ducking level. */
    void advance(float multiplier) {
        age++;
        if (fadeAge < fadeTicks) {
            fadeAge++;
            double t = fadeAge / (double) fadeTicks;
            // Equal-power curves: a crossfade keeps a constant loudness (sin² + cos² = 1).
            double shaped = fadeTo >= fadeFrom ? Math.sin(t * Math.PI / 2.0) : 1.0 - Math.cos(t * Math.PI / 2.0);
            gain = (float) (fadeFrom + (fadeTo - fadeFrom) * shaped);
        } else {
            gain = fadeTo;
        }
        volume = Math.max(0.0F, Math.min(1.0F, gain * multiplier));
    }

    /** Marks the voice stopped; the engine stops it on its next tick (callers also stop it directly). */
    void halt() {
        stop();
    }

    @Override
    public void tick() {
        // The director drives the gain from the client tick.
    }

    @Override
    public boolean canStartSilent() {
        // Fades start at gain 0 and the MUSIC slider may be at 0 when a track starts: start the stream anyway.
        return true;
    }
}
