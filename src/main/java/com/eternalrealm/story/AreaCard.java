package com.eternalrealm.story;

import java.util.Objects;

/**
 * The title card shown when a player enters an area: a title (the area's name) and a subtitle (one evocative phrase).
 */
public record AreaCard(StoryArea area, String titleKey, String subtitleKey) {
    public AreaCard {
        Objects.requireNonNull(area, "area");
        Check.dotted("titleKey", titleKey);
        Check.dotted("subtitleKey", subtitleKey);
    }

    public static AreaCard of(StoryArea area) {
        String prefix = StoryCatalog.KEY_PREFIX + "area." + Objects.requireNonNull(area, "area").slug();
        return new AreaCard(area, prefix + ".title", prefix + ".subtitle");
    }
}
