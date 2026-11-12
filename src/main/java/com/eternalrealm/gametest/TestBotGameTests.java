package com.eternalrealm.gametest;

import com.eternalrealm.character.CharacterSnapshot;
import com.eternalrealm.combat.forge.WeaponItem;
import com.eternalrealm.core.ModInfo;
import com.eternalrealm.job.JobType;
import com.eternalrealm.job.forge.JobActions;
import com.eternalrealm.monster.MonsterSpawn;
import com.eternalrealm.monster.MonsterTemplate;
import com.eternalrealm.monster.Monsters;
import com.eternalrealm.monster.forge.MonsterFactory;
import com.eternalrealm.server.ServerContext;
import com.eternalrealm.testkit.bots.BotIds;
import com.eternalrealm.testkit.bots.BotPlans;
import com.eternalrealm.testkit.bots.forge.BotBrain;
import com.eternalrealm.testkit.bots.forge.TestBot;
import com.eternalrealm.testkit.bots.forge.TestBots;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * The test bots (docs/modules/testkit.md §4) on a real server: they log in through the server's own login, move by a
 * player's inputs and physics, fight through the combat system's requests, run their errands without stumbling, and
 * log out like players.
 */
@GameTestHolder(ModInfo.MOD_ID)
@PrefixGameTestTemplate(false)
public final class TestBotGameTests {
    private static final String ARENA = "arena";
    /** Their own batch: a player logged into the world must not meet the other tests. */
    private static final String BATCH = "test_bots";

