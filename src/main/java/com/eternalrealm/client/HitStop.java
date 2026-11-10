package com.eternalrealm.client;

/**
 * A short freeze of the local player's own attack animation when a skill lands (hit-stop): the swing holds for a few
 * ticks, then goes on from where it stopped. Purely visual (the action's timing on the server is unchanged). Client
 * thread only; respects the camera shake switch of the client config.
 */
public final class HitStop {
    private static long startTick = Long.MIN_VALUE;
    private static int length;

    private HitStop() {
    }

    /** Freezes the local animation for {@code ticks} from now (a longer freeze in progress is kept). */
    public static void trigger(int ticks) {
        if (!com.eternalrealm.config.ERClientConfig.CAMERA_SHAKE.get()) {
            return;
        }
        long now = ClientCombatState.tick();
        if (now < startTick + length && startTick + length >= now + ticks) {
            return;
        }
        startTick = now;
        length = Math.max(0, ticks);
    }

    /**
     * The local animation's elapsed time with the freeze taken out: between the start of the freeze and its end the
     * elapsed time stays at its value at the start.
     */
    static float adjust(long tick, float partialTick, float elapsed) {
        if (length <= 0) {
            return elapsed;
        }
        float since = tick - startTick + partialTick;
        if (since <= 0.0F) {
            return elapsed;
        }
        return elapsed - Math.min(since, length);
    }

    static void clear() {
        startTick = Long.MIN_VALUE;
        length = 0;
    }
}
