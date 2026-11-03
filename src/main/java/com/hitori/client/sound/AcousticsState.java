package com.hitori.client.sound;

/**
 * What the acoustics engine (docs/DESIGN.md §10) needs to know about the player, polled once per client tick on the
 * client thread. The game provides it with {@link Acoustics#setState(AcousticsState)} (once, e.g. from its client
 * setup). Until it does, {@link AcousticsModes} reads {@code com.hitori.client.ClientState} reflectively if that class
 * exists (public static no-argument methods {@code inCloset()}, {@code peeking()}, {@code cameraView()} returning
 * boolean - a missing one counts as false), and otherwise falls back to what it can see by itself: the player's eyes
 * inside the layout's {@code closet} room behind the oshiire fusuma (its {@code open} state: 0 shut, 1 crack), and a
 * camera entity other than the player ({@code Minecraft#setCameraEntity}).
 */
public interface AcousticsState {
    /**
     * How shut in the player is: 1 = hiding inside a closed space (the 押し入れ or the western closet with its door
     * shut), 0.5 = peeking through the crack of the fusuma, 0 = not hiding. Values in between are allowed (a fusuma
     * sliding). Everything outside is low-passed accordingly (−18 dB HF at 1), the player's own sounds stay dry.
     */
    float enclosure();

    /**
     * True while the camera app is open and the client view (and with it the sound listener) is a fixed camera:
     * sounds near the camera get the band-limited microphone filter, sounds near the player's body are heard muffled
     * from where the body is, and {@code camera.mic_hiss} plays (see {@link Acoustics#setMicHiss(boolean)}).
     */
    boolean cameraView();
}
