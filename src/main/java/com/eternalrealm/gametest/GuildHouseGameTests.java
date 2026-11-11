package com.eternalrealm.gametest;

import com.eternalrealm.core.ModInfo;
import com.eternalrealm.estate.EstateKey;
import com.eternalrealm.estate.EstateStatus;
import com.eternalrealm.estate.forge.EstateBlueprints;
import com.eternalrealm.estate.forge.EstateLevels;
import com.eternalrealm.estate.forge.EstateService;
import com.eternalrealm.guild.Guild;
import com.eternalrealm.guild.GuildResult;
import com.eternalrealm.guild.GuildService;
import com.eternalrealm.guild.house.GateAccess;
import com.eternalrealm.guild.house.GuildHouseCatalog;
import com.eternalrealm.guild.house.GuildHouseOutcome;
import com.eternalrealm.guild.house.forge.GuildGates;
import com.eternalrealm.guild.house.forge.GuildHouseBlocks;
import com.eternalrealm.guild.house.forge.GuildHouses;
import com.eternalrealm.guild.house.forge.GuildPortalBlock;
import com.eternalrealm.guild.house.forge.GuildTheme;
import com.eternalrealm.player.PlayerDataService;
import com.eternalrealm.server.ServerContext;
import com.eternalrealm.world.forge.blueprint.Blueprint;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
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
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * The guild houses (docs/modules/guildhouse.md): every design ships a valid blueprint with its spots and the return
 * gate; only the guild master may buy; strangers are turned away at the gate; a member walks in through the gate,
 * finds the guild's house, walks out through its return gate; the master switches the design and the house is
 * rebuilt as the new one.
 */
@GameTestHolder(ModInfo.MOD_ID)
@PrefixGameTestTemplate(false)
public final class GuildHouseGameTests {
    private static final String EMPTY = "empty";
    private static final String FIRST = "northern_longhouse";
    private static final String SECOND = "mushroom_grove";

    private GuildHouseGameTests() {
    }

    /** All twenty designs load, resolve and carry the spots and blocks the game needs. */
    @GameTest(template = EMPTY, timeoutTicks = 600)
    public static void everyGuildHouseDesignIsAValidBlueprint(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        List<String> problems = new ArrayList<>();
        for (String design : GuildHouseCatalog.standard().designs().stream().map(d -> d.id()).toList()) {
            Optional<Blueprint> loaded = EstateBlueprints.get(level.getServer(), EstateKey.GUILD, design);
            if (loaded.isEmpty()) {
                problems.add(design + " does not load");
                continue;
            }
            List<String> palette = new ArrayList<>();
            loaded.get().resolvePalette(level.holderLookup(Registries.BLOCK), palette);
            palette.forEach(p -> problems.add(design + ": " + p));
            for (String spot : List.of(EstateService.ENTRY_SPOT, EstateService.EXIT_SPOT, "board")) {
                if (loaded.get().spot(spot).isEmpty()) {
                    problems.add(design + " has no " + spot + " spot");
                }
            }
            if (GuildTheme.byId(design).isEmpty()) {
                problems.add(design + " has no portal theme");
            }
        }
        check(problems.isEmpty(), "guild house blueprints: " + problems);
        helper.succeed();
    }

    /** Only the master buys; a stranger who walks into a gate stays outside. */
    @GameTest(template = EMPTY, timeoutTicks = 400)
    public static void onlyTheMasterBuysAndStrangersAreTurnedAway(GameTestHelper helper) {
        Setup s = setup(helper, "a");
        helper.succeedWhen(() -> {
            if (s.failure.get() != null) {
                throw s.failure.get();
            }
            s.waitForCharacters();
            if (s.stage.getAndIncrement() > 0) {
                return;
            }
            try {
                GuildHouses houses = houses();
                check(houses.buy(s.member, FIRST) == GuildHouseOutcome.NO_PERMISSION, "a member cannot buy");
                check(houses.service().design(s.guild).isEmpty(), "nothing was bought");
                check(houses.buy(s.stranger, FIRST) == GuildHouseOutcome.NOT_IN_GUILD, "a stranger cannot buy");
                BlockPos gate = helper.absolutePos(new BlockPos(2, 2, 2));
                BlockState portal = portal(FIRST);
                helper.getLevel().setBlock(gate, portal, 3);
                ServerLevel home = helper.getLevel();
                GuildGates.onPortal(s.stranger, gate, portal);
                check(s.stranger.level() == home && !EstateLevels.isEstateLevel(s.stranger.level()),
                        "a stranger stays outside the gate");
                GuildGates.onPortal(s.member, gate, portal);
                check(s.member.level() == home, "a member of a guild without a house stays outside too");
            } catch (GameTestAssertException e) {
                s.failure.set(e);
                throw e;
            } finally {
                s.close();
            }
        });
    }

