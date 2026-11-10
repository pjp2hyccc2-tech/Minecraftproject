package com.eternalrealm.hud;

import java.util.ArrayList;
import java.util.List;

/**
 * Where every part of the game's HUD goes for one screen size and what is showing at the moment (pure; the client
 * computes it once per frame and every overlay draws inside its rectangle). Nothing of ours overlaps anything else of
 * ours, and on screens at least {@value #ROOMY_WIDTH} GUI pixels wide nothing overlaps the vanilla chat or action bar
 * either (tested over the usual screen sizes and contents).
 *
 * <pre>
 * ┌ player frame ┐        ┌ boss HUD ┐          ┌ minimap ┐
 * │ effects      │        └──────────┘          │ labels  │
 * │ party        │        ambient dialogue      │ threat  │
 * │              │                              │ ranking │
 * │ (chat)       │      (action bar)            │ pickups │
 * │              │                              │ [equip] │
 * └──────────────┘ [Z][job skills][quick items] │ [bag]   │
 * </pre>
 *
 * The vanilla hotbar, hearts, armor, food, experience bar and potion icons are replaced by these parts.
 */
public final class HudLayout {
    public static final int MARGIN = 4;
    public static final int GAP = 4;
    /** From this width on the right column stays clear of a default-width chat. */
    public static final int ROOMY_WIDTH = 427;

    public static final int SLOT = 22;
    public static final int SLOT_GAP = 2;
    public static final int JOB_SLOTS = 6;
    public static final int QUICK_SLOTS = 3;
    /** Extra space before the ultimate (the last job slot). */
    public static final int ULTIMATE_GAP = 4;
    /** Between the weapon skill and the job skills, and between the job skills and the quick-use items. */
    public static final int GROUP_GAP = 8;
    /** Height of the weapon skill's name above its slot. */
    public static final int WEAPON_LABEL = 10;
    public static final int ICON = 22;

    public static final int PLAYER_MIN_WIDTH = 112;
    public static final int PLAYER_MAX_WIDTH = 150;
    /** Name row, health bar and experience bar with their padding. */
    public static final int PLAYER_BASE = 32;
    public static final int AIR_ROW = 5;
    public static final int POINTS_ROW = 11;
    /** One status effect icon (12 px) and its gap. */
    public static final int EFFECT = 13;
    public static final int EFFECT_ROWS = 2;

    public static final int PARTY_WIDTH = 100;
    public static final int PARTY_ROW = 17;
    public static final int PARTY_ROW_COMPACT = 12;
    public static final int RAID_SHOWN = 10;

    public static final int BOSS_TOP = 6;
    public static final int BOSS_HEIGHT = 52;
    public static final int BOSS_MIN_WIDTH = 120;
    public static final int BOSS_MAX_WIDTH = 320;
    public static final int DIALOGUE_MIN_WIDTH = 150;
    public static final int DIALOGUE_MAX_WIDTH = 330;
    public static final int DIALOGUE_MIN_HEIGHT = 36;

    public static final int MINIMAP_MIN = 48;
    public static final int LABEL_WIDTH = 140;
    public static final int LABEL_LINE = 10;
    public static final int THREAT_WIDTH = 108;
    public static final int THREAT_HEIGHT = 22;
    public static final int THREAT_RESULT_WIDTH = 138;
    public static final int THREAT_RESULT_HEIGHT = 52;
    public static final int RANKING_WIDTH = 118;
    public static final int RANKING_HEADER = 13;
    public static final int RANKING_ROW = 12;
    /** Narrowest right-column text block (names are shortened below their full width). */
    public static final int SIDE_MIN_WIDTH = 90;
    public static final int FEED_WIDTH = 140;
    public static final int FEED_ROW = 12;
    public static final int FEED_ROWS = 5;
    public static final int FEED_MIN_ROWS = 2;

