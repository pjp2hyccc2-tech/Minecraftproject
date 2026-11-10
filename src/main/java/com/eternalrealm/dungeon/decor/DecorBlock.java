package com.eternalrealm.dungeon.decor;

import java.util.Objects;

/**
 * One block of an instance's decoration, in layout coordinates (relative to the instance origin, like the build plan).
 *
 * @param state block state in the command syntax ({@code minecraft:lantern[hanging=true]}), or {@code #role} for the
 *              theme's palette block of a {@link com.eternalrealm.dungeon.BlockRole} ({@code #wall}, {@code #floor})
 * @param nbt   block entity data in SNBT (sign text), or null
 */
public record DecorBlock(int x, int y, int z, String state, String nbt) {
    public DecorBlock {
        Objects.requireNonNull(state, "state");
    }

    /** Whether the state names a palette role ({@code #wall}) rather than a block. */
    public boolean isRole() {
        return state.startsWith("#");
    }
}
