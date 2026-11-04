package jp.satsujinki.registry;

import jp.satsujinki.Satsujinki;
import jp.satsujinki.block.BloodSplatterBlock;
import jp.satsujinki.block.GuillotineBlock;
import jp.satsujinki.block.HostTerminalBlock;
import jp.satsujinki.block.MixerCoreBlock;
import jp.satsujinki.block.MixerGlassBlock;
import jp.satsujinki.block.PressHeadBlock;
import jp.satsujinki.block.SawBlock;
import jp.satsujinki.block.SpikeBedBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModBlocks {
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, Satsujinki.MODID);

    public static final RegistryObject<Block> MIXER_CORE = BLOCKS.register("mixer_core",
            () -> new MixerCoreBlock(metal().lightLevel(s -> 3)));
    public static final RegistryObject<Block> MIXER_CASING = BLOCKS.register("mixer_casing",
            () -> new Block(metal()));
    public static final RegistryObject<Block> MIXER_GLASS = BLOCKS.register("mixer_glass",
            () -> new MixerGlassBlock(BlockBehaviour.Properties.of().mapColor(MapColor.NONE).strength(3.0F, 12.0F)
                    .sound(SoundType.GLASS).noOcclusion().isValidSpawn(ModBlocks::never)
                    .isRedstoneConductor(ModBlocks::no).isSuffocating(ModBlocks::no).isViewBlocking(ModBlocks::no)));
    public static final RegistryObject<Block> PRESS_HEAD = BLOCKS.register("press_head",
            () -> new PressHeadBlock(metal().noOcclusion()));
    public static final RegistryObject<Block> SPIKE_BED = BLOCKS.register("spike_bed",
            () -> new SpikeBedBlock(metal().noOcclusion()));
    public static final RegistryObject<Block> SAW = BLOCKS.register("saw",
            () -> new SawBlock(metal().noOcclusion()));
    public static final RegistryObject<Block> GUILLOTINE = BLOCKS.register("guillotine",
            () -> new GuillotineBlock(BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(2.5F, 6.0F)
                    .sound(SoundType.WOOD).noOcclusion()));
    public static final RegistryObject<Block> BLOOD_SPLATTER = BLOCKS.register("blood_splatter",
            () -> new BloodSplatterBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_RED).noCollission()
                    .instabreak().sound(SoundType.HONEY_BLOCK).replaceable().noOcclusion().randomTicks()
                    .pushReaction(PushReaction.DESTROY).noLootTable()));
    public static final RegistryObject<Block> HOST_TERMINAL = BLOCKS.register("host_terminal",
            () -> new HostTerminalBlock(metal().noOcclusion().lightLevel(s -> 9)));
    public static final RegistryObject<Block> EXECUTION_TILES = BLOCKS.register("execution_tiles",
            () -> new Block(BlockBehaviour.Properties.of().mapColor(MapColor.QUARTZ).strength(1.5F, 6.0F).sound(SoundType.STONE)));
    public static final RegistryObject<Block> BLOODY_TILES = BLOCKS.register("bloody_tiles",
            () -> new Block(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_RED).strength(1.5F, 6.0F).sound(SoundType.STONE)));
    public static final RegistryObject<Block> RUSTED_PLATE = BLOCKS.register("rusted_plate",
            () -> new Block(BlockBehaviour.Properties.of().mapColor(MapColor.TERRACOTTA_ORANGE).strength(4.0F, 8.0F).sound(SoundType.METAL)));

    private ModBlocks() {
    }

    private static BlockBehaviour.Properties metal() {
        return BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(4.0F, 8.0F).sound(SoundType.METAL);
    }

    private static boolean never(BlockState state, BlockGetter level, BlockPos pos, EntityType<?> type) {
        return false;
    }

    private static boolean no(BlockState state, BlockGetter level, BlockPos pos) {
        return false;
    }
}
