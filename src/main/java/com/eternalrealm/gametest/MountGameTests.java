package com.eternalrealm.gametest;

import com.eternalrealm.character.SetLevelResult;
import com.eternalrealm.combat.forge.CombatHits;
import com.eternalrealm.core.ModInfo;
import com.eternalrealm.dungeon.forge.DungeonLevels;
import com.eternalrealm.mount.MountIndividual;
import com.eternalrealm.mount.MountKind;
import com.eternalrealm.mount.MountMotion;
import com.eternalrealm.mount.MountOwnership;
import com.eternalrealm.mount.MountRules;
import com.eternalrealm.mount.forge.MountEntity;
import com.eternalrealm.mount.forge.MountService;
import com.eternalrealm.mount.forge.MountStable;
import com.eternalrealm.player.PlayerDataService;
import com.eternalrealm.server.ServerContext;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.authlib.GameProfile;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.nio.charset.StandardCharsets;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Mounts (docs/modules/mounts.md): buying behind the level gates and ownership kept across a rejoin; calling a mount
 * (the rider sits on it; it cannot be hurt or targeted; a blow from the saddle gets the rider off instead of landing;
 * it leaves on dismount; not in combat or in the dungeons); the
 * speeds on the server's own physics (bike faster than the horse, the horse faster than a sprint); the griffin's
 * flight up to its ceiling, its glide and its descent, the fall guard of a rider who gets off in the air, and a horse
 * that cannot fly.
 */
@GameTestHolder(ModInfo.MOD_ID)
@PrefixGameTestTemplate(false)
public final class MountGameTests {
    private static final String ARENA = "arena";
    /** 41x41 closed hall: long lanes along the west wall, clear of the pillars (x 10..11, 18..22, 29..30). */
    private static final String HALL = "boss_arena";
    private static final MountMotion.Input FORWARD = new MountMotion.Input(1.0, 0.0, false, false);
    private static final MountMotion.Input CLIMB = new MountMotion.Input(0.0, 0.0, true, false);
    private static final MountMotion.Input DESCEND = new MountMotion.Input(0.0, 0.0, false, true);
    /** The first three mounts (the breeds have their own tests: MountBreedGameTests). */
    private static final java.util.Set<MountKind> FIRST_THREE = EnumSet.of(MountKind.HORSE, MountKind.GRIFFIN,
            MountKind.BIKE);

    private MountGameTests() {
    }

    /**
     * Stage 0: the character loads; the purchases at each level gate (refused below it, no gold taken), the gold
     * check, choosing; leave and rejoin. Stage 1: everything is still owned and the choice kept.
     */
    @GameTest(template = ARENA, timeoutTicks = 400)
    public static void mountsAreSoldByLevelAndKeptAcrossARejoin(GameTestHelper helper) {
        PlayerDataService data = context().playerData();
        FakePlayer player = rider(helper, "er_gt_mount_buy", new Vec3(4.5, 2.0, 4.5));
        UUID id = player.getUUID();
        data.onPlayerJoin(id, "gametest_mounts");
        AtomicInteger stage = new AtomicInteger();
        AtomicReference<GameTestAssertException> failure = new AtomicReference<>();
        helper.succeedWhen(() -> {
            if (failure.get() != null) {
                throw failure.get();
            }
            switch (stage.get()) {
                case 0 -> {
                    if (!data.isActive(id)) {
                        throw new GameTestAssertException("waiting for the character to load");
                    }
                    try {
                        buyAtEachGate(data, player);
                    } catch (GameTestAssertException e) {
                        data.onPlayerLeave(id);
                        failure.set(e);
                        throw e;
                    }
                    data.onPlayerLeave(id);
                    data.onPlayerJoin(id, "gametest_mounts"); // per-player I/O order: loads after the logout save
                    stage.set(1);
                    throw new GameTestAssertException("waiting for the save and the reload");
                }
                case 1 -> {
                    if (!data.isActive(id)) {
                        throw new GameTestAssertException("waiting for the character to reload");
                    }
                    MountOwnership owned = MountService.ownership(player);
                    data.onPlayerLeave(id);
                    stage.set(2);
                    if (!owned.kinds().equals(FIRST_THREE) || owned.size() != 4
                            || owned.selected().map(MountIndividual::kind).orElse(null) != MountKind.GRIFFIN) {
                        failure.set(new GameTestAssertException("after the rejoin the player has " + owned));
                        throw failure.get();
                    }
                }
                default -> {
                    // checked in stage 1
                }
            }
        });
    }

