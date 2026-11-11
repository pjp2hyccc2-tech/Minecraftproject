package com.eternalrealm.gametest;

import com.eternalrealm.core.ModInfo;
import com.eternalrealm.estate.EstateKey;
import com.eternalrealm.estate.EstateResult;
import com.eternalrealm.estate.EstateStatus;
import com.eternalrealm.estate.RecallRules;
import com.eternalrealm.estate.RoomDesign;
import com.eternalrealm.estate.RoomRules;
import com.eternalrealm.estate.StorageLabel;
import com.eternalrealm.estate.forge.ChestLabel;
import com.eternalrealm.estate.forge.EstateContents;
import com.eternalrealm.estate.forge.EstateItems;
import com.eternalrealm.estate.forge.EstateLevels;
import com.eternalrealm.estate.forge.EstateService;
import com.eternalrealm.estate.forge.InnService;
import com.eternalrealm.estate.forge.LedgerService;
import com.eternalrealm.estate.forge.RecallService;
import com.eternalrealm.estate.forge.RoomDoors;
import com.eternalrealm.estate.forge.StorageChestBlock;
import com.eternalrealm.item.Consumable;
import com.eternalrealm.item.forge.ERGearItems;
import com.eternalrealm.player.PlayerDataService;
import com.eternalrealm.server.ServerContext;
import com.eternalrealm.world.forge.blueprint.Blueprint;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * The inn rooms and the way home (docs/modules/estates.md): renting, moving to another room with everything (labels,
 * armour stands and frames included), moving down with the rest kept safe, giving an empty room back for half its
 * price; the sorting desk putting the bag away by the labels and handing things back without losing or doubling
 * any; the way home cast, broken by moving, refused in a fight and inside one's room, and the room's door.
 */
@GameTestHolder(ModInfo.MOD_ID)
@PrefixGameTestTemplate(false)
public final class InnGameTests {
    private static final String EMPTY = "empty";

    private InnGameTests() {
    }

    /** The innkeeper's document lists the five rooms with storage that only grows from room to room. */
    @GameTest(template = EMPTY)
    public static void theInnListsFiveRoomsWhoseStorageGrows(GameTestHelper helper) {
        FakePlayer player = fake(helper, "er_gt_inn_reader", new Vec3(2.5, 2.0, 2.5));
        check(!InnService.atInn(player), "no innkeeper stands in the test area");
        JsonObject document = InnService.document(player);
        List<JsonElement> designs = new java.util.ArrayList<>();
        document.getAsJsonArray("designs").forEach(designs::add);
        check(designs.size() == RoomDesign.values().length, "five rooms: " + designs);
        int[] previous = new int[5];
        String[] pieces = {"chest", "barrel", "shelf", "stand", "frame"};
        for (int i = 0; i < designs.size(); i++) {
            JsonObject design = designs.get(i).getAsJsonObject();
            check(design.get("id").getAsString().equals(RoomDesign.values()[i].id()), "rooms in price order");
            JsonObject storage = design.getAsJsonObject("storage");
            for (int p = 0; p < pieces.length; p++) {
                int count = storage.has(pieces[p]) ? storage.get(pieces[p]).getAsInt() : 0;
                check(count >= previous[p] && count > 0, design.get("id").getAsString() + " has " + count + " "
                        + pieces[p] + ", fewer than the room before");
                previous[p] = count;
            }
        }
        check(previous[0] == 12 && previous[2] == 16, "the grand suite has 12 chests and 16 shelves");
        helper.succeed();
    }