    /** Vanilla draws the chat from 40 px above the bottom upwards. */
    public static final int CHAT_BOTTOM_OFFSET = 40;
    /** Vanilla's action bar backdrop when nothing is drawn in the vanilla bottom rows (74 to 61 px above the bottom). */
    public static final int ACTION_BAR_TOP_OFFSET = 74;
    public static final int ACTION_BAR_HEIGHT = 13;
    public static final int ACTION_BAR_HALF_WIDTH = 100;
    /** Vanilla's title (4x text) ends this far above the centre; the dialogue stays above it. */
    public static final int TITLE_CLEARANCE = 44;
    /** Subtitles (bottom right, when switched on) push the two icons up by this much. */
    public static final int SUBTITLE_SHIFT = 40;

    private HudLayout() {
    }

    /**
     * What is on screen.
     *
     * @param chatWidth        vanilla chat width in GUI pixels (with its scale and padding)
     * @param chatHeight       vanilla chat height when not focused, in GUI pixels
     * @param minimapSize      minimap diameter, 0 when hidden
     * @param minimapLabelLines lines of text under the minimap (area name, coordinates)
     * @param bossRankRows     rows of the boss damage ranking (0 = none)
     * @param partyMembers     party members (including the player), 0 without a party
     * @param effects          status effects with an icon
     * @param subtitles        vanilla subtitles are switched on (drawn in the bottom right corner)
     */
    public record Input(int width, int height, int chatWidth, int chatHeight, int minimapSize, int minimapLabelLines,
                        boolean threatGauge, boolean threatResult, boolean boss, int bossRankRows, int partyMembers,
                        boolean raid, boolean pointsLine, boolean airRow, int effects, boolean dialogue,
                        boolean subtitles) {
        public Input {
            if (width < 1 || height < 1) {
                throw new IllegalArgumentException("screen " + width + "x" + height);
            }
            if (chatWidth < 0 || chatHeight < 0 || minimapSize < 0 || minimapLabelLines < 0 || bossRankRows < 0
                    || partyMembers < 0 || effects < 0) {
                throw new IllegalArgumentException("negative HUD input");
            }
        }

        /** A screen with the default chat (320 px wide plus padding, 90 px tall) and minimap, nothing else showing. */
        public static Input of(int width, int height) {
            return new Input(width, height, 328, 90, 96, 2, false, false, false, 0, 0, false, false, false, 0, false,
                    false);
        }

        public Input withChat(int chatWidth, int chatHeight) {
            return new Input(width, height, chatWidth, chatHeight, minimapSize, minimapLabelLines, threatGauge,
                    threatResult, boss, bossRankRows, partyMembers, raid, pointsLine, airRow, effects, dialogue, subtitles);
        }

        public Input withMinimap(int size, int labelLines) {
            return new Input(width, height, chatWidth, chatHeight, size, labelLines, threatGauge, threatResult, boss,
                    bossRankRows, partyMembers, raid, pointsLine, airRow, effects, dialogue, subtitles);
        }

        public Input withThreat(boolean gauge, boolean result) {
            return new Input(width, height, chatWidth, chatHeight, minimapSize, minimapLabelLines, gauge, result, boss,
                    bossRankRows, partyMembers, raid, pointsLine, airRow, effects, dialogue, subtitles);
        }

        public Input withBoss(boolean showing, int rankRows) {
            return new Input(width, height, chatWidth, chatHeight, minimapSize, minimapLabelLines, threatGauge,
                    threatResult, showing, rankRows, partyMembers, raid, pointsLine, airRow, effects, dialogue, subtitles);
        }

        public Input withParty(int members, boolean isRaid) {
            return new Input(width, height, chatWidth, chatHeight, minimapSize, minimapLabelLines, threatGauge,
                    threatResult, boss, bossRankRows, members, isRaid, pointsLine, airRow, effects, dialogue, subtitles);
        }

        public Input withPlayer(boolean points, boolean air, int effectCount) {
            return new Input(width, height, chatWidth, chatHeight, minimapSize, minimapLabelLines, threatGauge,
                    threatResult, boss, bossRankRows, partyMembers, raid, points, air, effectCount, dialogue, subtitles);
        }

        public Input withDialogue(boolean showing) {
            return new Input(width, height, chatWidth, chatHeight, minimapSize, minimapLabelLines, threatGauge,
                    threatResult, boss, bossRankRows, partyMembers, raid, pointsLine, airRow, effects, showing, subtitles);
        }

        public Input withSubtitles(boolean on) {
            return new Input(width, height, chatWidth, chatHeight, minimapSize, minimapLabelLines, threatGauge,
                    threatResult, boss, bossRankRows, partyMembers, raid, pointsLine, airRow, effects, dialogue, on);
        }
    }

