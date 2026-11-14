package com.eternalrealm.gametest;

import com.eternalrealm.character.CharacterSnapshot;
import com.eternalrealm.combat.WeaponType;
import com.eternalrealm.core.ModInfo;
import com.eternalrealm.item.Consumable;
import com.eternalrealm.item.EquipmentBase;
import com.eternalrealm.item.EquipmentData;
import com.eternalrealm.item.ItemPrices;
import com.eternalrealm.item.ItemRarity;
import com.eternalrealm.item.ItemTier;
import com.eternalrealm.item.Materials;
import com.eternalrealm.item.bag.Bag;
import com.eternalrealm.item.forge.BagBack;
import com.eternalrealm.item.forge.EquipmentStacks;
import com.eternalrealm.item.forge.Inventories;
import com.eternalrealm.item.forge.ShopService;
import com.eternalrealm.player.PlayerDataService;
import com.eternalrealm.server.ServerContext;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.authlib.GameProfile;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * The item shop's counter on a real server (2026-11-14, the shop's own screen): buying takes the gold and fills the
 * bag, a purchase the gold or the bag cannot take is refused and costs nothing, the shop lists what it would buy from
 * the front and the back, sells from either (never more than the stack), sells a piece of equipment only by its own id,
 * and refuses a slot whose item changed.
 */
