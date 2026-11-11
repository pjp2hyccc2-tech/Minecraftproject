package com.eternalrealm.block.forge;

import com.eternalrealm.core.ModInfo;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.DoubleHighBlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * The canal town's small gimmicks: strings of bunting (pennants, flags, paper lanterns, laundry, garlands), secret
 * doors that look exactly like the wall they are set in, hanging shop signs with a picture of the trade, and candle
 * lanterns floating on the canals. Registered next to {@link ERBlocks} (same creative tab); the capital build
 * ({@code tools/capital}) places exactly these ids, the art comes from {@code tools/art/canal_gimmicks.py}.
 */
public final class TownGimmickBlocks {
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, ModInfo.MOD_ID);
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, ModInfo.MOD_ID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, ModInfo.MOD_ID);
    private static final List<RegistryObject<Item>> TAB_ITEMS = new ArrayList<>();

    public static final RegistryObject<Block> BUNTING = block("bunting", () -> new BuntingBlock(
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(0.6F, 3.0F).sound(SoundType.CHAIN)
                    .noCollission().noOcclusion().pushReaction(PushReaction.DESTROY)), BlockItem::new);
    public static final RegistryObject<Block> SECRET_DOOR = block("secret_door", () -> new SecretDoorBlock(
            BlockBehaviour.Properties.of().mapColor(SecretDoorBlock::mapColor).strength(2.0F, 6.0F)
                    .sound(SoundType.STONE).noLootTable().pushReaction(PushReaction.BLOCK)
                    .isValidSpawn((state, level, pos, type) -> false)), DoubleHighBlockItem::new);
    public static final RegistryObject<Block> SHOP_SIGN = block("shop_sign", () -> new ShopSignBlock(
            BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(1.0F, 3.0F).sound(SoundType.HANGING_SIGN)
                    .noCollission().noOcclusion().pushReaction(PushReaction.DESTROY)), BlockItem::new);
    public static final RegistryObject<Block> FLOATING_LANTERN = block("floating_lantern",
            () -> new FloatingLanternBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_ORANGE).strength(0.3F)
                    .sound(SoundType.WOOD).lightLevel(state -> 9).noCollission().noOcclusion()
                    .pushReaction(PushReaction.DESTROY)), BlockItem::new);

    @SuppressWarnings("DataFlowIssue") // the data fixer type may be null
    public static final RegistryObject<BlockEntityType<BuntingBlockEntity>> BUNTING_ENTITY =
            BLOCK_ENTITIES.register("bunting",
                    () -> BlockEntityType.Builder.of(BuntingBlockEntity::new, BUNTING.get()).build(null));
    @SuppressWarnings("DataFlowIssue")
    public static final RegistryObject<BlockEntityType<SecretDoorBlockEntity>> SECRET_DOOR_ENTITY =
            BLOCK_ENTITIES.register("secret_door",
                    () -> BlockEntityType.Builder.of(SecretDoorBlockEntity::new, SECRET_DOOR.get()).build(null));
    @SuppressWarnings("DataFlowIssue")
    public static final RegistryObject<BlockEntityType<ShopSignBlockEntity>> SHOP_SIGN_ENTITY =
            BLOCK_ENTITIES.register("shop_sign",
                    () -> BlockEntityType.Builder.of(ShopSignBlockEntity::new, SHOP_SIGN.get()).build(null));
    @SuppressWarnings("DataFlowIssue")
    public static final RegistryObject<BlockEntityType<FloatingLanternBlockEntity>> FLOATING_LANTERN_ENTITY =
            BLOCK_ENTITIES.register("floating_lantern", () -> BlockEntityType.Builder.of(FloatingLanternBlockEntity::new,
                    FLOATING_LANTERN.get()).build(null));

    private TownGimmickBlocks() {
    }

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        modBus.addListener(TownGimmickBlocks::onBuildTab);
    }

    /** Our items join the capital's building tab. */
    private static void onBuildTab(BuildCreativeModeTabContentsEvent event) {
        if (ERBlocks.TAB.getKey() != null && ERBlocks.TAB.getKey().equals(event.getTabKey())) {
            for (RegistryObject<Item> item : TAB_ITEMS) {
                event.accept(item);
            }
        }
    }

    private static RegistryObject<Block> block(String name, Supplier<Block> factory,
                                               ItemFactory itemFactory) {
        RegistryObject<Block> block = BLOCKS.register(name, factory);
        TAB_ITEMS.add(ITEMS.register(name, () -> itemFactory.create(block.get(), new Item.Properties())));
        return block;
    }

    @FunctionalInterface
    private interface ItemFactory {
        Item create(Block block, Item.Properties properties);
    }
}
