package com.meikon.registry;

import com.meikon.Meikon;
import com.meikon.item.BellItem;
import com.meikon.item.EnvelopeItem;
import com.meikon.item.LanternItem;
import com.meikon.item.LoreItem;
import com.meikon.item.MarbleItem;
import com.meikon.item.NoteItem;
import com.meikon.item.OilItem;
import com.meikon.item.SaltItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.DoubleHighBlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.common.ForgeSpawnEggItem;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.ArrayList;
import java.util.List;

public final class ModItems {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, Meikon.MODID);
    /** Every block item, in registration order (for the creative tab). */
    public static final List<RegistryObject<Item>> BLOCK_ITEMS = new ArrayList<>();

    // ---------------------------------------------------------------- game items
    public static final RegistryObject<Item> CHOCHIN = ITEMS.register("chochin", () -> new LanternItem(single(), false));
    public static final RegistryObject<Item> CHOCHIN_LIT = ITEMS.register("chochin_lit", () -> new LanternItem(single(), true));
    public static final RegistryObject<Item> LAMP_OIL = ITEMS.register("lamp_oil", () -> new OilItem(new Item.Properties().stacksTo(8)));
    public static final RegistryObject<Item> SUZU = ITEMS.register("suzu",
            () -> new BellItem(new Item.Properties().stacksTo(7).rarity(Rarity.RARE)));
    public static final RegistryObject<Item> SHIO = ITEMS.register("shio", () -> new SaltItem(new Item.Properties().stacksTo(8)));
    public static final RegistryObject<Item> OFUDA = ITEMS.register("ofuda", () -> new LoreItem(new Item.Properties().stacksTo(4).rarity(Rarity.UNCOMMON)));
    public static final RegistryObject<Item> OHAJIKI = ITEMS.register("ohajiki", () -> new MarbleItem(new Item.Properties().stacksTo(16)));
    public static final RegistryObject<Item> KURA_KEY = ITEMS.register("kura_key", () -> new LoreItem(single()));
    public static final RegistryObject<Item> DOLL_HEAD = ITEMS.register("doll_head", () -> new LoreItem(single()));
    public static final RegistryObject<Item> MATCHES = ITEMS.register("matches", () -> new LoreItem(new Item.Properties().durability(6)));
    public static final RegistryObject<Item> NOTE = ITEMS.register("note", () -> new NoteItem(single()));
    public static final RegistryObject<Item> RED_ENVELOPE = ITEMS.register("red_envelope",
            () -> new EnvelopeItem(single().rarity(Rarity.EPIC)));
    public static final RegistryObject<Item> OMAMORI = ITEMS.register("omamori", () -> new LoreItem(single().rarity(Rarity.UNCOMMON)));
    public static final RegistryObject<Item> ASH = ITEMS.register("ash", () -> new LoreItem(single()));

    // ---------------------------------------------------------------- spawn eggs
    public static final RegistryObject<Item> HANAYOME_SPAWN_EGG = ITEMS.register("hanayome_spawn_egg",
            () -> new ForgeSpawnEggItem(ModEntities.HANAYOME, 0xEDE8DF, 0x1A1416, new Item.Properties()));
    public static final RegistryObject<Item> ICHIMATSU_SPAWN_EGG = ITEMS.register("ichimatsu_spawn_egg",
            () -> new ForgeSpawnEggItem(ModEntities.ICHIMATSU, 0x8E1B1B, 0x141010, new Item.Properties()));
    public static final RegistryObject<Item> KAGEBOSHI_SPAWN_EGG = ITEMS.register("kageboshi_spawn_egg",
            () -> new ForgeSpawnEggItem(ModEntities.KAGEBOSHI, 0x0A0A0C, 0xC8C8D0, new Item.Properties()));

    // ---------------------------------------------------------------- block items
    static {
        for (RegistryObject<Block> block : ModBlocks.BLOCKS.getEntries()) {
            String name = block.getId().getPath();
            boolean door = name.endsWith("_door");
            BLOCK_ITEMS.add(ITEMS.register(name, () -> door
                    ? new DoubleHighBlockItem(block.get(), new Item.Properties())
                    : new BlockItem(block.get(), new Item.Properties())));
        }
    }

    private ModItems() {}

    private static Item.Properties single() {
        return new Item.Properties().stacksTo(1);
    }
}
