package com.eternalrealm.estate.forge;

import com.eternalrealm.block.forge.ERBlocks;
import com.eternalrealm.core.ModInfo;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
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
 * The estates' blocks: the inn's room door (art and models from {@code tools/art/estate_blocks.py}; the capital
 * build places it by id). They appear in the mod's building tab.
 */
public final class EstateBlocks {
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, ModInfo.MOD_ID);
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, ModInfo.MOD_ID);

    /** Unbreakable in survival (it is part of the inn), no drops. */
    public static final RegistryObject<Block> ROOM_DOOR = BLOCKS.register("room_door", () -> new RoomDoorBlock(
            BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).instrument(NoteBlockInstrument.BASS)
                    .strength(-1.0F, 3600000.0F).noLootTable().sound(SoundType.WOOD).noOcclusion()
                    .pushReaction(PushReaction.BLOCK).isValidSpawn((state, level, pos, type) -> false)));
    public static final RegistryObject<Item> ROOM_DOOR_ITEM = ITEMS.register("room_door",
            () -> new BlockItem(ROOM_DOOR.get(), new Item.Properties()));

    private EstateBlocks() {
    }

    /** Mod construction. */
    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        modBus.addListener(EstateBlocks::onCreativeTab);
    }

    private static void onCreativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey().equals(ERBlocks.TAB.getKey())) {
            event.accept(ROOM_DOOR_ITEM.get());
        }
    }
}
