package com.eternalrealm.client.block;

import com.eternalrealm.config.ERClientConfig;

/** Client settings of the town's moving scenery. */
public final class Scenery {
    private Scenery() {
    }

    /** Whether flags, banners and bunting wave (setting {@code scenery.flagAnimation}; on until the config loads). */
    public static boolean flagsWave() {
        try {
            return ERClientConfig.FLAG_ANIMATION.get();
        } catch (IllegalStateException e) {
            return true;
        }
    }
}
