package com.eternalrealm.estate;

import com.eternalrealm.core.data.DataAccessException;

import java.util.Optional;

/** Storage of the estate registry (one document; file implementation: {@code FileEstateRepository}). */
public interface EstateRepository {
    /** The stored registry, or empty when nothing was stored yet. */
    Optional<EstateData> load() throws DataAccessException;

    void save(EstateData data) throws DataAccessException;
}
