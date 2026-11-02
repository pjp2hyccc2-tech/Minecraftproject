package com.sounan.registry;

import com.sounan.Sounan;
import com.sounan.item.GameItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.DoubleHighBlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraftforge.common.ForgeSpawnEggItem;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.ArrayList;
import java.util.List;

public final class ModItems {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, Sounan.MODID);
    /** Every block item, in registration order (for the creative tab). */
    public static final List<RegistryObject<Item>> BLOCK_ITEMS = new ArrayList<>();
    public static final List<RegistryObject<Item>> GAME_ITEMS = new ArrayList<>();

    // ---------------------------------------------------------------- game items
    public static final RegistryObject<Item> FLASHLIGHT = game("flashlight", GameItem.Kind.FLASHLIGHT, single());
    public static final RegistryObject<Item> FLASHLIGHT_ON = game("flashlight_on", GameItem.Kind.FLASHLIGHT_ON, single());
    public static final RegistryObject<Item> FLASHLIGHT_LOW = game("flashlight_low", GameItem.Kind.FLASHLIGHT_LOW, single());
    public static final RegistryObject<Item> PHONE = game("phone", GameItem.Kind.PHONE, single());
    public static final RegistryObject<Item> PHONE_LIGHT = game("phone_light", GameItem.Kind.PHONE_LIGHT, single());
    public static final RegistryObject<Item> PHONE_CAMERA = game("phone_camera", GameItem.Kind.PHONE_CAMERA, single());
    public static final RegistryObject<Item> BATTERY = game("battery", GameItem.Kind.BATTERY, new Item.Properties().stacksTo(4));
    public static final RegistryObject<Item> POWER_BANK = game("power_bank", GameItem.Kind.POWER_BANK, single());
    public static final RegistryObject<Item> BEAR_BELL = game("bear_bell", GameItem.Kind.BEAR_BELL, new Item.Properties().durability(5));
    public static final RegistryObject<Item> ROPE = game("rope", GameItem.Kind.ROPE, single());
    public static final RegistryObject<Item> TRAIL_MAP = game("trail_map", GameItem.Kind.TRAIL_MAP, single());
    public static final RegistryObject<Item> FLARE = game("flare", GameItem.Kind.FLARE, single());
    public static final RegistryObject<Item> FLARE_LIT = game("flare_lit", GameItem.Kind.FLARE_LIT, single());
    public static final RegistryObject<Item> OMAMORI = game("omamori", GameItem.Kind.OMAMORI, single().rarity(Rarity.UNCOMMON));
    public static final RegistryObject<Item> HUT_KEY = game("hut_key", GameItem.Kind.HUT_KEY, single());
    public static final RegistryObject<Item> OMIKI = game("omiki", GameItem.Kind.OMIKI, single());
    public static final RegistryObject<Item> MEMO = game("memo", GameItem.Kind.MEMO, single());

    // ---------------------------------------------------------------- spawn eggs (creative / debugging)
    public static final RegistryObject<Item> NOZOKI_SPAWN_EGG = ITEMS.register("nozoki_spawn_egg",
            () -> new ForgeSpawnEggItem(ModEntities.NOZOKI, 0xD8D6D0, 0x111111, new Item.Properties()));
    public static final RegistryObject<Item> YOBIGOE_SPAWN_EGG = ITEMS.register("yobigoe_spawn_egg",
            () -> new ForgeSpawnEggItem(ModEntities.YOBIGOE, 0xC8571E, 0xFFF4C0, new Item.Properties()));
    public static final RegistryObject<Item> USHIRO_SPAWN_EGG = ITEMS.register("ushiro_spawn_egg",
            () -> new ForgeSpawnEggItem(ModEntities.USHIRO, 0x9A9A96, 0x0C0C0C, new Item.Properties()));
    public static final RegistryObject<Item> KOYANUSHI_SPAWN_EGG = ITEMS.register("koyanushi_spawn_egg",
            () -> new ForgeSpawnEggItem(ModEntities.KOYANUSHI, 0x8C8676, 0x3A3328, new Item.Properties()));
    public static final RegistryObject<Item> OYAMASAMA_SPAWN_EGG = ITEMS.register("oyamasama_spawn_egg",
            () -> new ForgeSpawnEggItem(ModEntities.OYAMASAMA, 0x2A2420, 0xE8E2D4, new Item.Properties()));
    public static final RegistryObject<Item> UTSURUMONO_SPAWN_EGG = ITEMS.register("utsurumono_spawn_egg",
            () -> new ForgeSpawnEggItem(ModEntities.UTSURUMONO, 0x6E7E86, 0xD0E0E0, new Item.Properties()));

    // ---------------------------------------------------------------- block items
    static {
        for (RegistryObject<Block> block : ModBlocks.BLOCKS.getEntries()) {
            String name = block.getId().getPath();
            BLOCK_ITEMS.add(ITEMS.register(name, () -> block.get() instanceof DoorBlock
                    ? new DoubleHighBlockItem(block.get(), new Item.Properties())
                    : new BlockItem(block.get(), new Item.Properties())));
        }
    }

    private ModItems() {}

    private static RegistryObject<Item> game(String name, GameItem.Kind kind, Item.Properties props) {
        RegistryObject<Item> ro = ITEMS.register(name, () -> new GameItem(kind, props));
        GAME_ITEMS.add(ro);
        return ro;
    }

    private static Item.Properties single() {
        return new Item.Properties().stacksTo(1);
    }
}
