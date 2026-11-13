package com.eternalrealm.social.phone;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Set;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PhoneDataTest {
    private static final UUID A = new UUID(0, 1);
    private static final UUID B = new UUID(0, 2);
    private static final UUID C = new UUID(0, 3);
    private static final String POSTED = "00000000000000a1";
    private static final String SENT = "00000000000000b2";

    private static PhoneData sample() {
        PhoneData data = PhoneData.empty();
        data.friends().seen(A, "Alice", 1);
        data.friends().seen(B, "Bob", 1);
        data.friends().seen(C, "Carol", 1);
        data.friends().request(A, B, 2);
        data.friends().accept(B, A);
        data.photos().add(POSTED, A, 3);
        data.photos().use(POSTED);
        data.timeline().post(A, "a photo", POSTED, 3);
        data.photos().add(SENT, B, 4);
        data.photos().use(SENT);
        data.messages().send(B, A, "look", SENT, 4, new ArrayList<>());
        return data;
    }

    @Test
    public void whoMaySeeAPhoto() {
        PhoneData data = sample();
        assertTrue(data.canSee(C, POSTED));
        assertTrue(data.canSee(A, SENT) && data.canSee(B, SENT));
        assertFalse("not in C's conversations", data.canSee(C, SENT));
        data.friends().block(C, A);
        assertFalse("blocked authors' photos are hidden", data.canSee(C, POSTED));
        assertTrue("from both sides", !data.canSee(C, POSTED) && data.canSee(A, POSTED));
        assertFalse(data.canSee(A, "../secret"));
        assertEquals(Set.of(POSTED, SENT), data.photosInUse());
    }

    @Test
    public void theWholeDocumentSurvivesSaving() {
        PhoneData data = sample();
        String saved = data.toJson().toString();
        PhoneData loaded = PhoneData.fromJson(JsonParser.parseString(saved).getAsJsonObject());
        assertTrue(loaded.friends().areFriends(A, B));
        assertEquals(1, loaded.timeline().size());
        assertEquals(1, loaded.messages().unread(A, B));
        assertTrue(loaded.photos().has(POSTED) && loaded.photos().has(SENT));
        assertEquals(saved.length(), loaded.toJson().toString().length());
    }

    @Test
    public void aDamagedDocumentLoadsWhatItCan() {
        JsonObject json = sample().toJson();
        JsonArray posts = json.getAsJsonObject("timeline").getAsJsonArray("posts");
        JsonObject bad = new JsonObject();
        bad.addProperty("id", "not a number");
        posts.add(bad);
        JsonObject evil = new JsonObject();
        evil.addProperty("id", 77);
        evil.addProperty("author", A.toString());
        evil.addProperty("text", "§kbad");
        evil.addProperty("photo", "../../level.dat");
        posts.add(evil);
        json.add("messages", new JsonArray()); // the wrong kind entirely
        PhoneData loaded = PhoneData.fromJson(json);
        assertEquals("the good post only", 1, loaded.timeline().size());
        assertTrue(loaded.messages().summaries(A).isEmpty());
        assertTrue(loaded.friends().areFriends(A, B));
        assertTrue(PhoneData.fromJson(new JsonObject()).photosInUse().isEmpty());
    }
}
