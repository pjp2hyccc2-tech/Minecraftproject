package com.eternalrealm.player;

import com.eternalrealm.character.CharacterRecord;
import com.eternalrealm.character.CharacterRepository;
import com.eternalrealm.core.data.DataAccessException;
import com.eternalrealm.season.SeasonId;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;

/** Thread-safe in-memory repositories with failure injection and gates for ordering tests. */
final class InMemoryRepositories {
    private InMemoryRepositories() {
    }

    static final class Characters implements CharacterRepository {
        final Map<String, CharacterRecord> stored = new ConcurrentHashMap<>();
        final AtomicInteger saves = new AtomicInteger();
        final AtomicInteger failNextSaves = new AtomicInteger();
        volatile boolean failLoads;
        volatile CountDownLatch saveGate;
        /** Only saves matching this filter wait at {@link #saveGate}. */
        volatile Predicate<CharacterRecord> saveGateFilter = record -> true;
        volatile CountDownLatch loadGate;

        static String key(UUID playerId, SeasonId season) {
            return season.value() + "/" + playerId;
        }

        @Override
        public Optional<CharacterRecord> load(UUID playerId, SeasonId season) throws DataAccessException {
            await(loadGate);
            if (failLoads) {
                throw new DataAccessException("injected load failure");
            }
            return Optional.ofNullable(stored.get(key(playerId, season)));
        }

        @Override
        public void save(CharacterRecord record) throws DataAccessException {
            if (saveGateFilter.test(record)) {
                await(saveGate);
            }
            if (failNextSaves.getAndUpdate(n -> Math.max(0, n - 1)) > 0) {
                throw new DataAccessException("injected save failure");
            }
            stored.put(key(record.playerId(), record.seasonId()), record);
            saves.incrementAndGet();
        }

        private static void await(CountDownLatch gate) throws DataAccessException {
            if (gate == null) {
                return;
            }
            try {
                if (!gate.await(10, TimeUnit.SECONDS)) {
                    throw new DataAccessException("gate timeout");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new DataAccessException("interrupted");
            }
        }
    }

    static final class Accounts implements AccountRepository {
        final Map<UUID, AccountRecord> stored = new ConcurrentHashMap<>();

        @Override
        public Optional<AccountRecord> load(UUID playerId) {
            return Optional.ofNullable(stored.get(playerId));
        }

        @Override
        public void save(AccountRecord record) {
            stored.put(record.playerId(), record);
        }
    }
}
