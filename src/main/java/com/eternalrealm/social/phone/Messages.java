package com.eternalrealm.social.phone;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Messages between two players on the 魔導スマホ (pure): one conversation per pair, the latest {@link #KEEP} messages
 * of each, what each side has read, and conversations left quiet for {@link #QUIET_DAYS} days forgotten. Who may write
 * to whom is decided by the caller (friends, not blocked). Server thread only.
 */
public final class Messages {
    public static final int KEEP = 60;
    public static final int QUIET_DAYS = 90;
    private static final long DAY = 86_400_000L;

    /** One message; {@code photo} is a photo id or "" for none. */
    public record Message(long id, UUID from, String text, String photo, long time) {
    }

    /** A conversation as listed: with whom, the last message, how many are unread. */
    public record Summary(UUID other, Message last, int unread) {
    }

    private static final class Conversation {
        final UUID a;
        final UUID b;
        final ArrayDeque<Message> messages = new ArrayDeque<>();
        long readA;
        long readB;

        Conversation(UUID a, UUID b) {
            this.a = a;
            this.b = b;
        }

        UUID other(UUID me) {
            return me.equals(a) ? b : a;
        }

        long read(UUID me) {
            return me.equals(a) ? readA : readB;
        }

        void read(UUID me, long id) {
            if (me.equals(a)) {
                readA = Math.max(readA, id);
            } else {
                readB = Math.max(readB, id);
            }
        }
    }

    private final Map<String, Conversation> conversations = new HashMap<>();
    private final Map<UUID, Set<String>> byPlayer = new HashMap<>();
    private long nextId = 1;

    private static String key(UUID a, UUID b) {
        return a.compareTo(b) < 0 ? a + ":" + b : b + ":" + a;
    }

    private Conversation conversation(UUID a, UUID b) {
        String key = key(a, b);
        Conversation found = conversations.get(key);
        if (found == null) {
            found = a.compareTo(b) < 0 ? new Conversation(a, b) : new Conversation(b, a);
            conversations.put(key, found);
            byPlayer.computeIfAbsent(a, id -> new HashSet<>()).add(key);
            byPlayer.computeIfAbsent(b, id -> new HashSet<>()).add(key);
        }
        return found;
    }

    /**
     * Keeps a message from {@code from} to {@code to} (read by its writer).
     *
     * @param released photos of messages that fell out of the conversation (added to)
     */
    public Message send(UUID from, UUID to, String text, String photo, long now, List<String> released) {
        Conversation conversation = conversation(from, to);
        Message message = new Message(nextId++, from, text, photo == null ? "" : photo, now);
        conversation.messages.addLast(message);
        conversation.read(from, message.id());
        while (conversation.messages.size() > KEEP) {
            Message gone = conversation.messages.removeFirst();
            if (!gone.photo().isEmpty()) {
                released.add(gone.photo());
            }
        }
        return message;
    }

    /** The conversation of {@code me} with {@code other}, oldest first. */
    public List<Message> history(UUID me, UUID other) {
        Conversation conversation = conversations.get(key(me, other));
        return conversation == null ? List.of() : List.copyOf(conversation.messages);
    }

    public void markRead(UUID me, UUID other) {
        Conversation conversation = conversations.get(key(me, other));
        if (conversation != null && !conversation.messages.isEmpty()) {
            conversation.read(me, conversation.messages.getLast().id());
        }
    }

    public int unread(UUID me, UUID other) {
        Conversation conversation = conversations.get(key(me, other));
        return conversation == null ? 0 : unread(conversation, me);
    }

    private static int unread(Conversation conversation, UUID me) {
        long read = conversation.read(me);
        int count = 0;
        Iterator<Message> back = conversation.messages.descendingIterator();
        while (back.hasNext()) {
            Message message = back.next();
            if (message.id() <= read) {
                break;
            }
            if (!message.from().equals(me)) {
                count++;
            }
        }
        return count;
    }

    public int unreadTotal(UUID me) {
        int total = 0;
        for (String key : byPlayer.getOrDefault(me, Set.of())) {
            total += unread(conversations.get(key), me);
        }
        return total;
    }

    /** {@code me}'s conversations, the latest first. */
    public List<Summary> summaries(UUID me) {
        List<Summary> out = new ArrayList<>();
        for (String key : byPlayer.getOrDefault(me, Set.of())) {
            Conversation conversation = conversations.get(key);
            if (!conversation.messages.isEmpty()) {
                out.add(new Summary(conversation.other(me), conversation.messages.getLast(), unread(conversation, me)));
            }
        }
        out.sort(Comparator.comparingLong((Summary s) -> s.last().id()).reversed());
        return out;
    }

    /** Forgets conversations quiet for {@link #QUIET_DAYS}; @return the photos they held */
    public List<String> forgetQuiet(long now) {
        List<String> released = new ArrayList<>();
        Iterator<Map.Entry<String, Conversation>> it = conversations.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, Conversation> entry = it.next();
            Conversation conversation = entry.getValue();
            long last = conversation.messages.isEmpty() ? 0L : conversation.messages.getLast().time();
            if (now - last > QUIET_DAYS * DAY) {
                for (Message message : conversation.messages) {
                    if (!message.photo().isEmpty()) {
                        released.add(message.photo());
                    }
                }
                byPlayer.getOrDefault(conversation.a, new HashSet<>()).remove(entry.getKey());
                byPlayer.getOrDefault(conversation.b, new HashSet<>()).remove(entry.getKey());
                it.remove();
            }
        }
        return released;
    }

    /** Every photo a message holds. */
    public Set<String> photos() {
        Set<String> out = new HashSet<>();
        for (Conversation conversation : conversations.values()) {
            for (Message message : conversation.messages) {
                if (!message.photo().isEmpty()) {
                    out.add(message.photo());
                }
            }
        }
        return out;
    }

    /** Whether {@code viewer} may see the photo (it is in one of their conversations). */
    public boolean canSee(UUID viewer, String photo) {
        for (String key : byPlayer.getOrDefault(viewer, Set.of())) {
            for (Message message : conversations.get(key).messages) {
                if (message.photo().equals(photo)) {
                    return true;
                }
            }
        }
        return false;
    }

    // ------------------------------------------------------------------ saving

    public JsonObject toJson() {
        JsonObject out = new JsonObject();
        out.addProperty("next", nextId);
        JsonArray list = new JsonArray();
        for (Conversation conversation : conversations.values()) {
            JsonObject c = new JsonObject();
            c.addProperty("a", conversation.a.toString());
            c.addProperty("b", conversation.b.toString());
            c.addProperty("readA", conversation.readA);
            c.addProperty("readB", conversation.readB);
            JsonArray messages = new JsonArray();
            for (Message message : conversation.messages) {
                JsonObject m = new JsonObject();
                m.addProperty("id", message.id());
                m.addProperty("from", message.from().toString());
                m.addProperty("text", message.text());
                if (!message.photo().isEmpty()) {
                    m.addProperty("photo", message.photo());
                }
                m.addProperty("time", message.time());
                messages.add(m);
            }
            c.add("messages", messages);
            list.add(c);
        }
        out.add("conversations", list);
        return out;
    }

    public static Messages fromJson(JsonObject json) {
        Messages out = new Messages();
        long highest = 0L;
        for (JsonElement e : Json.array(json, "conversations")) {
            JsonObject c = Json.object(e);
            Optional<UUID> a = Json.uuid(c, "a");
            Optional<UUID> b = Json.uuid(c, "b");
            if (a.isEmpty() || b.isEmpty() || a.get().equals(b.get())) {
                continue;
            }
            Conversation conversation = out.conversation(a.get(), b.get());
            for (JsonElement me : Json.array(c, "messages")) {
                JsonObject m = Json.object(me);
                Optional<UUID> from = Json.uuid(m, "from");
                long id = Json.number(m, "id", 0L);
                String photo = Json.string(m, "photo", "");
                if (from.isEmpty() || id <= 0L || (!from.get().equals(a.get()) && !from.get().equals(b.get()))
                        || (!photo.isEmpty() && !PhotoRules.validId(photo))) {
                    continue;
                }
                conversation.messages.addLast(new Message(id, from.get(),
                        PhoneText.clean(Json.string(m, "text", ""), false), photo, Json.number(m, "time", 0L)));
                highest = Math.max(highest, id);
            }
            while (conversation.messages.size() > KEEP) {
                conversation.messages.removeFirst();
            }
            UUID first = a.get().compareTo(b.get()) < 0 ? a.get() : b.get();
            long readOfA = Json.number(c, "readA", 0L);
            long readOfB = Json.number(c, "readB", 0L);
            // the saved a/b are already in order, but be safe if a document was edited by hand
            conversation.read(first, first.equals(a.get()) ? readOfA : readOfB);
            conversation.read(first.equals(a.get()) ? b.get() : a.get(), first.equals(a.get()) ? readOfB : readOfA);
        }
        out.nextId = Math.max(Json.number(json, "next", 1L), highest + 1);
        return out;
    }
}
