package com.eternalrealm.client.sound;

import com.eternalrealm.sound.ERSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraftforge.registries.RegistryObject;

/**
 * Client-side interface sounds (SoundSource.MASTER, not positional), for screens and dialogue. Client thread only.
 */
public final class UiSounds {
    private static final RandomSource RANDOM = RandomSource.create();

    private UiSounds() {
    }

    public static void click() {
        play(ERSounds.UI_CLICK, 1.0F, 0.6F);
    }

    /** Opening the journal or turning one of its pages. */
    public static void journalOpen() {
        play(ERSounds.UI_JOURNAL_OPEN, 0.95F + RANDOM.nextFloat() * 0.1F, 0.8F);
    }

    /** One dialogue blip; call it every few characters while NPC text is revealed. */
    public static void npcTalk() {
        play(ERSounds.UI_NPC_TALK, 0.92F + RANDOM.nextFloat() * 0.16F, 0.5F);
    }

    /** Plays any registered ETERNAL REALM sound as an interface sound; does nothing if it is not registered. */
    public static void play(RegistryObject<SoundEvent> sound, float pitch, float volume) {
        if (!sound.isPresent()) {
            return;
        }
        Minecraft.getInstance().getSoundManager().play(new SimpleSoundInstance(sound.get().getLocation(),
                SoundSource.MASTER, volume, pitch, SoundInstance.createUnseededRandom(), false, 0,
                SoundInstance.Attenuation.NONE, 0.0D, 0.0D, 0.0D, true));
    }
}
