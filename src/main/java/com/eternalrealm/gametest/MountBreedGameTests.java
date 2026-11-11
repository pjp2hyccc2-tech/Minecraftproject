package com.eternalrealm.gametest;

import com.eternalrealm.character.SetLevelResult;
import com.eternalrealm.core.ModInfo;
import com.eternalrealm.economy.ItemInfo;
import com.eternalrealm.economy.forge.MarketItems;
import com.eternalrealm.mount.MountFamily;
import com.eternalrealm.mount.MountIndividual;
import com.eternalrealm.mount.MountKind;
import com.eternalrealm.mount.MountLook;
import com.eternalrealm.mount.MountLooks;
import com.eternalrealm.mount.MountMotion;
import com.eternalrealm.mount.MountRules;
import com.eternalrealm.mount.MountTalent;
import com.eternalrealm.mount.MountTraits;
import com.eternalrealm.mount.forge.MountDeeds;
import com.eternalrealm.mount.forge.MountEntities;
import com.eternalrealm.mount.forge.MountEntity;
import com.eternalrealm.mount.forge.MountService;
import com.eternalrealm.mount.forge.MountStable;
import com.eternalrealm.player.PlayerDataService;
import com.eternalrealm.server.ServerContext;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.SplittableRandom;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * The ten breeds (docs/modules/mounts.md): every breed is in the stable's catalogue with four offers whose values stay
 * hidden; every breed comes when called and carries its rider; every horse breed runs about twice a sprint and jumps
 * within its rules (the leaper higher, the halohorn once more in the air, the gale glides down slowly); the blaze surges
 * after a long gallop; the tide gallops over water that slows the other horses; every griffin breed flies up to its
 * ceiling and never above it, the owl hangs in the air, the storm dives steeper, the void climbs fastest, the frost's
 * ceiling stands higher and the lion runs on the ground; a breed is sold from its own level and traded as its own
 * species. The rides are driven tick by tick at once (one drive is one tick of ridden movement), so neither the test
 * scheduler nor the tests around can add a move.
 */
@GameTestHolder(ModInfo.MOD_ID)
@PrefixGameTestTemplate(false)
public final class MountBreedGameTests {
    private static final String ARENA = "arena";
    /** 41x41 closed hall with 13 blocks of headroom: long lanes along the west wall (x below 10). */
    private static final String HALL = "boss_arena";
    private static final MountMotion.Input FORWARD = new MountMotion.Input(1.0, 0.0, false, false);
    private static final MountMotion.Input JUMP = new MountMotion.Input(0.0, 0.0, true, false);
    private static final MountMotion.Input DESCEND = new MountMotion.Input(0.0, 0.0, false, true);

    private MountBreedGameTests() {
    }

    /** The stable lists every kind with its family, talent, level and price; four offers of each breed, looks only. */
    @GameTest(template = ARENA)
    public static void everyBreedIsInTheStableWithFourOffersAndHiddenValues(GameTestHelper helper) {
        FakePlayer player = rider(helper, "er_gt_breed_catalogue", new Vec3(4.5, 2.0, 4.5));
        JsonObject screen = MountStable.screen(player, true, null);
        Set<MountKind> listed = new HashSet<>();
        for (JsonElement element : screen.getAsJsonArray("kinds")) {
            JsonObject json = element.getAsJsonObject();
            MountKind kind = MountKind.byId(json.get("kind").getAsString())
                    .orElseThrow(() -> new GameTestAssertException("unknown kind in the stable: " + json));
            check(listed.add(kind), "listed twice: " + kind);
            check(kind.family().id().equals(json.get("family").getAsString())
                    && kind.talent().id().equals(json.get("talent").getAsString())
                    && json.get("level").getAsInt() == kind.requiredLevel()
                    && json.get("price").getAsLong() == kind.price(), "the stable's line of " + kind + ": " + json);
            if (!MountLooks.varies(kind)) {
                continue;
            }
            JsonArray offers = json.getAsJsonArray("offers");
            check(offers != null && offers.size() == MountRules.OFFERS, kind + " shows four: " + offers);
            for (JsonElement offer : offers) {
                JsonObject look = offer.getAsJsonObject();
                check(!look.has("speed") && !look.has("accel") && !look.has("lift") && !look.has("grade"),
                        "an offer shows its look only: " + look);
                check(MountLooks.valid(kind, new MountLook(look.get("coat").getAsString(),
                        look.get("shape").getAsString())), kind + " offers its own coats and shapes: " + look);
            }
        }
        check(listed.size() == MountKind.values().length, "the stable lists " + listed);
        helper.succeed();
    }

