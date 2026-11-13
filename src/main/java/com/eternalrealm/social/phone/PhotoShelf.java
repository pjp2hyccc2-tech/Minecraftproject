package com.eternalrealm.social.phone;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Which photos the server keeps (pure; the files themselves are the service's): who sent each and when, oldest first.
 * A photo sent but never posted nor messaged within {@link #PENDING_MILLIS} goes, as does one no post or message holds
 * any more, and the oldest once more than {@link PhotoRules#KEEP} are kept.
 */
public final class PhotoShelf {
    public static final long PENDING_MILLIS = 15 * 60_000L;

    public record Entry(String id, UUID owner, long time, boolean used) {
    }

    private final LinkedHashMap<String, Entry> entries = new LinkedHashMap<>();

    /** A new photo; @return photos pushed out to make room (their files must go) */
    public List<String> add(String id, UUID owner, long now) {
        entries.put(id, new Entry(id, owner, now, false));
        List<String> gone = new ArrayList<>();
        Iterator<String> oldest = entries.keySet().iterator();
        while (entries.size() > PhotoRules.KEEP && oldest.hasNext()) {
            String first = oldest.next();
            if (!first.equals(id)) {
                oldest.remove();
                gone.add(first);
            }
        }
        return gone;
    }

    public Optional<Entry> get(String id) {
        return Optional.ofNullable(entries.get(id));
    }

    public boolean has(String id) {
        return entries.containsKey(id);
    }

    /** {@code who} sent it and it is not used yet: they may put it in one post or message. */
    public boolean usableBy(String id, UUID who) {
        Entry entry = entries.get(id);
        return entry != null && !entry.used() && entry.owner().equals(who);
    }

    public void use(String id) {
        Entry entry = entries.get(id);
        if (entry != null) {
            entries.put(id, new Entry(entry.id(), entry.owner(), entry.time(), true));
        }
    }

    public void release(String id) {
        entries.remove(id);
    }

    /** Photos to delete now: unused for too long, or used but held by nothing any more. */
    public List<String> sweep(long now, Set<String> inUse) {
        List<String> gone = new ArrayList<>();
        Iterator<Map.Entry<String, Entry>> it = entries.entrySet().iterator();
        while (it.hasNext()) {
            Entry entry = it.next().getValue();
            boolean stale = entry.used() ? !inUse.contains(entry.id()) : now - entry.time() > PENDING_MILLIS;
            if (stale) {
                it.remove();
                gone.add(entry.id());
            }
        }
        return gone;
    }

    public int size() {
        return entries.size();
    }

    /** Every photo on the shelf. */
    public Set<String> ids() {
        return Set.copyOf(entries.keySet());
    }

    public JsonObject toJson() {
        JsonObject out = new JsonObject();
        JsonArray list = new JsonArray();
        for (Entry entry : entries.values()) {
            JsonObject e = new JsonObject();
            e.addProperty("id", entry.id());
            e.addProperty("owner", entry.owner().toString());
            e.addProperty("time", entry.time());
            e.addProperty("used", entry.used());
            list.add(e);
        }
        out.add("photos", list);
        return out;
    }

    public static PhotoShelf fromJson(JsonObject json) {
        PhotoShelf shelf = new PhotoShelf();
        for (JsonElement element : Json.array(json, "photos")) {
            JsonObject e = Json.object(element);
            String id = Json.string(e, "id", "");
            Optional<UUID> owner = Json.uuid(e, "owner");
            if (PhotoRules.validId(id) && owner.isPresent()) {
                boolean used = e.has("used") && e.get("used").isJsonPrimitive() && e.get("used").getAsBoolean();
                shelf.entries.put(id, new Entry(id, owner.get(), Json.number(e, "time", 0L), used));
            }
        }
        return shelf;
    }
}
