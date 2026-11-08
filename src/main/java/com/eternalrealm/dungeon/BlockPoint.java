package com.eternalrealm.dungeon;

/**
 * A block position relative to an instance origin (layouts never know where they are placed). The platform adds the
 * origin of the {@link InstanceSlot} and its base height.
 */
public record BlockPoint(int x, int y, int z) {
    public BlockPoint offset(int dx, int dy, int dz) {
        return new BlockPoint(x + dx, y + dy, z + dz);
    }
}