    /** Each kind, called at once, carries the rider with its own look and values, cannot be hurt and leaves. */
    @GameTest(template = ARENA)
    public static void everyBreedComesWhenCalledAndCarriesItsRider(GameTestHelper helper) {
        Vec3 start = new Vec3(4.5, 2.0, 4.5);
        FakePlayer player = rider(helper, "er_gt_breed_call", start);
        SplittableRandom random = new SplittableRandom(17);
        for (MountKind kind : MountKind.values()) {
            Vec3 at = helper.absoluteVec(start);
            player.moveTo(at.x, at.y, at.z, 0.0F, 0.0F);
            MountIndividual individual = MountIndividual.roll(UUID.randomUUID(), kind, random);
            MountEntity mount = MountService.summonNow(player, individual)
                    .orElseThrow(() -> new GameTestAssertException("no " + kind + " came"));
            check(player.getVehicle() == mount && mount.kind() == kind && mount.getType() == MountEntities.type(kind),
                    "the player rides the " + kind);
            check(mount.look().equals(individual.look()) && mount.traits().equals(individual.traits())
                    && mount.individualId().filter(individual.id()::equals).isPresent(),
                    kind + " has its look and values: " + mount.look() + " " + mount.traits());
            check(mount.stats().equals(individual.stats()), kind + " moves by its own numbers");
            float health = mount.getHealth();
            check(!mount.hurt(player.damageSources().playerAttack(player), 50.0F) && mount.getHealth() == health
                    && !mount.isAttackable(), kind + " must not be hurt");
            MountService.dismiss(player, false);
            check(!player.isPassenger() && mount.isRemoved(), "getting off the " + kind + " sends it away");
        }
        helper.succeed();
    }