    /**
     * The master buys the longhouse; a member walks through the gate into the guild's own house and out through the
     * return gate; a stranger may not be there; the master switches to the mushroom grove, the house is rebuilt and
     * the members are now let in by the mushroom grove's gate.
     */
    @GameTest(template = EMPTY, timeoutTicks = 9000)
    public static void aMemberEntersLeavesAndTheHouseSwitchesDesign(GameTestHelper helper) {
        Setup s = setup(helper, "b");
        ServerLevel home = helper.getLevel();
        BlockPos gate = helper.absolutePos(new BlockPos(2, 2, 2));
        AtomicReference<Long> paid = new AtomicReference<>();
        helper.onEachTick(() -> {
            if (s.failure.get() != null || s.done) {
                return;
            }
            try {
                switch (s.stage.get()) {
                    case 0 -> {
                        if (!s.charactersReady()) {
                            return;
                        }
                        check(EstateService.available(), "the estates are not running");
                        PlayerDataService data = ServerContext.current().orElseThrow().playerData();
                        data.grantGold(s.master.getUUID(), 50_000);
                        long before = data.gold(s.master.getUUID());
                        check(houses().buy(s.master, FIRST) == GuildHouseOutcome.OK, "the master buys " + FIRST);
                        paid.set(before - data.gold(s.master.getUUID()));
                        check(paid.get() == GuildHouseCatalog.standard().design(FIRST).orElseThrow().price(),
                                "the master paid the price, not " + paid.get());
                        check(houses().buy(s.master, SECOND) == GuildHouseOutcome.ALREADY_OWNED,
                                "one house per guild");
                        s.stage.set(1);
                    }
                    case 1 -> {
                        if (!EstateService.ready(key(s.guild))) {
                            return;
                        }
                        BlockState portal = portal(FIRST);
                        home.setBlock(gate, portal, 3);
                        GuildGates.onPortal(s.member, gate, portal);
                        check(EstateLevels.isEstateLevel(s.member.level()), "the member walked into the house");
                        check(EstateService.at(s.member).map(k -> k.owner().equals(s.guild)).orElse(false),
                                "the member stands in the guild's own house");
                        Blueprint.Spot entry = EstateService.spot(key(s.guild), EstateService.ENTRY_SPOT)
                                .orElseThrow();
                        check(s.member.blockPosition().equals(entry.pos()), "arrived at the entry");
                        check(EstateService.mayAccess(s.member, key(s.guild)), "members may be there");
                        check(!EstateService.mayAccess(s.stranger, key(s.guild)), "strangers may not");
                        // out through the return gate: the portal is just behind the exit spot
                        Blueprint.Spot exit = EstateService.spot(key(s.guild), EstateService.EXIT_SPOT).orElseThrow();
                        BlockPos back = behind(exit);
                        BlockState returnGate = s.member.level().getBlockState(back);
                        check(returnGate.is(GuildHouseBlocks.PORTAL.get()), "the return gate behind the exit, not "
                                + returnGate);
                        GuildGates.forget(s.member.getUUID());
                        GuildGates.onPortal(s.member, back, returnGate);
                        check(s.member.level() == home, "back out in the world after the return gate");
                        // the trip made the fake player a real entity of the test level: take it out again at once,
                        // or the monsters of tests nearby would hunt it instead of their own targets
                        cleanUp(s.member);
                        // the master switches the design: half of the price difference is not charged twice
                        check(houses().switchTo(s.member, SECOND) == GuildHouseOutcome.NO_PERMISSION,
                                "only the master switches");
                        check(houses().switchTo(s.master, SECOND) == GuildHouseOutcome.OK, "switching designs");
                        s.stage.set(2);
                    }
                    case 2 -> {
                        Optional<EstateKey> now = EstateService.estate(EstateKey.GUILD, s.guild);
                        EstateStatus status = EstateService.status(key(s.guild));
                        if (now.isEmpty() || !now.get().design().equals(SECOND)
                                || status.phase() != EstateStatus.Phase.READY) {
                            return;
                        }
                        check(houses().service().design(s.guild).map(d -> d.id().equals(SECOND)).orElse(false),
                                "the house record shows the new design");
                        check(houses().service().gate(SECOND, s.guild) == GateAccess.ENTER,
                                "the new design's gate lets the members in");
                        check(houses().service().gate(FIRST, s.guild) == GateAccess.OTHER_DESIGN,
                                "the old design's gate now turns them to the right one");
                        check(EstateService.spot(key(s.guild), EstateService.ENTRY_SPOT).isPresent(),
                                "the new house has its entry");
                        check(houses().sell(s.master) == GuildHouseOutcome.OK, "the master sells the house");
                        s.done = true;
                        s.close();
                        helper.succeed();
                    }
                    default -> {
                    }
                }
            } catch (GameTestAssertException e) {
                s.failure.set(e);
                s.close();
                throw e;
            }
        });
    }

