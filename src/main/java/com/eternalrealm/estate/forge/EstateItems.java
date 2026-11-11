package com.eternalrealm.estate.forge;

import com.eternalrealm.estate.StorageLabel;
import com.eternalrealm.item.EquipmentData;
import com.eternalrealm.item.ItemRarity;
import com.eternalrealm.item.forge.ConsumableItem;
import com.eternalrealm.item.forge.EquipmentStacks;
import com.eternalrealm.item.forge.MaterialItem;
import com.eternalrealm.mount.forge.MountDeeds;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.BookItem;
import net.minecraft.world.item.EmptyMapItem;
import net.minecraft.world.item.EnchantedBookItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.item.PotionItem;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.minecraft.world.item.ShieldItem;
import net.minecraft.world.item.TieredItem;
import net.minecraft.world.item.TridentItem;
import net.minecraft.world.item.WritableBookItem;
import net.minecraft.world.item.WrittenBookItem;
import net.minecraftforge.common.Tags;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Optional;

/**
 * How the sorting desk sees things: their kind ({@link StorageLabel}: gear, potions, materials, food, books,
 * treasure, everything else) and a key that is the same for stacks that merge.
 */
public final class EstateItems {
    private EstateItems() {
    }

    /** The kind of a thing, as the labels name kinds. */
    public static StorageLabel kind(ItemStack stack) {
        if (stack.isEmpty()) {
            return StorageLabel.NONE;
        }
        Optional<EquipmentData> equipment = EquipmentStacks.read(stack);
        if (equipment.isPresent()) {
            EquipmentData data = equipment.get();
            return data.isRelic() || data.rarity().compareTo(ItemRarity.EPIC) >= 0 ? StorageLabel.TREASURE
                    : StorageLabel.GEAR;
        }
        Item item = stack.getItem();
        if (MountDeeds.isDeed(stack) || item instanceof BookItem || item instanceof WritableBookItem
                || item instanceof WrittenBookItem || item instanceof EnchantedBookItem || item instanceof MapItem
                || item instanceof EmptyMapItem || stack.is(Items.PAPER)) {
            return StorageLabel.BOOK;
        }
        if (item instanceof ConsumableItem || item instanceof PotionItem) {
            return StorageLabel.POTION;
        }
        if (stack.isEdible()) {
            return StorageLabel.FOOD;
        }
        if (item instanceof MaterialItem) {
            return StorageLabel.MATERIAL;
        }
        if (item instanceof ArmorItem || item instanceof TieredItem || item instanceof ProjectileWeaponItem
                || item instanceof ShieldItem || item instanceof TridentItem) {
            return StorageLabel.GEAR;
        }
        if (stack.is(Tags.Items.GEMS) || stack.is(Tags.Items.INGOTS_GOLD) || stack.is(Tags.Items.INGOTS_NETHERITE)
                || stack.is(Items.NETHER_STAR) || stack.is(Items.TOTEM_OF_UNDYING) || stack.is(Items.ENCHANTED_GOLDEN_APPLE)) {
            return StorageLabel.TREASURE;
        }
        if (stack.is(Tags.Items.INGOTS) || stack.is(Tags.Items.ORES) || stack.is(Tags.Items.NUGGETS)
                || stack.is(Tags.Items.RAW_MATERIALS) || stack.is(Tags.Items.DUSTS) || stack.is(Tags.Items.LEATHER)
                || stack.is(Tags.Items.STRING) || stack.is(ItemTags.LOGS) || stack.is(ItemTags.PLANKS)) {
            return StorageLabel.MATERIAL;
        }
        return StorageLabel.MISC;
    }

    /** Whether a shelf (a chiseled bookshelf) takes it. */
    public static boolean isShelfBook(ItemStack stack) {
        return stack.is(ItemTags.BOOKSHELF_BOOKS);
    }

    /** The same for two stacks exactly when they merge (item and data). */
    public static String key(ItemStack stack) {
        if (stack.isEmpty()) {
            return "";
        }
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());
        CompoundTag tag = stack.getTag();
        return id + (tag == null || tag.isEmpty() ? "" : "#" + Integer.toHexString(tag.hashCode()));
    }
}
