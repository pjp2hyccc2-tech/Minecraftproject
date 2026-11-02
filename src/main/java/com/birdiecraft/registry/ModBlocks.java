package com.birdiecraft.registry;

import com.birdiecraft.BirdieCraft;
import com.birdiecraft.block.FlagBlock;
import com.birdiecraft.block.GreenBlock;
import com.birdiecraft.block.StakeBlock;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public final class ModBlocks {
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, BirdieCraft.MODID);
    /** Every block that has an item, in creative tab order. */
    public static final List<RegistryObject<? extends Block>> WITH_ITEMS = new ArrayList<>();

    private static BlockBehaviour.Properties turf(MapColor color) {
        return BlockBehaviour.Properties.of().mapColor(color).strength(0.6F).sound(SoundType.GRASS);
    }

    private static BlockBehaviour.Properties sand(MapColor color) {
        return BlockBehaviour.Properties.of().mapColor(color).strength(0.5F).sound(SoundType.SAND);
    }

    public static final RegistryObject<Block> TEE_BOX = register("tee_box", () -> new Block(turf(MapColor.GRASS)));
    public static final RegistryObject<Block> TEE_MARKER = register("tee_marker", () -> new Block(turf(MapColor.COLOR_LIGHT_GREEN)));
    public static final RegistryObject<Block> FAIRWAY = register("fairway", () -> new Block(turf(MapColor.GRASS)));
    public static final RegistryObject<Block> FAIRWAY_DARK = register("fairway_dark", () -> new Block(turf(MapColor.GRASS)));
    public static final RegistryObject<Block> FRINGE = register("fringe", () -> new Block(turf(MapColor.COLOR_LIGHT_GREEN)));
    public static final RegistryObject<Block> GREEN = register("green", () -> new GreenBlock(turf(MapColor.EMERALD)));
    public static final RegistryObject<Block> GOLF_CUP = register("golf_cup", () -> new Block(turf(MapColor.EMERALD)));
    public static final RegistryObject<Block> GOLF_FLAG = register("golf_flag", () -> new FlagBlock(BlockBehaviour.Properties.of()
            .mapColor(MapColor.WOOL).strength(0.2F).noCollission().noOcclusion().sound(SoundType.WOOL)));
    public static final RegistryObject<Block> ROUGH = register("rough", () -> new Block(turf(MapColor.PLANT)));
    public static final RegistryObject<Block> ROUGH_DRY = register("rough_dry", () -> new Block(turf(MapColor.TERRACOTTA_YELLOW)));
    public static final RegistryObject<Block> ROUGH_SNOW = register("rough_snow", () -> new Block(turf(MapColor.SNOW).sound(SoundType.SNOW)));
    public static final RegistryObject<Block> ROUGH_ASH = register("rough_ash", () -> new Block(turf(MapColor.COLOR_GRAY)));
    public static final RegistryObject<Block> DEEP_ROUGH = register("deep_rough", () -> new Block(turf(MapColor.COLOR_GREEN)));
    public static final RegistryObject<Block> BUNKER_SAND = register("bunker_sand", () -> new Block(sand(MapColor.SAND)));
    public static final RegistryObject<Block> BUNKER_WHITE = register("bunker_white", () -> new Block(sand(MapColor.QUARTZ)));
    public static final RegistryObject<Block> BUNKER_RED = register("bunker_red", () -> new Block(sand(MapColor.COLOR_ORANGE)));
    public static final RegistryObject<Block> BUNKER_ASH = register("bunker_ash", () -> new Block(sand(MapColor.COLOR_BLACK)));
    public static final RegistryObject<Block> CART_PATH = register("cart_path", () -> new Block(BlockBehaviour.Properties.of()
            .mapColor(MapColor.STONE).strength(1.2F, 6.0F).sound(SoundType.STONE)));
    public static final RegistryObject<Block> OB_STAKE = register("ob_stake", () -> new StakeBlock(BlockBehaviour.Properties.of()
            .mapColor(MapColor.SNOW).strength(0.3F).noOcclusion().sound(SoundType.WOOD)));
    public static final RegistryObject<Block> MAPLE_LEAVES = register("maple_leaves", () -> new LeavesBlock(
            BlockBehaviour.Properties.copy(Blocks.OAK_LEAVES).mapColor(MapColor.COLOR_RED)));
    public static final RegistryObject<Block> GINKGO_LEAVES = register("ginkgo_leaves", () -> new LeavesBlock(
            BlockBehaviour.Properties.copy(Blocks.OAK_LEAVES).mapColor(MapColor.COLOR_YELLOW)));

    private static <T extends Block> RegistryObject<T> register(String name, Supplier<T> supplier) {
        RegistryObject<T> block = BLOCKS.register(name, supplier);
        ModItems.ITEMS.register(name, () -> new BlockItem(block.get(), new Item.Properties()));
        WITH_ITEMS.add(block);
        return block;
    }

    private ModBlocks() {
    }
}
