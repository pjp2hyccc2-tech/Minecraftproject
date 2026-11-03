package com.hitori.game;

import javax.annotation.Nullable;

/**
 * The procedure (docs/DESIGN.md §1): every line of the printout is a step, done by a real action in the apartment.
 * The preparation's steps may be done in any order (the plush's own ones follow from the items); from 3:00 they go
 * in order. HUD: {@code step.hitori.<id>}; printout: {@code printout.hitori.step.<id>}.
 */
public enum Step {
    // Phase 0 — preparation
    PLUSH("plush", GameData.PHASE_PREP),
    CUT("cut", GameData.PHASE_PREP),
    RICE("rice", GameData.PHASE_PREP),
    NAILS("nails", GameData.PHASE_PREP),
    SEW("sew", GameData.PHASE_PREP),
    BATH("bath", GameData.PHASE_PREP),
    SALT("salt", GameData.PHASE_PREP),
    CAMERAS("cameras", GameData.PHASE_PREP),
    TV("tv", GameData.PHASE_PREP),
    // Phase 1 — the ritual (3:00)
    NAME("name", GameData.PHASE_RITUAL),
    FIRST_ONI("first_oni", GameData.PHASE_RITUAL),
    TUB("tub", GameData.PHASE_RITUAL),
    LIGHTS("lights", GameData.PHASE_RITUAL),
    COUNT("count", GameData.PHASE_RITUAL),
    KNIFE("knife", GameData.PHASE_RITUAL),
    STAB("stab", GameData.PHASE_RITUAL),
    NEXT_ONI("next_oni", GameData.PHASE_RITUAL),
    HIDE("hide", GameData.PHASE_RITUAL),
    // Phase 3 — ending it
    FIND("find", GameData.PHASE_FINISH),
    SPIT("spit", GameData.PHASE_FINISH),
    WIN("win", GameData.PHASE_FINISH),
    BURN("burn", GameData.PHASE_FINISH);

    public static final Step[] PREP = {PLUSH, CUT, RICE, NAILS, SEW, BATH, SALT, CAMERAS, TV};
    public static final Step[] RITUAL = {NAME, FIRST_ONI, TUB, LIGHTS, COUNT, KNIFE, STAB, NEXT_ONI, HIDE};
    public static final Step[] FINISH = {FIND, SPIT, WIN, BURN};
    public static final Step[] ALL = values();

    public final String id;
    public final int phase;

    Step(String id, int phase) {
        this.id = id;
        this.phase = phase;
    }

    @Nullable
    public static Step byId(String id) {
        for (Step s : ALL) if (s.id.equals(id)) return s;
        return null;
    }

    @Nullable
    public static Step byIndex(int i) {
        return i >= 0 && i < ALL.length ? ALL[i] : null;
    }

    public static boolean allDone(GameData d, Step[] steps) {
        for (Step s : steps) if (!d.done(s)) return false;
        return true;
    }

    /** The first undone step of a list, or null. */
    @Nullable
    public static Step firstUndone(GameData d, Step[] steps) {
        for (Step s : steps) if (!d.done(s)) return s;
        return null;
    }

    /**
     * What the HUD shows now (index into {@link #ALL}), or one of the extra lines: {@link #HUD_WAIT} (the preparation is
     * done, waiting for 3:00), {@link #HUD_HIDING} (hidden: wait), {@link #HUD_NONE}.
     */
    public static int hudLine(GameData d) {
        switch (d.phase) {
            case GameData.PHASE_PREP -> {
                Step s = firstUndone(d, PREP);
                return s == null ? HUD_WAIT : s.ordinal();
            }
            case GameData.PHASE_RITUAL -> {
                Step s = firstUndone(d, RITUAL);
                return s == null ? HUD_HIDING : s.ordinal();
            }
            case GameData.PHASE_HIDING -> {
                return d.done(HIDE) ? HUD_HIDING : HIDE.ordinal();
            }
            case GameData.PHASE_FINISH -> {
                Step s = firstUndone(d, FINISH);
                return s == null ? HUD_NONE : s.ordinal();
            }
            default -> {
                return HUD_NONE;
            }
        }
    }

    public static final int HUD_NONE = -1, HUD_WAIT = 100, HUD_HIDING = 101;
}