    /**
     * The rectangles. Empty ({@link Rect#NONE}) when the part is not showing or has no room. {@code dialogue} is the
     * largest box the ambient dialogue may use (drawn from its top unless {@code dialogueFromBottom}).
     */
    public record Layout(Rect player, Rect effects, int effectsPerRow, Rect party, int partyRow, int partyPerColumn,
                         int partyShown, Rect boss, Rect dialogue, boolean dialogueFromBottom, Rect minimap,
                         Rect minimapLabels, Rect threat, Rect threatResult, Rect ranking, int rankingRows,
                         Rect skillBar, Rect weaponSkill, Rect weaponLabel, List<Rect> jobSlots, List<Rect> quickSlots,
                         Rect equipment, Rect bag, Rect pickupFeed, Rect chat, Rect actionBar) {
        public Layout {
            jobSlots = List.copyOf(jobSlots);
            quickSlots = List.copyOf(quickSlots);
        }

        /** Every part of ours that takes space (not the reserved vanilla areas), for overlap checks. */
        public List<Rect> elements() {
            List<Rect> all = new ArrayList<>(List.of(player, effects, party, boss, dialogue, minimap, minimapLabels,
                    threat, threatResult, ranking, skillBar, weaponLabel, equipment, bag, pickupFeed));
            all.removeIf(Rect::isEmpty);
            return all;
        }
    }

