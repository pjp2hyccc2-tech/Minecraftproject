package com.eternalrealm.dungeon.decor;

import com.eternalrealm.dungeon.BlockBox;
import com.eternalrealm.dungeon.BlockPoint;
import com.eternalrealm.dungeon.DungeonTheme;

import java.util.List;
import java.util.Objects;

/**
 * The decoration of one instance layout ({@link DungeonDecor#plan}): blocks to set after the build plan, in a safe
 * order (structure, then furniture, then what hangs on it), and what the platform needs around it.
 *
 * @param blocks       the blocks, in placement order
 * @param extraBoxes   boxes outside the layout's {@code occupiedBoxes()} the decoration builds into (the entrance
 *                     hall's wings, the set-piece rooms, the antechamber, alcoves behind walls): clear them too
 * @param fluidBoxes   boxes holding water or lava: clear these first, without block updates, so nothing flows
 * @param revivalPoint feet position in front of the entrance hall's revival statue, where fallen members come back
 * @param revivalYaw   facing at the revival point (Minecraft yaw: 0 = +z), towards the entrance door
 * @param setPieces    the set-piece rooms (vault, library, shrine...) between the combat rooms
 * @param antechamber  the hall before the boss arena (the last corridor rebuilt), or null if it could not be widened
 * @param hall         the whole entrance hall (the layout's hall and its wings)
 */
public record DecorPlan(DungeonTheme theme, List<DecorBlock> blocks, List<BlockBox> extraBoxes,
                        List<BlockBox> fluidBoxes, BlockPoint revivalPoint, float revivalYaw,
                        List<BlockBox> setPieces, BlockBox antechamber, BlockBox hall) {
    public DecorPlan {
        Objects.requireNonNull(theme, "theme");
        blocks = List.copyOf(blocks);
        extraBoxes = List.copyOf(extraBoxes);
        fluidBoxes = List.copyOf(fluidBoxes);
        Objects.requireNonNull(revivalPoint, "revivalPoint");
        setPieces = List.copyOf(setPieces);
        Objects.requireNonNull(hall, "hall");
    }
}
