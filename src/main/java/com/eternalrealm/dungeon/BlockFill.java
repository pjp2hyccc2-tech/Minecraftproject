package com.eternalrealm.dungeon;

import java.util.Objects;

/** One build step: fill every block of {@code box} with the block of {@code role}. Later steps overwrite earlier ones. */
public record BlockFill(BlockBox box, BlockRole role) {
    public BlockFill {
        Objects.requireNonNull(box, "box");
        Objects.requireNonNull(role, "role");
    }
}
