package com.eternalrealm.story;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * The ending played for everyone when the King of the End falls for the first time: about a minute of timed lines,
 * then the staff roll. After the staff roll, play the victory epilogue ({@link StoryTrigger.SeasonEnded}(true)).
 *
 * @param beats                lines with their time from the start of the sequence (the first at tick 0, strictly
 *                             increasing)
 * @param staffRoll            credits, shown one by one every {@code creditIntervalTicks} after the last beat has been
 *                             on screen for {@link #HOLD_TICKS}
 * @param creditIntervalTicks  time between two credits
 */
public record EndingSequence(List<Beat> beats, List<Credit> staffRoll, int creditIntervalTicks) {
    /** How long the last line stays before the staff roll starts. */
    public static final int HOLD_TICKS = 60;

    /** How a beat is shown. */
    public enum Display {
        /** A chat line with the speaker's name (narration without a name). */
        CHAT,
        /** Also on screen in the title area (as a subtitle, the text is long): the key moments. */
        TITLE
    }

    public enum CreditKind {
        /** A section heading or the work's title. */
        HEADING,
        /** A normal credit line. */
        CREDIT,
        /** The text is a heading; the platform lists the heroes' names after it (e.g. the raid that won). */
        PLAYERS
    }

    public record Beat(int atTick, DialogueLine line, Display display) {
        public Beat {
            Check.range("atTick", atTick, 0, 20 * 60 * 10);
            Objects.requireNonNull(line, "line");
            Objects.requireNonNull(display, "display");
        }
    }

    public record Credit(CreditKind kind, String textKey) {
        public Credit {
            Objects.requireNonNull(kind, "kind");
            Check.dotted("textKey", textKey);
        }
    }

    public EndingSequence {
        beats = List.copyOf(beats);
        staffRoll = List.copyOf(staffRoll);
        Check.range("creditIntervalTicks", creditIntervalTicks, 20, 200);
        if (beats.isEmpty() || beats.get(0).atTick() != 0) {
            throw new IllegalArgumentException("the ending must start with a beat at tick 0");
        }
        for (int i = 1; i < beats.size(); i++) {
            if (beats.get(i).atTick() <= beats.get(i - 1).atTick()) {
                throw new IllegalArgumentException("ending beats must be in strictly increasing time: " + i);
            }
        }
        if (staffRoll.isEmpty()) {
            throw new IllegalArgumentException("the staff roll is empty");
        }
        if (staffRoll.stream().filter(credit -> credit.kind() == CreditKind.PLAYERS).count() != 1) {
            throw new IllegalArgumentException("the staff roll needs exactly one PLAYERS entry");
        }
    }

    /** When the staff roll starts, from the start of the sequence. */
    public int staffRollStartTick() {
        return beats.get(beats.size() - 1).atTick() + HOLD_TICKS;
    }

    /** Ticks from the first line to the end of the last credit's interval. */
    public int totalTicks() {
        return staffRollStartTick() + staffRoll.size() * creditIntervalTicks;
    }

    /** The beats as one dialogue (id "ending"), without timing. */
    public Dialogue asDialogue() {
        List<DialogueLine> lines = new ArrayList<>(beats.size());
        for (Beat beat : beats) {
            lines.add(beat.line());
        }
        return new Dialogue("ending", lines);
    }
}
