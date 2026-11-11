package com.eternalrealm.persistence.file;

import com.eternalrealm.core.data.DataAccessException;
import com.eternalrealm.guild.house.GuildHouse;
import com.eternalrealm.guild.house.GuildHouseRepository;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/**
 * The guild houses' data as one document {@code <dataRoot>/guild_houses.json} (outside {@code seasons/}: like the
 * guilds, their houses survive season resets).
 */
public final class FileGuildHouseRepository implements GuildHouseRepository {
    private final Path dataRoot;
    private final AtomicFileStore store;

    public FileGuildHouseRepository(Path dataRoot, AtomicFileStore store) {
        this.dataRoot = Objects.requireNonNull(dataRoot, "dataRoot");
        this.store = Objects.requireNonNull(store, "store");
    }

    @Override
    public List<GuildHouse> loadAll() throws DataAccessException {
        return store.read(path(), GuildHouseCodec::decode).orElse(List.of());
    }

    @Override
    public void saveAll(List<GuildHouse> houses) throws DataAccessException {
        store.write(path(), GuildHouseCodec.encode(houses));
    }

    Path path() {
        return dataRoot.resolve("guild_houses.json");
    }
}
