package com.eternalrealm.gametest;

import com.eternalrealm.character.CharacterRecord;
import com.eternalrealm.character.CharacterRules;
import com.eternalrealm.character.CharacterSnapshot;
import com.eternalrealm.character.SetLevelResult;
import com.eternalrealm.character.StatType;
import com.eternalrealm.core.ModInfo;
import com.eternalrealm.estate.EstateKey;
import com.eternalrealm.estate.EstateResult;
import com.eternalrealm.estate.EstateStatus;
import com.eternalrealm.estate.Showcase;
import com.eternalrealm.estate.forge.EstateLevels;
import com.eternalrealm.estate.forge.EstateService;
import com.eternalrealm.estate.forge.InnService;
import com.eternalrealm.estate.forge.Showcases;
import com.eternalrealm.guild.house.forge.GuildGates;
import com.eternalrealm.guild.house.forge.GuildHouseBlocks;
import com.eternalrealm.guild.house.forge.GuildPortalBlock;
import com.eternalrealm.guild.house.forge.GuildTheme;
import com.eternalrealm.player.PlayerDataService;
import com.eternalrealm.server.ServerContext;
import com.eternalrealm.world.forge.blueprint.Blueprint;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * The test kit (docs/modules/testkit.md): {@code /er admin test max} (operators only) takes a character to the level
 * cap with the purse full and every point unspent; a tester holding the test pass walks through a guild gate into the
 * design's showcase (built on the first try), out through its return gate, tours a room's showcase, and is taken out
 * when the pass is taken back; a player without the pass is refused everywhere.
 */
@GameTestHolder(ModInfo.MOD_ID)
@PrefixGameTestTemplate(false)
public final class TestKitGameTests {
    private static final String EMPTY = "empty";
    /** The smallest guild house and room: quick to build. */
    private static final String HOUSE = "pirate_ship";
    private static final String ROOM = "library";

    private TestKitGameTests() {
    }

    /**
     * Through the real command tree (the {@code /er} alias included): refused without permission, then the level cap,
     * the gold limit and every stat and skill point unspent.
     */
    @GameTest(template = EMPTY, timeoutTicks = 400)
    public static void theTestCommandMaxesACharacterOut(GameTestHelper helper) {
        ServerContext context = context();
        PlayerDataService data = context.playerData();
        FakePlayer player = fake(helper, "er_gt_kit_max", new Vec3(2.5, 2.0, 2.5));
        UUID id = player.getUUID();
        data.onPlayerJoin(id, player.getGameProfile().getName());
        AtomicInteger stage = new AtomicInteger();
        AtomicReference<GameTestAssertException> failure = new AtomicReference<>();
        helper.succeedWhen(() -> {
            if (failure.get() != null) {
                throw failure.get();
            }
            check(data.isActive(id), "waiting for the character to load");
            if (stage.getAndIncrement() > 0) {
                return;
            }
            try {
                check(data.setLevel(id, 5) == SetLevelResult.SUCCESS, "level 5");
                check(data.allocateStat(id, StatType.VIT, 3).isSuccess(), "three points into VIT");
                data.grantGold(id, 100L);
                run(player, 0, "er admin test max");
                CharacterSnapshot before = data.snapshot(id).orElseThrow();
                check(before.level() == 5 && before.allocatedStats().total() == 3,
                        "a player without permission changes nothing: " + before);

                run(player, 2, "er admin test max");
                CharacterRules rules = data.rules();
                CharacterSnapshot maxed = data.snapshot(id).orElseThrow();
                check(maxed.level() == rules.levelCap(), "the level cap, not " + maxed.level());
                check(maxed.gold() == CharacterRecord.MAX_GOLD, "the purse is full: " + maxed.gold());
                check(maxed.allocatedStats().total() == 0, "every stat point is back in the pool");
                check(maxed.unspentStatPoints() == rules.earnedStatPoints(rules.levelCap()),
                        "all the stat points of the cap: " + maxed.unspentStatPoints());
                check(maxed.unspentSkillPoints() == rules.earnedSkillPoints(rules.levelCap()),
                        "all the skill points of the cap: " + maxed.unspentSkillPoints());
                check(!Showcases.hasPass(player), "max alone opens nothing");
                run(player, 2, "eternalrealm admin test lock");
                check(!Showcases.hasPass(player), "locking without a pass is harmless");
            } catch (GameTestAssertException e) {
                failure.set(e);
                throw e;
            } finally {
                data.onPlayerLeave(id);
            }
        });
    }

