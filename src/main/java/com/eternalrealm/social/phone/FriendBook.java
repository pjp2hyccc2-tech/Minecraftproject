package com.eternalrealm.social.phone;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Who is friends with whom on the 魔導スマホ (pure): requests (one side asks, the other accepts or declines; asking
 * someone who already asked you makes you friends), friendships (both ways), blocks (a block ends the friendship and
 * any request, and stops requests, messages and seeing each other's posts), and the names of everyone who has played
 * here (to find a friend by name while they are offline). Server thread only.
 */
public final class FriendBook {
    public static final int MAX_FRIENDS = 100;
    /** Requests waiting for one player. */
    public static final int MAX_REQUESTS = 50;
    public static final int MAX_BLOCKED = 200;

    public enum Result {
        OK,
        /** The request was answered at once: they had asked first. */
        BECAME_FRIENDS,
        SELF,
        UNKNOWN,
        ALREADY_FRIENDS,
        ALREADY_ASKED,
        /** You blocked them (unblock first). */
        YOU_BLOCKED,
        /** They blocked you (said as "cannot ask them"). */
        UNAVAILABLE,
        YOUR_LIST_FULL,
        THEIR_LIST_FULL,
        NO_REQUEST,
        NOT_FRIENDS,
        NOT_BLOCKED
    }

    public record Profile(UUID id, String name, long seen) {
    }

    private final Map<UUID, Profile> profiles = new HashMap<>();
    private final Map<String, UUID> byName = new HashMap<>();
    private final Map<UUID, Set<UUID>> friends = new HashMap<>();
    /** Requests to a player: who asked, when. */
    private final Map<UUID, LinkedHashMap<UUID, Long>> requests = new HashMap<>();
    private final Map<UUID, Set<UUID>> blocked = new HashMap<>();

    /** A player came online with this name. */
    public void seen(UUID id, String name, long now) {
        Profile old = profiles.get(id);
        if (old != null && !old.name().equals(name)) {
            byName.remove(old.name().toLowerCase(Locale.ROOT), id);
        }
        profiles.put(id, new Profile(id, name, now));
        byName.put(name.toLowerCase(Locale.ROOT), id);
    }

    public Optional<Profile> profile(UUID id) {
        return Optional.ofNullable(profiles.get(id));
    }

    /** The name last seen for {@code id} ("?" for someone never seen). */
    public String name(UUID id) {
        Profile profile = profiles.get(id);
        return profile == null ? "?" : profile.name();
    }

    /** Everyone who has played here under that name (any case), the latest first. */
    public Optional<UUID> byName(String name) {
        return name == null ? Optional.empty() : Optional.ofNullable(byName.get(name.toLowerCase(Locale.ROOT)));
    }

    public boolean areFriends(UUID a, UUID b) {
        return friends.getOrDefault(a, Set.of()).contains(b);
    }

    /** {@code by} blocked {@code who}. */
    public boolean blocks(UUID by, UUID who) {
        return blocked.getOrDefault(by, Set.of()).contains(who);
    }

    public boolean eitherBlocks(UUID a, UUID b) {
        return blocks(a, b) || blocks(b, a);
    }

    public boolean asked(UUID from, UUID to) {
        return requests.getOrDefault(to, new LinkedHashMap<>()).containsKey(from);
    }

    public Result request(UUID from, UUID to, long now) {
        if (from.equals(to)) {
            return Result.SELF;
        }
        if (!profiles.containsKey(to)) {
            return Result.UNKNOWN;
        }
        if (areFriends(from, to)) {
            return Result.ALREADY_FRIENDS;
        }
        if (blocks(from, to)) {
            return Result.YOU_BLOCKED;
        }
        if (blocks(to, from)) {
            return Result.UNAVAILABLE;
        }
        if (asked(to, from)) {
            Result accepted = accept(from, to);
            return accepted == Result.OK ? Result.BECAME_FRIENDS : accepted;
        }
        if (asked(from, to)) {
            return Result.ALREADY_ASKED;
        }
        if (count(from) >= MAX_FRIENDS) {
            return Result.YOUR_LIST_FULL;
        }
        if (count(to) >= MAX_FRIENDS || requests.getOrDefault(to, new LinkedHashMap<>()).size() >= MAX_REQUESTS) {
            return Result.THEIR_LIST_FULL;
        }
        requests.computeIfAbsent(to, id -> new LinkedHashMap<>()).put(from, now);
        return Result.OK;
    }

    /** {@code me} accepts {@code from}'s request. */
    public Result accept(UUID me, UUID from) {
        if (!asked(from, me)) {
            return Result.NO_REQUEST;
        }
        if (count(me) >= MAX_FRIENDS) {
            return Result.YOUR_LIST_FULL;
        }
        if (count(from) >= MAX_FRIENDS) {
            return Result.THEIR_LIST_FULL;
        }
        dropRequests(me, from);
        friends.computeIfAbsent(me, id -> new HashSet<>()).add(from);
        friends.computeIfAbsent(from, id -> new HashSet<>()).add(me);
        return Result.OK;
    }

    public Result decline(UUID me, UUID from) {
        LinkedHashMap<UUID, Long> mine = requests.get(me);
        if (mine == null || mine.remove(from) == null) {
            return Result.NO_REQUEST;
        }
        return Result.OK;
    }

    public Result remove(UUID me, UUID friend) {
        if (!areFriends(me, friend)) {
            return Result.NOT_FRIENDS;
        }
        unfriend(me, friend);
        return Result.OK;
    }

    public Result block(UUID me, UUID who) {
        if (me.equals(who)) {
            return Result.SELF;
        }
        Set<UUID> mine = blocked.computeIfAbsent(me, id -> new HashSet<>());
        if (!mine.contains(who) && mine.size() >= MAX_BLOCKED) {
            return Result.YOUR_LIST_FULL;
        }
        mine.add(who);
        unfriend(me, who);
        dropRequests(me, who);
        return Result.OK;
    }

    public Result unblock(UUID me, UUID who) {
        Set<UUID> mine = blocked.get(me);
        if (mine == null || !mine.remove(who)) {
            return Result.NOT_BLOCKED;
        }
        return Result.OK;
    }

    private void unfriend(UUID a, UUID b) {
        friends.getOrDefault(a, new HashSet<>()).remove(b);
        friends.getOrDefault(b, new HashSet<>()).remove(a);
    }

    private void dropRequests(UUID a, UUID b) {
        requests.getOrDefault(a, new LinkedHashMap<>()).remove(b);
        requests.getOrDefault(b, new LinkedHashMap<>()).remove(a);
    }

    private int count(UUID id) {
        return friends.getOrDefault(id, Set.of()).size();
    }

    /** {@code me}'s friends, by name. */
    public List<UUID> friendsOf(UUID me) {
        List<UUID> out = new ArrayList<>(friends.getOrDefault(me, Set.of()));
        out.sort(Comparator.comparing((UUID id) -> name(id).toLowerCase(Locale.ROOT)).thenComparing(UUID::toString));
        return out;
    }

    /** Who asked {@code me}, newest first, with when. */
    public List<Map.Entry<UUID, Long>> requestsTo(UUID me) {
        List<Map.Entry<UUID, Long>> out = new ArrayList<>(requests.getOrDefault(me, new LinkedHashMap<>()).entrySet());
        out.sort(Comparator.comparing((Map.Entry<UUID, Long> e) -> e.getValue()).reversed());
        return out;
    }

    /** Whom {@code me} asked (still waiting). */
    public List<UUID> askedBy(UUID me) {
        List<UUID> out = new ArrayList<>();
        for (Map.Entry<UUID, LinkedHashMap<UUID, Long>> entry : requests.entrySet()) {
            if (entry.getValue().containsKey(me)) {
                out.add(entry.getKey());
            }
        }
        out.sort(Comparator.comparing((UUID id) -> name(id).toLowerCase(Locale.ROOT)));
        return out;
    }

    public List<UUID> blockedBy(UUID me) {
        List<UUID> out = new ArrayList<>(blocked.getOrDefault(me, Set.of()));
        out.sort(Comparator.comparing((UUID id) -> name(id).toLowerCase(Locale.ROOT)));
        return out;
    }

    // ------------------------------------------------------------------ saving

    public JsonObject toJson() {
        JsonObject out = new JsonObject();
        JsonArray people = new JsonArray();
        for (Profile profile : profiles.values()) {
            JsonObject p = new JsonObject();
            p.addProperty("id", profile.id().toString());
            p.addProperty("name", profile.name());
            p.addProperty("seen", profile.seen());
            people.add(p);
        }
        out.add("profiles", people);
        JsonArray pairs = new JsonArray();
        for (Map.Entry<UUID, Set<UUID>> entry : friends.entrySet()) {
            for (UUID other : entry.getValue()) {
                if (entry.getKey().compareTo(other) < 0) {
                    JsonArray pair = new JsonArray();
                    pair.add(entry.getKey().toString());
                    pair.add(other.toString());
                    pairs.add(pair);
                }
            }
        }
        out.add("friends", pairs);
        JsonArray asks = new JsonArray();
        for (Map.Entry<UUID, LinkedHashMap<UUID, Long>> entry : requests.entrySet()) {
            for (Map.Entry<UUID, Long> ask : entry.getValue().entrySet()) {
                JsonObject r = new JsonObject();
                r.addProperty("from", ask.getKey().toString());
                r.addProperty("to", entry.getKey().toString());
                r.addProperty("time", ask.getValue());
                asks.add(r);
            }
        }
        out.add("requests", asks);
        JsonArray blocks = new JsonArray();
        for (Map.Entry<UUID, Set<UUID>> entry : blocked.entrySet()) {
            for (UUID who : entry.getValue()) {
                JsonObject b = new JsonObject();
                b.addProperty("by", entry.getKey().toString());
                b.addProperty("who", who.toString());
                blocks.add(b);
            }
        }
        out.add("blocked", blocks);
        return out;
    }

    /** Lenient: entries that cannot be read are skipped. */
    public static FriendBook fromJson(JsonObject json) {
        FriendBook book = new FriendBook();
        for (JsonElement e : Json.array(json, "profiles")) {
            JsonObject p = Json.object(e);
            Optional<UUID> id = Json.uuid(p, "id");
            String name = Json.string(p, "name", "");
            if (id.isPresent() && PhoneText.validName(name)) {
                book.seen(id.get(), name, Json.number(p, "seen", 0L));
            }
        }
        for (JsonElement e : Json.array(json, "friends")) {
            if (e.isJsonArray() && e.getAsJsonArray().size() == 2) {
                Optional<UUID> a = Json.uuid(e.getAsJsonArray().get(0));
                Optional<UUID> b = Json.uuid(e.getAsJsonArray().get(1));
                if (a.isPresent() && b.isPresent() && !a.get().equals(b.get())) {
                    book.friends.computeIfAbsent(a.get(), id -> new HashSet<>()).add(b.get());
                    book.friends.computeIfAbsent(b.get(), id -> new HashSet<>()).add(a.get());
                }
            }
        }
        for (JsonElement e : Json.array(json, "requests")) {
            JsonObject r = Json.object(e);
            Optional<UUID> from = Json.uuid(r, "from");
            Optional<UUID> to = Json.uuid(r, "to");
            if (from.isPresent() && to.isPresent() && !from.get().equals(to.get())) {
                book.requests.computeIfAbsent(to.get(), id -> new LinkedHashMap<>()).put(from.get(),
                        Json.number(r, "time", 0L));
            }
        }
        for (JsonElement e : Json.array(json, "blocked")) {
            JsonObject b = Json.object(e);
            Optional<UUID> by = Json.uuid(b, "by");
            Optional<UUID> who = Json.uuid(b, "who");
            if (by.isPresent() && who.isPresent()) {
                book.blocked.computeIfAbsent(by.get(), id -> new HashSet<>()).add(who.get());
            }
        }
        return book;
    }
}