    public static Layout compute(Input in) {
        int w = in.width();
        int h = in.height();
        Rect chat = chatArea(in);
        Rect actionBar = new Rect(Math.max(0, w / 2 - ACTION_BAR_HALF_WIDTH), Math.max(0, h - ACTION_BAR_TOP_OFFSET),
                Math.min(w, ACTION_BAR_HALF_WIDTH * 2), Math.min(ACTION_BAR_HEIGHT, h));

        // ---- bottom centre: weapon skill, job skills, quick-use items
        int barWidth = SLOT + GROUP_GAP + JOB_SLOTS * SLOT + (JOB_SLOTS - 1) * SLOT_GAP + ULTIMATE_GAP + GROUP_GAP
                + QUICK_SLOTS * SLOT + (QUICK_SLOTS - 1) * SLOT_GAP;
        int barX = Math.max(0, (w - barWidth) / 2);
        int barY = Math.max(0, h - MARGIN - SLOT);
        Rect skillBar = new Rect(barX, barY, Math.min(barWidth, w), SLOT);
        Rect weaponSkill = new Rect(barX, barY, SLOT, SLOT);
        Rect weaponLabel = new Rect(barX, Math.max(0, barY - WEAPON_LABEL - 1), Math.min(150, barWidth), WEAPON_LABEL);
        List<Rect> jobSlots = new ArrayList<>();
        int x = barX + SLOT + GROUP_GAP;
        for (int i = 0; i < JOB_SLOTS; i++) {
            if (i == JOB_SLOTS - 1) {
                x += ULTIMATE_GAP;
            }
            jobSlots.add(new Rect(x, barY, SLOT, SLOT));
            x += SLOT + SLOT_GAP;
        }
        x += GROUP_GAP - SLOT_GAP;
        List<Rect> quickSlots = new ArrayList<>();
        for (int i = 0; i < QUICK_SLOTS; i++) {
            quickSlots.add(new Rect(x, barY, SLOT, SLOT));
            x += SLOT + SLOT_GAP;
        }

        // ---- bottom right: equipment above the bag
        int iconX = w - MARGIN - ICON;
        int bagY = h - MARGIN - ICON - (in.subtitles() ? SUBTITLE_SHIFT : 0);
        Rect bag = new Rect(iconX, bagY, ICON, ICON);
        Rect equipment = new Rect(iconX, bagY - GAP - ICON, ICON, ICON);

        // ---- the two columns; the boss HUD goes between them, or above both when there is no room
        int lowest = weaponLabel.y() - GAP;
        Columns columns = columns(in, MARGIN, chat, actionBar, lowest, equipment);
        Rect boss = Rect.NONE;
        if (in.boss()) {
            int span = freeWidth(columns.placed(), BOSS_TOP, BOSS_TOP + BOSS_HEIGHT, w);
            if (span >= BOSS_MIN_WIDTH) {
                int bossWidth = Math.min(BOSS_MAX_WIDTH, span);
                boss = new Rect((w - bossWidth) / 2, BOSS_TOP, bossWidth, BOSS_HEIGHT);
            } else {
                int bossWidth = Math.min(BOSS_MAX_WIDTH, w - 2 * MARGIN);
                boss = new Rect((w - bossWidth) / 2, MARGIN, bossWidth, BOSS_HEIGHT);
                columns = columns(in, boss.bottom() + GAP, chat, actionBar, lowest, equipment);
            }
        }

        // ---- ambient dialogue: under the boss HUD, above the vanilla title
        Rect dialogue = Rect.NONE;
        boolean fromBottom = false;
        if (in.dialogue()) {
            int top = boss.isEmpty() ? MARGIN + 2 : boss.bottom() + GAP;
            int maxHeight = Math.max(DIALOGUE_MIN_HEIGHT, h / 2 - TITLE_CLEARANCE - top);
            List<Rect> placed = new ArrayList<>(columns.placed());
            placed.addAll(List.of(boss, equipment, bag, skillBar, weaponLabel));
            int span = Math.min(DIALOGUE_MAX_WIDTH, freeWidth(placed, top, top + maxHeight, w));
            if (span >= DIALOGUE_MIN_WIDTH) {
                dialogue = new Rect((w - span) / 2, top, span, maxHeight);
            } else {
                // too narrow up there: just above the action bar, as wide as the columns allow
                int bottom = actionBar.y() - GAP;
                int boxTop = Math.max(0, bottom - DIALOGUE_MIN_HEIGHT);
                List<Rect> others = new ArrayList<>(placed);
                others.remove(columns.feed());
                int width = Math.min(DIALOGUE_MAX_WIDTH, freeWidth(others, boxTop, bottom, w));
                if (width >= DIALOGUE_MIN_WIDTH / 2) {
                    dialogue = new Rect((w - width) / 2, boxTop, width, bottom - boxTop);
                    fromBottom = true;
                } // else: no room on this (tiny) screen while the side cards show; the line waits for the next frame
            }
        }
        Rect feed = columns.feed();
        if (dialogue.intersects(feed)) {
            feed = Rect.NONE; // tiny screens only: the dialogue matters more than the pickup list
        }

        return new Layout(columns.player(), columns.effects(), columns.effectsPerRow(), columns.party(),
                columns.partyRow(), columns.partyPerColumn(), columns.partyShown(), boss, dialogue, fromBottom,
                columns.minimap(), columns.labels(), columns.threat(), columns.threatResult(), columns.ranking(),
                columns.rankingRows(), skillBar, weaponSkill, weaponLabel, jobSlots, quickSlots, equipment, bag, feed,
                chat, actionBar);
    }

    /** The region vanilla's unfocused chat can cover (bottom left, from 40 px above the bottom upwards). */
    static Rect chatArea(Input in) {
        int bottom = Math.max(0, in.height() - CHAT_BOTTOM_OFFSET);
        int height = Math.min(in.chatHeight(), bottom);
        return new Rect(0, bottom - height, Math.min(in.width(), in.chatWidth()), height);
    }

