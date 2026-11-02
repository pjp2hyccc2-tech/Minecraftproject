package com.clockworkdiver.registry;

import com.clockworkdiver.ClockworkDiver;
import com.clockworkdiver.block.CopperPipeBlock;
import com.clockworkdiver.block.MechanismBlock;
import com.clockworkdiver.block.RepairBenchBlock;
import com.clockworkdiver.block.RustClusterBlock;
import com.clockworkdiver.block.ScrewHeadBlock;
import com.clockworkdiver.block.SteamVentBlock;
import com.clockworkdiver.block.ToothSocketBlock;
import com.clockworkdiver.block.WindingCrankBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModBlocks {
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, ClockworkDiver.MODID);

    public static final RegistryObject<Block> BRASS_BLOCK = BLOCKS.register("brass_block",
            () -> new Block(metal(MapColor.GOLD)));
    public static final RegistryObject<Block> BRASS_PLATE = BLOCKS.register("brass_plate",
            () -> new Block(metal(MapColor.TERRACOTTA_ORANGE)));
    public static final RegistryObject<Block> BRASS_TILES = BLOCKS.register("brass_tiles",
            () -> new Block(metal(MapColor.GOLD)));
    public static final RegistryObject<Block> RUSTY_PLATE = BLOCKS.register("rusty_plate",
            () -> new Block(metal(MapColor.TERRACOTTA_RED)));
    public static final RegistryObject<Block> COPPER_PIPE = BLOCKS.register("copper_pipe",
            () -> new CopperPipeBlock(metal(MapColor.COLOR_ORANGE).noOcclusion()));
    public static final RegistryObject<Block> JEWEL_BEARING = BLOCKS.register("jewel_bearing",
            () -> new Block(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_RED).strength(2.0f, 6.0f)
                    .sound(SoundType.AMETHYST).lightLevel(s -> 14)));
    public static final RegistryObject<Block> STEAM_VENT = BLOCKS.register("steam_vent",
            () -> new SteamVentBlock(metal(MapColor.COLOR_GRAY)));
    public static final RegistryObject<Block> RUST_CLUSTER = BLOCKS.register("rust_cluster",
            () -> new RustClusterBlock(BlockBehaviour.Properties.of().mapColor(MapColor.TERRACOTTA_RED)
                    .strength(2.0f, 1.0f).sound(SoundType.GRAVEL).noOcclusion().noLootTable()
                    .pushReaction(PushReaction.DESTROY)));
    public static final RegistryObject<Block> TOOTH_SOCKET = BLOCKS.register("tooth_socket",
            () -> new ToothSocketBlock(metal(MapColor.GOLD).noOcclusion().lightLevel(s -> s.getValue(ToothSocketBlock.FILLED) ? 0 : 9)));
    public static final RegistryObject<Block> SCREW_HEAD = BLOCKS.register("screw_head",
            () -> new ScrewHeadBlock(metal(MapColor.METAL).lightLevel(s -> 6)));
    public static final RegistryObject<Block> WINDING_CRANK = BLOCKS.register("winding_crank",
            () -> new WindingCrankBlock(metal(MapColor.METAL).noOcclusion().lightLevel(s -> 7)));
    public static final RegistryObject<Block> REPAIR_BENCH = BLOCKS.register("repair_bench",
            () -> new RepairBenchBlock(BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(2.5f, 6.0f)
                    .sound(SoundType.WOOD).noOcclusion()));
    public static final RegistryObject<Block> MECHANISM = BLOCKS.register("mechanism",
            () -> new MechanismBlock(BlockBehaviour.Properties.of().mapColor(MapColor.GOLD).strength(2.0f, 6.0f)
                    .sound(SoundType.METAL).noOcclusion().noCollission().noLootTable()));

    private ModBlocks() {}

    private static BlockBehaviour.Properties metal(MapColor color) {
        return BlockBehaviour.Properties.of().mapColor(color).strength(3.0f, 6.0f).sound(SoundType.METAL);
    }
}
