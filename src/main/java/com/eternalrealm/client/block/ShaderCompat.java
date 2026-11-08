package com.eternalrealm.client.block;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.lang.reflect.Method;

/**
 * Whether a shader pack is active (Oculus/Iris), through the Iris API by reflection so neither is required. Real
 * shader light shafts make our fake beams unnecessary.
 */
public final class ShaderCompat {
    private static final Logger LOG = LogUtils.getLogger();
    private static Object api;
    private static Method inUse;
    private static boolean resolved;
    private static boolean cached;
    private static long checkedAt = Long.MIN_VALUE;

    private ShaderCompat() {
    }

    /** Re-checked at most every 20 ms of wall time. */
    public static boolean shaderPackInUse() {
        long now = System.nanoTime();
        if (now - checkedAt < 20_000_000L) {
            return cached;
        }
        checkedAt = now;
        cached = query();
        return cached;
    }

    private static boolean query() {
        if (!resolved) {
            resolved = true;
            for (String name : new String[] {"net.irisshaders.iris.api.v0.IrisApi", "net.coderbot.iris.api.v0.IrisApi"}) {
                try {
                    Class<?> type = Class.forName(name);
                    api = type.getMethod("getInstance").invoke(null);
                    inUse = type.getMethod("isShaderPackInUse");
                    LOG.info("Shader API found ({}): sunbeams yield to the shader's light shafts", name);
                    break;
                } catch (ReflectiveOperationException | LinkageError ignored) {
                    // not installed
                }
            }
        }
        if (inUse == null) {
            return false;
        }
        try {
            return (Boolean) inUse.invoke(api);
        } catch (ReflectiveOperationException | RuntimeException e) {
            inUse = null;
            return false;
        }
    }
}
