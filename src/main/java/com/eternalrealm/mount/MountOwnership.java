package com.eternalrealm.mount;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;

/**
 * The mounts a player owns and the one they call ({@link MountRules#EXTENSION} of the player data: {@code
 * {"owned": ["horse", ...], "selected": "horse"}}). Immutable.
 */
public final class MountOwnership {
    public static final MountOwnership NONE = new MountOwnership(EnumSet.noneOf(MountKind.class), null);

    private final Set<MountKind> owned;
    private final MountKind selected;

    private MountOwnership(Set<MountKind> owned, MountKind selected) {
        EnumSet<MountKind> copy = owned.isEmpty() ? EnumSet.noneOf(MountKind.class) : EnumSet.copyOf(owned);
        this.owned = Collections.unmodifiableSet(copy);
        this.selected = selected != null && copy.contains(selected) ? selected : null;
    }

    public Set<MountKind> owned() {
        return owned;
    }

    public boolean owns(MountKind kind) {
        return owned.contains(kind);
    }

    /** The mount picked to be called, if the player picked one they own. */
    public Optional<MountKind> selected() {
        return Optional.ofNullable(selected);
    }

    /** The mount a call brings: the picked one, else the best owned one (the last bought tier). */
    public Optional<MountKind> selectedOrFirst() {
        if (selected != null) {
            return Optional.of(selected);
        }
        MountKind best = null;
        for (MountKind kind : owned) {
            best = kind; // enum order: horse, griffin, bike
        }
        return Optional.ofNullable(best);
    }

    /** With {@code kind} owned (and picked: a new mount is the one called next). */
    public MountOwnership withBought(MountKind kind) {
        EnumSet<MountKind> more = owned.isEmpty() ? EnumSet.noneOf(MountKind.class) : EnumSet.copyOf(owned);
        more.add(kind);
        return new MountOwnership(more, kind);
    }

    /** With {@code kind} picked (ignored if not owned). */
    public MountOwnership withSelected(MountKind kind) {
        return owns(kind) ? new MountOwnership(owned, kind) : this;
    }

    public String toJson() {
        JsonObject json = new JsonObject();
        JsonArray array = new JsonArray();
        for (MountKind kind : owned) {
            array.add(kind.id());
        }
        json.add("owned", array);
        if (selected != null) {
            json.addProperty("selected", selected.id());
        }
        return json.toString();
    }

    /** Lenient: unknown ids and malformed documents are dropped (nothing owned at worst). */
    public static MountOwnership fromJson(String text) {
        if (text == null || text.isBlank()) {
            return NONE;
        }
        try {
            JsonElement parsed = JsonParser.parseString(text);
            if (parsed == null || !parsed.isJsonObject()) {
                return NONE;
            }
            JsonObject json = parsed.getAsJsonObject();
            EnumSet<MountKind> owned = EnumSet.noneOf(MountKind.class);
            if (json.has("owned") && json.get("owned").isJsonArray()) {
                for (JsonElement element : json.getAsJsonArray("owned")) {
                    if (element.isJsonPrimitive()) {
                        MountKind.byId(element.getAsString()).ifPresent(owned::add);
                    }
                }
            }
            MountKind selected = json.has("selected") && json.get("selected").isJsonPrimitive()
                    ? MountKind.byId(json.get("selected").getAsString()).orElse(null) : null;
            return new MountOwnership(owned, selected);
        } catch (JsonParseException | IllegalStateException | UnsupportedOperationException e) {
            return NONE;
        }
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof MountOwnership that && owned.equals(that.owned) && selected == that.selected;
    }

    @Override
    public int hashCode() {
        return owned.hashCode() * 31 + (selected == null ? 0 : selected.hashCode());
    }

    @Override
    public String toString() {
        return toJson();
    }
}