    private record Columns(Rect player, Rect effects, int effectsPerRow, Rect party, int partyRow, int partyPerColumn,
                           int partyShown, Rect minimap, Rect labels, Rect threat, Rect threatResult, Rect ranking,
                           int rankingRows, Rect feed) {
        List<Rect> placed() {
            return List.of(player, effects, party, minimap, labels, threat, threatResult, ranking, feed);
        }
    }

    private static Columns columns(Input in, int top, Rect chat, Rect actionBar, int lowest, Rect equipment) {
        int w = in.width();
        // ---- left: player frame, status effects, party
        int playerWidth = Math.max(PLAYER_MIN_WIDTH, Math.min(PLAYER_MAX_WIDTH, w / 4));
        int playerHeight = PLAYER_BASE + (in.airRow() ? AIR_ROW : 0) + (in.pointsLine() ? POINTS_ROW : 0);
        Rect player = new Rect(MARGIN, top, playerWidth, playerHeight);
        int perRow = Math.max(1, playerWidth / EFFECT);
        int effectRows = in.effects() == 0 ? 0 : Math.min(EFFECT_ROWS, (in.effects() + perRow - 1) / perRow);
        Rect effects = effectRows == 0 ? Rect.NONE : new Rect(MARGIN, player.bottom() + 2, playerWidth, effectRows * EFFECT);
        int partyTop = (effects.isEmpty() ? player.bottom() : effects.bottom()) + GAP;
        // ---- right: minimap, its labels, the field threat, its result, the boss ranking, the pickup feed; each part
        // goes below the previous one and only if it ends above the equipment icon
        int sideRight = w - MARGIN;
        int columnBottom = equipment.y() - GAP;
        List<Rect> keepClear = List.of(chat, actionBar);
        int size = Math.min(in.minimapSize(), columnBottom - top);
        if (size > 0 && sideRight - size < chat.right() + GAP && !chat.isEmpty()) {
            size = Math.min(size, chat.y() - GAP - top); // keep the disc above the chat
        }
        Rect minimap = size < MINIMAP_MIN ? Rect.NONE : new Rect(sideRight - size, top, size, size);
        int y = minimap.isEmpty() ? top : minimap.bottom() + 2;
        Rect labels = Rect.NONE;
        if (!minimap.isEmpty() && in.minimapLabelLines() > 0) {
            int lines = Math.min(in.minimapLabelLines(), Math.max(0, (columnBottom - y) / LABEL_LINE));
            if (lines > 0) {
                labels = side(sideRight, y, LABEL_WIDTH, lines * LABEL_LINE, keepClear);
                y = labels.bottom() + 2;
            }
        }
        Rect threat = Rect.NONE;
        if (in.threatGauge() && y + THREAT_HEIGHT <= columnBottom) {
            threat = side(sideRight, y, THREAT_WIDTH, THREAT_HEIGHT, keepClear);
            y = threat.bottom() + 2;
        }
        Rect threatResult = Rect.NONE;
        if (in.threatResult() && y + THREAT_RESULT_HEIGHT <= columnBottom) {
            threatResult = side(sideRight, y, THREAT_RESULT_WIDTH, THREAT_RESULT_HEIGHT, keepClear);
            y = threatResult.bottom() + GAP;
        }
        Rect ranking = Rect.NONE;
        int rankingRows = 0;
        if (in.boss() && in.bossRankRows() > 0) {
            int room = columnBottom - FEED_MIN_ROWS * FEED_ROW - GAP - y;
            rankingRows = Math.max(0, Math.min(in.bossRankRows(), (room - RANKING_HEADER) / RANKING_ROW));
            if (rankingRows > 0) {
                ranking = side(sideRight, y, RANKING_WIDTH, RANKING_HEADER + rankingRows * RANKING_ROW, keepClear);
                y = ranking.bottom() + GAP;
            }
        }
        int feedRows = Math.max(0, Math.min(FEED_ROWS, (columnBottom - y) / FEED_ROW));
        Rect feed = feedRows == 0 ? Rect.NONE
                : side(sideRight, columnBottom - feedRows * FEED_ROW, FEED_WIDTH, feedRows * FEED_ROW, keepClear);

        // ---- the party under the player frame (computed last: two columns must not reach the right column)
        int partyLimit = chat.isEmpty() ? lowest : Math.min(lowest, chat.y() - GAP);
        int rows = in.raid() ? Math.min(RAID_SHOWN, in.partyMembers()) : in.partyMembers();
        List<Rect> right = List.of(minimap, labels, threat, threatResult, ranking, feed, equipment);
        Party party = party(rows, partyTop, partyLimit, 2);
        if (in.boss() && party.rect().width() > playerWidth) {
            // two columns of frames would reach under the boss HUD: start them below it
            party = party(rows, Math.max(partyTop, BOSS_TOP + BOSS_HEIGHT + GAP), partyLimit, 2);
        }
        for (Rect rect : right) {
            if (party.rect().intersects(new Rect(rect.x() - GAP, rect.y(), rect.width() + GAP, rect.height()))) {
                party = party(rows, partyTop, partyLimit, 1); // narrow screen: one column, as many as fit
                break;
            }
        }
        return new Columns(player, effects, perRow, party.rect(), party.rowHeight(), party.perColumn(), party.shown(),
                minimap, labels, threat, threatResult, ranking, rankingRows, feed);
    }

