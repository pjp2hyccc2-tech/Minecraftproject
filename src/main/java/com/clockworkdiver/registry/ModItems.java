package com.clockworkdiver.registry;

import com.clockworkdiver.ClockworkDiver;
import com.clockworkdiver.item.BrokenClockItem;
import com.clockworkdiver.item.KeepsakeItem;
import com.clockworkdiver.item.SpectaclesItem;
import com.clockworkdiver.item.WorkshopKeyItem;
import com.clockworkdiver.item.WrenchItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.common.ForgeSpawnEggItem;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.function.Supplier;

public final class ModItems {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, ClockworkDiver.MODID);

    public static final RegistryObject<Item> BROKEN_CLOCK = ITEMS.register("broken_clock",
            () -> new BrokenClockItem(new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON)));
    public static final RegistryObject<Item> CHRONO_SPECTACLES = ITEMS.register("chrono_spectacles",
            () -> new SpectaclesItem(new Item.Properties().stacksTo(1).rarity(Rarity.RARE)));
    public static final RegistryObject<Item> TINKER_WRENCH = ITEMS.register("tinker_wrench",
            () -> new WrenchItem(new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON)));
    public static final RegistryObject<Item> WORKSHOP_KEY = ITEMS.register("workshop_key",
            () -> new WorkshopKeyItem(new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON)));
    public static final RegistryObject<Item> GEAR_TOOTH = ITEMS.register("gear_tooth",
            () -> new Item(new Item.Properties().stacksTo(16)));
    public static final RegistryObject<Item> BRASS_COIN = ITEMS.register("brass_coin",
            () -> new Item(new Item.Properties()));
    public static final RegistryObject<Item> BOLT = ITEMS.register("bolt",
            () -> new Item(new Item.Properties()));
    public static final RegistryObject<Item> KEEPSAKE = ITEMS.register("keepsake",
            () -> new KeepsakeItem(new Item.Properties().stacksTo(1).rarity(Rarity.RARE)));

    public static final RegistryObject<Item> BRASS_BLOCK = block("brass_block", ModBlocks.BRASS_BLOCK);
    public static final RegistryObject<Item> BRASS_PLATE = block("brass_plate", ModBlocks.BRASS_PLATE);
    public static final RegistryObject<Item> BRASS_TILES = block("brass_tiles", ModBlocks.BRASS_TILES);
    public static final RegistryObject<Item> RUSTY_PLATE = block("rusty_plate", ModBlocks.RUSTY_PLATE);
    public static final RegistryObject<Item> COPPER_PIPE = block("copper_pipe", ModBlocks.COPPER_PIPE);
    public static final RegistryObject<Item> JEWEL_BEARING = block("jewel_bearing", ModBlocks.JEWEL_BEARING);
    public static final RegistryObject<Item> STEAM_VENT = block("steam_vent", ModBlocks.STEAM_VENT);
    public static final RegistryObject<Item> REPAIR_BENCH = block("repair_bench", ModBlocks.REPAIR_BENCH);
    public static final RegistryObject<Item> RUST_CLUSTER = block("rust_cluster", ModBlocks.RUST_CLUSTER);
    public static final RegistryObject<Item> TOOTH_SOCKET = block("tooth_socket", ModBlocks.TOOTH_SOCKET);
    public static final RegistryObject<Item> SCREW_HEAD = block("screw_head", ModBlocks.SCREW_HEAD);
    public static final RegistryObject<Item> WINDING_CRANK = block("winding_crank", ModBlocks.WINDING_CRANK);
    public static final RegistryObject<Item> TUNING_BELL = block("tuning_bell", ModBlocks.TUNING_BELL);
    public static final RegistryObject<Item> DUST_PILE = block("dust_pile", ModBlocks.DUST_PILE);
    public static final RegistryObject<Item> MEMORY_COG = block("memory_cog", ModBlocks.MEMORY_COG);

    public static final RegistryObject<Item> CUSTOMER_SPAWN_EGG = ITEMS.register("customer_spawn_egg",
            () -> new ForgeSpawnEggItem(ModEntities.CUSTOMER, 0x6B4A2B, 0xD4AF37, new Item.Properties()));
    public static final RegistryObject<Item> RUST_MITE_SPAWN_EGG = ITEMS.register("rust_mite_spawn_egg",
            () -> new ForgeSpawnEggItem(ModEntities.RUST_MITE, 0x8B3A1A, 0xC0702A, new Item.Properties()));
    public static final RegistryObject<Item> COG_GREMLIN_SPAWN_EGG = ITEMS.register("cog_gremlin_spawn_egg",
            () -> new ForgeSpawnEggItem(ModEntities.COG_GREMLIN, 0x4E6B3A, 0xD4AF37, new Item.Properties()));
    public static final RegistryObject<Item> DUST_MOTH_SPAWN_EGG = ITEMS.register("dust_moth_spawn_egg",
            () -> new ForgeSpawnEggItem(ModEntities.DUST_MOTH, 0x9C9283, 0x5A5148, new Item.Properties()));
    public static final RegistryObject<Item> TIME_EATER_SPAWN_EGG = ITEMS.register("time_eater_spawn_egg",
            () -> new ForgeSpawnEggItem(ModEntities.TIME_EATER, 0x2B1D3A, 0xE0C060, new Item.Properties()));

    private ModItems() {}

    private static RegistryObject<Item> block(String name, Supplier<Block> block) {
        return ITEMS.register(name, () -> new BlockItem(block.get(), new Item.Properties()));
    }
}
