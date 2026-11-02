package com.meikon.game;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/** A hiding closet: {@code inside} is the 1×1 space behind the door, {@code front} the lower half of the door. */
public record Closet(int index, BlockPos inside, BlockPos front, Direction facing) {
    public boolean contains(BlockPos feet) {
        return feet.equals(inside) || feet.equals(front);
    }
}
