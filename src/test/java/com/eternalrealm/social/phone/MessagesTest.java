package com.eternalrealm.social.phone;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class MessagesTest {
    private static final UUID A = new UUID(0, 1);
    private static final UUID B = new UUID(0, 2);
    private static final UUID C = new UUID(0, 3);
    private static final String PHOTO = "0123456789abcdef";

    @Test
    public void aConversationIsSharedAndCountsWhatIsUnread() {
        Messages messages = new Messages();
        List<String> released = new ArrayList<>();
        messages.send(A, B, "hello", "", 1, released);
        messages.send(A, B, "are you there?", "", 2, released);
        assertEquals("the writer has read their own", 0, messages.unread(A, B));
        assertEquals(2, messages.unread(B, A));
        assertEquals(2, messages.unreadTotal(B));
        messages.send(B, A, "yes", "", 3, released);
        assertEquals("answering means having read", 0, messages.unread(B, A));
        assertEquals(1, messages.unread(A, B));
        assertEquals(3, messages.history(A, B).size());
        assertEquals(messages.history(A, B), messages.history(B, A));
        messages.markRead(A, B);
        assertEquals(0, messages.unreadTotal(A));
        assertTrue(released.isEmpty());
    }

    @Test
    public void conversationsAreListedLatestFirst() {
        Messages messages = new Messages();
        List<String> released = new ArrayList<>();
        messages.send(A, B, "to B", "", 1, released);
        messages.send(C, A, "from C", PHOTO, 2, released);
        List<Messages.Summary> list = messages.summaries(A);
        assertEquals(2, list.size());
        assertEquals(C, list.get(0).other());
        assertEquals(1, list.get(0).unread());
        assertEquals(PHOTO, list.get(0).last().photo());
        assertEquals(B, list.get(1).other());
        assertTrue(messages.canSee(A, PHOTO) && messages.canSee(C, PHOTO));
        assertFalse(messages.canSee(B, PHOTO));
    }

    @Test
    public void onlyTheLatestAreKeptAndTheirPhotosReleased() {
        Messages messages = new Messages();
        List<String> released = new ArrayList<>();
        messages.send(A, B, "with a photo", PHOTO, 0, released);
        for (int i = 1; i <= Messages.KEEP; i++) {
            messages.send(A, B, "m" + i, "", i, released);
        }
        assertEquals(Messages.KEEP, messages.history(A, B).size());
        assertEquals(List.of(PHOTO), released);
        assertFalse(messages.photos().contains(PHOTO));
    }

    @Test
    public void quietConversationsAreForgotten() {
        Messages messages = new Messages();
        List<String> released = new ArrayList<>();
        long day = 86_400_000L;
        messages.send(A, B, "old", PHOTO, 0, released);
        messages.send(A, C, "new", "", Messages.QUIET_DAYS * day, released);
        assertEquals(List.of(PHOTO), messages.forgetQuiet(Messages.QUIET_DAYS * day + 1));
        assertTrue(messages.history(A, B).isEmpty());
        assertEquals(1, messages.history(A, C).size());
        assertEquals(1, messages.summaries(A).size());
    }

    @Test
    public void everythingSurvivesSaving() {
        Messages messages = new Messages();
        List<String> released = new ArrayList<>();
        messages.send(A, B, "hi", PHOTO, 1, released);
        messages.send(B, A, "yo", "", 2, released);
        Messages loaded = Messages.fromJson(messages.toJson());
        assertEquals(messages.history(A, B), loaded.history(A, B));
        assertEquals(1, loaded.unread(A, B));
        assertEquals(0, loaded.unread(B, A));
        Messages.Message next = loaded.send(A, B, "next", "", 3, released);
        assertTrue("ids keep counting up", next.id() > 2);
    }
}
