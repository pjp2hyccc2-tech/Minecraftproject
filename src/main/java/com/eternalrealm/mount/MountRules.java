package com.eternalrealm.mount;

import com.eternalrealm.core.ModInfo;

import java.util.List;

/**
 * The rules of buying, calling and flying mounts (pure; the server enforces them, docs/modules/mounts.md).
 *
 * <ul>
 *     <li><b>Buying</b>: from the mount's level ({@link MountKind#requiredLevel()}), for its price, once.</li>
 *     <li><b>Calling</b>: the selected owned mount comes after {@value #SUMMON_TICKS} ticks of standing still; not
 *     in combat, not knocked down, not in water, not inside a dungeon or raid (their own dimension), not while
 *     taking part in a boss fight, and not where there is no room for it. Moving, taking a hit or pressing the key
 *     again stops the call.</li>
 *     <li><b>Riding</b>: the mount follows the rider's view; it cannot be hurt, targeted or pushed and leaves when the
 *     rider gets off, logs out, dies, changes dimension, enters a dungeon or joins a boss fight. Big hits do not
 *     knock the rider off. Nobody takes fall damage from riding: a rider who leaves a mount in the air is spared the
 *     fall.</li>
 *     <li><b>Flying</b> (griffin): at most {@value #FLIGHT_HEIGHT} blocks above the highest ground nearby and
 *     {@value #TOP_MARGIN} blocks below the top of the world.</li>
 * </ul>
 */
public final class MountRules {
    /** Ticks a call takes. */
    public static final int SUMMON_TICKS = 20;
    /** Moving this far (blocks) while calling stops the call. */
    public static final double SUMMON_MOVE_TOLERANCE = 1.0;
    /** Highest flight above the highest ground nearby (blocks). */
    public static final int FLIGHT_HEIGHT = 32;
    /** Distance of the ground samples around a flier (blocks) for the ceiling. */
    public static final int CEILING_SAMPLE_RADIUS = 12;
    /** Space kept below the top of the world (blocks). */
    public static final int TOP_MARGIN = 8;
    /** Ticks after leaving a mount in the air during which the rider takes no fall damage (also ends on landing). */
    public static final int FALL_GUARD_TICKS = 20 * 20;
    /** The extension of the player data that holds the owned mounts. */
    public static final String EXTENSION = "mounts";

    // UI actions (UiActionPacket) and the view channel (UiDataPacket)
    /** Call the selected mount, or get off the one ridden (stop a call in progress). */
    public static final String TOGGLE_ACTION = "mount.toggle";
    /** Open the list of owned mounts to pick the one to call. */
    public static final String CHOOSE_ACTION = "mount.choose";
    /** {@code {"owned": [ids], "selected": id or "", "riding": id or ""}}: for the HUD icon. */
    public static final String STATE_CHANNEL = "mount_state";

    // texts
    private static final String TEXT = "mount." + ModInfo.MOD_ID + ".";
    public static final String CALLING = TEXT + "calling";
    public static final String CALL_STOPPED = TEXT + "call_stopped";
    public static final String DISMISSED = TEXT + "dismissed";
    public static final String NONE_OWNED = TEXT + "none_owned";
    public static final String NOT_HERE = TEXT + "not_here";
    public static final String IN_COMBAT = TEXT + "in_combat";
    public static final String IN_BOSS_FIGHT = TEXT + "in_boss_fight";
    public static final String IN_WATER = TEXT + "in_water";
    public static final String DOWN = TEXT + "down";
    public static final String NO_ROOM = TEXT + "no_room";
    public static final String LEFT_BOSS_FIGHT = TEXT + "left_boss_fight";
    public static final String LEFT_HERE = TEXT + "left_here";
    public static final String FIGHT_ON_FOOT = TEXT + "fight_on_foot";
    public static final String BOUGHT = TEXT + "bought";
    public static final String SELECTED = TEXT + "selected";
    public static final String NOT_ENOUGH_GOLD = TEXT + "not_enough_gold";
    public static final String LEVEL_TOO_LOW = TEXT + "level_too_low";
    public static final String ALREADY_OWNED = TEXT + "already_owned";
    public static final String HINT = TEXT + "hint";
    public static final String FLIGHT_HINT = TEXT + "flight_hint";
    // the stable (vendor and chooser menus)
    public static final String STABLE_TITLE = TEXT + "stable.title";
    public static final String CHOOSE_TITLE = TEXT + "stable.choose_title";
    public static final String STABLE_PRICE = TEXT + "stable.price";
    public static final String STABLE_LEVEL = TEXT + "stable.level";
    public static final String STABLE_LOCKED = TEXT + "stable.locked";
    public static final String STABLE_OWNED = TEXT + "stable.owned";
    public static final String STABLE_BUY = TEXT + "stable.buy";
    public static final String STABLE_PICK = TEXT + "stable.pick";
    public static final String STABLE_SELECTED = TEXT + "stable.selected";
    public static final String STABLE_SPEED = TEXT + "stable.speed";
    public static final String STABLE_FLIES = TEXT + "stable.flies";
    public static final String STABLE_GOLD = TEXT + "stable.gold";
    public static final String STABLE_CHOOSE = TEXT + "stable.choose";
    public static final String STABLE_NONE = TEXT + "stable.none";
    public static final String STABLE_CONFIRM = TEXT + "stable.confirm";
    public static final String STABLE_CALL = TEXT + "stable.call";
    /** The stablemaster's name over his head (his NPC role's name). */
    public static final String STABLEMASTER = "npc." + ModInfo.MOD_ID + ".stable_master";

