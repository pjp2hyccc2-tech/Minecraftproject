package com.eternalrealm.story;

import java.util.Objects;

/**
 * One line of a dialogue: who speaks and what, both as translation keys (texts in docs/i18n/story.json). The text is
 * stored without quotation marks; the platform adds the formatting (see docs/modules/story.md).
 *
 * @param speakerKey translation key of the speaker's display name ({@link Speaker#key()})
 * @param textKey    translation key of the line itself
 */
public record DialogueLine(String speakerKey, String textKey) {
    public DialogueLine {
        Check.dotted("speakerKey", speakerKey);
        Check.dotted("textKey", textKey);
    }

    public static DialogueLine of(Speaker speaker, String textKey) {
        return new DialogueLine(Objects.requireNonNull(speaker, "speaker").key(), textKey);
    }

    /** Narration: show the text without a speaker name. */
    public boolean isNarration() {
        return Speaker.NARRATOR.key().equals(speakerKey);
    }
}