@GameTestHolder(ModInfo.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ShopGameTests {
    private static final String ARENA = "arena";

    private ShopGameTests() {
    }

    @GameTest(template = ARENA, timeoutTicks = 400)
    public static void theCounterBuysAndSellsFromTheFrontAndTheBack(GameTestHelper helper) {
        ServerContext context = ServerContext.current()
                .orElseThrow(() -> new GameTestAssertException("no server context"));
        PlayerDataService data = context.playerData();
        String name = "er_gt_shop";
        FakePlayer player = FakePlayerFactory.get(helper.getLevel(),
                new GameProfile(UUID.nameUUIDFromBytes(name.getBytes(StandardCharsets.UTF_8)), name));
        Vec3 at = helper.absoluteVec(new Vec3(4.5, 2.0, 4.5));
        player.moveTo(at.x, at.y, at.z, 0.0F, 0.0F);
        player.getInventory().clearContent();
        BagBack.clear(player);
        UUID id = player.getUUID();
        data.onPlayerJoin(id, name);
        AtomicBoolean done = new AtomicBoolean();
        AtomicReference<GameTestAssertException> failure = new AtomicReference<>();
        helper.succeedWhen(() -> {
            if (failure.get() != null) {
                throw failure.get();
            }
            if (done.get()) {
                return;
            }
            check(data.isActive(id), "waiting for the character to load");
            done.set(true);
            try {
                run(context, data, player);
            } catch (GameTestAssertException e) {
                failure.set(e);
                throw e;
            } finally {
                player.getInventory().clearContent();
                BagBack.clear(player);
                data.onPlayerLeave(id);
            }
        });
    }

    private static void run(ServerContext context, PlayerDataService data, FakePlayer player) {
        UUID id = player.getUUID();
        long start = gold(data, id);
        data.grantGold(id, 1000L);
        long gold = gold(data, id);
        check(gold == start + 1000L, "the purse was filled: " + gold);
        Inventory inventory = player.getInventory();
        String potion = Consumable.HEALTH_POTION_SMALL.itemId();
        long price = ItemPrices.consumableShopPrice(Consumable.HEALTH_POTION_SMALL);

        // buying: the gold goes, the potions come
        ShopService.Outcome bought = ShopService.buy(player, context, potion, 20);
        check(bought.ok() && bought.gold() == -price * 20, "twenty potions bought: " + bought);
        check(gold(data, id) == gold - price * 20, "the price was paid: " + gold(data, id));
        check(Inventories.counts(player).getOrDefault(potion, 0) == 20, "twenty potions in the bag: "
                + Inventories.counts(player));
        // too dear: refused, nothing paid
        long before = gold(data, id);
        ShopService.Outcome dear = ShopService.buy(player, context, Consumable.OBLIVION_ELIXIR.itemId(), 9);
        check(!dear.ok() && gold(data, id) == before, "nine elixirs are too dear: " + dear);
        check(!ShopService.buy(player, context, "minecraft:diamond", 1).ok(), "the shop sells no diamonds");

        // a full bag: refused, nothing paid
        inventory.clearContent();
        for (int slot = Bag.FIRST_SLOT; slot <= Bag.LAST_SLOT; slot++) {
            inventory.items.set(slot, new ItemStack(Items.STONE, 64));
        }
        for (int index = 0; index < Bag.BACK_SIZE; index++) {
            BagBack.set(player, index, new ItemStack(Items.STONE, 64));
        }
        ShopService.Outcome full = ShopService.buy(player, context, potion, 1);
        check(!full.ok() && gold(data, id) == before, "no room, no purchase: " + full);
        inventory.clearContent();
        BagBack.clear(player);

        // selling from the front and the back
        Item gel = Inventories.item(Materials.SLIME_GEL);
        check(gel != null, "slime gel exists");
        long each = ItemPrices.materialSellValue(Materials.SLIME_GEL).orElseThrow();
        inventory.items.set(3, new ItemStack(gel, 5));
        BagBack.set(player, 0, new ItemStack(gel, 4));
        JsonObject model = ShopService.model(player, false);
        check(listed(model, 3, 5) && listed(model, Bag.BACK_FIRST, 4), "the shop lists the gel of the front and the"
                + " back: " + model.get("sell"));
        before = gold(data, id);
        ShopService.Outcome front = ShopService.sell(player, context, 3, Materials.SLIME_GEL, 2, null);
        check(front.ok() && front.gold() == each * 2 && gold(data, id) == before + each * 2,
                "two gel sold from the front: " + front);
        check(inventory.items.get(3).getCount() == 3, "three left: " + inventory.items.get(3));
        ShopService.Outcome back = ShopService.sell(player, context, Bag.BACK_FIRST, Materials.SLIME_GEL, 99, null);
        check(back.ok() && back.gold() == each * 4, "the back's four sold, never more: " + back);
        check(BagBack.get(player, 0).isEmpty(), "the back's place is empty: " + BagBack.get(player, 0));
        ShopService.Outcome moved = ShopService.sell(player, context, 3, Materials.ENHANCEMENT_STONE, 1, null);
        check(!moved.ok() && inventory.items.get(3).getCount() == 3, "another item in the slot: refused");
        inventory.items.set(4, new ItemStack(Items.STONE, 10));
        check(!ShopService.sell(player, context, 4, "minecraft:stone", 10, null).ok()
                && inventory.items.get(4).getCount() == 10, "the shop buys no stone");

        // a piece of equipment: only by its own id, one at a time
        ItemStack sword = EquipmentStacks.create(EquipmentData.of(EquipmentBase.weapon(WeaponType.ONE_HANDED_SWORD,
                ItemTier.IRON), ItemRarity.COMMON, List.of()));
        UUID swordId = EquipmentStacks.read(sword).orElseThrow().uniqueId();
        inventory.items.set(5, sword);
        String swordItem = Inventories.idOf(sword);
        check(!ShopService.sell(player, context, 5, swordItem, 1, null).ok() && !inventory.items.get(5).isEmpty(),
                "a sword without its id is not sold");
        check(!ShopService.sell(player, context, 5, swordItem, 1, UUID.randomUUID()).ok()
                && !inventory.items.get(5).isEmpty(), "another sword's id: not sold");
        before = gold(data, id);
        ShopService.Outcome sold = ShopService.sell(player, context, 5, swordItem, 1, swordId);
        check(sold.ok() && inventory.items.get(5).isEmpty() && gold(data, id) == before + sold.gold()
                && sold.gold() > 0, "the sword sold by its id: " + sold);
    }

    /** Whether the shop's list holds bag slot {@code slot} with {@code count} things. */
    private static boolean listed(JsonObject model, int slot, int count) {
        JsonArray sell = model.getAsJsonArray("sell");
        for (int i = 0; i < sell.size(); i++) {
            JsonObject row = sell.get(i).getAsJsonObject();
            if (row.get("slot").getAsInt() == slot && row.get("count").getAsInt() == count) {
                return true;
            }
        }
        return false;
    }

    private static long gold(PlayerDataService data, UUID id) {
        return data.snapshot(id).map(CharacterSnapshot::gold).orElse(-1L);
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new GameTestAssertException(message);
        }
    }
}
