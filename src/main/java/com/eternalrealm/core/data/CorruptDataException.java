package com.eternalrealm.core.data;

/**
 * Stored data exists but cannot be parsed or violates basic invariants.
 * Storage implementations may try an older generation/backup before surfacing this.
 */
public class CorruptDataException extends DataAccessException {
    private static final long serialVersionUID = 1L;

    public CorruptDataException(String message) {
        super(message);
    }

    public CorruptDataException(String message, Throwable cause) {
        super(message, cause);
    }
}