    private record Party(Rect rect, int rowHeight, int perColumn, int shown) {
    }

    /** Party frames from {@code top} down to {@code limit}: full rows, then compact rows, then two columns. */
    private static Party party(int rows, int top, int limit, int maxColumns) {
        int available = Math.max(0, limit - top);
        if (rows == 0) {
            return new Party(Rect.NONE, PARTY_ROW, 0, 0);
        }
        int rowHeight = PARTY_ROW;
        int columns = 1;
        if (rows * rowHeight > available) {
            rowHeight = PARTY_ROW_COMPACT;
        }
        if (rows * rowHeight > available && maxColumns > 1) {
            columns = 2;
        }
        int perColumn = Math.min((rows + columns - 1) / columns, available / rowHeight);
        int shown = Math.min(rows, perColumn * columns);
        if (shown == 0) {
            return new Party(Rect.NONE, rowHeight, 0, 0);
        }
        int usedColumns = (shown + perColumn - 1) / perColumn;
        return new Party(new Rect(MARGIN, top, PARTY_WIDTH * usedColumns + GAP * (usedColumns - 1),
                perColumn * rowHeight), rowHeight, perColumn, shown);
    }

    /**
     * A right-aligned text block; narrower (down to {@link #SIDE_MIN_WIDTH}) where it would reach into the chat or the
     * action bar.
     */
    private static Rect side(int right, int y, int width, int height, List<Rect> keepClear) {
        int used = width;
        for (Rect clear : keepClear) {
            if (clear.spansRows(y, y + height) && right - used < clear.right() + GAP) {
                used = Math.max(SIDE_MIN_WIDTH, Math.min(used, right - (clear.right() + GAP)));
            }
        }
        return new Rect(right - used, y, used, height);
    }

    /**
     * The widest box centred on the screen that keeps {@link #GAP} from every placed part sharing its rows; 0 when a
     * part covers the centre.
     */
    static int freeWidth(List<Rect> placed, int top, int bottom, int screenWidth) {
        int center = screenWidth / 2;
        int left = MARGIN;
        int right = screenWidth - MARGIN;
        for (Rect rect : placed) {
            if (!rect.spansRows(top, bottom)) {
                continue;
            }
            if (rect.right() <= center) {
                left = Math.max(left, rect.right() + GAP);
            } else if (rect.x() >= center) {
                right = Math.min(right, rect.x() - GAP);
            } else {
                return 0;
            }
        }
        return Math.max(0, 2 * Math.min(center - left, right - center));
    }
}