    /**
     * The tester (pass) and a stranger (none) at a guild gate: the stranger is refused; the tester is held back while
     * the showcase is built, then walks in, may be there (the stranger may not), and leaves by the return gate. The
     * tester tours a room's showcase; the inn offers the tour to the tester only; taking the pass back takes the
     * tester out.
     */
    @GameTest(template = EMPTY, timeoutTicks = 9000)
    public static void aTesterToursTheShowcasesAndAStrangerCannot(GameTestHelper helper) {
        ServerLevel home = helper.getLevel();
        FakePlayer tester = fake(helper, "er_gt_kit_tester", new Vec3(1.5, 2.0, 4.5));
        FakePlayer stranger = fake(helper, "er_gt_kit_stranger", new Vec3(3.5, 2.0, 4.5));
        BlockPos gate = helper.absolutePos(new BlockPos(2, 2, 2));
        EstateKey house = Showcase.key(EstateKey.GUILD, HOUSE);
        EstateKey room = Showcase.key(EstateKey.ROOM, ROOM);
        AtomicInteger stage = new AtomicInteger();
        AtomicBoolean done = new AtomicBoolean();
        AtomicReference<GameTestAssertException> failure = new AtomicReference<>();
        Runnable cleanUp = () -> {
            for (FakePlayer player : List.of(tester, stranger)) {
                Showcases.revoke(player);
                EstateService.leave(player);
                cleanUp(player);
            }
            EstateService.release(house);
            EstateService.release(room);
        };
        helper.onEachTick(() -> {
            if (failure.get() != null || done.get()) {
                return;
            }
            try {
                switch (stage.get()) {
                    case 0 -> {
                        check(EstateService.available(), "the estates are not running");
                        check(Showcases.designs(EstateKey.ROOM).size() == 5, "five rooms to tour");
                        check(Showcases.designs(EstateKey.GUILD).size() == 20, "twenty guild houses to tour");
                        // a clean start: no pass, no showcase left from an earlier run
                        Showcases.revoke(tester);
                        Showcases.revoke(stranger);
                        EstateService.release(house);
                        EstateService.release(room);
                        check(Showcases.status(EstateKey.GUILD, HOUSE).phase() == EstateStatus.Phase.NONE,
                                "the showcase is gone");
                        check(Showcases.grant(tester), "the tester gets the pass");
                        check(!Showcases.grant(tester), "only once");
                        BlockState portal = portal(HOUSE);
                        home.setBlock(gate, portal, 3);
                        GuildGates.onPortal(stranger, gate, portal);
                        check(stranger.level() == home, "a stranger stays outside the gate");
                        check(Showcases.status(EstateKey.GUILD, HOUSE).phase() == EstateStatus.Phase.NONE,
                                "a stranger builds nothing");
                        GuildGates.onPortal(tester, gate, portal);
                        check(tester.level() == home, "the tester waits while the showcase is built");
                        check(Showcases.status(EstateKey.GUILD, HOUSE).phase() == EstateStatus.Phase.BUILDING,
                                "the gate started the showcase");
                        check(Showcases.tour(tester, EstateKey.ROOM, ROOM) == EstateResult.BUILDING,
                                "the room's showcase is being built too");
                        check(Showcases.tour(stranger, EstateKey.ROOM, ROOM) == EstateResult.NOT_ALLOWED,
                                "no tour without the pass");
                        stage.set(1);
                    }
                    case 1 -> {
                        if (!Showcases.status(EstateKey.GUILD, HOUSE).enterable()
                                || !Showcases.status(EstateKey.ROOM, ROOM).enterable()) {
                            return;
                        }
                        BlockState portal = portal(HOUSE);
                        home.setBlock(gate, portal, 3);
                        GuildGates.forget(tester.getUUID());
                        GuildGates.onPortal(tester, gate, portal);
                        check(EstateLevels.isEstateLevel(tester.level()), "the tester walked into the showcase");
                        check(EstateService.at(tester).filter(house::sameEstate).isPresent(),
                                "the tester stands in the showcase of " + HOUSE);
                        check(EstateService.mayAccess(tester, house), "the pass lets the tester in");
                        check(!EstateService.mayAccess(stranger, house), "a stranger may not be there");
                        Blueprint.Spot exit = EstateService.spot(house, EstateService.EXIT_SPOT).orElseThrow();
                        BlockPos back = behind(exit);
                        BlockState returnGate = tester.level().getBlockState(back);
                        check(returnGate.is(GuildHouseBlocks.PORTAL.get()), "the return gate behind the exit, not "
                                + returnGate);
                        GuildGates.forget(tester.getUUID());
                        GuildGates.onPortal(tester, back, returnGate);
                        check(tester.level() == home, "back out in the world after the return gate");
                        cleanUp(tester);

                        // the inn: the tour is offered to the tester only, and leads into the room's showcase
                        JsonObject offered = InnService.document(tester);
                        check(offered.get("tour").getAsBoolean(), "the innkeeper offers the tester a tour");
                        boolean ready = false;
                        for (JsonElement design : offered.getAsJsonArray("designs")) {
                            JsonObject json = design.getAsJsonObject();
                            if (ROOM.equals(json.get("id").getAsString())) {
                                ready = "ready".equals(json.get("tourPhase").getAsString());
                            }
                        }
                        check(ready, "the inn shows the room's showcase ready");
                        check(!InnService.document(stranger).get("tour").getAsBoolean(), "but not the stranger");
                        check(Showcases.tour(tester, EstateKey.ROOM, ROOM) == EstateResult.OK, "touring the room");
                        check(EstateService.at(tester).filter(room::sameEstate).isPresent(),
                                "the tester stands in the showcase of the " + ROOM);
                        check(EstateService.mayAccess(tester, room) && !EstateService.mayAccess(stranger, room),
                                "the pass decides who may be in the room's showcase");

                        // the pass is taken back: out at once, and no way in any more
                        check(Showcases.revoke(tester), "the pass is taken back");
                        check(!EstateLevels.isEstateLevel(tester.level()), "the tester was taken out");
                        check(!EstateService.mayAccess(tester, room), "no pass, no showcase");
                        check(Showcases.tour(tester, EstateKey.ROOM, ROOM) == EstateResult.NOT_ALLOWED,
                                "no tour any more");
                        cleanUp(tester);
                        done.set(true);
                        cleanUp.run();
                        helper.succeed();
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
    }

    // ------------------------------------------------------------------ helpers

    /** Runs a command as {@code player} with the given permission level. */
    private static void run(ServerPlayer player, int permission, String command) {
        player.server.getCommands().performPrefixedCommand(player.createCommandSourceStack().withPermission(permission)
                .withSuppressedOutput(), command);
    }

    private static BlockState portal(String design) {
        return GuildHouseBlocks.PORTAL.get().defaultBlockState()
                .setValue(GuildPortalBlock.THEME, GuildTheme.byId(design).orElseThrow())
                .setValue(GuildPortalBlock.AXIS, Direction.Axis.X);
    }

    /** The block behind a spot: the opposite of the way it faces (yaw 0 = south). */
    private static BlockPos behind(Blueprint.Spot spot) {
        double rad = Math.toRadians(spot.yaw());
        int dx = (int) Math.round(-Math.sin(rad));
        int dz = (int) Math.round(Math.cos(rad));
        return spot.pos().offset(-dx, 0, -dz);
    }

    /** A fake player that went through the estates is a real entity of its level now: take it out again. */
    private static void cleanUp(ServerPlayer player) {
        if (player.level() instanceof ServerLevel level && level.getPlayerByUUID(player.getUUID()) == player) {
            level.removePlayerImmediately(player, Entity.RemovalReason.CHANGED_DIMENSION);
        }
    }

    private static FakePlayer fake(GameTestHelper helper, String name, Vec3 at) {
        FakePlayer player = FakePlayerFactory.get(helper.getLevel(), new GameProfile(
                UUID.nameUUIDFromBytes(name.getBytes(StandardCharsets.UTF_8)), name));
        Vec3 position = helper.absoluteVec(at);
        player.moveTo(position.x, position.y, position.z, 0.0F, 0.0F);
        player.setDeltaMovement(Vec3.ZERO);
        return player;
    }

    private static ServerContext context() {
        return ServerContext.current().orElseThrow(() -> new GameTestAssertException("no server context"));
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new GameTestAssertException(message);
        }
    }
}
