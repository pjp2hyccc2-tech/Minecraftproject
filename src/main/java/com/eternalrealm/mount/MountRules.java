package com.eternalrealm.mount;

import com.eternalrealm.core.ModInfo;

import java.util.List;

/**
 * The rules of buying, calling and flying mounts (pure; the server enforces them, docs/modules/mounts.md).
 *
 * <ul>
 *     <li><b>Buying</b>: from the mount's level ({@link MountKind#requiredLevel()}), for its price, at the
 *     stablemaster, who shows {@value #OFFERS} horses or griffins with their looks; their values (個体値) show only
 *     once one is bought. Several of a kind may be kept, {@value #STABLE_CAPACITY} mounts in all; one arcbike.</li>
 *     <li><b>Deeds</b>: a kept horse or griffin can be made into a deed (an item: traded, sold at the market) and a
 *     deed taken into a stable again by a player of the mount's level with room.</li>
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
    /** Mounts one character may keep. */
    public static final int STABLE_CAPACITY = 12;
    /** Horses or griffins the stablemaster shows at a time. */
    public static final int OFFERS = 4;
    /** Showing other horses or griffins costs this share of the price (percent). */
    public static final int REROLL_PERCENT = 5;
    /** How close to the stablemaster buying, showing others and making deeds happen (blocks). */
    public static final double STABLE_REACH = 8.0;

    // UI actions (UiActionPacket) and the view channel (UiDataPacket)
    /** Call the selected mount, or get off the one ridden (stop a call in progress). */
    public static final String TOGGLE_ACTION = "mount.toggle";
    /** Open the list of owned mounts to pick the one to call. */
    public static final String CHOOSE_ACTION = "mount.choose";
    /** {@code {"owned": [ids], "selected": id or "", "riding": id or ""}}: for the HUD icon. */
    public static final String STATE_CHANNEL = "mount_state";
    /** The stable screen: the offers (looks only), the kept mounts with their values, the gold. */
    public static final String STABLE_CHANNEL = "mount.stable";
    /** Buy offer {@code index} of {@code kind} (at the stablemaster). */
    public static final String BUY_ACTION = "mount.stable.buy";
    /** Other offers of {@code kind} for {@link #REROLL_PERCENT} % of its price (at the stablemaster). */
    public static final String REROLL_ACTION = "mount.stable.reroll";
    /** Pick kept mount {@code id} (anywhere); with {@code call} it is called at once. */
    public static final String SELECT_ACTION = "mount.select";
    /** Make kept mount {@code id} into a deed (at the stablemaster). */
    public static final String DEED_ACTION = "mount.deed";
    /** Take the deed in bag {@code slot} into the stable (at the stablemaster). */
    public static final String REDEEM_ACTION = "mount.redeem";
    /** The compound a deed item carries (also read by the market: {@code species} and {@code grade}). */
    public static final String DEED_TAG = "er_mount";

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
    public static final String STABLE_FULL = TEXT + "stable_full";
    public static final String NOT_AT_STABLE = TEXT + "not_at_stable";
    public static final String REROLLED = TEXT + "rerolled";
    public static final String REVEAL = TEXT + "reveal";
    public static final String LUCKY = TEXT + "lucky";
    public static final String DEED_MADE = TEXT + "deed_made";
    public static final String DEED_TAKEN = TEXT + "deed_taken";
    public static final String DEED_BAG_FULL = TEXT + "deed_bag_full";
    public static final String DEED_NOT_TRADEABLE = TEXT + "deed_not_tradeable";
    public static final String DEED_ALREADY_KEPT = TEXT + "deed_already_kept";
    public static final String DEED_BROKEN = TEXT + "deed_broken";
    public static final String DEED_ITEM = "item." + ModInfo.MOD_ID + ".mount_deed";
    public static final String DEED_NAME = "item." + ModInfo.MOD_ID + ".mount_deed.named";
    public static final String DEED_LOOK = "item." + ModInfo.MOD_ID + ".mount_deed.look";
    public static final String DEED_VALUES = "item." + ModInfo.MOD_ID + ".mount_deed.values";
    public static final String DEED_GRADE = "item." + ModInfo.MOD_ID + ".mount_deed.grade";
    public static final String DEED_USE = "item." + ModInfo.MOD_ID + ".mount_deed.use";
    public static final String DEED_LEVEL = "item." + ModInfo.MOD_ID + ".mount_deed.level";
    public static final String HINT = TEXT + "hint";
    public static final String FLIGHT_HINT = TEXT + "flight_hint";
    // the stable screen (the stablemaster's and the choose key's)
    public static final String STABLE_TITLE = TEXT + "stable.title";
    public static final String CHOOSE_TITLE = TEXT + "stable.choose_title";
    public static final String STABLE_LOCKED = TEXT + "stable.locked";
    public static final String STABLE_OWNED = TEXT + "stable.owned";
    public static final String STABLE_GOLD = TEXT + "stable.gold";
    public static final String STABLE_NONE = TEXT + "stable.none";
    public static final String SCREEN_LOADING = TEXT + "stable.loading";
    public static final String SCREEN_TAB_STABLE = TEXT + "stable.tab_stable";
    public static final String SCREEN_TAB_DEEDS = TEXT + "stable.tab_deeds";
    public static final String SCREEN_KIND_LINE = TEXT + "stable.kind_line";
    public static final String SCREEN_NO_GOLD = TEXT + "stable.no_gold";
    public static final String SCREEN_BUY = TEXT + "stable.buy";
    public static final String SCREEN_CONFIRM = TEXT + "stable.confirm";
    public static final String SCREEN_CONFIRM_SHORT = TEXT + "stable.confirm_short";
    public static final String SCREEN_REROLL = TEXT + "stable.reroll";
    public static final String SCREEN_RARE = TEXT + "stable.rare";
    public static final String SCREEN_HIDDEN = TEXT + "stable.hidden";
    public static final String SCREEN_VALUES_LATER = TEXT + "stable.values_later";
    public static final String SCREEN_AT_STABLE = TEXT + "stable.at_stable";
    public static final String SCREEN_AT_STABLE_SHORT = TEXT + "stable.at_stable_short";
    public static final String SCREEN_MACHINE = TEXT + "stable.machine";
    public static final String SCREEN_CALL = TEXT + "stable.call";
    public static final String SCREEN_PICK = TEXT + "stable.pick";
    public static final String SCREEN_DEED = TEXT + "stable.deed";
    public static final String SCREEN_SPEED = TEXT + "stable.value_speed";
    public static final String SCREEN_ACCEL = TEXT + "stable.value_accel";
    public static final String SCREEN_JUMP = TEXT + "stable.value_jump";
    public static final String SCREEN_FLIGHT = TEXT + "stable.value_flight";
    public static final String SCREEN_TOTAL = TEXT + "stable.total";
    public static final String SCREEN_DEEDS_NOTE = TEXT + "stable.deeds_note";
    public static final String SCREEN_NO_DEEDS = TEXT + "stable.no_deeds";
    public static final String SCREEN_REDEEM = TEXT + "stable.redeem";
    public static final String SCREEN_FULL_SHORT = TEXT + "stable.full_short";
    public static final String SCREEN_KEPT_SHORT = TEXT + "stable.kept_short";
    public static final String SCREEN_REVEAL_TITLE = TEXT + "stable.reveal_title";
    public static final String SCREEN_REVEAL_OK = TEXT + "stable.reveal_ok";
    /** The stablemaster's name over his head (his NPC role's name). */
    public static final String STABLEMASTER = "npc." + ModInfo.MOD_ID + ".stable_master";

    private MountRules() {
    }

    /** What buying a mount would do. */
    public enum Purchase {
        OK, LEVEL_TOO_LOW, NOT_ENOUGH_GOLD, ALREADY_OWNED, STABLE_FULL
    }

    /**
     * Checks a purchase (the caller then takes the gold and records the mount): one arcbike at most; a full stable
     * takes no more; then the level and the gold.
     */
    public static Purchase purchase(MountKind kind, int level, long gold, MountOwnership owned) {
        if (!MountLooks.varies(kind) && owned.owns(kind)) {
            return Purchase.ALREADY_OWNED;
        }
        if (owned.size() >= STABLE_CAPACITY) {
            return Purchase.STABLE_FULL;
        }
        if (level < kind.requiredLevel()) {
            return Purchase.LEVEL_TOO_LOW;
        }
        if (gold < kind.price()) {
            return Purchase.NOT_ENOUGH_GOLD;
        }
        return Purchase.OK;
    }

    /** Gold for showing other offers of {@code kind}. */
    public static long rerollCost(MountKind kind) {
        return Math.max(1L, kind.price() * REROLL_PERCENT / 100);
    }

    /** Whether a kept mount of {@code kind} can be made into a deed (not the arcbike: it is not bred). */
    public static boolean tradeable(MountKind kind) {
        return MountLooks.varies(kind);
    }

    /** What taking a deed into the stable would do. */
    public enum Redeem {
        OK, LEVEL_TOO_LOW, STABLE_FULL, ALREADY_KEPT, NOT_TRADEABLE
    }

    public static Redeem redeem(MountIndividual mount, int level, MountOwnership owned) {
        if (!tradeable(mount.kind())) {
            return Redeem.NOT_TRADEABLE;
        }
        if (owned.mount(mount.id()).isPresent()) {
            return Redeem.ALREADY_KEPT;
        }
        if (owned.size() >= STABLE_CAPACITY) {
            return Redeem.STABLE_FULL;
        }
        if (level < mount.kind().requiredLevel()) {
            return Redeem.LEVEL_TOO_LOW;
        }
        return Redeem.OK;
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
                STABLE_LOCKED, STABLE_OWNED, STABLE_GOLD, STABLE_NONE, SCREEN_LOADING, SCREEN_TAB_STABLE,
                SCREEN_TAB_DEEDS, SCREEN_KIND_LINE, SCREEN_NO_GOLD, SCREEN_BUY, SCREEN_CONFIRM, SCREEN_CONFIRM_SHORT,
                SCREEN_REROLL, SCREEN_RARE, SCREEN_HIDDEN, SCREEN_VALUES_LATER, SCREEN_AT_STABLE, SCREEN_AT_STABLE_SHORT,
                SCREEN_MACHINE, SCREEN_CALL, SCREEN_PICK, SCREEN_DEED, SCREEN_SPEED, SCREEN_ACCEL, SCREEN_JUMP,
                SCREEN_FLIGHT, SCREEN_TOTAL, SCREEN_DEEDS_NOTE, SCREEN_NO_DEEDS, SCREEN_REDEEM, SCREEN_FULL_SHORT,
                SCREEN_KEPT_SHORT, SCREEN_REVEAL_TITLE, SCREEN_REVEAL_OK,
                STABLEMASTER, STABLE_FULL, NOT_AT_STABLE, REROLLED, REVEAL, LUCKY, DEED_ITEM, DEED_MADE, DEED_TAKEN, DEED_BAG_FULL,
                DEED_NOT_TRADEABLE, DEED_ALREADY_KEPT, DEED_BROKEN, DEED_NAME, DEED_LOOK, DEED_VALUES, DEED_GRADE,
                DEED_USE, DEED_LEVEL));
        for (MountKind kind : MountKind.values()) {
            keys.add(kind.translationKey());
            keys.add(kind.descriptionKey());
            for (MountLooks.Coat coat : MountLooks.coats(kind)) {
                if (MountLooks.varies(kind)) {
                    keys.add(MountLooks.coatKey(kind, coat.id()));
                }
            }
            for (MountLooks.Shape shape : MountLooks.shapes(kind)) {
                if (MountLooks.varies(kind)) {
                    keys.add(MountLooks.shapeKey(kind, shape.id()));
                }
            }
        }
        for (MountTraits.Grade grade : MountTraits.Grade.values()) {
            keys.add(grade.translationKey());
        }
        return keys;
    }
}
