package jp.satsujinki.registry;

import jp.satsujinki.Satsujinki;
import jp.satsujinki.item.ContestantTicketItem;
import jp.satsujinki.item.HostHandItem;
import jp.satsujinki.item.HostRemoteItem;
import jp.satsujinki.item.MopItem;
import jp.satsujinki.item.TrapKitItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModItems {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, Satsujinki.MODID);

    public static final RegistryObject<Item> HOST_HAND = ITEMS.register("host_hand",
            () -> new HostHandItem(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC)));
    public static final RegistryObject<Item> HOST_REMOTE = ITEMS.register("host_remote",
            () -> new HostRemoteItem(new Item.Properties().stacksTo(1).rarity(Rarity.RARE)));
    public static final RegistryObject<Item> CONTESTANT_TICKET = ITEMS.register("contestant_ticket",
            () -> new ContestantTicketItem(new Item.Properties().stacksTo(64)));
    public static final RegistryObject<Item> ARENA_KIT = ITEMS.register("arena_kit",
            () -> new TrapKitItem(TrapKitItem.Kind.ARENA, new Item.Properties().stacksTo(16).rarity(Rarity.UNCOMMON)));
    public static final RegistryObject<Item> MIXER_KIT = ITEMS.register("mixer_kit",
            () -> new TrapKitItem(TrapKitItem.Kind.MIXER, new Item.Properties().stacksTo(16).rarity(Rarity.UNCOMMON)));
    public static final RegistryObject<Item> PRESS_KIT = ITEMS.register("press_kit",
            () -> new TrapKitItem(TrapKitItem.Kind.PRESS, new Item.Properties().stacksTo(16).rarity(Rarity.UNCOMMON)));
    public static final RegistryObject<Item> MOP = ITEMS.register("mop",
            () -> new MopItem(new Item.Properties().durability(256)));

    public static final RegistryObject<Item> HOST_TERMINAL = block(ModBlocks.HOST_TERMINAL);
    public static final RegistryObject<Item> SAW = block(ModBlocks.SAW);
    public static final RegistryObject<Item> GUILLOTINE = block(ModBlocks.GUILLOTINE);
    public static final RegistryObject<Item> SPIKE_BED = block(ModBlocks.SPIKE_BED);
    public static final RegistryObject<Item> PRESS_HEAD = block(ModBlocks.PRESS_HEAD);
    public static final RegistryObject<Item> MIXER_CORE = block(ModBlocks.MIXER_CORE);
    public static final RegistryObject<Item> MIXER_CASING = block(ModBlocks.MIXER_CASING);
    public static final RegistryObject<Item> MIXER_GLASS = block(ModBlocks.MIXER_GLASS);
    public static final RegistryObject<Item> EXECUTION_TILES = block(ModBlocks.EXECUTION_TILES);
    public static final RegistryObject<Item> BLOODY_TILES = block(ModBlocks.BLOODY_TILES);
    public static final RegistryObject<Item> RUSTED_PLATE = block(ModBlocks.RUSTED_PLATE);

    private ModItems() {
    }

    private static RegistryObject<Item> block(RegistryObject<Block> block) {
        return ITEMS.register(block.getId().getPath(), () -> new BlockItem(block.get(), new Item.Properties()));
    }
}
