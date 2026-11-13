package com.eternalrealm.social.phone;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * What the 魔導スマホ shows a player, as the JSON the server sends on UI channel {@code phone} (pure). Every view
 * carries the badges ({@code unread} messages, friend {@code requests}, new {@code activity} on the player's posts) and
 * {@code view}: {@code home}, {@code friends}, {@code messages}, {@code chat}, {@code feed}, {@code post}, {@code activity}.
 */
public final class PhoneView {
    private final PhoneData data;
    private final Predicate<UUID> online;

    /** @param online whether a player is on the server now */
    public PhoneView(PhoneData data, Predicate<UUID> online) {
        this.data = data;
        this.online = online;
    }

    private JsonObject base(UUID me, String view) {
        JsonObject out = new JsonObject();
        out.addProperty("view", view);
        out.addProperty("unread", data.messages().unreadTotal(me));
        out.addProperty("requests", data.friends().requestsTo(me).size());
        out.addProperty("activity", data.timeline().unseenActivity(me));
        return out;
    }

    private JsonObject person(UUID id) {
        JsonObject out = new JsonObject();
        out.addProperty("id", id.toString());
        out.addProperty("name", data.friends().name(id));
        out.addProperty("online", online.test(id));
        return out;
    }

    public JsonObject home(UUID me) {
        JsonObject out = base(me, "home");
        out.addProperty("friendsOnline", (int) data.friends().friendsOf(me).stream().filter(online).count());
        out.addProperty("friends", data.friends().friendsOf(me).size());
        return out;
    }

    public JsonObject friends(UUID me) {
        JsonObject out = base(me, "friends");
        JsonArray friends = new JsonArray();
        List<UUID> list = data.friends().friendsOf(me);
        // online first, then by name (friendsOf is by name)
        for (int pass = 0; pass < 2; pass++) {
            for (UUID id : list) {
                if (online.test(id) == (pass == 0)) {
                    JsonObject f = person(id);
                    f.addProperty("unread", data.messages().unread(me, id));
                    friends.add(f);
                }
            }
        }
        out.add("friends", friends);
        JsonArray requests = new JsonArray();
        for (Map.Entry<UUID, Long> request : data.friends().requestsTo(me)) {
            JsonObject r = person(request.getKey());
            r.addProperty("time", request.getValue());
            requests.add(r);
        }
        out.add("incoming", requests);
        JsonArray asked = new JsonArray();
        for (UUID id : data.friends().askedBy(me)) {
            asked.add(person(id));
        }
        out.add("asked", asked);
        JsonArray blocked = new JsonArray();
        for (UUID id : data.friends().blockedBy(me)) {
            blocked.add(person(id));
        }
        out.add("blocked", blocked);
        return out;
    }

    public JsonObject messages(UUID me) {
        JsonObject out = base(me, "messages");
        JsonArray list = new JsonArray();
        for (Messages.Summary summary : data.messages().summaries(me)) {
            if (data.friends().blocks(me, summary.other())) {
                continue;
            }
            JsonObject c = person(summary.other());
            c.addProperty("friend", data.friends().areFriends(me, summary.other()));
            c.addProperty("text", PhoneText.preview(summary.last().text(), 40));
            c.addProperty("photo", !summary.last().photo().isEmpty());
            c.addProperty("mine", summary.last().from().equals(me));
            c.addProperty("time", summary.last().time());
            c.addProperty("unread", summary.unread());
            list.add(c);
        }
        out.add("conversations", list);
        return out;
    }

    /** The conversation with {@code other} (call after marking it read, so the badge is right). */
    public JsonObject chat(UUID me, UUID other) {
        JsonObject out = base(me, "chat");
        JsonObject with = person(other);
        with.addProperty("friend", data.friends().areFriends(me, other));
        with.addProperty("blocked", data.friends().blocks(me, other));
        out.add("with", with);
        JsonArray list = new JsonArray();
        for (Messages.Message message : data.messages().history(me, other)) {
            JsonObject m = new JsonObject();
            m.addProperty("id", message.id());
            m.addProperty("mine", message.from().equals(me));
            m.addProperty("text", message.text());
            m.addProperty("photo", message.photo());
            m.addProperty("time", message.time());
            list.add(m);
        }
        out.add("messages", list);
        return out;
    }

    public JsonObject feed(UUID me, Timeline.Feed feed, long before, boolean moderator) {
        JsonObject out = base(me, "feed");
        out.addProperty("feed", feed.name().toLowerCase(java.util.Locale.ROOT));
        out.addProperty("before", before);
        List<Timeline.Post> posts = data.timeline().feed(feed, me, before, Timeline.PAGE + 1,
                author -> !data.friends().eitherBlocks(me, author), author -> data.friends().areFriends(me, author));
        JsonArray list = new JsonArray();
        for (int i = 0; i < Math.min(posts.size(), Timeline.PAGE); i++) {
            list.add(post(me, posts.get(i), moderator, false));
        }
        out.add("posts", list);
        out.addProperty("more", posts.size() > Timeline.PAGE);
        return out;
    }

    /** One post with its comments (empty view when it is gone or hidden). */
    public JsonObject post(UUID me, long postId, boolean moderator) {
        JsonObject out = base(me, "post");
        data.timeline().byId(postId)
                .filter(post -> post.author().equals(me) || !data.friends().eitherBlocks(me, post.author()))
                .ifPresentOrElse(post -> out.add("post", post(me, post, moderator, true)),
                        () -> out.addProperty("gone", postId));
        return out;
    }

    private JsonObject post(UUID me, Timeline.Post post, boolean moderator, boolean withComments) {
        JsonObject p = new JsonObject();
        p.addProperty("id", post.id());
        p.add("author", person(post.author()));
        p.addProperty("mine", post.author().equals(me));
        p.addProperty("text", post.text());
        p.addProperty("photo", post.photo());
        p.addProperty("time", post.time());
        p.addProperty("likes", post.likeCount());
        p.addProperty("liked", post.likedBy(me));
        p.addProperty("comments", post.comments().size());
        p.addProperty("delete", moderator || post.author().equals(me));
        if (withComments) {
            JsonArray comments = new JsonArray();
            for (Timeline.Comment comment : post.comments()) {
                if (!comment.author().equals(me) && data.friends().eitherBlocks(me, comment.author())) {
                    continue;
                }
                JsonObject c = new JsonObject();
                c.addProperty("id", comment.id());
                c.add("author", person(comment.author()));
                c.addProperty("text", comment.text());
                c.addProperty("time", comment.time());
                c.addProperty("delete", moderator || comment.author().equals(me) || post.author().equals(me));
                comments.add(c);
            }
            p.add("commentList", comments);
        }
        return p;
    }

    /** What others did with {@code me}'s posts (call before marking it seen, to show what is new). */
    public JsonObject activity(UUID me) {
        JsonObject out = base(me, "activity");
        JsonArray list = new JsonArray();
        for (Timeline.Activity a : data.timeline().activityOf(me)) {
            if (data.friends().eitherBlocks(me, a.who())) {
                continue;
            }
            JsonObject o = person(a.who());
            o.addProperty("kind", a.kind().name().toLowerCase(java.util.Locale.ROOT));
            o.addProperty("post", a.post());
            o.addProperty("time", a.time());
            list.add(o);
        }
        out.add("items", list);
        return out;
    }
}