    private TestBotGameTests() {
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new GameTestAssertException(message);
        }
    }

    private static ServerContext context() {
        return ServerContext.current().orElseThrow(() -> new GameTestAssertException("the services are not running"));
    }

    private static double horizontal(Vec3 a, Vec3 b) {
        double dx = a.x - b.x;
        double dz = a.z - b.z;
        return Math.sqrt(dx * dx + dz * dz);
    }

    /**
     * A bot logs in (it is in the player list, as a test bot), walks across the arena on its own feet (steps of a
     * player's physics, not a teleport), and logs out like a player.
     */
    @GameTest(template = ARENA, batch = BATCH, timeoutTicks = 600)
    public static void aBotLogsInWalksAndLogsOut(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        int number = BotIds.MAX;
        UUID id = BotIds.idOf(number);
        TestBot bot = TestBots.spawnHeld(server, number, 1200);
        Vec3 start = helper.absoluteVec(new Vec3(1.5, 2.0, 4.5)); // on the arena's floor
        Vec3 end = helper.absoluteVec(new Vec3(7.5, 2.0, 4.5));
        AtomicInteger stage = new AtomicInteger();
        AtomicInteger steps = new AtomicInteger();
        AtomicReference<Vec3> last = new AtomicReference<>();
        AtomicBoolean done = new AtomicBoolean();
        AtomicReference<GameTestAssertException> failure = new AtomicReference<>();
        helper.onEachTick(() -> {
            if (failure.get() != null || done.get()) {
                return;
            }
            try {
                switch (stage.get()) {
                    case 0 -> {
                        check(server.getPlayerList().getPlayer(id) == bot, "the bot is in the player list");
                        check(TestBots.byId(id).orElse(null) == bot, "and known as a test bot");
                        check(bot.getGameProfile().getName().equals(BotIds.nameOf(number)),
                                "named " + BotIds.nameOf(number));
                        bot.teleportTo(helper.getLevel(), start.x, start.y, start.z, -90.0F, 0.0F);
                        bot.mind().walkTo(end, 0.6);
                        last.set(start);
                        stage.set(1);
                    }
                    case 1 -> {
                        check(bot.level() == helper.getLevel(), "still in the arena's world");
                        Vec3 now = bot.position();
                        double moved = horizontal(now, last.get());
                        check(moved < 1.0, "a step, not a jump: " + moved);
                        if (moved > 0.01) {
                            steps.incrementAndGet();
                        }
                        last.set(now);
                        if (horizontal(now, end) > 0.9) {
                            return;
                        }
                        check(Math.abs(now.y - start.y) < 0.6, "it walked on the floor: " + now.y);
                        check(steps.get() >= 8, "it took steps to get there: " + steps.get());
                        TestBots.remove(bot);
                        check(server.getPlayerList().getPlayer(id) == null, "logged out");
                        check(TestBots.byId(id).isEmpty(), "forgotten");
                        done.set(true);
                    }
                    default -> {
                    }
                }
            } catch (GameTestAssertException e) {
                failure.set(e);
                TestBots.remove(bot);
                throw e;
            }
        });
        helper.succeedWhen(() -> {
            if (failure.get() != null) {
                throw failure.get();
            }
            check(done.get(), "waiting for the bot");
        });
    }

    /**
     * A bot takes its job through the job service (the starter weapon comes into its hand), then fights a monster
     * through the combat system's requests until it has hurt it.
     */
    @GameTest(template = ARENA, batch = BATCH, timeoutTicks = 900)
    public static void aBotTakesAJobAndFights(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerContext context = context();
        int number = BotIds.MAX - 1;
        UUID id = BotIds.idOf(number);
        TestBot bot = TestBots.spawnHeld(server, number, 1700);
        AtomicInteger stage = new AtomicInteger();
        AtomicReference<Mob> enemy = new AtomicReference<>();
        AtomicReference<Float> before = new AtomicReference<>();
        AtomicBoolean done = new AtomicBoolean();
        AtomicReference<GameTestAssertException> failure = new AtomicReference<>();
        Runnable cleanUp = () -> {
            TestBots.remove(bot);
            if (enemy.get() != null) {
                enemy.get().discard();
            }
        };
        helper.onEachTick(() -> {
            if (failure.get() != null || done.get()) {
                return;
            }
            try {
                switch (stage.get()) {
                    case 0 -> {
                        if (!context.playerData().isActive(id)) {
                            return; // the character is loading
                        }
                        Vec3 at = helper.absoluteVec(new Vec3(2.5, 2.0, 4.5));
                        bot.teleportTo(helper.getLevel(), at.x, at.y, at.z, -90.0F, 0.0F);
                        CharacterSnapshot me = context.playerData().snapshot(id).orElseThrow();
                        if (!me.hasJob()) {
                            check(JobActions.chooseJob(bot, context, JobType.KNIGHT), "the job is taken");
                        }
                        check(WeaponItem.profileOf(bot.getMainHandItem()) != null, "a weapon in hand");
                        ServerLevel level = helper.getLevel();
                        MonsterTemplate template = Monsters.require("wandering_dead");
                        Vec3 there = helper.absoluteVec(new Vec3(6.5, 2.0, 4.5));
                        Mob mob = MonsterFactory.spawn(level, new MonsterSpawn(template, template.levels().min()),
                                there.x, there.y, there.z, 90.0F)
                                .orElseThrow(() -> new GameTestAssertException("no body for wandering_dead"));
                        mob.setNoAi(true);
                        enemy.set(mob);
                        before.set(mob.getHealth());
                        bot.mind().fight(mob);
                        stage.set(1);
                    }
                    case 1 -> {
                        Mob mob = enemy.get();
                        if (mob.isAlive() && mob.getHealth() >= before.get()) {
                            return;
                        }
                        check(bot.distanceTo(mob) < 4.5, "it closed in to fight: " + bot.distanceTo(mob));
                        cleanUp.run();
                        check(server.getPlayerList().getPlayer(id) == null, "logged out");
                        done.set(true);
                    }
                    default -> {
                    }
                }
            } catch (GameTestAssertException e) {
                failure.set(e);
                cleanUp.run();
                throw e;
            }
        });
        helper.succeedWhen(() -> {
            if (failure.get() != null) {
                throw failure.get();
            }
            Mob mob = enemy.get();
            check(done.get(), mob == null ? "waiting for the character to load"
                    : String.format(java.util.Locale.ROOT, "waiting for the bot to land a blow (%.1f blocks away, "
                    + "monster at %.0f/%.0f, bot sees it: %s)", bot.distanceTo(mob), mob.getHealth(), before.get(),
                    bot.hasLineOfSight(mob)));
        });
    }

    /**
     * A bot left to itself (but kept in town) takes the job of its number and goes about its errands for a while
     * without its plans ever going wrong.
     */
    @GameTest(template = ARENA, batch = BATCH, timeoutTicks = 2400)
    public static void aFreeBotTakesItsJobAndRunsErrands(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerContext context = context();
        int number = BotIds.MAX - 2;
        UUID id = BotIds.idOf(number);
        TestBot bot = TestBots.spawnHeld(server, number, 2600); // logged out by itself should the test fail
        bot.mind().keepInTown(true);
        bot.mind().release();
        AtomicInteger inTown = new AtomicInteger();
        AtomicBoolean done = new AtomicBoolean();
        AtomicReference<GameTestAssertException> failure = new AtomicReference<>();
        helper.onEachTick(() -> {
            if (failure.get() != null || done.get()) {
                return;
            }
            try {
                check(bot.mind().failures() == 0, "its plans went wrong (see the log)");
                CharacterSnapshot me = context.playerData().snapshot(id).orElse(null);
                if (me == null || !me.hasJob() || bot.mind().activity() == BotBrain.Activity.SETUP) {
                    return;
                }
                check(me.jobId().equals(BotPlans.jobOf(number).id()), "the job of its number: " + me.jobId());
                if (inTown.incrementAndGet() < 200) {
                    return; // ten seconds of errands
                }
                check(bot.mind().activity() != BotBrain.Activity.HUNT, "kept in town");
                TestBots.remove(bot);
                check(server.getPlayerList().getPlayer(id) == null, "logged out");
                done.set(true);
            } catch (GameTestAssertException e) {
                failure.set(e);
                TestBots.remove(bot);
                throw e;
            }
        });
        helper.succeedWhen(() -> {
            if (failure.get() != null) {
                throw failure.get();
            }
            check(done.get(), "waiting for the bot to take its job and run errands");
        });
    }
}
