package com.reisatsu.game;

/**
 * The notes (docs/DESIGN.md §3.11). Text: lang keys {@code note.reisatsu.<id>.title / .body}. The world places the
 * paper blocks and calls {@code FixtureEvents.note(player, id)}; {@link #REPORT} and {@link #MEDIUM} are given at the
 * start.
 */
public final class Notes {
    public static final String REPORT = "report", HANDOVER = "handover", CLIPPING = "clipping", RESERVATION = "reservation",
            DIARY = "diary", DRAWING = "drawing", FLYER = "flyer", FIRE = "fire", CLEANING = "cleaning", MEDIUM = "medium";
    public static final String[] ALL = {REPORT, HANDOVER, CLIPPING, RESERVATION, DIARY, DRAWING, FLYER, FIRE, CLEANING, MEDIUM};
    /** The phenomena listed in the report (keys of the director's witness list). */
    public static final String[] PHENOMENA = {"door", "taps", "menus", "call", "chair", "lights", "plate", "music", "pa", "phone", "tv",
            "dryer", "drink", "knock", "bear", "glass", "prints", "board", "crash"};

    private Notes() {}
}
