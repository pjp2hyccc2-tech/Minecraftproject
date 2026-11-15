package com.eternalrealm.gametest;

import com.eternalrealm.character.SetLevelResult;
import com.eternalrealm.core.ModInfo;
import com.eternalrealm.player.PlayerDataService;
import com.eternalrealm.quest.AcceptResult;
import com.eternalrealm.quest.ActiveQuest;
import com.eternalrealm.quest.QuestLog;
import com.eternalrealm.quest.QuestLogCodec;
import com.eternalrealm.quest.forge.QuestService;
import com.eternalrealm.server.ServerContext;
import com.google.gson.JsonParser;
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
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * One run of a dungeon carries both story quests of its area, "clear the dungeon" and "defeat its boss", since the
 * boss falls in the clear (2026-11-15: 「ダンジョンのクエストがトータル二回ダンジョンに挑まないといけないからそれを修正して」).
 * Through the real quest service: a boss quest accepted after the dungeon quest, and one accepted before the rule
 * (credited when the log loads).
 */
@GameTestHolder(ModInfo.MOD_ID)
@PrefixGameTestTemplate(false)
public final class DungeonQuestGameTests {
    private static final String ARENA = "arena";
    private static final Map<String, Integer> AREA_ONE_TO_THE_DUNGEON = Map.of("m1_arrival", 1, "m1_hunt", 1,
            "m1_dungeon", 1);

    private DungeonQuestGameTests() {
    }

    @GameTest(template = ARENA, timeoutTicks = 400)
    public static void theBossQuestAfterItsDungeonQuestIsReadyAtOnce(GameTestHelper helper) {
        withCharacter(helper, "er_gt_dungeon_boss", (context, data, player) -> {
            UUID id = player.getUUID();
            SetLevelResult level = data.setLevel(id, 10);
            check(level == SetLevelResult.SUCCESS, "the character is level 10: " + level);
            QuestLog log = new QuestLog(List.of(), AREA_ONE_TO_THE_DUNGEON, QuestLog.NEVER_RESET, Set.of());
            check(data.setExtension(id, QuestService.EXTENSION, QuestLogCodec.toJson(log).toString()),
                    "the quest log was set");
            QuestService quests = context.game().quests();
            AcceptResult result = quests.accept(player, "m1_boss");
            check(result == AcceptResult.SUCCESS, "the boss quest could be accepted: " + result);
            // its only objective done: ready to turn in at once, no second run
            check(quests.progressOf(player, "m1_boss").equals(List.of(1)),
                    "the boss fell in the dungeon run: " + quests.progressOf(player, "m1_boss"));
        });
    }

    @GameTest(template = ARENA, timeoutTicks = 400)
    public static void aBossQuestAcceptedBeforeTheFixIsCreditedOnLoad(GameTestHelper helper) {
        withCharacter(helper, "er_gt_dungeon_boss_old", (context, data, player) -> {
            UUID id = player.getUUID();
            QuestLog log = new QuestLog(List.of(new ActiveQuest("m1_boss", List.of(0))), AREA_ONE_TO_THE_DUNGEON,
                    QuestLog.NEVER_RESET, Set.of());
            check(data.setExtension(id, QuestService.EXTENSION, QuestLogCodec.toJson(log).toString()),
                    "the quest log was set");
            QuestService quests = context.game().quests();
            check(quests.progressOf(player, "m1_boss").equals(List.of(1)),
                    "the stored boss quest should be credited: " + quests.progressOf(player, "m1_boss"));
            // and saved: the character's stored log has it too
            String stored = data.extension(id, QuestService.EXTENSION).orElse("");
            QuestLog saved;
            try {
                saved = QuestLogCodec.fromJson(JsonParser.parseString(stored));
            } catch (Exception e) {
                throw new GameTestAssertException("the stored quest log cannot be read: " + e);
            }
            check(saved.active().stream().anyMatch(quest -> quest.questId().equals("m1_boss")
                    && quest.progress().equals(List.of(1))), "the credit was saved: " + stored);
        });
    }

    @FunctionalInterface
    private interface Body {
        void run(ServerContext context, PlayerDataService data, FakePlayer player);
    }

    /** A fake player whose character is loaded; the body runs once, everything is let go afterwards. */
    private static void withCharacter(GameTestHelper helper, String name, Body body) {
        ServerContext context = ServerContext.current()
                .orElseThrow(() -> new GameTestAssertException("no server context"));
        PlayerDataService data = context.playerData();
        FakePlayer player = FakePlayerFactory.get(helper.getLevel(),
                new GameProfile(UUID.nameUUIDFromBytes(name.getBytes(StandardCharsets.UTF_8)), name));
        Vec3 at = helper.absoluteVec(new Vec3(4.5, 2.0, 4.5));
        player.moveTo(at.x, at.y, at.z, 0.0F, 0.0F);
        UUID id = player.getUUID();
        data.onPlayerJoin(id, name);
        AtomicBoolean done = new AtomicBoolean();
        AtomicReference<GameTestAssertException> failure = new AtomicReference<>();
        helper.succeedWhen(() -> {
            if (failure.get() != null) {
                throw failure.get(); // a failed check must not turn into a pass on the next tick
            }
            if (done.get()) {
                return;
            }
            check(data.isActive(id), "waiting for the character to load");
            done.set(true);
            try {
                body.run(context, data, player);
            } catch (GameTestAssertException e) {
                failure.set(e);
                throw e;
            } finally {
                context.game().quests().onLogout(id);
                data.onPlayerLeave(id);
            }
        });
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new GameTestAssertException(message);
        }
    }
}
