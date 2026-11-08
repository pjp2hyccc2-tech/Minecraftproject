package com.eternalrealm.dungeon;

/** {@code count} monsters of a template owned by the monster module, already scaled to the party size. */
public record MonsterCount(String templateId, int count) {
    public MonsterCount {
        Check.id("templateId", templateId);
        Check.range("count", count, 1, 1000);
    }
}
