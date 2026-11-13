package com.eternalrealm.gametest;

import com.eternalrealm.boss.Difficulty;
import com.eternalrealm.core.ModInfo;
import com.eternalrealm.dungeon.forge.GatekeeperService;
import com.eternalrealm.server.ServerContext;
import com.eternalrealm.server.UiArgs;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
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
 * In-game tests of the gatekeeper's screen (転移門): the board the server sends lists every dungeon and the raid with
 * its boss numbers, records and refusals, opens on the entrance's dungeon, and a run only starts from a screen the
 * gatekeeper opened nearby.
 */
@GameTestHolder(ModInfo.MOD_ID)
@PrefixGameTestTemplate(false)
public final class GatekeeperGameTests {
    private static final String ARENA = "arena";

    private GatekeeperGameTests() {
    }

    @GameTest(template = ARENA)
    public static void theBoardListsEveryDungeonAndOpensOnTheEntrance(GameTestHelper helper) {
        ServerContext context = context();
        FakePlayer player = player(helper, "gate_board");
        JsonObject board = GatekeeperService.view(player, context, "d3");
        check("d3".equals(board.get("focus").getAsString()), "an entrance's gatekeeper opens on its own dungeon");
        check(!board.get("inParty").getAsBoolean() && board.get("leader").getAsBoolean()
                && board.get("partySize").getAsInt() == 1, "a player alone sets out solo");
        check(board.getAsJsonArray("rewards").size() == 3, "one reward line per difficulty");
        JsonArray entries = board.getAsJsonArray("entries");
        check(entries.size() == 9, "eight dungeons and the raid, got " + entries.size());
        check("d1".equals(entries.get(0).getAsJsonObject().get("id").getAsString())
                && "final".equals(entries.get(8).getAsJsonObject().get("id").getAsString()), "in order, raid last");
        for (JsonElement element : entries) {
            JsonObject row = element.getAsJsonObject();
            String id = row.get("id").getAsString();
            check(row.getAsJsonArray("hp").size() == 3 && row.getAsJsonArray("dmg").size() == 3
                    && row.getAsJsonArray("best").size() == 3, id + ": numbers for the three difficulties");
            check(!row.get("boss").getAsString().isEmpty(), id + ": names its boss");
            check(row.getAsJsonArray("hp").get(2).getAsDouble() > row.getAsJsonArray("hp").get(0).getAsDouble(),
                    id + ": EX bosses have more health");
            boolean open = row.get("open").getAsBoolean();
            String refusal = row.get("refusal").getAsString();
            check(open ? refusal.isEmpty() : "sealed".equals(refusal),
                    id + ": a solo player is only refused a sealed dungeon, got '" + refusal + "'");
        }
        check(entries.get(0).getAsJsonObject().get("open").getAsBoolean(), "the first dungeon is always open");
        check("d1".equals(GatekeeperService.view(player, context, null).get("focus").getAsString()),
                "a new level-1 player is shown the first dungeon");
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void settingOutNeedsAScreenOpenedNearby(GameTestHelper helper) {
        ServerContext context = context();
        FakePlayer player = player(helper, "gate_session");
        GatekeeperService.forget(player.getUUID());
        check(isKey(GatekeeperService.setOut(player, context, "d1", Difficulty.NORMAL),
                "message.eternalrealm.gate.too_far"), "no screen, no run");
        GatekeeperService.remember(player);
        player.moveTo(player.getX() + 20.0, player.getY(), player.getZ());
        check(isKey(GatekeeperService.setOut(player, context, "d1", Difficulty.NORMAL),
                "message.eternalrealm.gate.too_far"), "a player who walked away cannot set out from the screen");
        GatekeeperService.remember(player);
        boolean refused = false;
        try {
            GatekeeperService.setOut(player, context, "d9", Difficulty.NORMAL);
        } catch (UiArgs.InvalidArgumentException e) {
            refused = true;
        }
        check(refused, "an id that is no dungeon is a violation");
        GatekeeperService.forget(player.getUUID());
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

    private static ServerContext context() {
        return ServerContext.current().orElseThrow(() -> new GameTestAssertException("ServerContext is not installed"));
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new GameTestAssertException(message);
        }
    }
}