    /**
     * Every horse breed: about twice a sprint on the server's physics, and a jump within the rules: the leaper's
     * clearly higher, none flies. The halohorn's second press in the air lifts it again; the gale glides down slowly
     * with jump held and falls without.
     */
    @GameTest(template = HALL, timeoutTicks = 100)
    public static void everyHorseBreedRunsAndJumpsWithinItsRules(GameTestHelper helper) {
        List<MountKind> horses = MountKind.breeds(MountFamily.HORSE);
        Map<MountKind, Double> leaps = new EnumMap<>(MountKind.class);
        for (int i = 0; i < horses.size(); i++) {
            MountKind kind = horses.get(i);
            Vec3 lane = new Vec3(1.8 + i * 1.45, 2.0, 2.5);
            FakePlayer player = rider(helper, "er_gt_breed_run_" + i, lane);
            MountEntity mount = MountService.summonNow(player, MountIndividual.standard(UUID.randomUUID(), kind))
                    .orElseThrow(() -> new GameTestAssertException("no room for the " + kind));
            Vec3 mark = null;
            for (int tick = 1; tick <= 30; tick++) {
                mount.drive(FORWARD);
                if (tick == 20) {
                    mark = mount.position();
                }
            }
            double speed = horizontal(mount.position().subtract(mark)) / 10.0;
            double top = kind.stats().topSpeed();
            check(speed >= top * 0.85 && speed <= top * 1.02 && speed >= MountMotion.SPRINT_SPEED * 1.8,
                    String.format(Locale.ROOT, "%s runs at %.3f (top %.3f)", kind, speed, top));
            MountService.dismiss(player, false);
            // the jump: jump held for 40 ticks from a standstill
            Vec3 back = helper.absoluteVec(lane.add(0.0, 0.0, 16.0));
            player.moveTo(back.x, back.y, back.z, 0.0F, 0.0F);
            MountEntity jumper = MountService.summonNow(player, MountIndividual.standard(UUID.randomUUID(), kind))
                    .orElseThrow(() -> new GameTestAssertException("no room for the " + kind + " to jump"));
            double floor = Double.NaN;
            double highest = Double.NEGATIVE_INFINITY;
            for (int tick = 0; tick < 40; tick++) {
                jumper.drive(JUMP);
                if (jumper.onGround()) {
                    floor = Double.isNaN(floor) ? jumper.getY() : Math.min(floor, jumper.getY());
                }
                highest = Math.max(highest, jumper.getY());
            }
            double leap = highest - floor;
            leaps.put(kind, leap);
            boolean leaper = kind.talent() == MountTalent.GREAT_LEAP;
            check(!Double.isNaN(floor) && leap >= (leaper ? 3.2 : 1.4) && leap <= (leaper ? 4.8 : 3.5),
                    String.format(Locale.ROOT, "%s jumps %.2f above the floor", kind, leap));
            MountService.dismiss(player, false);
        }
        check(leaps.get(MountKind.HORSE_BLOOM) > leaps.get(MountKind.HORSE) + 0.8,
                "the leaper clears more than the warhorse: " + leaps);
        // the halohorn: a fresh press in the air steps on it once more
        FakePlayer stepper = rider(helper, "er_gt_breed_step", new Vec3(4.5, 2.0, 30.5));
        MountEntity halo = MountService.summonNow(stepper, MountIndividual.standard(UUID.randomUUID(),
                MountKind.HORSE_HALO)).orElseThrow(() -> new GameTestAssertException("no room for the halohorn"));
        double ground = halo.getY();
        double top = ground;
        for (int tick = 0; tick < 40; tick++) {
            halo.drive(tick == 0 || tick == 9 ? JUMP : MountMotion.Input.NONE);
            top = Math.max(top, halo.getY());
        }
        check(top - ground > leaps.get(MountKind.HORSE_HALO) + 0.8 && top - ground < 6.0,
                String.format(Locale.ROOT, "the step on the air lifts the halohorn to %.2f (one jump %.2f)",
                        top - ground, leaps.get(MountKind.HORSE_HALO)));
        MountService.dismiss(stepper, false);
        // the gale: high in the hall, it glides with jump held and falls without
        FakePlayer glider = rider(helper, "er_gt_breed_glide", new Vec3(7.5, 2.0, 30.5));
        MountEntity gale = MountService.summonNow(glider, MountIndividual.standard(UUID.randomUUID(),
                MountKind.HORSE_GALE)).orElseThrow(() -> new GameTestAssertException("no room for the gale"));
        gale.moveTo(gale.getX(), gale.getY() + 10.0, gale.getZ(), 0.0F, 0.0F);
        gale.setOnGround(false);
        for (int tick = 0; tick < 6; tick++) {
            gale.drive(JUMP);
        }
        double from = gale.getY();
        for (int tick = 0; tick < 10; tick++) {
            gale.drive(JUMP);
        }
        double glided = from - gale.getY();
        from = gale.getY();
        for (int tick = 0; tick < 10; tick++) {
            gale.drive(MountMotion.Input.NONE);
        }
        double fell = from - gale.getY();
        check(glided > 0.5 && glided <= MountTalent.GLIDE_SINK * 10.0 + 0.05 && fell > glided * 2.0,
                String.format(Locale.ROOT, "the gale glides %.2f in 10 ticks and falls %.2f", glided, fell));
        MountService.dismiss(glider, false);
        helper.succeed();
    }

    /** The blaze, galloping on, flares up: its surge runs faster than its own top speed. */
    @GameTest(template = HALL, timeoutTicks = 100)
    public static void theBlazeSurgesAfterALongGallop(GameTestHelper helper) {
        FakePlayer player = rider(helper, "er_gt_breed_surge", new Vec3(4.5, 2.0, 1.8));
        MountEntity blaze = MountService.summonNow(player, MountIndividual.standard(UUID.randomUUID(),
                MountKind.HORSE_BLAZE)).orElseThrow(() -> new GameTestAssertException("no room for the blaze"));
        double top = MountKind.HORSE_BLAZE.stats().topSpeed();
        int surgedAt = -1;
        Vec3 mark = null;
        for (int tick = 1; tick <= 62; tick++) {
            blaze.drive(FORWARD);
            if (surgedAt < 0 && blaze.ride().surging()) {
                surgedAt = tick;
            }
            if (tick == 58) {
                mark = blaze.position();
            }
        }
        double speed = horizontal(blaze.position().subtract(mark)) / 4.0;
        MountService.dismiss(player, false);
        check(surgedAt > MountTalent.SURGE_TICKS && surgedAt < 60, "the blaze surged at tick " + surgedAt);
        check(speed > top * 1.04, String.format(Locale.ROOT, "surging, it runs at %.3f (top %.3f)", speed, top));
        helper.succeed();
    }

