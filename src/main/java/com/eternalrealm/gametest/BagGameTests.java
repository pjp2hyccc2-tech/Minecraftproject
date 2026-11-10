package com.eternalrealm.gametest;

import com.eternalrealm.combat.WeaponType;
import com.eternalrealm.core.ModInfo;
import com.eternalrealm.item.EquipmentBase;
import com.eternalrealm.item.EquipmentData;
import com.eternalrealm.item.EquipmentSlot;
import com.eternalrealm.item.ItemRarity;
import com.eternalrealm.item.ItemTier;
import com.eternalrealm.item.bag.Bag;
import com.eternalrealm.item.forge.BagService;
import com.eternalrealm.item.forge.Bags;
import com.eternalrealm.item.forge.EquipmentStacks;
import com.eternalrealm.item.forge.GearArmorItem;
import com.mojang.authlib.GameProfile;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
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
import java.util.UUID;

/**
 * The bag on a real server: what the game gives goes into bag slots 1..35 (never the weapon slot) and the rest drops at
 * the player's feet; equipping swaps weapons into slot 0 and armor into its slot; taking off, sorting and discarding.
 */
@GameTestHolder(ModInfo.MOD_ID)
@PrefixGameTestTemplate(false)
public final class BagGameTests {
    private static final String ARENA = "arena";

    private BagGameTests() {
    }

    @GameTest(template = ARENA)
    public static void givingFillsTheBagAndDropsTheRest(GameTestHelper helper) {
        FakePlayer player = player(helper, "er_gt_bag_give");
        Inventory inventory = player.getInventory();
        ItemStack weapon = gear(EquipmentBase.weapon(WeaponType.ONE_HANDED_SWORD, ItemTier.IRON));
        inventory.items.set(0, weapon);
        for (int slot = Bag.FIRST_SLOT; slot < Bag.LAST_SLOT; slot++) {
            inventory.items.set(slot, new ItemStack(Items.STONE, 64));
        }
        inventory.items.set(Bag.LAST_SLOT, new ItemStack(Items.TORCH, 60));
        Bags.give(player, new ItemStack(Items.TORCH, 10));
        check(inventory.items.get(Bag.LAST_SLOT).getCount() == 64, "the torches should top up their stack");
        check(inventory.items.get(0) == weapon, "the weapon slot must never receive items");
        List<ItemEntity> dropped = helper.getLevel().getEntitiesOfClass(ItemEntity.class,
                new AABB(player.blockPosition()).inflate(3.0), entity -> entity.getItem().is(Items.TORCH));
        check(dropped.size() == 1 && dropped.get(0).getItem().getCount() == 6,
                "the six torches that did not fit should lie at the player's feet: " + dropped);
        dropped.forEach(ItemEntity::discard);
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void equippingSwapsWeaponsAndArmor(GameTestHelper helper) {
        FakePlayer player = player(helper, "er_gt_bag_equip");
        Inventory inventory = player.getInventory();
        ItemStack sword = gear(EquipmentBase.weapon(WeaponType.ONE_HANDED_SWORD, ItemTier.IRON));
        ItemStack spear = gear(EquipmentBase.weapon(WeaponType.SPEAR, ItemTier.IRON));
        ItemStack helmet = gear(EquipmentBase.armor(EquipmentSlot.HEAD, ItemTier.IRON));
        ItemStack worn = gear(EquipmentBase.armor(EquipmentSlot.HEAD, ItemTier.IRON));
        inventory.items.set(0, sword);
        inventory.items.set(5, spear);
        inventory.items.set(7, helmet);
        net.minecraft.world.entity.EquipmentSlot head = GearArmorItem.vanillaSlot(EquipmentSlot.HEAD);
        player.setItemSlot(head, worn);
        check(BagService.equip(player, 5) == BagService.EquipResult.OK, "the spear should be equipped");
        check(EquipmentStacks.read(inventory.items.get(0)).map(EquipmentData::weaponType).orElse(null)
                == WeaponType.SPEAR, "the spear should be in slot 0");
        check(EquipmentStacks.read(inventory.items.get(5)).map(EquipmentData::weaponType).orElse(null)
                == WeaponType.ONE_HANDED_SWORD, "the sword should be back in the bag slot");
        UUID helmetId = EquipmentStacks.read(helmet).orElseThrow().uniqueId();
        UUID wornId = EquipmentStacks.read(worn).orElseThrow().uniqueId();
        check(BagService.equip(player, 7) == BagService.EquipResult.OK, "the helmet should be equipped");
        check(EquipmentStacks.read(player.getItemBySlot(head)).orElseThrow().uniqueId().equals(helmetId),
                "the new helmet should be worn");
        check(EquipmentStacks.read(inventory.items.get(7)).orElseThrow().uniqueId().equals(wornId),
                "the old helmet should be in the bag slot");
        inventory.items.set(9, new ItemStack(Items.STONE));
        check(BagService.equip(player, 9) == BagService.EquipResult.NOT_EQUIPMENT, "stone is not equipment");
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void takingOffSortingAndDiscarding(GameTestHelper helper) {
        FakePlayer player = player(helper, "er_gt_bag_sort");
        Inventory inventory = player.getInventory();
        net.minecraft.world.entity.EquipmentSlot head = GearArmorItem.vanillaSlot(EquipmentSlot.HEAD);
        player.setItemSlot(head, gear(EquipmentBase.armor(EquipmentSlot.HEAD, ItemTier.IRON)));
        inventory.items.set(1, new ItemStack(Items.STONE, 10));
        inventory.items.set(4, new ItemStack(Items.STONE, 20));
        check(BagService.unequip(player, EquipmentSlot.HEAD), "taking off with free slots must work");
        check(player.getItemBySlot(head).isEmpty(), "the helmet should be off");
        check(EquipmentStacks.isEquipment(inventory.items.get(2)), "the helmet should be in the first free slot 2");
        BagService.sort(player);
        check(EquipmentStacks.isEquipment(inventory.items.get(1)), "sorting puts equipment first");
        check(inventory.items.get(2).is(Items.STONE) && inventory.items.get(2).getCount() == 30,
                "sorting merges the stone stacks: " + inventory.items.get(2));
        check(inventory.items.get(3).isEmpty(), "the rest of the bag is empty after sorting");
        BagService.discard(player, 2);
        check(inventory.items.get(2).isEmpty(), "the discarded stack is gone");
        helper.succeed();
    }

    private static ItemStack gear(EquipmentBase base) {
        return EquipmentStacks.create(EquipmentData.of(base, ItemRarity.COMMON, List.of()));
    }

    private static FakePlayer player(GameTestHelper helper, String name) {
        FakePlayer player = FakePlayerFactory.get(helper.getLevel(),
                new GameProfile(UUID.nameUUIDFromBytes(name.getBytes(StandardCharsets.UTF_8)), name));
        Vec3 position = helper.absoluteVec(new Vec3(4.5, 2.0, 4.5));
        player.moveTo(position.x, position.y, position.z, 0.0F, 0.0F);
        player.getInventory().clearContent();
        return player;
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new GameTestAssertException(message);
        }
    }
}
