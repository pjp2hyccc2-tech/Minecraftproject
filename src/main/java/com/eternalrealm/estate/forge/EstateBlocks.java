package com.eternalrealm.estate.forge;

import com.eternalrealm.block.forge.ERBlocks;
import com.eternalrealm.core.ModInfo;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * The estates' blocks: the inn's room door, the labelled storage chest and the sorting desk of the rooms (art and
 * models from {@code tools/art/estate_blocks.py}; the capital and room builds place them by id). They appear in the
 * mod's building tab.
 */
public final class EstateBlocks {
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, ModInfo.MOD_ID);
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, ModInfo.MOD_ID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, ModInfo.MOD_ID);

    /** Unbreakable in survival (it is part of the inn), no drops. */
    public static final RegistryObject<Block> ROOM_DOOR = BLOCKS.register("room_door", () -> new RoomDoorBlock(
            BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).instrument(NoteBlockInstrument.BASS)
                    .strength(-1.0F, 3600000.0F).noLootTable().sound(SoundType.WOOD).noOcclusion()
                    .pushReaction(PushReaction.BLOCK).isValidSpawn((state, level, pos, type) -> false)));
    public static final RegistryObject<Item> ROOM_DOOR_ITEM = ITEMS.register("room_door",
            () -> new BlockItem(ROOM_DOOR.get(), new Item.Properties()));

    /** Sturdy like a chest; outside the estates it drops itself and its contents. */
    public static final RegistryObject<Block> STORAGE_CHEST = BLOCKS.register("storage_chest", () -> new StorageChestBlock(
            BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).instrument(NoteBlockInstrument.BASS)
                    .strength(2.5F).sound(SoundType.WOOD).ignitedByLava()));
    public static final RegistryObject<Item> STORAGE_CHEST_ITEM = ITEMS.register("storage_chest",
            () -> new BlockItem(STORAGE_CHEST.get(), new Item.Properties()));
    public static final RegistryObject<BlockEntityType<StorageChestBlockEntity>> STORAGE_CHEST_ENTITY =
            BLOCK_ENTITIES.register("storage_chest", () -> BlockEntityType.Builder.of(StorageChestBlockEntity::new,
                    STORAGE_CHEST.get()).build(null));

    public static final RegistryObject<Block> SORTING_DESK = BLOCKS.register("sorting_desk", () -> new SortingDeskBlock(
            BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).instrument(NoteBlockInstrument.BASS)
                    .strength(2.5F).sound(SoundType.WOOD).noOcclusion().ignitedByLava()));
    public static final RegistryObject<Item> SORTING_DESK_ITEM = ITEMS.register("sorting_desk",
            () -> new BlockItem(SORTING_DESK.get(), new Item.Properties()));

    private EstateBlocks() {
    }

    /** Mod construction. */
    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        modBus.addListener(EstateBlocks::onCreativeTab);
        InnService.register(); // the inn's screens, the innkeeper, the doors' menu, the ledger and the way home
    }

    private static void onCreativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey().equals(ERBlocks.TAB.getKey())) {
            event.accept(ROOM_DOOR_ITEM.get());
            event.accept(STORAGE_CHEST_ITEM.get());
            event.accept(SORTING_DESK_ITEM.get());
        }
    }
}