    /**
     * A lane of still water over the floor: the tide gallops on top of it at full speed while a warhorse wades
     * through it slowly. Driven once per tick (the entities' own ticks notice the water).
     */
    @GameTest(template = HALL, timeoutTicks = 200)
    public static void theTideGallopsOverWaterThatSlowsTheOtherHorses(GameTestHelper helper) {
        for (int z = 1; z <= 38; z++) {
            for (int x = 1; x <= 4; x++) {
                helper.setBlock(new BlockPos(x, 2, z), Blocks.WATER);
            }
            helper.setBlock(new BlockPos(5, 2, z), Blocks.STONE);
        }
        for (int x = 1; x <= 5; x++) {
            helper.setBlock(new BlockPos(x, 2, 39), Blocks.STONE);
        }
        FakePlayer tideRider = rider(helper, "er_gt_breed_tide", new Vec3(1.9, 3.2, 2.5));
        FakePlayer horseRider = rider(helper, "er_gt_breed_wade", new Vec3(3.8, 2.2, 2.5));
        MountEntity tide = MountService.summonNow(tideRider, MountIndividual.standard(UUID.randomUUID(),
                MountKind.HORSE_TIDE)).orElseThrow(() -> new GameTestAssertException("no room for the tide"));
        MountEntity horse = MountService.summonNow(horseRider, MountIndividual.standard(UUID.randomUUID(),
                MountKind.HORSE)).orElseThrow(() -> new GameTestAssertException("no room for the warhorse"));
        double surface = helper.absoluteVec(new Vec3(0.0, 2.5, 0.0)).y;
        AtomicInteger ticks = new AtomicInteger();
        Vec3[] marks = new Vec3[2];
        AtomicBoolean done = new AtomicBoolean();
        helper.onEachTick(() -> {
            if (done.get()) {
                return;
            }
            int tick = ticks.incrementAndGet();
            tide.drive(FORWARD);
            horse.drive(FORWARD);
            if (tick == 25) {
                marks[0] = tide.position();
                marks[1] = horse.position();
            } else if (tick == 35) {
                done.set(true);
                double tideSpeed = horizontal(tide.position().subtract(marks[0])) / 10.0;
                double horseSpeed = horizontal(horse.position().subtract(marks[1])) / 10.0;
                double tideY = tide.getY();
                MountService.dismiss(tideRider, false);
                MountService.dismiss(horseRider, false);
                String speeds = String.format(Locale.ROOT, "tide %.3f at y %.2f (surface %.2f), warhorse %.3f",
                        tideSpeed, tideY, surface, horseSpeed);
                if (tideSpeed < MountKind.HORSE_TIDE.stats().topSpeed() * 0.85
                        || horseSpeed > MountKind.HORSE.stats().topSpeed() * 0.5 || tideY < surface - 0.3) {
                    helper.fail("the tide must gallop on the water and the warhorse wade: " + speeds);
                    return;
                }
                helper.succeed();
            }
        });
    }

