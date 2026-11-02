package com.meikon.client;

import java.lang.reflect.Method;

/** Detects whether a shader pack is currently in use: Iris/Oculus via the Iris API, OptiFine via its Shaders class (both reflectively). */
public final class ShaderCompat {
    private static Object api;
    private static Method inUse;
    private static boolean looked;
    private static boolean cached;
    private static long cachedAt = Long.MIN_VALUE;

    private ShaderCompat() {}

    public static boolean active() {
        long now = ClientState.clientTicks;
        if (now - cachedAt < 20 && cachedAt != Long.MIN_VALUE) return cached;
        cachedAt = now;
        cached = query();
        return cached;
    }

    private static java.lang.reflect.Field optifineLoaded;
    private static boolean lookedOptifine;

    private static boolean query() {
        return queryIris() || queryOptifine();
    }

    private static boolean queryOptifine() {
        try {
            if (!lookedOptifine) {
                lookedOptifine = true;
                optifineLoaded = Class.forName("net.optifine.shaders.Shaders").getField("shaderPackLoaded");
            }
            return optifineLoaded != null && optifineLoaded.getBoolean(null);
        } catch (Throwable t) {
            optifineLoaded = null;
            return false;
        }
    }

    private static boolean queryIris() {
        try {
            if (!looked) {
                looked = true;
                Class<?> c = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
                api = c.getMethod("getInstance").invoke(null);
                inUse = c.getMethod("isShaderPackInUse");
            }
            if (api == null || inUse == null) return false;
            return (Boolean) inUse.invoke(api);
        } catch (Throwable t) {
            api = null;
            inUse = null;
            return false;
        }
    }
}
