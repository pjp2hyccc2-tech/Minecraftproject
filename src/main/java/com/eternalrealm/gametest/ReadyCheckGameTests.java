package com.eternalrealm.gametest;

import com.eternalrealm.boss.Difficulty;
import com.eternalrealm.core.ModInfo;
import com.eternalrealm.dungeon.forge.GatekeeperService;
import com.eternalrealm.dungeon.forge.ReadyChecks;
import com.eternalrealm.party.Party;
import com.eternalrealm.party.PartyService;
import com.eternalrealm.server.ServerContext;
import com.mojang.authlib.GameProfile;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;

/**
 * In-game tests of the ready check (出発の準備): a party leader setting out from the gate goes through it, and a party
 * that cannot go (here: members who are not online, as fake players never are) is told why and leaves nothing behind.
 * The check itself (answers, countdown, time-out, a changed party) is covered by {@code ReadyCheckTest}.
 */
@GameTestHolder(ModInfo.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ReadyCheckGameTests {
    private static final String ARENA = "arena";

    private ReadyCheckGameTests() {
    }

    @GameTest(template = ARENA)
    public static void aPartyThatCannotGoIsToldWhyAndNothingLingers(GameTestHelper helper) {
        ServerContext context = ServerContext.current()
                .orElseThrow(() -> new GameTestAssertException("ServerContext is not installed"));
        FakePlayer leader = player(helper, "ready_leader");
        FakePlayer member = player(helper, "ready_member");
        PartyService parties = context.game().parties().service();
        long now = helper.getLevel().getServer().getTickCount();
        parties.leave(leader.getUUID());
        parties.leave(member.getUUID());
        parties.create(leader.getUUID(), now);
        Party founded = parties.partyOf(leader.getUUID())
                .orElseThrow(() -> new GameTestAssertException("no party was founded"));
        parties.invite(leader.getUUID(), member.getUUID(), now);
        parties.accept(member.getUUID(), founded.id(), now);
        Party party = parties.partyOf(leader.getUUID()).orElseThrow();
        check(party.size() == 2 && party.isLeader(leader.getUUID()), "a party of two led by the leader");
        try {
            ReadyChecks checks = context.game().dungeons().readyChecks();
            check(isKey(checks.begin(leader, party, "d1", Difficulty.NORMAL),
                    "message.eternalrealm.dungeon.member_offline"), "members away: no ready check");
            check(!checks.isChecking(leader.getUUID()) && !checks.isChecking(member.getUUID()), "nothing lingers");
            GatekeeperService.remember(leader);
            check(isKey(GatekeeperService.setOut(leader, context, "d1", Difficulty.NORMAL),
                    "message.eternalrealm.dungeon.member_offline"), "the gate asks the party the same way");
            check(!checks.isChecking(leader.getUUID()), "still nothing lingers");
            GatekeeperService.remember(member);
            check(isKey(GatekeeperService.setOut(member, context, "d1", Difficulty.NORMAL),
                    "message.eternalrealm.dungeon.leader_only"), "a member who is not the leader cannot set out");
        } finally {
            parties.disband(leader.getUUID());
            GatekeeperService.forget(leader.getUUID());
            GatekeeperService.forget(member.getUUID());
        }
        helper.succeed();
    }

    private static boolean isKey(Optional<Component> message, String key) {
        return message.isPresent() && message.get().getContents() instanceof TranslatableContents contents
                && key.equals(contents.getKey());
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