    private static void buyAtEachGate(PlayerDataService data, FakePlayer player) {
        UUID id = player.getUUID();
        check(data.setLevel(id, 9) == SetLevelResult.SUCCESS, "setting level 9");
        data.grantGold(id, MountKind.HORSE.price() + MountKind.GRIFFIN.price());
        long gold = data.gold(id);
        expect(MountService.buy(player, MountKind.HORSE), MountRules.Purchase.LEVEL_TOO_LOW, "the horse at Lv9");
        check(data.gold(id) == gold && !MountService.ownership(player).owns(MountKind.HORSE),
                "a refused purchase must take no gold and give nothing");
        check(data.setLevel(id, 10) == SetLevelResult.SUCCESS, "setting level 10");
        List<MountIndividual> shown = MountStable.offers(player, MountKind.HORSE);
        check(shown.size() == MountRules.OFFERS, "the stablemaster shows " + MountRules.OFFERS + " horses");
        JsonObject screen = MountStable.screen(player, true, null);
        for (JsonElement kind : screen.getAsJsonArray("kinds")) {
            if (kind.getAsJsonObject().has("offers")) {
                for (JsonElement offer : kind.getAsJsonObject().getAsJsonArray("offers")) {
                    JsonObject json = offer.getAsJsonObject();
                    check(!json.has("speed") && !json.has("accel") && !json.has("lift") && !json.has("grade")
                            && json.has("coat"), "an offer shows its look only, not its values: " + json);
                }
            }
        }
        expect(MountService.buy(player, MountKind.HORSE), MountRules.Purchase.OK, "the horse at Lv10");
        check(data.gold(id) == gold - MountKind.HORSE.price(), "the horse costs " + MountKind.HORSE.price()
                + ", gold went from " + gold + " to " + data.gold(id));
        check(MountService.ownership(player).mount(shown.get(0).id()).filter(shown.get(0)::equals).isPresent(),
                "the horse bought is the one shown, with its look and values");
        check(!MountStable.offers(player, MountKind.HORSE).equals(shown), "after a purchase other horses are shown");
        data.grantGold(id, MountKind.HORSE.price());
        expect(MountService.buy(player, MountKind.HORSE), MountRules.Purchase.OK, "a second horse (each differs)");
        check(MountService.ownership(player).count(MountKind.HORSE) == 2, "two horses kept");
        expect(MountService.buy(player, MountKind.GRIFFIN), MountRules.Purchase.LEVEL_TOO_LOW, "the griffin at Lv10");
        check(data.setLevel(id, 30) == SetLevelResult.SUCCESS, "setting level 30");
        expect(MountService.buy(player, MountKind.GRIFFIN), MountRules.Purchase.OK, "the griffin at Lv30");
        expect(MountService.buy(player, MountKind.BIKE), MountRules.Purchase.LEVEL_TOO_LOW, "the bike at Lv30");
        check(data.setLevel(id, 50) == SetLevelResult.SUCCESS, "setting level 50");
        long left = data.gold(id);
        check(left == 0 || data.spendGold(id, left), "spending the rest of the gold");
        expect(MountService.buy(player, MountKind.BIKE), MountRules.Purchase.NOT_ENOUGH_GOLD, "the bike without gold");
        data.grantGold(id, MountKind.BIKE.price());
        expect(MountService.buy(player, MountKind.BIKE), MountRules.Purchase.OK, "the bike at Lv50");
        check(data.gold(id) == 0, "the bike costs all the gold that was left, " + data.gold(id) + " left");
        data.grantGold(id, MountKind.BIKE.price());
        expect(MountService.buy(player, MountKind.BIKE), MountRules.Purchase.ALREADY_OWNED, "one arcbike at most");
        check(data.spendGold(id, MountKind.BIKE.price()), "taking the test gold back");
        MountOwnership owned = MountService.ownership(player);
        check(owned.kinds().equals(FIRST_THREE)
                        && owned.selected().map(MountIndividual::kind).orElse(null) == MountKind.BIKE,
                "after buying all three: " + owned);
        check(MountService.select(player, MountKind.GRIFFIN), "choosing the griffin");
    }

