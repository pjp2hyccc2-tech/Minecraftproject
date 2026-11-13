package com.eternalrealm.dungeon;

import com.eternalrealm.world.AreaDefinition;
import com.eternalrealm.world.Areas;
import com.eternalrealm.world.WorldProgress;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * The gatekeeper's board (the 転移門 screen, docs/modules/boss.md section 4): the eight dungeons and the final raid as
 * a player sees them, which one the screen opens on, and whether the player may set out. Pure: built from the
 * season's {@link WorldProgress}; the Forge side adds the player's party and records, sends it and starts the run.
 */
public final class GateBoard {
    /** A dungeon this many levels above the player is still the one the screen opens on. */
    public static final int LEVEL_SLACK = 2;
    /** How long a screen the gatekeeper opened can still send the party off (5 minutes). */
    public static final long SESSION_TICKS = 20L * 60 * 5;
    /** How far the player may stray from where they spoke to the gatekeeper and still set out (blocks). */
    public static final double SESSION_RANGE = 8.0;

    private GateBoard() {
    }

    /** Why the player may not set out from the screen; the server checks again when they try. */
    public enum Refusal {
        NONE(""),
        /** The dungeon's area (or, for the final raid, any dungeon boss) has not been beaten yet this season. */
        SEALED("sealed"),
        /** Only the party leader starts a run. */
        NOT_LEADER("leader"),
        /** The party is smaller or larger than the dungeon allows. */
        PARTY_SIZE("party_size");

        private final String id;

        Refusal(String id) {
            this.id = id;
        }

        /** Wire id ("" for none). */
        public String id() {
            return id;
        }
    }

    /**
     * One line of the board.
     *
     * @param id             "d1".."d8" or "final"
     * @param rooms          combat rooms before the boss (0 for the raid: it is the arena alone)
     * @param open           the season has opened it
     * @param conquered      its boss has fallen at least once this season (by anyone)
     * @param unlockBossId   while sealed: the boss whose first defeat opens it ({@code null} for the raid, which needs
     *                       every dungeon boss, and for open entries)
     */
    public record Entry(String id, String nameKey, int areaId, String bossId, int recommendedLevel, int minParty,
                        int maxParty, long timeLimitTicks, int rooms, boolean raid, boolean open, boolean conquered,
                        String unlockBossId) {
        public Entry {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(nameKey, "nameKey");
            Objects.requireNonNull(bossId, "bossId");
        }
    }

    /**
     * Where a player opened the screen: setting out is accepted for {@link #SESSION_TICKS} while they stay within
     * {@link #SESSION_RANGE} of that spot in the same dimension.
     */
    public record Session(long openedTick, String dimension, double x, double y, double z) {
        public Session {
            Objects.requireNonNull(dimension, "dimension");
        }

        public boolean allows(long now, String dimension, double x, double y, double z) {
            if (now < openedTick || now - openedTick > SESSION_TICKS || !this.dimension.equals(dimension)) {
                return false;
            }
            double dx = x - this.x;
            double dy = y - this.y;
            double dz = z - this.z;
            return dx * dx + dy * dy + dz * dz <= SESSION_RANGE * SESSION_RANGE;
        }

        public boolean expired(long now) {
            return now < openedTick || now - openedTick > SESSION_TICKS;
        }
    }

    /** The eight dungeons in order, then the final raid. */
    public static List<Entry> entries(WorldProgress progress) {
        Objects.requireNonNull(progress, "progress");
        List<Entry> entries = new ArrayList<>();
        for (DungeonDefinition dungeon : Dungeons.all()) {
            boolean open = progress.isAreaUnlocked(dungeon.areaId());
            String unlock = open ? null : Areas.byId(dungeon.areaId()).map(AreaDefinition::unlockedByBossId)
                    .orElse(null);
            entries.add(new Entry(dungeon.id(), dungeon.translationKey(), dungeon.areaId(), dungeon.bossId(),
                    dungeon.recommendedLevel(), dungeon.minPartySize(), dungeon.maxPartySize(),
                    dungeon.timeLimitTicks(), dungeon.roomCount(), false, open,
                    progress.isBossDefeated(dungeon.bossId()), unlock));
        }
        RaidDefinition raid = Dungeons.FINAL_RAID;
        boolean open = progress.isAreaUnlocked(raid.areaId()) && progress.finalBossUnlocked();
        entries.add(new Entry(raid.id(), raid.translationKey(), raid.areaId(), raid.bossId(), raid.recommendedLevel(),
                raid.minPartySize(), raid.maxPartySize(), raid.timeLimitTicks(), 0, true, open,
                progress.isBossDefeated(raid.bossId()), null));
        return List.copyOf(entries);
    }

    public static Optional<Entry> find(List<Entry> entries, String id) {
        if (id == null) {
            return Optional.empty();
        }
        for (Entry entry : entries) {
            if (entry.id().equals(id)) {
                return Optional.of(entry);
            }
        }
        return Optional.empty();
    }

    /**
     * The entry the screen opens on: the dungeon of the entrance the gatekeeper guards; else the one the player ran
     * last, if it is open; else the hardest open dungeon their level is ready for (up to {@link #LEVEL_SLACK} above
     * it); else the first open one.
     */
    public static String focus(List<Entry> entries, String entranceId, String lastRunId, int playerLevel) {
        if (entries.isEmpty()) {
            throw new IllegalArgumentException("no entries");
        }
        Optional<Entry> entrance = find(entries, entranceId);
        if (entrance.isPresent()) {
            return entrance.get().id();
        }
        Optional<Entry> last = find(entries, lastRunId);
        if (last.isPresent() && last.get().open()) {
            return last.get().id();
        }
        Entry fit = null;
        for (Entry entry : entries) {
            if (entry.open() && !entry.raid() && entry.recommendedLevel() <= playerLevel + LEVEL_SLACK
                    && (fit == null || entry.recommendedLevel() >= fit.recommendedLevel())) {
                fit = entry;
            }
        }
        if (fit != null) {
            return fit.id();
        }
        for (Entry entry : entries) {
            if (entry.open()) {
                return entry.id();
            }
        }
        return entries.get(0).id();
    }

    /**
     * Whether the player may set out for {@code entry} from the screen.
     *
     * @param inParty         the player is in a party (a solo player is their own leader)
     * @param partySize       members including the player (1 when solo)
     * @param ignorePartySize the server lets any party size in ({@code dungeons.ignorePartySize})
     */
    public static Refusal check(Entry entry, boolean inParty, boolean leader, int partySize, boolean ignorePartySize) {
        if (!entry.open()) {
            return Refusal.SEALED;
        }
        if (inParty && !leader) {
            return Refusal.NOT_LEADER;
        }
        if (!ignorePartySize && (partySize < entry.minParty() || partySize > entry.maxParty())) {
            return Refusal.PARTY_SIZE;
        }
        return Refusal.NONE;
    }

    /** Dungeon bosses ({@code b1}..{@code b8}) that have fallen this season: the final raid opens at all of them. */
    public static int conqueredDungeons(WorldProgress progress) {
        int count = 0;
        for (String boss : Areas.regularBossIds()) {
            if (progress.isBossDefeated(boss)) {
                count++;
            }
        }
        return count;
    }
}
