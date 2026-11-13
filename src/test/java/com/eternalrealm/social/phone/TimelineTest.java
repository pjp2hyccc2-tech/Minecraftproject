package com.eternalrealm.social.phone;

import org.junit.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TimelineTest {
    private static final UUID A = new UUID(0, 1);
    private static final UUID B = new UUID(0, 2);
    private static final UUID C = new UUID(0, 3);
    private static final String PHOTO = "00000000000000aa";

    @Test
    public void likesToggleAndTellTheAuthor() {
        Timeline timeline = new Timeline();
        long id = timeline.post(A, "first post", PHOTO, 1).post().id();
        assertEquals(true, timeline.toggleLike(B, id, 2).orElseThrow());
        assertEquals(true, timeline.toggleLike(A, id, 3).orElseThrow());
        assertEquals(2, timeline.byId(id).orElseThrow().likeCount());
        assertEquals("the author's own like is no news", 1, timeline.unseenActivity(A));
        assertEquals(false, timeline.toggleLike(B, id, 4).orElseThrow());
        assertEquals(1, timeline.byId(id).orElseThrow().likeCount());
        assertTrue(timeline.toggleLike(B, 999, 5).isEmpty());
        timeline.seeActivity(A);
        assertEquals(0, timeline.unseenActivity(A));
    }

    @Test
    public void commentsAreLimitedAndDeletedByTheRightPeople() {
        Timeline timeline = new Timeline();
        long id = timeline.post(A, "post", "", 1).post().id();
        Timeline.Comment byB = timeline.comment(B, id, "nice", 2).orElseThrow();
        Timeline.Comment byC = timeline.comment(C, id, "wow", 3).orElseThrow();
        assertEquals(2, timeline.unseenActivity(A));
        assertFalse("C may not delete B's comment", timeline.deleteComment(id, byB.id(), C, false));
        assertTrue("the post's author may", timeline.deleteComment(id, byB.id(), A, false));
        assertTrue("its writer may", timeline.deleteComment(id, byC.id(), C, false));
        for (int i = 0; i < Timeline.MAX_COMMENTS; i++) {
            timeline.comment(B, id, "c" + i, 10 + i);
        }
        assertTrue(timeline.comment(B, id, "one too many", 100).isEmpty());
    }

    @Test
    public void postsAreDeletedByTheirAuthorOrAModerator() {
        Timeline timeline = new Timeline();
        long a = timeline.post(A, "mine", PHOTO, 1).post().id();
        long b = timeline.post(B, "theirs", "", 2).post().id();
        assertTrue(timeline.delete(a, B, false).isEmpty());
        assertEquals(PHOTO, timeline.delete(a, A, false).orElseThrow());
        assertEquals("", timeline.delete(b, C, true).orElseThrow());
        assertEquals(0, timeline.size());
    }

    @Test
    public void theFeedHidesBlockedAuthorsAndPages() {
        Timeline timeline = new Timeline();
        for (int i = 0; i < 20; i++) {
            timeline.post(i % 2 == 0 ? B : C, "p" + i, "", i);
        }
        timeline.post(A, "me", "", 99);
        List<Timeline.Post> all = timeline.feed(Timeline.Feed.ALL, A, 0, 100, author -> !author.equals(C), author -> true);
        assertEquals(11, all.size()); // B's ten and A's own
        assertEquals(A, all.get(0).author());
        List<Timeline.Post> friends = timeline.feed(Timeline.Feed.FRIENDS, A, 0, 100, author -> true,
                author -> author.equals(C));
        assertEquals(11, friends.size());
        assertEquals(1, timeline.feed(Timeline.Feed.MINE, A, 0, 100, author -> true, author -> true).size());
        List<Timeline.Post> first = timeline.feed(Timeline.Feed.ALL, A, 0, 5, author -> true, author -> true);
        List<Timeline.Post> next = timeline.feed(Timeline.Feed.ALL, A, first.get(4).id(), 5, author -> true,
                author -> true);
        assertTrue(next.get(0).id() < first.get(4).id());
    }

    @Test
    public void theOldestPostsGoWithTheirPhotos() {
        Timeline timeline = new Timeline();
        timeline.post(A, "oldest", PHOTO, 0);
        List<String> released = List.of();
        for (int i = 1; i <= Timeline.KEEP; i++) {
            Timeline.Posted posted = timeline.post(B, "p" + i, "", i);
            if (!posted.released().isEmpty()) {
                released = posted.released();
            }
        }
        assertEquals(Timeline.KEEP, timeline.size());
        assertEquals(List.of(PHOTO), released);
        assertTrue(timeline.postWithPhoto(PHOTO).isEmpty());
    }

    @Test
    public void everythingSurvivesSaving() {
        Timeline timeline = new Timeline();
        long id = timeline.post(A, "line one\nline two", PHOTO, 1).post().id();
        timeline.toggleLike(B, id, 2);
        timeline.comment(C, id, "hi", 3);
        Timeline loaded = Timeline.fromJson(timeline.toJson());
        Timeline.Post post = loaded.byId(id).orElseThrow();
        assertEquals("line one\nline two", post.text());
        assertEquals(PHOTO, post.photo());
        assertTrue(post.likedBy(B));
        assertEquals("hi", post.comments().get(0).text());
        assertEquals(2, loaded.unseenActivity(A));
        assertTrue(loaded.post(B, "next", "", 4).post().id() > id);
        assertEquals(timeline.toJson().toString().length(), Timeline.fromJson(timeline.toJson()).toJson().toString().length());
    }
}
