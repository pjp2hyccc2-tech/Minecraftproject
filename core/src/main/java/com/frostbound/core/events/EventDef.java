package com.frostbound.core.events;

import com.frostbound.core.city.City;
import com.frostbound.core.util.Msg;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * A choice event. Random events need {@code condition} to hold and roll by {@code weight}; scripted events
 * are only opened by the simulation (warnings, crises, follow-ups, expedition finds).
 */
public final class EventDef {
    /** Applies a choice; returns the message shown in the history. */
    public interface Effect {
        Msg apply(City city, List<String> args);
    }

    public record Choice(String key, Predicate<City> available, Effect effect) {
    }

    public final String id;
    public final boolean major;
    public final boolean scripted;
    final Predicate<City> condition;
    final double weight;
    final int cooldownDays;
    final Function<City, List<String>> args;
    public final List<Choice> choices;

    EventDef(String id, boolean major, boolean scripted, Predicate<City> condition, double weight, int cooldownDays,
            Function<City, List<String>> args, List<Choice> choices) {
        this.id = id;
        this.major = major;
        this.scripted = scripted;
        this.condition = condition;
        this.weight = weight;
        this.cooldownDays = cooldownDays;
        this.args = args;
        this.choices = choices;
    }

    public String titleKey() {
        return "frostbound.event." + id + ".title";
    }

    public String textKey() {
        return "frostbound.event." + id + ".text";
    }
}
