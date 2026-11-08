package com.eternalrealm.ranking;

import com.eternalrealm.core.ModInfo;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * The additive counters of {@link SeasonStats}: everything a {@link SeasonStatsTracker} increments. {@code level} and
 * {@code totalExperience} are not counters; they mirror the character and are set, not added.
 *
 * <p>{@link #id()} is the stable persistence key and equals the {@link SeasonStats} component name. Never rename or
 * reuse an id; append new counters at the end. Stored data that lacks a counter reads it as 0
 * ({@link SeasonStatsCodec}), so adding a counter needs no schema change.</p>
 */
public enum SeasonCounter {
    /** Monsters the player got the kill credit for, elites included. Bosses are counted in {@link #BOSSES_KILLED}. */
    MONSTERS_KILLED("monstersKilled"),
    /** Elite monsters killed; every elite kill is also a {@link #MONSTERS_KILLED} kill. */
    ELITE_KILLS("eliteKills"),
    /** Bosses defeated, counted for every participant of the fight. */
    BOSSES_KILLED("bossesKilled"),
    /** Damage dealt to monsters in tenths of a damage point, so that fractional damage adds up exactly. */
    DAMAGE_DEALT("damageDealt"),
    DEATHS("deaths"),
    /** Every dungeon clear, solo or in a party. */
    DUNGEON_CLEARS("dungeonClears"),
    /** Dungeon clears with at least {@link SeasonStatsTracker#PARTY_MIN_MEMBERS} members (also in {@link #DUNGEON_CLEARS}). */
    PARTY_DUNGEON_CLEARS("partyDungeonClears"),
    QUESTS_COMPLETED("questsCompleted"),
    AREAS_DISCOVERED("areasDiscovered"),
    /** Blocks travelled. */
    DISTANCE_TRAVELLED("distanceTravelled"),
    ITEMS_CRAFTED("itemsCrafted"),
    PVP_WINS("pvpWins"),
    PVP_LOSSES("pvpLosses"),
    /** Guild contribution points; the guild ranking sums them over the guild's members. */
    GUILD_CONTRIBUTION("guildContribution");

    private static final List<SeasonCounter> ALL = List.of(values());

    private final String id;

    SeasonCounter(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    /** Label for statistics screens, e.g. {@code season_stat.eternalrealm.monsters_killed}. */
    public String translationKey() {
        return "season_stat." + ModInfo.MOD_ID + "." + name().toLowerCase(Locale.ROOT);
    }

    /** All counters in declaration order (cached; {@code values()} allocates a new array per call). */
    public static List<SeasonCounter> all() {
        return ALL;
    }

    public static Optional<SeasonCounter> byId(String id) {
        for (SeasonCounter counter : ALL) {
            if (counter.id.equals(id)) {
                return Optional.of(counter);
            }
        }
        return Optional.empty();
    }
}