    /**
     * A kept griffin made into a deed leaves the stable; the market files the deed under mounts by species and grade;
     * a player below the griffin's level cannot take it in, one at the level can, with the same look and values, and
     * the deed is used up. An arcbike is never a deed.
     */
    @GameTest(template = ARENA, timeoutTicks = 300)
    public static void aDeedCarriesAMountToAnotherStable(GameTestHelper helper) {
        PlayerDataService data = context().playerData();
        String run = UUID.randomUUID().toString().substring(0, 6);
        FakePlayer giver = rider(helper, "er_gt_deed_a_" + run, new Vec3(3.5, 2.0, 3.5));
        FakePlayer taker = rider(helper, "er_gt_deed_b_" + run, new Vec3(6.5, 2.0, 6.5));
        data.onPlayerJoin(giver.getUUID(), giver.getGameProfile().getName());
        data.onPlayerJoin(taker.getUUID(), taker.getGameProfile().getName());
        java.util.concurrent.atomic.AtomicBoolean done = new java.util.concurrent.atomic.AtomicBoolean();
        AtomicReference<GameTestAssertException> failure = new AtomicReference<>();
        helper.succeedWhen(() -> {
            if (failure.get() != null) {
                throw failure.get();
            }
            if (done.get()) {
                return;
            }
            check(data.isActive(giver.getUUID()) && data.isActive(taker.getUUID()), "waiting for the characters");
            done.set(true);
            try {
                MountIndividual griffin = MountIndividual.roll(UUID.randomUUID(), MountKind.GRIFFIN,
                        new java.util.SplittableRandom(11));
                check(MountService.keep(giver, griffin) && MountService.grant(giver, MountKind.BIKE),
                        "giving the griffin and the bike");
                for (int slot = 1; slot <= 35; slot++) {
                    giver.getInventory().items.set(slot, net.minecraft.world.item.ItemStack.EMPTY);
                    taker.getInventory().items.set(slot, net.minecraft.world.item.ItemStack.EMPTY);
                }
                check(MountStable.makeDeed(giver, griffin.id()), "making the deed");
                check(MountService.ownership(giver).mount(griffin.id()).isEmpty(), "the griffin left the stable");
                net.minecraft.world.item.ItemStack deed = giver.getInventory().items.get(1);
                check(com.eternalrealm.mount.forge.MountDeeds.read(deed).filter(griffin::equals).isPresent(),
                        "the deed holds the griffin with its look and values: " + deed.getTag());
                com.eternalrealm.economy.ItemInfo info = com.eternalrealm.economy.forge.MarketItems.describe(deed, 1);
                check("mount".equals(info.category()) && ("mount:griffin:" + griffin.grade().id()).equals(info.group()),
                        "the market files the deed as " + info.category() + " / " + info.group());
                MountIndividual bike = MountService.ownership(giver).first(MountKind.BIKE).orElseThrow();
                check(!MountStable.makeDeed(giver, bike.id()) && MountService.ownership(giver).owns(MountKind.BIKE),
                        "an arcbike is never a deed");
                giver.getInventory().items.set(1, net.minecraft.world.item.ItemStack.EMPTY);
                taker.getInventory().items.set(1, deed);
                check(data.setLevel(taker.getUUID(), 20) == SetLevelResult.SUCCESS, "setting level 20");
                check(MountStable.redeem(taker, 1) == MountRules.Redeem.LEVEL_TOO_LOW
                        && !taker.getInventory().items.get(1).isEmpty(), "a level 20 player keeps the deed");
                check(data.setLevel(taker.getUUID(), 30) == SetLevelResult.SUCCESS, "setting level 30");
                check(MountStable.redeem(taker, 1) == MountRules.Redeem.OK, "a level 30 player takes it in");
                check(MountService.ownership(taker).mount(griffin.id()).filter(griffin::equals).isPresent(),
                        "the same griffin, look and values, is in the new stable");
                check(taker.getInventory().items.get(1).isEmpty(), "the deed is used up");
                check(MountStable.redeem(taker, 1) != MountRules.Redeem.OK, "nothing more to take in");
            } catch (GameTestAssertException e) {
                failure.set(e);
                throw e;
            } finally {
                data.onPlayerLeave(giver.getUUID());
                data.onPlayerLeave(taker.getUUID());
            }
        });
    }

