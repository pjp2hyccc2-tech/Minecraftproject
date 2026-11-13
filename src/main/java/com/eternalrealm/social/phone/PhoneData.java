package com.eternalrealm.social.phone;

import com.google.gson.JsonObject;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Everything the 魔導スマホ keeps on the server (pure): friends, messages, the board and the photo index, saved as one
 * document so a save is never half-written. Server thread only.
 */
public final class PhoneData {
    public static final int VERSION = 1;

    private final FriendBook friends;
    private final Messages messages;
    private final Timeline timeline;
    private final PhotoShelf photos;

    private PhoneData(FriendBook friends, Messages messages, Timeline timeline, PhotoShelf photos) {
        this.friends = friends;
        this.messages = messages;
        this.timeline = timeline;
        this.photos = photos;
    }

    public static PhoneData empty() {
        return new PhoneData(new FriendBook(), new Messages(), new Timeline(), new PhotoShelf());
    }

    public FriendBook friends() {
        return friends;
    }

    public Messages messages() {
        return messages;
    }

    public Timeline timeline() {
        return timeline;
    }

    public PhotoShelf photos() {
        return photos;
    }

    /** Photos a post or a message still holds. */
    public Set<String> photosInUse() {
        Set<String> out = new HashSet<>(timeline.photos());
        out.addAll(messages.photos());
        return out;
    }

    /** May {@code viewer} see this photo: in a post of someone not blocked either way, or in their conversation. */
    public boolean canSee(UUID viewer, String photo) {
        if (!PhotoRules.validId(photo)) {
            return false;
        }
        boolean posted = timeline.postWithPhoto(photo)
                .map(post -> post.author().equals(viewer) || !friends.eitherBlocks(viewer, post.author()))
                .orElse(false);
        return posted || messages.canSee(viewer, photo)
                || photos.get(photo).map(entry -> entry.owner().equals(viewer)).orElse(false);
    }

    public JsonObject toJson() {
        JsonObject out = new JsonObject();
        out.addProperty("version", VERSION);
        out.add("friends", friends.toJson());
        out.add("messages", messages.toJson());
        out.add("timeline", timeline.toJson());
        out.add("photos", photos.toJson());
        return out;
    }

    /** Lenient: whatever cannot be read is left out. */
    public static PhoneData fromJson(JsonObject json) {
        return new PhoneData(FriendBook.fromJson(Json.object(json, "friends")),
                Messages.fromJson(Json.object(json, "messages")), Timeline.fromJson(Json.object(json, "timeline")),
                PhotoShelf.fromJson(Json.object(json, "photos")));
    }
}
