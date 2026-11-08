package com.eternalrealm.story;

import java.util.List;

/**
 * A short scene: lines shown one after another, {@link StoryCatalog#LINE_INTERVAL_TICKS} apart.
 *
 * @param id    unique within the catalog, e.g. "prologue", "boss.b1.intro", "npc.item_shop.3"
 * @param lines at least one line, in order
 */
public record Dialogue(String id, List<DialogueLine> lines) {
    public Dialogue {
        Check.dotted("id", id);
        lines = List.copyOf(lines);
        if (lines.isEmpty()) {
            throw new IllegalArgumentException(id + ": a dialogue needs at least one line");
        }
    }

    public int size() {
        return lines.size();
    }

    public DialogueLine first() {
        return lines.get(0);
    }

    /** How long showing every line takes at the standard pace (each line, the last one included, gets one interval). */
    public int durationTicks() {
        return lines.size() * StoryCatalog.LINE_INTERVAL_TICKS;
    }
}
