package com.meikon.game;

/**
 * Game values mirrored to the logical client (set by the client packet handler). Common code may read these
 * ONLY when running on the client side ({@code level.isClientSide}); the server never uses them.
 */
public final class ClientView {
    public static volatile boolean inGame;
    public static volatile int phase;
    public static volatile int bells;
    public static volatile boolean kuraUnlocked;
    public static volatile boolean ritualReady;

    private ClientView() {}

    public static void reset() {
        inGame = false;
        phase = 0;
        bells = 0;
        kuraUnlocked = false;
        ritualReady = false;
    }
}