    /**
     * Every griffin breed under a ceiling 8 above the floor: it climbs to the ceiling and never above it. Let go, the
     * owl hangs in the air while the others glide down; the storm's dive is steeper than the Azurewing's; the void
     * reaches the ceiling first; the frost's own ceiling over open ground stands 16 higher; the lion runs on the ground.
     */
    @GameTest(template = HALL, timeoutTicks = 100)
    public static void everyGriffinBreedFliesWithinItsRules(GameTestHelper helper) {
        List<MountKind> griffins = MountKind.breeds(MountFamily.GRIFFIN);
        Map<MountKind, Integer> climbTicks = new EnumMap<>(MountKind.class);
        Map<MountKind, Double> sank = new EnumMap<>(MountKind.class);
        Map<MountKind, Double> dived = new EnumMap<>(MountKind.class);
        for (int i = 0; i < griffins.size(); i++) {
            MountKind kind = griffins.get(i);
            FakePlayer player = rider(helper, "er_gt_breed_fly_" + i, new Vec3(2.5 + (i % 3) * 3.0, 2.0,
                    3.5 + (i / 3) * 4.0));
            MountEntity mount = MountService.summonNow(player, MountIndividual.standard(UUID.randomUUID(), kind))
                    .orElseThrow(() -> new GameTestAssertException("no room for the " + kind));
            double ground = mount.getY();
            double ceiling = ground + 8.0;
            mount.fixCeilingForTests(ceiling);
            int reached = -1;
            for (int tick = 1; tick <= 120 && reached < 0; tick++) {
                mount.drive(JUMP);
                check(mount.getY() <= ceiling + 1.0E-3, kind + " rose above its ceiling: " + mount.getY());
                if (mount.getY() >= ceiling - 0.6) {
                    reached = tick;
                }
            }
            check(reached > 0, kind + " never reached its ceiling: y " + (mount.getY() - ground));
            for (int tick = 0; tick < 30; tick++) {
                mount.drive(JUMP); // settles against the ceiling
            }
            climbTicks.put(kind, reached);
            double top = mount.getY();
            for (int tick = 0; tick < 20; tick++) {
                mount.drive(MountMotion.Input.NONE);
            }
            sank.put(kind, top - mount.getY());
            double before = mount.getY();
            for (int tick = 0; tick < 6; tick++) {
                mount.drive(DESCEND);
            }
            dived.put(kind, before - mount.getY());
            MountService.dismiss(player, false);
        }
        for (MountKind kind : griffins) {
            if (kind.talent() == MountTalent.HOVER) {
                check(sank.get(kind) < 0.15, kind + " should hang in the air, sank " + sank.get(kind));
            } else {
                check(sank.get(kind) > 0.1 && sank.get(kind) < 2.5, kind + " should glide down gently: " + sank);
            }
        }
        check(dived.get(MountKind.GRIFFIN_STORM) > dived.get(MountKind.GRIFFIN) * 1.3,
                "the storm dives steeper: " + dived);
        check(climbTicks.get(MountKind.GRIFFIN_VOID) < climbTicks.get(MountKind.GRIFFIN),
                "the void climbs fastest: " + climbTicks);
        // the frost's ceiling: the same place, 16 higher (unless the top of the world holds both)
        MountEntity frost = MountEntities.type(MountKind.GRIFFIN_FROST).create(helper.getLevel());
        MountEntity plain = MountEntities.type(MountKind.GRIFFIN).create(helper.getLevel());
        check(frost != null && plain != null, "griffins for the ceiling");
        Vec3 spot = helper.absoluteVec(new Vec3(20.5, 2.0, 35.5));
        frost.moveTo(spot.x, spot.y, spot.z);
        plain.moveTo(spot.x, spot.y, spot.z);
        double top = helper.getLevel().getMaxBuildHeight() - MountRules.TOP_MARGIN;
        double frostCeiling = frost.terrainCeiling();
        double plainCeiling = plain.terrainCeiling();
        check(frostCeiling == Math.min(top, plainCeiling + MountTalent.HIGH_FLIGHT_EXTRA),
                "the frost flies higher: " + frostCeiling + " against " + plainCeiling);
        // the lion runs on the ground nearly as fast as it flies; the Azurewing walks
        double lion = groundSpeed(helper, MountKind.GRIFFIN_GOLD, 2.5, "er_gt_breed_lion");
        double walker = groundSpeed(helper, MountKind.GRIFFIN, 7.5, "er_gt_breed_walk");
        check(lion > MountKind.GRIFFIN_GOLD.stats().groundSpeed() * 0.85 && lion > walker * 1.4,
                String.format(Locale.ROOT, "the lion runs at %.3f, the Azurewing walks at %.3f", lion, walker));
        helper.succeed();
    }

    private static double groundSpeed(GameTestHelper helper, MountKind kind, double x, String name) {
        FakePlayer player = rider(helper, name, new Vec3(x, 2.0, 14.5));
        MountEntity mount = MountService.summonNow(player, MountIndividual.standard(UUID.randomUUID(), kind))
                .orElseThrow(() -> new GameTestAssertException("no room for the " + kind));
        Vec3 mark = null;
        for (int tick = 1; tick <= 30; tick++) {
            mount.drive(FORWARD);
            if (tick == 20) {
                mark = mount.position();
            }
        }
        double speed = horizontal(mount.position().subtract(mark)) / 10.0;
        MountService.dismiss(player, false);
        return speed;
    }

