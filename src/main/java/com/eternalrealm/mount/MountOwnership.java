package com.eternalrealm.mount;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * The mounts a player keeps (their stable) and the one they call ({@link MountRules#EXTENSION} of the player data:
 * {@code {"v": 2, "mounts": [individual, ...], "selected": "<id>"}}, oldest first). Several of one kind may be kept.
 * Data from before individuality ({@code {"owned": ["horse", ...], "selected": "horse"}}) is read as one standard
 * mount of the classic look per kind. Immutable.
 */
public final class MountOwnership {
    public static final MountOwnership NONE = new MountOwnership(List.of(), null);
    private static final int VERSION = 2;

    private final List<MountIndividual> mounts;
    private final UUID selected;

    private MountOwnership(List<MountIndividual> mounts, UUID selected) {
        Map<UUID, MountIndividual> unique = new LinkedHashMap<>();
        for (MountIndividual mount : mounts) {
            unique.putIfAbsent(mount.id(), mount);
        }
        this.mounts = List.copyOf(unique.values());
        this.selected = selected != null && unique.containsKey(selected) ? selected : null;
    }

    /** Every mount kept, oldest first. */
    public List<MountIndividual> mounts() {
        return mounts;
    }

    public int size() {
        return mounts.size();
    }

    public boolean isEmpty() {
        return mounts.isEmpty();
    }

    public Optional<MountIndividual> mount(UUID id) {
        for (MountIndividual mount : mounts) {
            if (mount.id().equals(id)) {
                return Optional.of(mount);
            }
        }
        return Optional.empty();
    }

    /** The kinds of which at least one is kept. */
    public Set<MountKind> kinds() {
        EnumSet<MountKind> kinds = EnumSet.noneOf(MountKind.class);
        for (MountIndividual mount : mounts) {
            kinds.add(mount.kind());
        }
        return Collections.unmodifiableSet(kinds);
    }

    public boolean owns(MountKind kind) {
        return count(kind) > 0;
    }

    public int count(MountKind kind) {
        int count = 0;
        for (MountIndividual mount : mounts) {
            if (mount.kind() == kind) {
                count++;
            }
        }
        return count;
    }

    /** The first kept mount of {@code kind}. */
    public Optional<MountIndividual> first(MountKind kind) {
        for (MountIndividual mount : mounts) {
            if (mount.kind() == kind) {
                return Optional.of(mount);
            }
        }
        return Optional.empty();
    }

    /** The mount picked to be called, if the player picked one they keep. */
    public Optional<MountIndividual> selected() {
        return selected == null ? Optional.empty() : mount(selected);
    }

    /** The mount a call brings: the picked one, else the newest kept. */
    public Optional<MountIndividual> selectedOrFirst() {
        Optional<MountIndividual> picked = selected();
        if (picked.isPresent() || mounts.isEmpty()) {
            return picked;
        }
        return Optional.of(mounts.get(mounts.size() - 1));
    }

    /** With {@code mount} kept (and picked: a new mount is the one called next). */
    public MountOwnership withAdded(MountIndividual mount) {
        List<MountIndividual> more = new ArrayList<>(mounts);
        more.add(mount);
        return new MountOwnership(more, mount.id());
    }

    /** With {@code id} picked (ignored if not kept). */
    public MountOwnership withSelected(UUID id) {
        return mount(id).isPresent() ? new MountOwnership(mounts, id) : this;
    }

    /** Without {@code id} (made into a deed); the pick falls back to the newest kept. */
    public MountOwnership without(UUID id) {
        List<MountIndividual> fewer = new ArrayList<>(mounts);
        fewer.removeIf(mount -> mount.id().equals(id));
        return new MountOwnership(fewer, id.equals(selected) ? null : selected);
    }

    public String toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("v", VERSION);
        JsonArray array = new JsonArray();
        for (MountIndividual mount : mounts) {
            array.add(mount.toJson());
        }
        json.add("mounts", array);
        if (selected != null) {
            json.addProperty("selected", selected.toString());
        }
        return json.toString();
    }

    /** {@link #fromJson(String, UUID)} without an owner (old data gets ids that are the same for everyone). */
    public static MountOwnership fromJson(String text) {
        return fromJson(text, new UUID(0L, 0L));
    }

    /**
     * Lenient: unknown kinds and malformed entries are dropped (nothing kept at worst). Old data becomes one standard
     * mount per kind, with ids made from {@code owner} and the kind (the same on every load).
     */
    public static MountOwnership fromJson(String text, UUID owner) {
        if (text == null || text.isBlank()) {
            return NONE;
        }
        try {
            JsonElement parsed = JsonParser.parseString(text);
            if (parsed == null || !parsed.isJsonObject()) {
                return NONE;
            }
            JsonObject json = parsed.getAsJsonObject();
            if (json.has("mounts") && json.get("mounts").isJsonArray()) {
                List<MountIndividual> mounts = new ArrayList<>();
                for (JsonElement element : json.getAsJsonArray("mounts")) {
                    MountIndividual.fromJson(element).ifPresent(mounts::add);
                }
                UUID selected = json.has("selected") && json.get("selected").isJsonPrimitive()
                        ? MountIndividual.uuid(json.get("selected").getAsString()).orElse(null) : null;
                return new MountOwnership(mounts, selected);
            }
            return legacy(json, owner);
        } catch (JsonParseException | IllegalStateException | UnsupportedOperationException e) {
            return NONE;
        }
    }

    private static MountOwnership legacy(JsonObject json, UUID owner) {
        List<MountIndividual> mounts = new ArrayList<>();
        EnumSet<MountKind> kinds = EnumSet.noneOf(MountKind.class);
        if (json.has("owned") && json.get("owned").isJsonArray()) {
            for (JsonElement element : json.getAsJsonArray("owned")) {
                if (element.isJsonPrimitive()) {
                    MountKind.byId(element.getAsString()).ifPresent(kinds::add);
                }
            }
        }
        UUID selected = null;
        MountKind picked = json.has("selected") && json.get("selected").isJsonPrimitive()
                ? MountKind.byId(json.get("selected").getAsString()).orElse(null) : null;
        for (MountKind kind : kinds) {
            MountIndividual mount = MountIndividual.standard(legacyId(owner, kind), kind);
            mounts.add(mount);
            if (kind == picked) {
                selected = mount.id();
            }
        }
        return new MountOwnership(mounts, selected);
    }

    /** The id given to an old {@code kind} of {@code owner} (stable across loads until the data is saved anew). */
    public static UUID legacyId(UUID owner, MountKind kind) {
        return UUID.nameUUIDFromBytes(("eternalrealm:mount:" + owner + ":" + kind.id()).getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof MountOwnership that && mounts.equals(that.mounts)
                && java.util.Objects.equals(selected, that.selected);
    }

    @Override
    public int hashCode() {
        return mounts.hashCode() * 31 + (selected == null ? 0 : selected.hashCode());
    }

    @Override
    public String toString() {
        return toJson();
    }
}