    /**
     * Stage 0: owning the horse, the call starts. Stage 1 (after the call time): the player rides it; it cannot be
     * hurt or targeted; getting off sends it away; the call is refused in combat and in the dungeons.
     */
    @GameTest(template = ARENA, timeoutTicks = 300)
    public static void aCalledMountCarriesTheRiderAndLeavesOnDismount(GameTestHelper helper) {
        ServerContext context = context();
        PlayerDataService data = context.playerData();
        FakePlayer player = rider(helper, "er_gt_mount_call", new Vec3(4.5, 2.0, 4.5));
        UUID id = player.getUUID();
        data.onPlayerJoin(id, "gametest_mount_call");
        AtomicInteger stage = new AtomicInteger();
        AtomicReference<GameTestAssertException> failure = new AtomicReference<>();
        helper.succeedWhen(() -> {
            if (failure.get() != null) {
                throw failure.get();
            }
            try {
                switch (stage.get()) {
                    case 0 -> {
                        if (!data.isActive(id)) {
                            throw new GameTestAssertException("waiting for the character to load");
                        }
                        check(MountService.grant(player, MountKind.HORSE), "granting the horse");
                        MountRules.Call call = MountService.startCall(player);
                        check(call == MountRules.Call.OK && MountService.isCalling(player), "the call: " + call);
                        stage.set(1);
                        throw new GameTestAssertException("waiting for the horse");
                    }
                    case 1 -> {
                        if (!(player.getVehicle() instanceof MountEntity mount)) {
                            throw new GameTestAssertException("waiting for the horse");
                        }
                        stage.set(2);
                        checkRide(context, player, mount);
                        data.onPlayerLeave(id);
                    }
                    default -> {
                        // checked in stage 1
                    }
                }
            } catch (GameTestAssertException e) {
                if (!e.getMessage().startsWith("waiting")) {
                    data.onPlayerLeave(id);
                    failure.set(e);
                }
                throw e;
            }
        });
    }

    private static void checkRide(ServerContext context, FakePlayer player, MountEntity mount) {
        check(mount.kind() == MountKind.HORSE && !MountService.isCalling(player), "the player should ride the horse");
        float health = mount.getHealth();
        check(!mount.hurt(player.damageSources().playerAttack(player), 50.0F) && mount.getHealth() == health,
                "a mount must not be hurt");
        FakePlayer stranger = FakePlayerFactory.get(player.serverLevel(), profile("er_gt_mount_stranger"));
        check(!CombatHits.isValidTarget(stranger, mount) && !mount.isAttackable() && !mount.isPickable(),
                "a mount must not be a target");
        // a blow from the saddle does not land: the rider gets off instead, and the horse leaves
        LivingAttackEvent blow = new LivingAttackEvent(stranger, player.damageSources().playerAttack(player), 5.0F);
        MountService.onAttack(blow);
        check(blow.isCanceled(), "a blow from the saddle must not land");
        check(!player.isPassenger() && mount.isRemoved(), "striking from the saddle gets the rider off");
        context.combat().markInCombat(player);
        check(MountService.startCall(player) == MountRules.Call.IN_COMBAT && !MountService.isCalling(player),
                "no call in combat");
        ServerLevel dungeons = player.server.getLevel(DungeonLevels.DUNGEONS);
        if (dungeons != null) {
            FakePlayer below = FakePlayerFactory.get(dungeons, player.getGameProfile());
            check(MountRules.call(MountService.ownership(below), MountService.situation(below, MountKind.HORSE))
                    == MountRules.Call.NOT_HERE, "no call in the dungeons");
        }
    }

