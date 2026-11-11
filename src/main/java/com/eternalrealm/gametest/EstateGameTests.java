package com.eternalrealm.gametest;

import com.eternalrealm.core.ModInfo;
import com.eternalrealm.estate.EstateKey;
import com.eternalrealm.estate.EstateResult;
import com.eternalrealm.estate.EstateStatus;
import com.eternalrealm.estate.forge.EstateBlueprints;
import com.eternalrealm.estate.forge.EstateLevels;
import com.eternalrealm.estate.forge.EstateService;
import com.eternalrealm.estate.forge.RoomDoors;
import com.eternalrealm.world.forge.blueprint.Blueprint;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * The estates core (docs/modules/estates.md): every shipped design is a valid blueprint; a claimed room is built,
 * entered (the return point remembered) and left again; strangers are refused and sent out; nothing can be broken
 * or placed inside; a room door without a room sends the player to the innkeeper; a released estate is wiped.
 */
@GameTestHolder(ModInfo.MOD_ID)
@PrefixGameTestTemplate(false)
public final class EstateGameTests {
    private static final String EMPTY = "empty";
    private static final String DESIGN = "garret";

    private EstateGameTests() {
    }

    @GameTest(template = EMPTY, timeoutTicks = 600)
    public static void estateBlueprintsAreValid(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Map<String, List<String>> designs = EstateBlueprints.designs(level.getServer().getResourceManager());
        for (com.eternalrealm.estate.RoomDesign room : com.eternalrealm.estate.RoomDesign.values()) {
            check(designs.getOrDefault(EstateKey.ROOM, List.of()).contains(room.id()), "the room designs " + designs
                    + " lack " + room.id());
        }
        List<String> problems = new ArrayList<>();
        for (Map.Entry<String, List<String>> kind : designs.entrySet()) {
            for (String design : kind.getValue()) {
                Optional<Blueprint> loaded = EstateBlueprints.get(level.getServer(), kind.getKey(), design);
                if (loaded.isEmpty()) {
                    problems.add(kind.getKey() + "/" + design + " does not load");
                    continue;
                }
                Blueprint blueprint = loaded.get();
                List<String> palette = new ArrayList<>();
                blueprint.resolvePalette(level.holderLookup(Registries.BLOCK), palette);
                palette.forEach(p -> problems.add(kind.getKey() + "/" + design + ": " + p));
                for (Blueprint.Tile tile : blueprint.tiles()) {
                    try {
                        blueprint.decode(level.getServer().getResourceManager(), tile);
                    } catch (Exception e) {
                        problems.add(tile.file() + ": " + e.getMessage());
                    }
                }
                for (String spot : List.of(EstateService.ENTRY_SPOT, EstateService.EXIT_SPOT)) {
                    if (blueprint.spot(spot).isEmpty()) {
                        problems.add(kind.getKey() + "/" + design + " has no " + spot + " spot");
                    }
                }
            }
        }
        check(problems.isEmpty(), "blueprint problems: " + problems);
        check(EstateLevels.level(level.getServer()).isPresent(), "the estates dimension is missing");
        helper.succeed();
    }

    /** Claim, the build, in at the entry (the return point remembered), the estate found by position, out again. */
    @GameTest(template = EMPTY, timeoutTicks = 1800)
    public static void aRoomIsBuiltEnteredAndLeft(GameTestHelper helper) {
        FakePlayer player = fake(helper, "er_gt_estate_owner", new Vec3(2.5, 2.0, 2.5));
        EstateKey key = new EstateKey(EstateKey.ROOM, player.getUUID(), DESIGN);
        Vec3 start = player.position();
        ServerLevel home = helper.getLevel();
        check(EstateService.available(), "the estates are not running");
        EstateService.release(key);
        AtomicInteger stage = new AtomicInteger();
        AtomicReference<GameTestAssertException> failure = new AtomicReference<>();
        helper.onEachTick(() -> {
            if (failure.get() != null) {
                return;
            }
            try {
                switch (stage.get()) {
                    case 0 -> {
                        if (EstateService.estate(EstateKey.ROOM, player.getUUID()).isPresent()) {
                            return; // a leftover of an earlier run is being released
                        }
                        check(EstateService.claim(key) == EstateResult.OK, "claiming a room");
                        check(EstateService.claim(key) == EstateResult.ALREADY_CLAIMED, "one room per player");
                        check(EstateService.enter(player, key) == EstateResult.BUILDING, "no entry while building");
                        stage.set(1);
                    }
                    case 1 -> {
                        EstateStatus status = EstateService.status(key);
                        if (status.phase() != EstateStatus.Phase.READY) {
                            return;
                        }
                        check(EstateService.enter(player, key) == EstateResult.OK, "entering the built room");
                        check(EstateLevels.isEstateLevel(player.level()), "the player is in the estates");
                        Optional<EstateKey> here = EstateService.at(player);
                        check(here.isPresent() && here.get().sameEstate(key), "the room is found where the player is");
                        Blueprint.Spot entry = EstateService.spot(key, EstateService.ENTRY_SPOT).orElseThrow();
                        check(player.blockPosition().equals(entry.pos()), "arrived at the entry " + entry.pos()
                                + ", not " + player.blockPosition());
                        stage.set(2);
                    }
                    case 2 -> {
                        check(EstateService.leave(player), "leaving");
                        check(player.level() == home, "back in the level the player came from");
                        check(player.position().distanceTo(start) < 0.01, "back where the player was: "
                                + player.position() + " / " + start);
                        check(!EstateService.leave(player), "leaving twice does nothing");
                        check(EstateService.release(key) == EstateResult.OK, "releasing the room");
                        stage.set(3);
                    }
                    default -> {
                    }
                }
            } catch (GameTestAssertException e) {
                failure.set(e);
            }
        });
        helper.succeedWhen(() -> {
            if (failure.get() != null) {
                cleanUp(player);
                throw failure.get();
            }
            check(stage.get() == 3, "stage " + stage.get() + " (" + EstateService.status(key) + ")");
            check(EstateService.estate(EstateKey.ROOM, player.getUUID()).isEmpty(), "the room is released");
            cleanUp(player);
        });
    }

