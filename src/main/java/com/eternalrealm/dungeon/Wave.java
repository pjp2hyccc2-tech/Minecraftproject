package com.eternalrealm.dungeon;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Monsters that spawn together; the next wave of the room spawns when all of them are dead. */
public record Wave(List<MonsterGroup> groups) {
    public Wave {
        groups = List.copyOf(groups);
        if (groups.isEmpty()) {
            throw new IllegalArgumentException("a wave needs at least one monster group");
        }
        Set<String> templates = new HashSet<>();
        for (MonsterGroup group : groups) {
            if (!templates.add(group.templateId())) {
                throw new IllegalArgumentException("template listed twice in a wave: " + group.templateId());
            }
        }
    }

    public static Wave of(MonsterGroup... groups) {
        return new Wave(List.of(groups));
    }

    /** The monsters to spawn for a party of {@code partySize}, in group order. */
    public List<MonsterCount> spawnsFor(int partySize) {
        List<MonsterCount> spawns = new ArrayList<>(groups.size());
        for (MonsterGroup group : groups) {
            spawns.add(new MonsterCount(group.templateId(), group.countFor(partySize)));
        }
        return List.copyOf(spawns);
    }

    /** Total number of monsters for a party of {@code partySize}. */
    public int totalFor(int partySize) {
        int total = 0;
        for (MonsterGroup group : groups) {
            total += group.countFor(partySize);
        }
        return total;
    }
}
