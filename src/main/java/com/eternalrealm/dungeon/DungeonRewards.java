package com.eternalrealm.dungeon;

import com.eternalrealm.boss.Difficulty;
import com.eternalrealm.item.ItemRarity;

import java.util.Objects;

/**
 * What a dungeon clear hands every member by difficulty: the rarity the reward piece is raised to at least, and the
 * enhancement stones that come with it. One table for the reward itself ({@code DungeonService}) and for the gatekeeper's
 * screen, which shows it before the party sets out.
 */
public final class DungeonRewards {
    private DungeonRewards() {
    }

    /** The reward piece rolls its rarity as usual and is then raised to at least this. */
    public static ItemRarity rarityFloor(Difficulty difficulty) {
        return switch (Objects.requireNonNull(difficulty, "difficulty")) {
            case NORMAL -> ItemRarity.UNCOMMON;
            case HARD -> ItemRarity.RARE;
            case EX -> ItemRarity.EPIC;
        };
    }

    /** Enhancement stones given with the reward piece. */
    public static int stones(Difficulty difficulty) {
        return switch (Objects.requireNonNull(difficulty, "difficulty")) {
            case NORMAL -> 2;
            case HARD -> 3;
            case EX -> 5;
        };
    }
}