    /**
     * Rent the garret (1,000 G), fill it, move to the canal room (4,000 G more): everything is where it was, the
     * relabelled chest keeps its label, the helmet stays on its stand and the sword in its frame; fill the canal room
     * beyond what the garret holds and move back (2,000 G back): nothing is lost or doubled, the overflow waits in the
     * keepsafe and is handed over; empty the room and give it back (500 G back).
     */
    @GameTest(template = EMPTY, timeoutTicks = 6000)
    public static void aRoomIsRentedMovedWithItsThingsAndGivenBack(GameTestHelper helper) {
        PlayerDataService data = context().playerData();
        FakePlayer player = fake(helper, "er_gt_inn_guest", new Vec3(2.5, 2.0, 2.5));
        UUID id = player.getUUID();
        EstateService.estate(EstateKey.ROOM, id).ifPresent(EstateService::release);
        data.onPlayerJoin(id, player.getGameProfile().getName());
        AtomicInteger stage = new AtomicInteger();
        AtomicLong gold = new AtomicLong();
        AtomicLong dirt = new AtomicLong();
        AtomicReference<GameTestAssertException> failure = new AtomicReference<>();
        helper.onEachTick(() -> {
            if (failure.get() != null) {
                return;
            }
            try {
                switch (stage.get()) {
                    case 0 -> {
                        if (!data.isActive(id) || EstateService.estate(EstateKey.ROOM, id).isPresent()) {
                            return;
                        }
                        data.grantGold(id, 60_000);
                        gold.set(data.gold(id));
                        check(InnService.rent(player, RoomDesign.GARRET) == RoomRules.Outcome.OK, "renting the garret");
                        check(data.gold(id) == gold.get() - 1_000, "the garret costs 1,000 G");
                        check(InnService.rent(player, RoomDesign.CANAL) == RoomRules.Outcome.HAS_ROOM, "one room each");
                        stage.set(1);
                    }
                    case 1 -> {
                        EstateKey key = room(id, "garret");
                        if (key == null || !EstateService.ready(key)) {
                            return;
                        }
                        EstateContents.Found found = EstateService.contents(key).orElseThrow();
                        container(found, "chest.1").setItem(0, new ItemStack(Items.IRON_INGOT, 5));
                        ServerLevel estates = EstateLevels.level(player.server).orElseThrow();
                        BlockPos chest3 = pos(found, "chest.3");
                        StorageChestBlock.setLabel(estates, chest3, ChestLabel.BOOK);
                        AABB box = EstateService.box(key).orElseThrow();
                        stand(estates, box, "stand.1").setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
                        frame(estates, box, "frame.1").setItem(new ItemStack(Items.IRON_SWORD));
                        check(InnService.giveUp(player) == RoomRules.Outcome.NOT_EMPTY, "a full room is not given up");
                        check(InnService.change(player, RoomDesign.GARRET) == RoomRules.Outcome.SAME_ROOM, "same room");
                        long before = data.gold(id);
                        check(InnService.change(player, RoomDesign.CANAL) == RoomRules.Outcome.OK, "moving up");
                        check(data.gold(id) == before - 4_000, "moving up costs the difference");
                        stage.set(2);
                    }
                    case 2 -> {
                        EstateKey key = room(id, "canal");
                        if (key == null || EstateService.status(key).phase() != EstateStatus.Phase.READY) {
                            return;
                        }
                        EstateContents.Found found = EstateService.contents(key).orElseThrow();
                        ItemStack ingots = container(found, "chest.1").getItem(0);
                        check(ingots.is(Items.IRON_INGOT) && ingots.getCount() == 5, "the ingots moved: " + ingots);
                        ServerLevel estates = EstateLevels.level(player.server).orElseThrow();
                        check(StorageChestBlock.labelAt(estates, pos(found, "chest.3")) == ChestLabel.BOOK,
                                "the chest kept its label");
                        AABB box = EstateService.box(key).orElseThrow();
                        check(stand(estates, box, "stand.1").getItemBySlot(EquipmentSlot.HEAD).is(Items.IRON_HELMET),
                                "the helmet is on the stand");
                        check(frame(estates, box, "frame.1").getItem().is(Items.IRON_SWORD), "the sword is in its frame");
                        // fill the canal room: more than the garret holds, two diamonds among it
                        container(found, "chest.5").setItem(3, new ItemStack(Items.DIAMOND, 2));
                        for (String name : CANAL_BOXES) {
                            Container box = container(found, name);
                            for (int slot = 0; slot < box.getContainerSize(); slot++) {
                                if (box.getItem(slot).isEmpty()) {
                                    box.setItem(slot, new ItemStack(Items.DIRT, 64));
                                }
                            }
                        }
                        dirt.set(dirt(found));
                        check(dirt.get() > 162 * 64L, "the canal room holds more than the garret can");
                        long before = data.gold(id);
                        check(InnService.change(player, RoomDesign.GARRET) == RoomRules.Outcome.OK, "moving down");
                        check(data.gold(id) == before + 2_000, "moving down hands back half the difference");
                        stage.set(3);
                    }
                    case 3 -> {
                        EstateKey key = room(id, "garret");
                        if (key == null || EstateService.status(key).phase() != EstateStatus.Phase.READY) {
                            return;
                        }
                        EstateContents.Found found = EstateService.contents(key).orElseThrow();
                        check(container(found, "chest.1").getItem(0).getCount() == 5, "the ingots kept their slot");
                        List<ItemStack> kept = EstateService.keepsafe(id);
                        check(!kept.isEmpty(), "what the garret cannot hold waits in the keepsafe");
                        long keptDirt = kept.stream().filter(s -> s.is(Items.DIRT)).mapToLong(ItemStack::getCount).sum();
                        check(dirt(found) + keptDirt == dirt.get(), "no dirt lost or doubled: " + (dirt(found) + keptDirt)
                                + " / " + dirt.get());
                        long diamonds = kept.stream().filter(s -> s.is(Items.DIAMOND)).mapToLong(ItemStack::getCount)
                                .sum() + found.holders().stream().filter(h -> h instanceof EstateContents.ContainerHolder)
                                .mapToLong(h -> total(((EstateContents.ContainerHolder) h).container(), Items.DIAMOND))
                                .sum();
                        check(diamonds == 2, "both diamonds are still there: " + diamonds);
                        int keptBefore = kept.size();
                        check(InnService.claimKeepsafe(player) > 0, "the keepsafe is handed over as far as the bag holds");
                        check(EstateService.keepsafe(id).size() < keptBefore, "the keepsafe shrank");
                        EstateContents.takeAll(found);
                        long before = data.gold(id);
                        check(InnService.giveUp(player) == RoomRules.Outcome.OK, "giving the empty room back");
                        check(data.gold(id) == before + 500, "half the garret's price is handed back");
                        check(EstateService.estate(EstateKey.ROOM, id).isEmpty(), "the room is gone");
                        stage.set(4);
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
                data.onPlayerLeave(id);
                throw failure.get();
            }
            check(stage.get() == 4, "stage " + stage.get());
            data.onPlayerLeave(id);
        });
    }

    /**
     * The sorting desk puts the bag away by the labels (ingots to the materials chest, the sword to the gear chest,
     * books on a shelf, bread in a barrel, the potion stays in the bag) and hands a stack back; nothing is lost or
     * doubled.
     */
    @GameTest(template = EMPTY, timeoutTicks = 3600)
    public static void theSortingDeskPutsAwayByLabelAndHandsBack(GameTestHelper helper) {
        FakePlayer player = fake(helper, "er_gt_inn_sorter", new Vec3(2.5, 2.0, 2.5));
        EstateKey key = RoomDesign.GARRET.key(player.getUUID());
        EstateService.estate(EstateKey.ROOM, player.getUUID()).ifPresent(EstateService::release);
        AtomicInteger stage = new AtomicInteger();
        AtomicReference<GameTestAssertException> failure = new AtomicReference<>();
        Item potion = ERGearItems.consumable(Consumable.HEALTH_POTION_SMALL);
        helper.onEachTick(() -> {
            if (failure.get() != null) {
                return;
            }
            try {
                switch (stage.get()) {
                    case 0 -> {
                        if (EstateService.estate(EstateKey.ROOM, player.getUUID()).isPresent()) {
                            return;
                        }
                        check(EstateService.claim(key) == EstateResult.OK, "claiming a room");
                        stage.set(1);
                    }
                    case 1 -> {
                        if (!EstateService.ready(key)) {
                            return;
                        }
                        player.getInventory().items.set(1, new ItemStack(Items.IRON_INGOT, 20));
                        player.getInventory().items.set(2, new ItemStack(Items.IRON_SWORD));
                        player.getInventory().items.set(3, new ItemStack(Items.BREAD, 4));
                        player.getInventory().items.set(4, new ItemStack(Items.BOOK, 2));
                        player.getInventory().items.set(5, new ItemStack(potion, 3));
                        LedgerService.Deposit done = LedgerService.deposit(player, key, Optional.empty());
                        check(done.items() == 27, "27 things put away, got " + done);
                        EstateContents.Found found = EstateService.contents(key).orElseThrow();
                        check(total(container(found, "chest.3"), Items.IRON_INGOT) == 20, "ingots to the materials chest");
                        check(total(container(found, "chest.1"), Items.IRON_SWORD) == 1, "the sword to the gear chest");
                        check(total(container(found, "shelf.1"), Items.BOOK) + total(container(found, "shelf.2"),
                                Items.BOOK) == 2, "the books on the shelves");
                        int bread = 0;
                        for (String barrel : List.of("barrel.1", "barrel.2", "barrel.3")) {
                            bread += total(container(found, barrel), Items.BREAD);
                        }
                        check(bread == 4, "the bread in an unlabelled barrel");
                        check(player.getInventory().items.get(5).is(potion) && player.getInventory().items.get(5)
                                .getCount() == 3, "the potions stay in the bag");
                        for (int slot = 1; slot <= 4; slot++) {
                            check(player.getInventory().items.get(slot).isEmpty(), "bag slot " + slot + " is empty");
                        }
                        JsonObject ledger = LedgerService.document(key, "all", 0);
                        check(ledger.get("total").getAsInt() == 4, "four lines in the ledger: " + ledger.get("total"));
                        int taken = LedgerService.take(player, key, EstateItems.key(new ItemStack(Items.IRON_INGOT)),
                                false);
                        check(taken == 20, "the stack of ingots comes back, got " + taken);
                        check(count(player, Items.IRON_INGOT) == 20, "20 ingots in the bag");
                        check(total(container(found, "chest.3"), Items.IRON_INGOT) == 0, "none left in the chest");
                        LedgerService.Deposit food = LedgerService.deposit(player, key, Optional.of(StorageLabel.POTION));
                        check(food.items() == 3 && count(player, potion) == 0, "putting away the potions takes them");
                        check(total(container(found, "chest.2"), potion) == 3, "the potions go to the potion chest");
                        check(EstateService.release(key) == EstateResult.OK, "releasing the room");
                        stage.set(2);
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
                throw failure.get();
            }
            check(stage.get() == 2, "stage " + stage.get());
        });
    }

    /**
     * The way home: a cast broken by moving; a cast that arrives in the room; refused inside one's room, in a fight
     * and right after arriving; the room's door from inside opens its menu instead of throwing the player out.
     */
    @GameTest(template = EMPTY, timeoutTicks = 3600)
    public static void theWayHomeIsCastBrokenAndRefused(GameTestHelper helper) {
        FakePlayer player = fake(helper, "er_gt_inn_traveller", new Vec3(2.5, 2.0, 2.5));
        EstateKey key = RoomDesign.GARRET.key(player.getUUID());
        EstateService.estate(EstateKey.ROOM, player.getUUID()).ifPresent(EstateService::release);
        RecallService.resetCooldown(player.getUUID());
        Vec3 start = player.position();
        ServerLevel home = helper.getLevel();
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
                        if (EstateService.estate(EstateKey.ROOM, player.getUUID()).isPresent()) {
                            return;
                        }
                        check(RecallService.start(player) == RecallRules.Check.NO_ROOM, "no room, no way home");
                        check(EstateService.claim(key) == EstateResult.OK, "claiming a room");
                        stage.set(1);
                    }
                    case 1 -> {
                        if (!EstateService.ready(key)) {
                            return;
                        }
                        player.hurtTime = 10;
                        check(RecallService.start(player) == RecallRules.Check.IN_COMBAT, "refused in a fight");
                        player.hurtTime = 0;
                        check(RecallService.start(player) == RecallRules.Check.OK, "the cast starts");
                        check(RecallService.isCasting(player), "casting");
                        check(RecallService.start(player) == RecallRules.Check.CASTING, "one cast at a time");
                        player.moveTo(player.getX() + 2.0, player.getY(), player.getZ(), 0.0F, 0.0F);
                        stage.set(2);
                    }
                    case 2 -> {
                        if (RecallService.isCasting(player)) {
                            check(waited.incrementAndGet() < 5, "moving did not break the cast");
                            return;
                        }
                        check(player.level() == home, "a broken cast goes nowhere");
                        player.moveTo(start.x, start.y, start.z, 0.0F, 0.0F);
                        check(RecallService.start(player) == RecallRules.Check.OK, "the cast starts again");
                        waited.set(0);
                        stage.set(3);
                    }
                    case 3 -> {
                        if (!EstateLevels.isEstateLevel(player.level())) {
                            check(waited.incrementAndGet() < RecallRules.CAST_TICKS + 40, "the cast never arrived");
                            return;
                        }
                        Optional<EstateKey> here = EstateService.at(player);
                        check(here.isPresent() && here.get().sameEstate(key), "home in one's own room");
                        check(RecallService.start(player) == RecallRules.Check.ALREADY_HOME, "already home");
                        Blueprint.Spot exit = EstateService.spot(key, EstateService.EXIT_SPOT).orElseThrow();
                        BlockPos door = doorNextTo((ServerLevel) player.level(), exit.pos());
                        RoomDoors.use(player, door);
                        check(EstateLevels.isEstateLevel(player.level()), "the door opens its menu; the player stays");
                        check(EstateService.leave(player), "leaving by the menu's way back");
                        check(player.level() == home && player.position().distanceTo(start) < 0.01,
                                "back where the cast began: " + player.position());
                        check(RecallService.start(player) == RecallRules.Check.COOLDOWN, "a minute's rest after arriving");
                        check(EstateService.release(key) == EstateResult.OK, "releasing the room");
                        stage.set(4);
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
            check(stage.get() == 4, "stage " + stage.get() + " (" + EstateService.status(key) + ")");
            cleanUp(player);
        });
    }

    // ------------------------------------------------------------------ helpers

    private static ServerContext context() {
        return ServerContext.current().orElseThrow(() -> new GameTestAssertException("no server context"));
    }

    private static final List<String> CANAL_BOXES = List.of("chest.1", "chest.2", "chest.3", "chest.4", "chest.5",
            "barrel.1", "barrel.2", "barrel.3", "barrel.4");

    private static long dirt(EstateContents.Found found) {
        long total = 0;
        for (EstateContents.Holder holder : found.holders()) {
            if (holder instanceof EstateContents.ContainerHolder container) {
                total += total(container.container(), Items.DIRT);
            }
        }
        return total;
    }

    private static EstateKey room(UUID owner, String design) {
        return EstateService.estate(EstateKey.ROOM, owner).filter(key -> key.design().equals(design)).orElse(null);
    }

    private static Container container(EstateContents.Found found, String name) {
        EstateContents.Holder holder = found.named(name).orElse(null);
        if (holder instanceof EstateContents.ContainerHolder container) {
            return container.container();
        }
        throw new GameTestAssertException("no container " + name + " in the room");
    }

    private static BlockPos pos(EstateContents.Found found, String name) {
        if (found.named(name).orElse(null) instanceof EstateContents.ContainerHolder container) {
            return container.pos();
        }
        throw new GameTestAssertException("no container " + name + " in the room");
    }

    private static ArmorStand stand(ServerLevel level, AABB box, String name) {
        for (ArmorStand stand : level.getEntitiesOfClass(ArmorStand.class, box)) {
            if (name.equals(stand.getPersistentData().getString(EstateContents.SLOT))) {
                return stand;
            }
        }
        throw new GameTestAssertException("no armour stand " + name);
    }

    private static ItemFrame frame(ServerLevel level, AABB box, String name) {
        for (ItemFrame frame : level.getEntitiesOfClass(ItemFrame.class, box)) {
            if (name.equals(frame.getPersistentData().getString(EstateContents.SLOT))) {
                return frame;
            }
        }
        throw new GameTestAssertException("no item frame " + name);
    }

    private static BlockPos doorNextTo(ServerLevel level, BlockPos spot) {
        for (BlockPos pos : List.of(spot.north(), spot.south(), spot.east(), spot.west())) {
            if (level.getBlockState(pos).getBlock() instanceof com.eternalrealm.estate.forge.RoomDoorBlock) {
                return pos;
            }
        }
        throw new GameTestAssertException("no room door next to the exit spot " + spot);
    }

    private static int total(Container container, Item item) {
        int total = 0;
        for (int i = 0; i < container.getContainerSize(); i++) {
            if (container.getItem(i).is(item)) {
                total += container.getItem(i).getCount();
            }
        }
        return total;
    }

    private static int count(ServerPlayer player, Item item) {
        int total = 0;
        for (ItemStack stack : player.getInventory().items) {
            if (stack.is(item)) {
                total += stack.getCount();
            }
        }
        return total;
    }

    private static FakePlayer fake(GameTestHelper helper, String name, Vec3 at) {
        FakePlayer player = FakePlayerFactory.get(helper.getLevel(), new GameProfile(
                UUID.nameUUIDFromBytes(name.getBytes(StandardCharsets.UTF_8)), name));
        Vec3 position = helper.absoluteVec(at);
        player.moveTo(position.x, position.y, position.z, 0.0F, 0.0F);
        player.setDeltaMovement(Vec3.ZERO);
        player.getInventory().clearContent();
        return player;
    }

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
