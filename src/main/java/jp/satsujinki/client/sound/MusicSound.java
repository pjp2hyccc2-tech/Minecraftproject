package jp.satsujinki.client.sound;

import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;

/** The death game theme: plays while a round runs, fades out at the end. */
public class MusicSound extends AbstractTickableSoundInstance {
    private boolean fading;

    public MusicSound(SoundEvent sound) {
        super(sound, SoundSource.MUSIC, SoundInstance.createUnseededRandom());
        this.looping = true;
        this.delay = 0;
        this.volume = 0.0F;
        this.relative = true;
        this.attenuation = SoundInstance.Attenuation.NONE;
        this.x = 0.0D;
        this.y = 0.0D;
        this.z = 0.0D;
    }

    public void fadeOut() {
        this.fading = true;
    }

    @Override
    public void tick() {
        if (this.fading) {
            this.volume -= 0.03F;
            if (this.volume <= 0.0F) this.stop();
        } else {
            this.volume = Math.min(0.9F, this.volume + 0.05F);
        }
    }
}
