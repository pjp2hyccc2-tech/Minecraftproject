package com.eternalrealm.persistence.file;

import com.eternalrealm.core.data.DataAccessException;
import com.eternalrealm.guild.Guild;
import com.eternalrealm.guild.GuildRepository;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/**
 * The permanent guild directory stored as one document {@code <dataRoot>/guilds.json} (outside {@code seasons/}:
 * guilds survive season resets; their contribution is kept per season inside the document).
 *
 * <p>One document keeps every save atomic for all guilds (a member moving between guilds can never be half-written).
 * At a few hundred guilds of up to 50 members the file stays in the hundreds of kilobytes.</p>
 */
public final class FileGuildRepository implements GuildRepository {
    private final Path dataRoot;
    private final AtomicFileStore store;

    public FileGuildRepository(Path dataRoot, AtomicFileStore store) {
        this.dataRoot = Objects.requireNonNull(dataRoot, "dataRoot");
        this.store = Objects.requireNonNull(store, "store");
    }

    @Override
    public List<Guild> loadAll() throws DataAccessException {
        return store.read(path(), GuildCodec::decode).orElse(List.of());
    }

    @Override
    public void saveAll(List<Guild> guilds) throws DataAccessException {
        store.write(path(), GuildCodec.encode(guilds));
    }

    Path path() {
        return dataRoot.resolve("guilds.json");
    }
}
