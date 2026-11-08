package com.eternalrealm.monster;

import java.util.Objects;

/** A monster the spawner should create: which template at which level. */
public record MonsterSpawn(MonsterTemplate template, int level) {
    public MonsterSpawn {
        Objects.requireNonNull(template, "template");
        Check.range("level", level, 1, MonsterScaling.MAX_LEVEL);
    }

    public MonsterStats stats() {
        return MonsterScaling.stats(template, level);
    }
}
