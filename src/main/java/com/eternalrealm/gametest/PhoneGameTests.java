package com.eternalrealm.gametest;

import com.eternalrealm.core.ModInfo;
import com.eternalrealm.server.UiArgs;
import com.eternalrealm.social.phone.Messages;
import com.eternalrealm.social.phone.PhoneData;
import com.eternalrealm.social.phone.Timeline;
import com.eternalrealm.social.phone.forge.PhoneService;
import com.google.gson.JsonObject;
import com.mojang.authlib.GameProfile;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

/**
 * In-game tests of the 魔導スマホ's server (PhoneService) with fake players: a friend request by name and its answer,
 * a message (cleaned of formatting codes) and reading it, a post with a like and a comment, a stranger who cannot
 * write, a block that hides posts both ways, and arguments no genuine client sends. The rules themselves are covered by
 * the unit tests of {@code social.phone}.
 */
@GameTestHolder(ModInfo.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PhoneGameTests {
    private static final String ARENA = "arena";

    private PhoneGameTests() {
    }

    @GameTest(template = ARENA)
    public static void friendsWriteToEachOtherAndPost(GameTestHelper helper) {
        PhoneService phone = PhoneService.current()
                .orElseThrow(() -> new GameTestAssertException("the phone service is not running"));
        PhoneData data = phone.data();
        FakePlayer alice = player(helper, "phone_alice");
        FakePlayer bob = player(helper, "phone_bob");
        FakePlayer carol = player(helper, "phone_carol");
        phone.seen(alice);
        phone.seen(bob);
        phone.seen(carol);
        UUID a = alice.getUUID();
        UUID b = bob.getUUID();
        long postId = 0;
        try {
            phone.act(alice, "friend", args("op", "request", "name", "PHONE_BOB"));
            check(data.friends().asked(a, b), "a request by name (any case)");
            phone.act(bob, "friend", args("op", "accept", "id", a.toString()));
            check(data.friends().areFriends(a, b), "accepted: friends both ways");

            phone.act(alice, "send", args("to", b.toString(), "text", "§cこんにちは\u0000"));
            List<Messages.Message> history = phone.history(b, a);
            check(history.size() == 1 && history.get(0).text().equals("こんにちは"), "the message, cleaned: " + history);
            check(phone.unread(b) == 1, "unread for Bob");
            phone.viewFor(bob, args("view", "chat", "id", a.toString()));
            check(phone.unread(b) == 0, "reading the conversation marks it read");

            phone.act(carol, "send", args("to", a.toString(), "text", "hi"));
            check(phone.history(a, carol.getUUID()).isEmpty(), "a stranger cannot write");

            phone.act(alice, "post", args("text", "初めての投稿"));
            List<Timeline.Post> mine = data.timeline().feed(Timeline.Feed.MINE, a, 0, 5, id -> true, id -> true);
            check(!mine.isEmpty() && mine.get(0).text().equals("初めての投稿"), "the post is on the board");
            postId = mine.get(0).id();
            JsonObject like = new JsonObject();
            like.addProperty("id", postId);
            phone.act(bob, "like", like);
            JsonObject comment = new JsonObject();
            comment.addProperty("id", postId);
            comment.addProperty("text", "いいね！");
            phone.act(bob, "comment", comment);
            Timeline.Post post = data.timeline().byId(postId).orElseThrow();
            check(post.likeCount() == 1 && post.comments().size() == 1, "a like and a comment");
            check(data.timeline().unseenActivity(a) == 2, "Alice has news");

            phone.act(alice, "friend", args("op", "block", "id", b.toString()));
            check(!data.friends().areFriends(a, b), "a block ends the friendship");
            check(data.timeline().feed(Timeline.Feed.ALL, b, 0, 50, id -> !data.friends().eitherBlocks(b, id),
                    id -> false).stream().noneMatch(p -> p.author().equals(a)), "Bob no longer sees Alice's posts");
            phone.act(bob, "send", args("to", a.toString(), "text", "?"));
            check(phone.history(a, b).size() == 1, "and cannot write to her");

            refused(() -> phone.viewFor(alice, args("view", "nonsense")), "an unknown view");
            refused(() -> phone.act(alice, "friend", args("op", "nonsense", "id", b.toString())), "an unknown operation");
            refused(() -> phone.act(alice, "post", args("text", "x", "photo", "../../level.dat")), "a photo id that is a path");
        } finally {
            if (postId > 0) {
                JsonObject delete = new JsonObject();
                delete.addProperty("id", postId);
                phone.act(alice, "delete", delete);
            }
            data.friends().unblock(a, b);
            data.friends().remove(a, b);
        }
        check(data.timeline().byId(postId).isEmpty(), "the author deleted the post");
        helper.succeed();
    }

    private static JsonObject args(String... pairs) {
        JsonObject out = new JsonObject();
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            out.addProperty(pairs[i], pairs[i + 1]);
        }
        return out;
    }

    private static void refused(Runnable action, String what) {
        try {
            action.run();
        } catch (UiArgs.InvalidArgumentException expected) {
            return;
        }
        throw new GameTestAssertException(what + " was accepted");
    }

    private static FakePlayer player(GameTestHelper helper, String name) {
        FakePlayer player = FakePlayerFactory.get(helper.getLevel(),
                new GameProfile(UUID.nameUUIDFromBytes(name.getBytes(StandardCharsets.UTF_8)), name));
        Vec3 position = helper.absoluteVec(new Vec3(4.5, 2.0, 4.5));
        player.moveTo(position.x, position.y, position.z, 0.0F, 0.0F);
        return player;
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new GameTestAssertException(message);
        }
    }
}
