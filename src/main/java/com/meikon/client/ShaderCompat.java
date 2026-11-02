package com.meikon.client;

import java.lang.reflect.Method;

/** Detects whether an Iris/Oculus shader pack is currently in use (via the Iris API, reflectively). */
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

    private static boolean query() {
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
