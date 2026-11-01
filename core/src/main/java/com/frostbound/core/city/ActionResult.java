package com.frostbound.core.city;

import com.frostbound.core.util.Msg;

/** Result of a player command: success or a translatable reason. */
public record ActionResult(boolean ok, Msg msg) {
    public static ActionResult ok(String key, Object... args) {
        return new ActionResult(true, Msg.of(key, args));
    }

    public static ActionResult fail(String key, Object... args) {
        return new ActionResult(false, Msg.of(key, args));
    }
}