    /** Both mounts run south for 30 ticks on the server's physics; the speed is measured over ticks 20..30. */
    @GameTest(template = HALL, timeoutTicks = 200)
    public static void theBikeOutrunsTheHorseWhichOutrunsASprint(GameTestHelper helper) {
        FakePlayer horseRider = rider(helper, "er_gt_mount_run_h", new Vec3(4.5, 2.0, 3.5));
        FakePlayer bikeRider = rider(helper, "er_gt_mount_run_b", new Vec3(7.5, 2.0, 3.5));
        MountEntity horse = MountService.summonNow(horseRider, MountKind.HORSE)
                .orElseThrow(() -> new GameTestAssertException("no room for the horse"));
        MountEntity bike = MountService.summonNow(bikeRider, MountKind.BIKE)
                .orElseThrow(() -> new GameTestAssertException("no room for the bike"));
        // vanilla's sprint on the ground: movement speed x 1.3, kept by the ground's friction (0.6 x 0.91)
        double sprint = horseRider.getAttributeValue(Attributes.MOVEMENT_SPEED) * 1.3 / (1.0 - 0.6 * 0.91);
        AtomicInteger ticks = new AtomicInteger();
        Vec3[] marks = new Vec3[2];
        helper.onEachTick(() -> {
            int tick = ticks.incrementAndGet();
            if (tick > 30) {
                return;
            }
            horse.drive(FORWARD);
            bike.drive(FORWARD);
            if (tick == 20) {
                marks[0] = horse.position();
                marks[1] = bike.position();
            } else if (tick == 30) {
                double horseSpeed = horizontal(horse.position().subtract(marks[0])) / 10.0;
                double bikeSpeed = horizontal(bike.position().subtract(marks[1])) / 10.0;
                MountService.dismiss(horseRider, false);
                MountService.dismiss(bikeRider, false);
                if (horseRider.isPassenger() || !horse.isRemoved() || !bike.isRemoved()) {
                    helper.fail("getting off must send the mounts away");
                    return;
                }
                String speeds = String.format(java.util.Locale.ROOT, "sprint %.3f, horse %.3f, bike %.3f blocks/tick",
                        sprint, horseSpeed, bikeSpeed);
                if (horseSpeed < sprint * 1.8 || bikeSpeed < horseSpeed * 1.4) {
                    helper.fail("the bike must run at least 1.4x the horse and the horse 1.8x a sprint: " + speeds);
                    return;
                }
                helper.succeed();
            }
        });
    }

