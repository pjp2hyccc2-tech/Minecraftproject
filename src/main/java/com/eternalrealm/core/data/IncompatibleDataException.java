package com.eternalrealm.core.data;

/**
 * Stored data was written by a newer, unsupported version of the mod (schema version too high).
 * Unlike {@link CorruptDataException} the data must <em>not</em> be quarantined or replaced:
 * it is valid, this server is simply too old to read it.
 */
public class IncompatibleDataException extends DataAccessException {
    private static final long serialVersionUID = 1L;

    public IncompatibleDataException(String message) {
        super(message);
    }
}
