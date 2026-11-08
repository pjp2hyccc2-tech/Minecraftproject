package com.eternalrealm.guild;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Outcome of a {@link GuildService} operation.
 *
 * @param guild  on success, the affected guild after the operation; empty on failure and after a disband
 * @param events what happened, in order; a failure can still close an invite found to be expired
 */
public record GuildResult(GuildOutcome outcome, Optional<Guild> guild, List<GuildEvent> events) {
    public GuildResult {
        Objects.requireNonNull(outcome, "outcome");
        Objects.requireNonNull(guild, "guild");
        events = List.copyOf(events);
        if (!outcome.isSuccess() && guild.isPresent()) {
            throw new IllegalArgumentException("a failed operation carries no guild");
        }
    }

    static GuildResult ok(Guild guild, List<GuildEvent> events) {
        return new GuildResult(GuildOutcome.OK, Optional.ofNullable(guild), events);
    }

    static GuildResult failure(GuildOutcome outcome, List<GuildEvent> events) {
        return new GuildResult(outcome, Optional.empty(), events);
    }

    static GuildResult failure(GuildOutcome outcome) {
        return failure(outcome, List.of());
    }

    public boolean isSuccess() {
        return outcome.isSuccess();
    }
}
