package com.eternalrealm.monster;

import java.util.List;

/**
 * What a monster may drop when killed by a player ({@link LootRoller}).
 *
 * @param entries   items, each rolled independently
 * @param equipment the equipment roll, or {@code null} if the table never drops equipment
 */
public record DropTable(String id, List<DropEntry> entries, EquipmentRoll equipment) {
    public static final int MAX_ENTRIES = 16;

    public DropTable {
        Check.id("id", id);
        entries = List.copyOf(entries);
        Check.range("entries", entries.size(), 0, MAX_ENTRIES);
    }

    /** A table that never drops anything (boss summons). */
    public static DropTable empty(String id) {
        return new DropTable(id, List.of(), null);
    }

    public boolean dropsEquipment() {
        return equipment != null;
    }

    public boolean isEmpty() {
        return entries.isEmpty() && equipment == null;
    }
}