    // ------------------------------------------------------------------ helpers

    /** Three characters (a master, a member and a stranger) and a guild of the first two. */
    private static final class Setup {
        final FakePlayer master;
        final FakePlayer member;
        final FakePlayer stranger;
        final UUID guild;
        final AtomicInteger stage = new AtomicInteger();
        final AtomicReference<GameTestAssertException> failure = new AtomicReference<>();
        boolean done;
        private boolean closed;

        Setup(FakePlayer master, FakePlayer member, FakePlayer stranger, UUID guild) {
            this.master = master;
            this.member = member;
            this.stranger = stranger;
            this.guild = guild;
        }

        boolean charactersReady() {
            PlayerDataService data = ServerContext.current().orElseThrow().playerData();
            return data.isActive(master.getUUID()) && data.isActive(member.getUUID())
                    && data.isActive(stranger.getUUID());
        }

        void waitForCharacters() {
            check(charactersReady(), "waiting for the characters to load");
        }

        void close() {
            if (closed) {
                return;
            }
            closed = true;
            ServerContext.current().ifPresent(context -> {
                GuildHouses.get().ifPresent(houses -> {
                    if (houses.service().design(guild).isPresent()) {
                        houses.sell(master);
                    }
                });
                EstateService.estate(EstateKey.GUILD, guild).ifPresent(EstateService::release);
                context.game().guilds().service().disband(master.getUUID());
                for (FakePlayer player : List.of(master, member, stranger)) {
                    EstateService.leave(player);
                    cleanUp(player);
                    context.playerData().onPlayerLeave(player.getUUID());
                }
            });
        }
    }

    private static Setup setup(GameTestHelper helper, String which) {
        ServerContext context = ServerContext.current()
                .orElseThrow(() -> new GameTestAssertException("ServerContext is not installed"));
        check(context.game().guilds().available(), "the guilds are not running");
        check(GuildHouses.get().map(GuildHouses::available).orElse(false), "the guild houses are not running");
        String run = UUID.randomUUID().toString().replace("-", "").substring(0, 4).toUpperCase(Locale.ROOT);
        FakePlayer master = fake(helper, "er_gt_gh" + which + "m_" + run, new Vec3(1.5, 2.0, 4.5));
        FakePlayer member = fake(helper, "er_gt_gh" + which + "a_" + run, new Vec3(2.5, 2.0, 4.5));
        FakePlayer stranger = fake(helper, "er_gt_gh" + which + "s_" + run, new Vec3(3.5, 2.0, 4.5));
        for (FakePlayer player : List.of(master, member, stranger)) {
            context.playerData().onPlayerJoin(player.getUUID(), player.getGameProfile().getName());
        }
        GuildService guilds = context.game().guilds().service();
        Instant now = Instant.now();
        GuildResult created = guilds.create(master.getUUID(), "Gate" + run + which, "G" + run.substring(0, 3), now);
        check(created.isSuccess(), "creating the test guild: " + created.outcome());
        Guild guild = created.guild().orElseThrow();
        check(guilds.invite(master.getUUID(), member.getUUID(), now).isSuccess(), "inviting the member");
        check(guilds.accept(member.getUUID(), guild.id(), now).isSuccess(), "the member joins");
        return new Setup(master, member, stranger, guild.id());
    }

    private static GuildHouses houses() {
        return GuildHouses.get().orElseThrow(() -> new GameTestAssertException("the guild houses are not running"));
    }

    private static EstateKey key(UUID guild) {
        return EstateService.estate(EstateKey.GUILD, guild)
                .orElseThrow(() -> new GameTestAssertException("the guild has no estate"));
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

    private static FakePlayer fake(GameTestHelper helper, String name, Vec3 at) {
        FakePlayer player = FakePlayerFactory.get(helper.getLevel(), new GameProfile(
                UUID.nameUUIDFromBytes(name.getBytes(StandardCharsets.UTF_8)), name));
        Vec3 position = helper.absoluteVec(at);
        player.moveTo(position.x, position.y, position.z, 0.0F, 0.0F);
        player.setDeltaMovement(Vec3.ZERO);
        return player;
    }

    /** A fake player that went through the estates is a real entity of its level now: take it out again. */
    private static void cleanUp(ServerPlayer player) {
        if (player.level() instanceof ServerLevel level && level.getPlayerByUUID(player.getUUID()) == player) {
            level.removePlayerImmediately(player, Entity.RemovalReason.CHANGED_DIMENSION);
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new GameTestAssertException(message);
        }
    }
}
