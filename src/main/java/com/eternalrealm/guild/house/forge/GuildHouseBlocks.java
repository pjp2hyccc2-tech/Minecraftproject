package com.eternalrealm.guild.house.forge;

import com.eternalrealm.core.ModInfo;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** The guild houses' blocks: the gates' portal surface and the guild board (art: tools/art/guild_portal.py). */
public final class GuildHouseBlocks {
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, ModInfo.MOD_ID);
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, ModInfo.MOD_ID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, ModInfo.MOD_ID);

    public static final RegistryObject<Block> PORTAL = BLOCKS.register("guild_portal", () -> new GuildPortalBlock(
            BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_PURPLE).strength(-1.0F, 3600000.0F).noCollission()
                    .noOcclusion().noLootTable().noParticlesOnBreak().lightLevel(state -> 11)
                    .sound(SoundType.AMETHYST).pushReaction(PushReaction.BLOCK)
                    .isValidSpawn((state, level, pos, type) -> false)
                    .isRedstoneConductor((state, level, pos) -> false)
                    .isSuffocating((state, level, pos) -> false)
                    .isViewBlocking((state, level, pos) -> false)));
    public static final RegistryObject<Block> BOARD = BLOCKS.register("guild_board", () -> new GuildBoardBlock(
            BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(-1.0F, 3600000.0F).noOcclusion()
                    .noLootTable().sound(SoundType.WOOD).pushReaction(PushReaction.BLOCK)));
    public static final RegistryObject<Item> BOARD_ITEM = ITEMS.register("guild_board",
            () -> new BlockItem(BOARD.get(), new Item.Properties()));

    @SuppressWarnings("DataFlowIssue")
    public static final RegistryObject<BlockEntityType<GuildBoardBlockEntity>> BOARD_ENTITY =
            BLOCK_ENTITIES.register("guild_board",
                    () -> BlockEntityType.Builder.of(GuildBoardBlockEntity::new, BOARD.get()).build(null));

    private GuildHouseBlocks() {
    }

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
    }
}