    /**
     * A stranger may not enter someone's room; one who is put inside anyway is sent out within a second. Nothing
     * can be broken or placed inside, not even by the owner.
     */
    @GameTest(template = EMPTY, timeoutTicks = 1800)
    public static void strangersAreRefusedAndNothingBreaks(GameTestHelper helper) {
        FakePlayer owner = fake(helper, "er_gt_estate_host", new Vec3(2.5, 2.0, 2.5));
        FakePlayer stranger = fake(helper, "er_gt_estate_stranger", new Vec3(4.5, 2.0, 4.5));
        EstateKey key = new EstateKey(EstateKey.ROOM, owner.getUUID(), DESIGN);
        ServerLevel home = helper.getLevel();
        EstateService.release(key);
        AtomicInteger stage = new AtomicInteger();
        AtomicInteger waited = new AtomicInteger();
        AtomicReference<GameTestAssertException> failure = new AtomicReference<>();
        helper.onEachTick(() -> {
            if (failure.get() != null) {
                return;
            }
            try {
                switch (stage.get()) {
                    case 0 -> {
                        if (EstateService.estate(EstateKey.ROOM, owner.getUUID()).isPresent()) {
                            return;
                        }
                        check(EstateService.claim(key) == EstateResult.OK, "claiming a room");
                        stage.set(1);
                    }
                    case 1 -> {
                        if (!EstateService.ready(key)) {
                            return;
                        }
                        check(EstateService.enter(stranger, key) == EstateResult.NOT_ALLOWED, "a stranger is refused");
                        check(stranger.level() == home, "the stranger stays outside");
                        check(EstateService.enter(owner, key) == EstateResult.OK, "the owner enters");
                        ServerLevel estates = (ServerLevel) owner.level();
                        BlockPos feet = owner.blockPosition();
                        BlockPos floor = feet.below();
                        BlockState before = estates.getBlockState(floor);
                        check(!before.isAir(), "the room has a floor");
                        check(!owner.gameMode.destroyBlock(floor), "breaking the floor is refused");
                        check(estates.getBlockState(floor) == before, "the floor is still there");
                        BlockEvent.EntityPlaceEvent place = new BlockEvent.EntityPlaceEvent(
                                net.minecraftforge.common.util.BlockSnapshot.create(estates.dimension(), estates, feet),
                                estates.getBlockState(floor), owner);
                        MinecraftForge.EVENT_BUS.post(place);
                        check(place.isCanceled(), "placing a block is refused");
                        // put the stranger inside by force: the estate's watch sends them out
                        stranger.teleportTo(estates, owner.getX(), owner.getY(), owner.getZ(), 0.0F, 0.0F);
                        check(EstateLevels.isEstateLevel(stranger.level()), "the stranger was put inside");
                        stage.set(2);
                    }
                    case 2 -> {
                        if (EstateLevels.isEstateLevel(stranger.level())) {
                            check(waited.incrementAndGet() < 60, "the stranger was not sent out");
                            return;
                        }
                        // the stranger never came in through a door, so has no way back: off to the capital
                        check(!EstateLevels.isEstateLevel(stranger.level()), "the stranger was sent out");
                        check(EstateService.leave(owner), "the owner leaves");
                        check(EstateService.release(key) == EstateResult.OK, "releasing the room");
                        stage.set(3);
                    }
                    default -> {
                    }
                }
            } catch (GameTestAssertException e) {
                failure.set(e);
            }
        });
        helper.succeedWhen(() -> {
            if (failure.get() != null) {
                cleanUp(owner);
                cleanUp(stranger);
                throw failure.get();
            }
            check(stage.get() == 3, "stage " + stage.get() + " (" + EstateService.status(key) + ")");
            cleanUp(owner);
            cleanUp(stranger);
        });
    }

    /** Using a room door without renting a room tells the player to see the innkeeper and goes nowhere. */
    @GameTest(template = EMPTY)
    public static void aRoomDoorWithoutARoomGoesNowhere(GameTestHelper helper) {
        FakePlayer player = fake(helper, "er_gt_estate_doorless", new Vec3(2.5, 2.0, 2.5));
        EstateService.release(new EstateKey(EstateKey.ROOM, player.getUUID(), DESIGN));
        BlockPos door = helper.absolutePos(new BlockPos(1, 1, 1));
        Vec3 before = player.position();
        RoomDoors.use(player, door);
        check(player.level() == helper.getLevel() && player.position().distanceTo(before) < 0.01,
                "the player stayed where they were");
        helper.succeed();
    }

    // ------------------------------------------------------------------ helpers

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
