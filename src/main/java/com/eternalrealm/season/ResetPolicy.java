package com.eternalrealm.season;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * What the rollover to the next season does with a player's progress (docs/SEASON_SYSTEM.md 5, setting D-005).
 * Applied when a player's character is first created in the new season, from their character of the previous one;
 * the previous season's files are never modified (season data is separated by season id).
 *
 * <p>Not configurable, because they are not season data: the season statistics and rankings always start empty, and
 * permanent data — account, cosmetics, titles, achievements and the hall of fame — is always kept (it is stored
 * outside {@code seasons/<id>/}).</p>
 *
 * @param resetLevel  start again at level 1 (otherwise level and EXP carry over)
 * @param resetStats  refund the stat allocation (STR, VIT, ...); required when the level resets, because a level 1
 *                    character has no points to keep allocated
 * @param resetGold   start with no currency
 * @param resetQuests start the quests again (quest, dungeon and boss progress)
 * @param inventory   what happens to equipment and inventory
 */
public record ResetPolicy(boolean resetLevel, boolean resetStats, boolean resetGold, boolean resetQuests,
                          InventoryReset inventory) {
    public ResetPolicy {
        Objects.requireNonNull(inventory, "inventory");
        if (resetLevel && !resetStats) {
            throw new IllegalArgumentException("A level reset also resets the stat allocation");
        }
    }

    /** The standard season: everything season-scoped starts fresh. */
    public static ResetPolicy defaults() {
        return new ResetPolicy(true, true, true, true, InventoryReset.RESET);
    }

    /** Equipment and inventory at the rollover. {@link #id()} is the stable config value. */
    public enum InventoryReset {
        /** Start with an empty inventory and no equipment. */
        RESET("reset"),
        /** Carry everything over. */
        KEEP("keep"),
        /** Carry over everything except items marked as season-limited. */
        REMOVE_SEASON_LIMITED("remove_season_limited");

        private static final List<InventoryReset> ALL = List.of(values());

        private final String id;

        InventoryReset(String id) {
            this.id = id;
        }

        public String id() {
            return id;
        }

        public static Optional<InventoryReset> byId(String id) {
            for (InventoryReset reset : ALL) {
                if (reset.id.equals(id)) {
                    return Optional.of(reset);
                }
            }
            return Optional.empty();
        }
    }
}
