package com.frostbound.core.city;

import com.frostbound.core.util.Msg;

/** A message for players right now (toast and sound); drained by the Forge layer every tick. */
public record Notice(Kind kind, Msg msg, String sound) {
    public enum Kind {
        INFO,
        GOOD,
        WARNING,
        MAJOR
    }
}
