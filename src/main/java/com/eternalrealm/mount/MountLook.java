package com.eternalrealm.mount;

import java.util.Objects;

/**
 * What one mount looks like: its coat (the model's texture variant) and its shape (mane or wings). The ids come from
 * {@link MountLooks}. Immutable.
 */
public record MountLook(String coat, String shape) {
    public MountLook {
        Objects.requireNonNull(coat, "coat");
        Objects.requireNonNull(shape, "shape");
    }

    /** {@code coat/shape}: the form synced to clients and kept in deeds. */
    public String encode() {
        return coat + "/" + shape;
    }

    /** Reads {@link #encode()}; a malformed text gives empty ids (callers fall back to the classic look). */
    public static MountLook decode(String text) {
        if (text == null) {
            return new MountLook("", "");
        }
        int slash = text.indexOf('/');
        return slash < 0 ? new MountLook(text, "") : new MountLook(text.substring(0, slash), text.substring(slash + 1));
    }
}