    private MountRules() {
    }

    /** What buying a mount would do. */
    public enum Purchase {
        OK, LEVEL_TOO_LOW, NOT_ENOUGH_GOLD, ALREADY_OWNED
    }

    /** Checks a purchase (the caller then takes the gold and records the mount). */
    public static Purchase purchase(MountKind kind, int level, long gold, MountOwnership owned) {
        if (owned.owns(kind)) {
            return Purchase.ALREADY_OWNED;
        }
        if (level < kind.requiredLevel()) {
            return Purchase.LEVEL_TOO_LOW;
        }
        if (gold < kind.price()) {
            return Purchase.NOT_ENOUGH_GOLD;
        }
        return Purchase.OK;
    }

    /** Why a call cannot start (or must stop). */
    public enum Call {
        OK, NONE_OWNED, NOT_HERE, IN_COMBAT, IN_BOSS_FIGHT, IN_WATER, DOWN, NO_ROOM;

        /** The message telling the player why (empty for {@link #OK}). */
        public String messageKey() {
            return switch (this) {
                case OK -> "";
                case NONE_OWNED -> MountRules.NONE_OWNED;
                case NOT_HERE -> MountRules.NOT_HERE;
                case IN_COMBAT -> MountRules.IN_COMBAT;
                case IN_BOSS_FIGHT -> MountRules.IN_BOSS_FIGHT;
                case IN_WATER -> MountRules.IN_WATER;
                case DOWN -> MountRules.DOWN;
                case NO_ROOM -> MountRules.NO_ROOM;
            };
        }
    }

    /**
     * Where the player stands when calling.
     *
     * @param mountAllowedHere not inside a dungeon, raid or other instance
     * @param inBossFight      taking part in a boss fight (dungeon or field)
     * @param inCombat         recently hit or attacking
     * @param down             knocked down (waiting for revival) or dead
     * @param inWater          in water or lava
     * @param room             the mount's body fits where the player stands
     */
    public record Situation(boolean mountAllowedHere, boolean inBossFight, boolean inCombat, boolean down,
                            boolean inWater, boolean room) {
    }

    /** Whether the selected mount may be called (checked when the call starts and every tick while it lasts). */
    public static Call call(MountOwnership owned, Situation where) {
        if (owned.selectedOrFirst().isEmpty()) {
            return Call.NONE_OWNED;
        }
        if (!where.mountAllowedHere()) {
            return Call.NOT_HERE;
        }
        if (where.down()) {
            return Call.DOWN;
        }
        if (where.inBossFight()) {
            return Call.IN_BOSS_FIGHT;
        }
        if (where.inCombat()) {
            return Call.IN_COMBAT;
        }
        if (where.inWater()) {
            return Call.IN_WATER;
        }
        if (!where.room()) {
            return Call.NO_ROOM;
        }
        return Call.OK;
    }

    /**
     * The highest a flier may fly: {@value #FLIGHT_HEIGHT} blocks above the highest ground sampled around it, and
     * {@value #TOP_MARGIN} blocks below the top of the world.
     *
     * @param groundHeights ground heights (the first air block above the ground) around the flier
     * @param worldTop      the level's build limit (exclusive)
     */
    public static double flightCeiling(List<Integer> groundHeights, int worldTop) {
        int highest = Integer.MIN_VALUE;
        for (int height : groundHeights) {
            highest = Math.max(highest, height);
        }
        double top = worldTop - TOP_MARGIN;
        return highest == Integer.MIN_VALUE ? top : Math.min(top, highest + (double) FLIGHT_HEIGHT);
    }

    /** Every text key the mounts use (names and descriptions of the mounts included). */
    public static List<String> textKeys() {
        List<String> keys = new java.util.ArrayList<>(List.of(CALLING, CALL_STOPPED, DISMISSED, NONE_OWNED,
                NOT_HERE, IN_COMBAT, IN_BOSS_FIGHT, IN_WATER, DOWN, NO_ROOM, LEFT_BOSS_FIGHT, LEFT_HERE, FIGHT_ON_FOOT, BOUGHT, SELECTED,
                NOT_ENOUGH_GOLD, LEVEL_TOO_LOW, ALREADY_OWNED, HINT, FLIGHT_HINT, STABLE_TITLE, CHOOSE_TITLE,
                STABLE_PRICE, STABLE_LEVEL, STABLE_LOCKED, STABLE_OWNED, STABLE_BUY, STABLE_PICK, STABLE_SELECTED,
                STABLE_SPEED, STABLE_FLIES, STABLE_GOLD, STABLE_CHOOSE, STABLE_NONE, STABLE_CONFIRM, STABLE_CALL,
                STABLEMASTER));
        for (MountKind kind : MountKind.values()) {
            keys.add(kind.translationKey());
            keys.add(kind.descriptionKey());
        }
        return keys;
    }
}
