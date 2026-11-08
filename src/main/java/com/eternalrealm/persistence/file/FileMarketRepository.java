package com.eternalrealm.persistence.file;

import com.eternalrealm.core.data.DataAccessException;
import com.eternalrealm.economy.MarketData;
import com.eternalrealm.economy.MarketRepository;
import com.eternalrealm.season.SeasonId;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

/**
 * A season's market (listings and mailboxes) stored as {@code <dataRoot>/seasons/<seasonId>/market.json}.
 *
 * <p>Season-scoped on purpose: listed items and pending gold belong to the same economy as the characters' wallets and
 * inventories, which are season data. A season reset therefore resets the market with them, and a configured
 * carry-over must migrate the market together with the characters (docs/modules/party.md).</p>
 */
public final class FileMarketRepository implements MarketRepository {
    private final Path dataRoot;
    private final AtomicFileStore store;

    public FileMarketRepository(Path dataRoot, AtomicFileStore store) {
        this.dataRoot = Objects.requireNonNull(dataRoot, "dataRoot");
        this.store = Objects.requireNonNull(store, "store");
    }

    @Override
    public Optional<MarketData> load(SeasonId season) throws DataAccessException {
        return store.read(pathFor(season), json -> MarketCodec.decode(json, season));
    }

    @Override
    public void save(MarketData data) throws DataAccessException {
        store.write(pathFor(data.season()), MarketCodec.encode(data));
    }

    /** SeasonId cannot contain path separators, so the path cannot escape {@code dataRoot}. */
    Path pathFor(SeasonId season) {
        return dataRoot.resolve("seasons").resolve(season.value()).resolve("market.json");
    }
}
