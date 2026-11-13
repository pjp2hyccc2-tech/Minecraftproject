package com.eternalrealm.social.phone;

import org.junit.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class FriendBookTest {
    private static final UUID A = new UUID(0, 1);
    private static final UUID B = new UUID(0, 2);
    private static final UUID C = new UUID(0, 3);

    private static FriendBook book() {
        FriendBook book = new FriendBook();
        book.seen(A, "Alice", 1);
        book.seen(B, "Bob", 1);
        book.seen(C, "Carol", 1);
        return book;
    }

    @Test
    public void askingThenAcceptingMakesFriendsBothWays() {
        FriendBook book = book();
        assertEquals(FriendBook.Result.OK, book.request(A, B, 10));
        assertEquals(FriendBook.Result.ALREADY_ASKED, book.request(A, B, 11));
        assertEquals(1, book.requestsTo(B).size());
        assertEquals(List.of(B), book.askedBy(A));
        assertEquals(FriendBook.Result.OK, book.accept(B, A));
        assertTrue(book.areFriends(A, B) && book.areFriends(B, A));
        assertTrue(book.requestsTo(B).isEmpty());
        assertEquals(FriendBook.Result.ALREADY_FRIENDS, book.request(B, A, 12));
        assertEquals(FriendBook.Result.NO_REQUEST, book.accept(B, A));
    }

    @Test
    public void askingSomeoneWhoAskedYouAcceptsAtOnce() {
        FriendBook book = book();
        book.request(A, B, 10);
        assertEquals(FriendBook.Result.BECAME_FRIENDS, book.request(B, A, 11));
        assertTrue(book.areFriends(A, B));
    }

    @Test
    public void declineRemoveAndTheObviousRefusals() {
        FriendBook book = book();
        assertEquals(FriendBook.Result.SELF, book.request(A, A, 1));
        assertEquals(FriendBook.Result.UNKNOWN, book.request(A, new UUID(9, 9), 1));
        book.request(A, B, 1);
        assertEquals(FriendBook.Result.OK, book.decline(B, A));
        assertEquals(FriendBook.Result.NO_REQUEST, book.decline(B, A));
        assertFalse(book.areFriends(A, B));
        book.request(A, C, 1);
        book.accept(C, A);
        assertEquals(FriendBook.Result.OK, book.remove(C, A));
        assertFalse(book.areFriends(A, C));
        assertEquals(FriendBook.Result.NOT_FRIENDS, book.remove(C, A));
    }

    @Test
    public void aBlockEndsEverythingAndStopsRequests() {
        FriendBook book = book();
        book.request(A, B, 1);
        book.accept(B, A);
        book.request(C, A, 2);
        assertEquals(FriendBook.Result.OK, book.block(A, B));
        assertFalse(book.areFriends(A, B));
        assertTrue(book.blocks(A, B) && book.eitherBlocks(B, A));
        assertEquals(FriendBook.Result.YOU_BLOCKED, book.request(A, B, 3));
        assertEquals(FriendBook.Result.UNAVAILABLE, book.request(B, A, 3));
        assertEquals(FriendBook.Result.OK, book.block(A, C));
        assertTrue("C's request is gone", book.requestsTo(A).isEmpty());
        assertEquals(FriendBook.Result.OK, book.unblock(A, B));
        assertEquals(FriendBook.Result.NOT_BLOCKED, book.unblock(A, B));
        assertEquals(FriendBook.Result.OK, book.request(B, A, 4));
    }

    @Test
    public void listsAreLimited() {
        FriendBook book = book();
        for (int i = 0; i < FriendBook.MAX_REQUESTS; i++) {
            UUID asker = new UUID(1, i);
            book.seen(asker, "p" + i, 1);
            assertEquals(FriendBook.Result.OK, book.request(asker, A, i));
        }
        assertEquals(FriendBook.Result.THEIR_LIST_FULL, book.request(B, A, 99));
        for (int i = 0; i < FriendBook.MAX_FRIENDS; i++) {
            UUID friend = new UUID(2, i);
            book.seen(friend, "f" + i, 1);
            book.request(C, friend, i);
            book.accept(friend, C);
        }
        assertEquals(FriendBook.Result.YOUR_LIST_FULL, book.request(C, B, 1));
    }

    @Test
    public void namesAreFoundInAnyCaseAndFollowRenames() {
        FriendBook book = book();
        assertEquals(A, book.byName("alice").orElseThrow());
        book.seen(A, "Alicia", 2);
        assertTrue(book.byName("Alice").isEmpty());
        assertEquals(A, book.byName("ALICIA").orElseThrow());
        assertEquals("Alicia", book.name(A));
        assertEquals("?", book.name(new UUID(7, 7)));
    }

    @Test
    public void everythingSurvivesSaving() {
        FriendBook book = book();
        book.request(A, B, 5);
        book.request(C, A, 6);
        book.accept(A, C);
        book.block(B, C);
        FriendBook loaded = FriendBook.fromJson(book.toJson());
        assertTrue(loaded.areFriends(A, C) && loaded.areFriends(C, A));
        assertTrue(loaded.asked(A, B));
        assertTrue(loaded.blocks(B, C));
        assertEquals("Carol", loaded.name(C));
        assertEquals(book.toJson().toString().length(), loaded.toJson().toString().length());
    }
}
