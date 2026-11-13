package com.eternalrealm.social.phone;

import com.google.gson.JsonObject;
import org.junit.Test;

import java.util.ArrayList;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PhoneViewTest {
    private static final UUID A = new UUID(0, 1);
    private static final UUID B = new UUID(0, 2);
    private static final UUID C = new UUID(0, 3);

    private static PhoneData data() {
        PhoneData data = PhoneData.empty();
        data.friends().seen(A, "Alice", 1);
        data.friends().seen(B, "Bob", 1);
        data.friends().seen(C, "Carol", 1);
        data.friends().request(A, B, 2);
        data.friends().accept(B, A);
        data.friends().request(A, C, 2);
        data.friends().accept(C, A);
        return data;
    }

    @Test
    public void badgesTravelWithEveryView() {
        PhoneData data = data();
        data.messages().send(B, A, "hi", "", 3, new ArrayList<>());
        data.friends().seen(new UUID(0, 9), "Dan", 1);
        data.friends().request(new UUID(0, 9), A, 4);
        long post = data.timeline().post(A, "post", "", 5).post().id();
        data.timeline().comment(B, post, "nice", 6);
        PhoneView view = new PhoneView(data, id -> id.equals(B));
        for (JsonObject json : new JsonObject[]{view.home(A), view.friends(A), view.messages(A),
                view.feed(A, Timeline.Feed.ALL, 0, false)}) {
            assertEquals(1, json.get("unread").getAsInt());
            assertEquals(1, json.get("requests").getAsInt());
            assertEquals(1, json.get("activity").getAsInt());
        }
        assertEquals(1, view.home(A).get("friendsOnline").getAsInt());
    }

    @Test
    public void onlineFriendsComeFirst() {
        PhoneView view = new PhoneView(data(), id -> id.equals(C));
        JsonObject friends = view.friends(A);
        assertEquals("Carol", friends.getAsJsonArray("friends").get(0).getAsJsonObject().get("name").getAsString());
        assertTrue(friends.getAsJsonArray("friends").get(0).getAsJsonObject().get("online").getAsBoolean());
    }

    @Test
    public void theFeedPagesAndHidesBlockedCommenters() {
        PhoneData data = data();
        for (int i = 0; i < Timeline.PAGE + 2; i++) {
            data.timeline().post(B, "p" + i, "", i);
        }
        long last = data.timeline().post(B, "with comments", "", 100).post().id();
        data.timeline().comment(C, last, "from Carol", 101);
        data.timeline().comment(A, last, "from Alice", 102);
        data.friends().block(A, C);
        PhoneView view = new PhoneView(data, id -> false);
        JsonObject page = view.feed(A, Timeline.Feed.ALL, 0, false);
        assertEquals(Timeline.PAGE, page.getAsJsonArray("posts").size());
        assertTrue(page.get("more").getAsBoolean());
        JsonObject post = view.post(A, last, false).getAsJsonObject("post");
        assertEquals(1, post.getAsJsonArray("commentList").size());
        assertFalse("not A's post, A no moderator", post.get("delete").getAsBoolean());
        assertTrue(view.post(A, last, true).getAsJsonObject("post").get("delete").getAsBoolean());
        assertTrue(view.post(A, 12345, false).has("gone"));
    }

    @Test
    public void aChatShowsWhoWroteWhat() {
        PhoneData data = data();
        data.messages().send(A, B, "mine", "", 1, new ArrayList<>());
        data.messages().send(B, A, "theirs", "", 2, new ArrayList<>());
        JsonObject chat = new PhoneView(data, id -> true).chat(A, B);
        assertTrue(chat.getAsJsonObject("with").get("friend").getAsBoolean());
        assertTrue(chat.getAsJsonArray("messages").get(0).getAsJsonObject().get("mine").getAsBoolean());
        assertFalse(chat.getAsJsonArray("messages").get(1).getAsJsonObject().get("mine").getAsBoolean());
    }
}