    /**
     * A breed is sold from its own level (refused below it, no gold taken) and kept as itself; made into a deed, the
     * market files it as its own species, and only a player of its level takes it in.
     */
    @GameTest(template = ARENA, timeoutTicks = 300)
    public static void aBreedIsSoldFromItsLevelAndTradedAsItsOwnSpecies(GameTestHelper helper) {
        PlayerDataService data = context().playerData();
        String run = UUID.randomUUID().toString().substring(0, 6);
        FakePlayer buyer = rider(helper, "er_gt_breed_buy_" + run, new Vec3(3.5, 2.0, 3.5));
        FakePlayer taker = rider(helper, "er_gt_breed_take_" + run, new Vec3(6.5, 2.0, 6.5));
        data.onPlayerJoin(buyer.getUUID(), buyer.getGameProfile().getName());
        data.onPlayerJoin(taker.getUUID(), taker.getGameProfile().getName());
        AtomicBoolean done = new AtomicBoolean();
        AtomicReference<GameTestAssertException> failure = new AtomicReference<>();
        helper.succeedWhen(() -> {
            if (failure.get() != null) {
                throw failure.get();
            }
            if (done.get()) {
                return;
            }
            check(data.isActive(buyer.getUUID()) && data.isActive(taker.getUUID()), "waiting for the characters");
            done.set(true);
            try {
                MountKind kind = MountKind.HORSE_TIDE;
                UUID id = buyer.getUUID();
                check(data.setLevel(id, kind.requiredLevel() - 1) == SetLevelResult.SUCCESS, "setting the level");
                data.grantGold(id, kind.price());
                long gold = data.gold(id);
                check(MountService.buy(buyer, kind) == MountRules.Purchase.LEVEL_TOO_LOW && data.gold(id) == gold
                        && !MountService.ownership(buyer).owns(kind), "the tide is not sold below Lv" + kind.requiredLevel());
                check(data.setLevel(id, kind.requiredLevel()) == SetLevelResult.SUCCESS, "setting the level");
                MountIndividual shown = MountStable.offers(buyer, kind).get(0);
                check(MountService.buy(buyer, kind) == MountRules.Purchase.OK && data.gold(id) == gold - kind.price(),
                        "the tide is sold at Lv" + kind.requiredLevel() + " for " + kind.price());
                MountIndividual bought = MountService.ownership(buyer).mount(shown.id()).orElseThrow(
                        () -> new GameTestAssertException("the tide bought is the one shown"));
                check(bought.kind() == kind && MountLooks.valid(kind, bought.look()) && bought.equals(shown),
                        "kept as a tide with its look and values: " + bought.toJson());
                for (int slot = 1; slot <= 35; slot++) {
                    buyer.getInventory().items.set(slot, ItemStack.EMPTY);
                    taker.getInventory().items.set(slot, ItemStack.EMPTY);
                }
                check(MountStable.makeDeed(buyer, bought.id()), "making the deed");
                ItemStack deed = buyer.getInventory().items.get(1);
                check(MountDeeds.read(deed).filter(bought::equals).isPresent(), "the deed holds the tide");
                ItemInfo info = MarketItems.describe(deed, 1);
                check(("mount:" + kind.id() + ":" + bought.grade().id()).equals(info.group()),
                        "the market files the deed as " + info.group());
                buyer.getInventory().items.set(1, ItemStack.EMPTY);
                taker.getInventory().items.set(1, deed);
                check(data.setLevel(taker.getUUID(), kind.requiredLevel() - 1) == SetLevelResult.SUCCESS, "level");
                check(MountStable.redeem(taker, 1) == MountRules.Redeem.LEVEL_TOO_LOW, "below the breed's level");
                check(data.setLevel(taker.getUUID(), kind.requiredLevel()) == SetLevelResult.SUCCESS, "level");
                check(MountStable.redeem(taker, 1) == MountRules.Redeem.OK
                        && MountService.ownership(taker).mount(bought.id()).filter(bought::equals).isPresent(),
                        "a player of the breed's level takes the tide in");
                check(MountTraits.Grade.byId(bought.grade().id()).isPresent(), "the grade");
            } catch (GameTestAssertException e) {
                failure.set(e);
                throw e;
            } finally {
                data.onPlayerLeave(buyer.getUUID());
                data.onPlayerLeave(taker.getUUID());
            }
        });
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

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new GameTestAssertException(message);
        }
    }
}
