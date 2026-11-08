package com.eternalrealm.core.data;

/**
 * Failure while reading or writing persistent data (I/O error, database error, ...).
 * Callers must treat the data as <em>unavailable</em>, never as "absent": creating fresh data
 * after this exception would overwrite the player's real progress.
 */
public class DataAccessException extends Exception {
    private static final long serialVersionUID = 1L;

    public DataAccessException(String message) {
        super(message);
    }

    public DataAccessException(String message, Throwable cause) {
        super(message, cause);
    }
}
