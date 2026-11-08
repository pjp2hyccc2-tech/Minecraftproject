package com.eternalrealm.world;

import com.eternalrealm.season.SeasonId;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Server-wide story progress of one season: which bosses have been defeated (first defeat time per boss) and, derived
 * from that, which areas are open for everyone. Area 1 is always open; area {@code N} opens with the first defeat of its
 * {@link AreaDefinition#unlockedByBossId() unlocking boss}; the final boss is unlocked once {@code b1}..{@code b8} have
 * fallen. Progress never goes backwards within a season and resets with the season (a new season has its own file).
 *
 * <p><b>Threading:</b> mutable and confined to the server thread; persistence receives immutable
 * {@link WorldProgressRecord}s from {@link #toRecord()}. Every change increments {@link #revision()}; the state is dirty
 * while {@code revision > savedRevision}.</p>
 */
public final class WorldProgress {
    private final SeasonId seasonId;
    private final Map<String, Instant> firstDefeats = new TreeMap<>();
    private long revision;
    private long savedRevision;

    private WorldProgress(SeasonId seasonId) {
        this.seasonId = Objects.requireNonNull(seasonId, "seasonId");
    }

    /** Start of a season: only the starting area is open. Not dirty (there is nothing worth saving yet). */
    public static WorldProgress fresh(SeasonId seasonId) {
        return new WorldProgress(seasonId);
    }

    public static WorldProgress fromRecord(WorldProgressRecord record) {
        WorldProgress progress = new WorldProgress(record.seasonId());
        progress.firstDefeats.putAll(record.bossFirstDefeats());
        return progress;
    }

    public WorldProgressRecord toRecord() {
        return new WorldProgressRecord(seasonId, firstDefeats);
    }

    /**
     * Records that {@code bossId} was defeated (by anyone) at {@code at}. Only the first defeat counts; it is idempotent
     * afterwards, so the Forge layer may report every kill.
     *
     * @throws IllegalArgumentException for an id that is not in {@link Areas#bossIds()} (a programming error)
     */
    public BossDefeatResult recordBossDefeat(String bossId, Instant at) {
        Objects.requireNonNull(at, "at");
        if (!Areas.isBossId(bossId)) {
            throw new IllegalArgumentException("Unknown boss id: " + bossId);
        }
        if (firstDefeats.containsKey(bossId)) {
            return new BossDefeatResult(bossId, false, List.of(), false);
        }
        Set<Integer> unlockedBefore = unlockedAreaIds();
        boolean finalUnlockedBefore = finalBossUnlocked();
        firstDefeats.put(bossId, at);
        revision++;
        List<AreaDefinition> newlyUnlocked = new ArrayList<>();
        for (AreaDefinition area : Areas.all()) {
            if (isAreaUnlocked(area.id()) && !unlockedBefore.contains(area.id())) {
                newlyUnlocked.add(area);
            }
        }
        return new BossDefeatResult(bossId, true, newlyUnlocked, !finalUnlockedBefore && finalBossUnlocked());
    }

    public boolean isBossDefeated(String bossId) {
        return firstDefeats.containsKey(bossId);
    }

    public Optional<Instant> firstDefeat(String bossId) {
        return Optional.ofNullable(firstDefeats.get(bossId));
    }

    /** Sorted, read-only. */
    public Set<String> defeatedBossIds() {
        return Collections.unmodifiableSet(new TreeSet<>(firstDefeats.keySet()));
    }

    /** @return {@code false} for unknown area ids */
    public boolean isAreaUnlocked(int areaId) {
        Optional<AreaDefinition> area = Areas.byId(areaId);
        if (area.isEmpty()) {
            return false;
        }
        String boss = area.get().unlockedByBossId();
        return boss == null || firstDefeats.containsKey(boss);
    }

    /** Sorted, read-only. */
    public Set<Integer> unlockedAreaIds() {
        Set<Integer> ids = new TreeSet<>();
        for (AreaDefinition area : Areas.all()) {
            if (isAreaUnlocked(area.id())) {
                ids.add(area.id());
            }
        }
        return Collections.unmodifiableSet(ids);
    }

    public List<AreaDefinition> unlockedAreas() {
        return Areas.all().stream().filter(area -> isAreaUnlocked(area.id())).toList();
    }

    /** All dungeon bosses ({@code b1}..{@code b8}) have been defeated, so 終焉の王 may be challenged. */
    public boolean finalBossUnlocked() {
        return firstDefeats.keySet().containsAll(Areas.regularBossIds());
    }

    public boolean finalBossDefeated() {
        return firstDefeats.containsKey(Areas.FINAL_BOSS_ID);
    }

    public SeasonId seasonId() {
        return seasonId;
    }

    public long revision() {
        return revision;
    }

    public boolean isDirty() {
        return revision > savedRevision;
    }

    /** Records that {@code persistedRevision} has been durably stored. Never moves backwards. */
    public void markSaved(long persistedRevision) {
        if (persistedRevision > savedRevision) {
            savedRevision = persistedRevision;
        }
    }
}
