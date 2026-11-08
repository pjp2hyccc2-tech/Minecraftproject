package com.eternalrealm.monster;

import com.eternalrealm.world.LevelRange;

import java.util.List;
import java.util.Objects;
import java.util.random.RandomGenerator;

/**
 * Which monsters populate an area ({@link com.eternalrealm.world.AreaDefinition#monsterTableId()}).
 *
 * <p>{@link #pick}: with probability {@code eliteChance} the pick comes from the elite entries, otherwise from the
 * normal entries; within the pool an entry is chosen by weight. The level is a uniform level of the area's range plus
 * the entry's offset, clamped into the template's own level range.</p>
 *
 * @param entries      NORMAL templates
 * @param eliteChance  probability in {@code [0, 1]} that a spawn is an elite
 * @param eliteEntries ELITE templates; required when {@code eliteChance > 0}
 */
public record MonsterTable(String id, List<Entry> entries, double eliteChance, List<Entry> eliteEntries) {
    /**
     * @param weight      relative weight within its pool
     * @param levelOffset added to the rolled area level (e.g. +1 for a tougher monster)
     */
    public record Entry(MonsterTemplate template, int weight, int levelOffset) {
        public Entry {
            Objects.requireNonNull(template, "template");
            Check.range("weight", weight, 1, 10_000);
            Check.range("levelOffset", levelOffset, -20, 20);
        }
    }

    public MonsterTable {
        Check.id("id", id);
        entries = List.copyOf(entries);
        eliteEntries = List.copyOf(eliteEntries);
        if (entries.isEmpty()) {
            throw new IllegalArgumentException(id + ": a monster table needs at least one entry");
        }
        requireRole(id, entries, MonsterRole.NORMAL);
        requireRole(id, eliteEntries, MonsterRole.ELITE);
        Check.range("eliteChance", eliteChance, 0.0, 1.0);
        if (eliteChance > 0.0 && eliteEntries.isEmpty()) {
            throw new IllegalArgumentException(id + ": an elite chance needs elite entries");
        }
    }

    public MonsterSpawn pick(LevelRange areaLevels, RandomGenerator random) {
        Objects.requireNonNull(areaLevels, "areaLevels");
        Objects.requireNonNull(random, "random");
        boolean elite = eliteChance > 0.0 && random.nextDouble() < eliteChance;
        Entry entry = weighted(elite ? eliteEntries : entries, random);
        int level = entry.template().levels().clamp(areaLevels.random(random) + entry.levelOffset());
        return new MonsterSpawn(entry.template(), level);
    }

    private static Entry weighted(List<Entry> pool, RandomGenerator random) {
        int total = 0;
        for (Entry entry : pool) {
            total += entry.weight();
        }
        int roll = random.nextInt(total);
        for (Entry entry : pool) {
            if (roll < entry.weight()) {
                return entry;
            }
            roll -= entry.weight();
        }
        throw new IllegalStateException("weights are positive, so the roll always lands on an entry");
    }

    private static void requireRole(String id, List<Entry> pool, MonsterRole role) {
        for (Entry entry : pool) {
            if (entry.template().role() != role) {
                throw new IllegalArgumentException(id + ": " + entry.template().id() + " must be a " + role + " template");
            }
        }
    }
}
