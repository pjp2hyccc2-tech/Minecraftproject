package com.eternalrealm.social.phone;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * The 魔導スマホ's social board (つぶやき, pure): posts with a photo or not, likes, comments, and for each author what
 * others did with their posts (the app's badge). The latest {@link #KEEP} posts are kept. Who may see whose posts is
 * decided by the caller (blocks hide both ways). Server thread only.
 */
public final class Timeline {
    public static final int KEEP = 500;
    public static final int MAX_COMMENTS = 50;
    public static final int PAGE = 8;
    public static final int ACTIVITY_KEPT = 30;

    public enum Feed { ALL, FRIENDS, MINE }

    public enum ActivityKind { LIKE, COMMENT }

    public record Comment(long id, UUID author, String text, long time) {
    }

    public record Activity(long seq, ActivityKind kind, UUID who, long post, long time) {
    }

    /** A post (its likes and comments change). */
    public static final class Post {
        private final long id;
        private final UUID author;
        private final String text;
        private final String photo;
        private final long time;
        private final LinkedHashSet<UUID> likes = new LinkedHashSet<>();
        private final List<Comment> comments = new ArrayList<>();

        Post(long id, UUID author, String text, String photo, long time) {
            this.id = id;
            this.author = author;
            this.text = text;
            this.photo = photo;
            this.time = time;
        }

        public long id() {
            return id;
        }

        public UUID author() {
            return author;
        }

        public String text() {
            return text;
        }

        /** A photo id, or "". */
        public String photo() {
            return photo;
        }

        public long time() {
            return time;
        }

        public int likeCount() {
            return likes.size();
        }

        public boolean likedBy(UUID who) {
            return likes.contains(who);
        }

        public List<Comment> comments() {
            return List.copyOf(comments);
        }
    }

    /** What a post did: the post, and photos of posts pushed out by it. */
    public record Posted(Post post, List<String> released) {
    }

    private final ArrayDeque<Post> posts = new ArrayDeque<>(); // newest first
    private final Map<Long, Post> byId = new HashMap<>();
    private final Map<UUID, ArrayDeque<Activity>> activity = new HashMap<>();
    private final Map<UUID, Long> activitySeen = new HashMap<>();
    private long nextPost = 1;
    private long nextComment = 1;
    private long nextActivity = 1;

    public Posted post(UUID author, String text, String photo, long now) {
        Post post = new Post(nextPost++, author, text, photo == null ? "" : photo, now);
        posts.addFirst(post);
        byId.put(post.id(), post);
        List<String> released = new ArrayList<>();
        while (posts.size() > KEEP) {
            Post gone = posts.removeLast();
            byId.remove(gone.id());
            if (!gone.photo().isEmpty()) {
                released.add(gone.photo());
            }
        }
        return new Posted(post, released);
    }

    public Optional<Post> byId(long id) {
        return Optional.ofNullable(byId.get(id));
    }

    /** Likes or unlikes; @return whether it is liked now, or empty when there is no such post */
    public Optional<Boolean> toggleLike(UUID who, long postId, long now) {
        Post post = byId.get(postId);
        if (post == null) {
            return Optional.empty();
        }
        if (post.likes.remove(who)) {
            return Optional.of(false);
        }
        post.likes.add(who);
        note(post, ActivityKind.LIKE, who, now);
        return Optional.of(true);
    }

    /** A comment, unless there is no such post or it is full. */
    public Optional<Comment> comment(UUID who, long postId, String text, long now) {
        Post post = byId.get(postId);
        if (post == null || post.comments.size() >= MAX_COMMENTS) {
            return Optional.empty();
        }
        Comment comment = new Comment(nextComment++, who, text, now);
        post.comments.add(comment);
        note(post, ActivityKind.COMMENT, who, now);
        return Optional.of(comment);
    }

    private void note(Post post, ActivityKind kind, UUID who, long now) {
        if (post.author().equals(who)) {
            return;
        }
        ArrayDeque<Activity> list = activity.computeIfAbsent(post.author(), id -> new ArrayDeque<>());
        list.addFirst(new Activity(nextActivity++, kind, who, post.id(), now));
        while (list.size() > ACTIVITY_KEPT) {
            list.removeLast();
        }
    }

    /**
     * Deletes a post (its author, or a moderator).
     *
     * @return the deleted post's photo ("" if it had none), or empty when there is no such post or it is not theirs
     */
    public Optional<String> delete(long postId, UUID by, boolean moderator) {
        Post post = byId.get(postId);
        if (post == null || (!moderator && !post.author().equals(by))) {
            return Optional.empty();
        }
        posts.remove(post);
        byId.remove(postId);
        return Optional.of(post.photo());
    }

    /** Deletes a comment (its author, the post's author, or a moderator). */
    public boolean deleteComment(long postId, long commentId, UUID by, boolean moderator) {
        Post post = byId.get(postId);
        if (post == null) {
            return false;
        }
        Iterator<Comment> it = post.comments.iterator();
        while (it.hasNext()) {
            Comment comment = it.next();
            if (comment.id() == commentId) {
                if (!moderator && !comment.author().equals(by) && !post.author().equals(by)) {
                    return false;
                }
                it.remove();
                return true;
            }
        }
        return false;
    }

    /**
     * A page of the board for {@code viewer}, newest first: posts older than {@code beforeId} (0: from the newest),
     * at most {@code limit}.
     *
     * @param visible authors the viewer may see (not blocked either way)
     * @param friend  whether an author is the viewer's friend
     */
    public List<Post> feed(Feed feed, UUID viewer, long beforeId, int limit, Predicate<UUID> visible,
                           Predicate<UUID> friend) {
        List<Post> out = new ArrayList<>();
        for (Post post : posts) {
            if (out.size() >= limit) {
                break;
            }
            if (beforeId > 0 && post.id() >= beforeId) {
                continue;
            }
            boolean mine = post.author().equals(viewer);
            boolean shown = switch (feed) {
                case ALL -> mine || visible.test(post.author());
                case FRIENDS -> mine || (friend.test(post.author()) && visible.test(post.author()));
                case MINE -> mine;
            };
            if (shown) {
                out.add(post);
            }
        }
        return out;
    }

    /** What others did with {@code author}'s posts, newest first. */
    public List<Activity> activityOf(UUID author) {
        return List.copyOf(activity.getOrDefault(author, new ArrayDeque<>()));
    }

    /** How much of it {@code author} has not looked at yet. */
    public int unseenActivity(UUID author) {
        long seen = activitySeen.getOrDefault(author, 0L);
        int count = 0;
        for (Activity a : activity.getOrDefault(author, new ArrayDeque<>())) {
            if (a.seq() > seen) {
                count++;
            }
        }
        return count;
    }

    public void seeActivity(UUID author) {
        ArrayDeque<Activity> list = activity.get(author);
        if (list != null && !list.isEmpty()) {
            activitySeen.put(author, list.getFirst().seq());
        }
    }

    /** Every photo a post holds. */
    public Set<String> photos() {
        Set<String> out = new HashSet<>();
        for (Post post : posts) {
            if (!post.photo().isEmpty()) {
                out.add(post.photo());
            }
        }
        return out;
    }

    /** The post holding {@code photo}, if any. */
    public Optional<Post> postWithPhoto(String photo) {
        for (Post post : posts) {
            if (post.photo().equals(photo)) {
                return Optional.of(post);
            }
        }
        return Optional.empty();
    }

    public int size() {
        return posts.size();
    }

    // ------------------------------------------------------------------ saving

    public JsonObject toJson() {
        JsonObject out = new JsonObject();
        out.addProperty("nextPost", nextPost);
        out.addProperty("nextComment", nextComment);
        out.addProperty("nextActivity", nextActivity);
        JsonArray list = new JsonArray();
        for (Post post : posts) {
            JsonObject p = new JsonObject();
            p.addProperty("id", post.id());
            p.addProperty("author", post.author().toString());
            p.addProperty("text", post.text());
            if (!post.photo().isEmpty()) {
                p.addProperty("photo", post.photo());
            }
            p.addProperty("time", post.time());
            JsonArray likes = new JsonArray();
            for (UUID who : post.likes) {
                likes.add(who.toString());
            }
            p.add("likes", likes);
            JsonArray comments = new JsonArray();
            for (Comment comment : post.comments) {
                JsonObject c = new JsonObject();
                c.addProperty("id", comment.id());
                c.addProperty("author", comment.author().toString());
                c.addProperty("text", comment.text());
                c.addProperty("time", comment.time());
                comments.add(c);
            }
            p.add("comments", comments);
            list.add(p);
        }
        out.add("posts", list);
        JsonArray acts = new JsonArray();
        for (Map.Entry<UUID, ArrayDeque<Activity>> entry : activity.entrySet()) {
            for (Activity a : entry.getValue()) {
                JsonObject o = new JsonObject();
                o.addProperty("to", entry.getKey().toString());
                o.addProperty("seq", a.seq());
                o.addProperty("kind", a.kind().name());
                o.addProperty("who", a.who().toString());
                o.addProperty("post", a.post());
                o.addProperty("time", a.time());
                acts.add(o);
            }
        }
        out.add("activity", acts);
        JsonObject seen = new JsonObject();
        for (Map.Entry<UUID, Long> entry : activitySeen.entrySet()) {
            seen.addProperty(entry.getKey().toString(), entry.getValue());
        }
        out.add("seen", seen);
        return out;
    }

    public static Timeline fromJson(JsonObject json) {
        Timeline out = new Timeline();
        long highPost = 0;
        long highComment = 0;
        long highActivity = 0;
        for (JsonElement e : Json.array(json, "posts")) {
            JsonObject p = Json.object(e);
            Optional<UUID> author = Json.uuid(p, "author");
            long id = Json.number(p, "id", 0L);
            String photo = Json.string(p, "photo", "");
            if (author.isEmpty() || id <= 0L || out.byId.containsKey(id)
                    || (!photo.isEmpty() && !PhotoRules.validId(photo))) {
                continue;
            }
            Post post = new Post(id, author.get(), PhoneText.clean(Json.string(p, "text", ""), true), photo,
                    Json.number(p, "time", 0L));
            for (JsonElement like : Json.array(p, "likes")) {
                Json.uuid(like).ifPresent(post.likes::add);
            }
            for (JsonElement ce : Json.array(p, "comments")) {
                JsonObject c = Json.object(ce);
                Optional<UUID> who = Json.uuid(c, "author");
                long cid = Json.number(c, "id", 0L);
                if (who.isPresent() && cid > 0L && post.comments.size() < MAX_COMMENTS) {
                    post.comments.add(new Comment(cid, who.get(), PhoneText.clean(Json.string(c, "text", ""), false),
                            Json.number(c, "time", 0L)));
                    highComment = Math.max(highComment, cid);
                }
            }
            out.posts.addLast(post); // saved newest first
            out.byId.put(id, post);
            highPost = Math.max(highPost, id);
            if (out.posts.size() >= KEEP) {
                break;
            }
        }
        for (JsonElement e : Json.array(json, "activity")) {
            JsonObject o = Json.object(e);
            Optional<UUID> to = Json.uuid(o, "to");
            Optional<UUID> who = Json.uuid(o, "who");
            ActivityKind kind = "COMMENT".equals(Json.string(o, "kind", "")) ? ActivityKind.COMMENT
                    : "LIKE".equals(Json.string(o, "kind", "")) ? ActivityKind.LIKE : null;
            long seq = Json.number(o, "seq", 0L);
            if (to.isEmpty() || who.isEmpty() || kind == null || seq <= 0L) {
                continue;
            }
            ArrayDeque<Activity> list = out.activity.computeIfAbsent(to.get(), id -> new ArrayDeque<>());
            if (list.size() < ACTIVITY_KEPT) {
                list.addLast(new Activity(seq, kind, who.get(), Json.number(o, "post", 0L), Json.number(o, "time", 0L)));
            }
            highActivity = Math.max(highActivity, seq);
        }
        JsonObject seen = Json.object(json, "seen");
        for (Map.Entry<String, JsonElement> entry : seen.entrySet()) {
            try {
                UUID id = UUID.fromString(entry.getKey());
                if (entry.getValue().isJsonPrimitive() && entry.getValue().getAsJsonPrimitive().isNumber()) {
                    out.activitySeen.put(id, entry.getValue().getAsLong());
                }
            } catch (IllegalArgumentException | UnsupportedOperationException ignored) {
                // a key that is no player: skip it
            }
        }
        out.nextPost = Math.max(Json.number(json, "nextPost", 1L), highPost + 1);
        out.nextComment = Math.max(Json.number(json, "nextComment", 1L), highComment + 1);
        out.nextActivity = Math.max(Json.number(json, "nextActivity", 1L), highActivity + 1);
        return out;
    }
}