    /**
     * The terrain's ceiling stands 32 above the ground; under a fixed ceiling the griffin climbs (jump held) until
     * the ceiling stops it, never above it; glides down gently without input; dives faster with sneak; a rider who
     * gets off in the air is spared the fall. A horse jumping all the while stays near the ground.
     */
    @GameTest(template = ARENA, timeoutTicks = 500)
    public static void theGriffinFliesToItsCeilingAndGlidesDown(GameTestHelper helper) {
        FakePlayer flier = rider(helper, "er_gt_mount_fly", new Vec3(5.5, 2.0, 5.5));
        FakePlayer jumper = rider(helper, "er_gt_mount_jump", new Vec3(1.5, 2.0, 1.5));
        MountEntity griffin = MountService.summonNow(flier, MountKind.GRIFFIN)
                .orElseThrow(() -> new GameTestAssertException("no room for the griffin"));
        MountEntity horse = MountService.summonNow(jumper, MountKind.HORSE)
                .orElseThrow(() -> new GameTestAssertException("no room for the horse"));
        double ground = griffin.getY();
        // the terrain's ceiling stands at least 32 above the ground under the griffin (or 8 under the world's top);
        // the flight itself runs under a fixed ceiling 8 above the ground, since the test world's sky varies
        double terrain = griffin.terrainCeiling();
        double worldTop = helper.getLevel().getMaxBuildHeight() - MountRules.TOP_MARGIN;
        if (terrain < Math.min(ground + MountRules.FLIGHT_HEIGHT, worldTop) - 0.01) {
            throw new GameTestAssertException("the terrain ceiling " + terrain + " should be at least "
                    + MountRules.FLIGHT_HEIGHT + " above the ground " + ground);
        }
        griffin.fixCeilingForTests(ground + 8.0);
        // the horse: 60 ticks of riding with jump held, driven at once (one drive is one tick of ridden movement,
        // collisions included), so neither the test scheduler nor the tests around can add a move or a push; it
        // leaps and lands again and again, never higher than one leap above the floor it lands on
        double floor = Double.NaN;
        double highest = Double.NEGATIVE_INFINITY;
        StringBuilder path = new StringBuilder();
        for (int i = 0; i < 60; i++) {
            horse.drive(CLIMB);
            if (horse.onGround()) {
                floor = Double.isNaN(floor) ? horse.getY() : Math.min(floor, horse.getY());
            }
            highest = Math.max(highest, horse.getY());
            if (i < 40) {
                path.append(String.format(java.util.Locale.ROOT, " %.2f%s", horse.getY(), horse.onGround() ? "g" : ""));
            }
        }
        boolean wet = horse.isInWater();
        MountService.dismiss(jumper, false);
        if (Double.isNaN(floor) || highest - floor < 0.5 || highest - floor > 3.5) {
            throw new GameTestAssertException("a horse jumps, it does not fly: " + (highest - floor)
                    + " above the floor " + floor + (wet ? " (in water)" : "") + ", path" + path);
        }
        AtomicInteger ticks = new AtomicInteger();
        AtomicInteger phase = new AtomicInteger();
        AtomicInteger inPhase = new AtomicInteger();
        double[] notes = new double[2]; // the griffin's top, the end of its glide
        helper.onEachTick(() -> {
            if (phase.get() > 3) {
                return;
            }
            int tick = ticks.incrementAndGet();
            int step = inPhase.incrementAndGet();
            double ceiling = griffin.flightCeiling();
            switch (phase.get()) {
                case 0 -> { // climb
                    griffin.drive(CLIMB);
                    if (griffin.getY() > ceiling + 1.0E-3) {
                        phase.set(4);
                        helper.fail("the griffin rose above its ceiling: y " + griffin.getY() + ", ceiling " + ceiling);
                    } else if (step > 20 && griffin.motion().y() < 1.0E-3) {
                        if (griffin.getY() < ceiling - 0.6) {
                            phase.set(4);
                            helper.fail("the griffin stopped at y " + griffin.getY() + " below its ceiling " + ceiling
                                    + " (ground " + ground + ")");
                            return;
                        }
                        notes[0] = griffin.getY();
                        next(phase, inPhase, 1);
                    } else if (step > 300) {
                        phase.set(4);
                        helper.fail("the griffin never reached its ceiling: y " + griffin.getY() + ", ceiling " + ceiling);
                    }
                }
                case 1 -> { // glide without input
                    griffin.drive(MountMotion.Input.NONE);
                    if (step == 20) {
                        notes[1] = griffin.getY();
                        double sank = notes[0] - notes[1];
                        if (sank < 0.2 || sank > 2.5 || griffin.onGround()) {
                            phase.set(4);
                            helper.fail("gliding 20 ticks should sink gently, sank " + sank);
                            return;
                        }
                        next(phase, inPhase, 2);
                    }
                }
                case 2 -> { // dive with sneak (8 ticks: still in the air afterwards)
                    griffin.drive(DESCEND);
                    if (step == 8) {
                        double dived = notes[1] - griffin.getY();
                        if (dived < (notes[0] - notes[1]) * 2.0) {
                            phase.set(4);
                            helper.fail("sneak should dive faster than the glide: dived " + dived);
                            return;
                        }
                        next(phase, inPhase, 3);
                    }
                }
                default -> { // get off in the air (after a few ticks of gliding)
                    griffin.drive(MountMotion.Input.NONE);
                    if (tick <= 61) {
                        return;
                    }
                    MountService.dismiss(flier, false);
                    phase.set(4);
                    if (!MountService.fallGuarded(flier) || !griffin.isRemoved()) {
                        helper.fail("getting off in the air must spare the fall and send the griffin away");
                        return;
                    }
                    helper.succeed();
                }
            }
        });
    }

    private static void next(AtomicInteger phase, AtomicInteger inPhase, int to) {
        phase.set(to);
        inPhase.set(0);
    }

    // ------------------------------------------------------------------ helpers

    private static double horizontal(Vec3 delta) {
        return Math.sqrt(delta.x * delta.x + delta.z * delta.z);
    }

    private static ServerContext context() {
        return ServerContext.current().orElseThrow(() -> new GameTestAssertException("ServerContext is not installed"));
    }

    private static GameProfile profile(String name) {
        return new GameProfile(UUID.nameUUIDFromBytes(name.getBytes(StandardCharsets.UTF_8)), name);
    }

    private static FakePlayer rider(GameTestHelper helper, String name, Vec3 at) {
        FakePlayer player = FakePlayerFactory.get(helper.getLevel(), profile(name));
        Vec3 position = helper.absoluteVec(at);
        player.stopRiding();
        player.moveTo(position.x, position.y, position.z, 0.0F, 0.0F);
        player.setYHeadRot(0.0F);
        player.setDeltaMovement(Vec3.ZERO);
        return player;
    }

    private static void expect(MountRules.Purchase actual, MountRules.Purchase wanted, String what) {
        check(actual == wanted, what + ": expected " + wanted + ", got " + actual);
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new GameTestAssertException(message);
        }
    }
}
