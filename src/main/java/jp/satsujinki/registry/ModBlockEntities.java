package jp.satsujinki.registry;

import jp.satsujinki.Satsujinki;
import jp.satsujinki.block.entity.GuillotineBlockEntity;
import jp.satsujinki.block.entity.MixerBlockEntity;
import jp.satsujinki.block.entity.PressBlockEntity;
import jp.satsujinki.block.entity.SawBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

@SuppressWarnings("ConstantConditions")
public final class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, Satsujinki.MODID);

    public static final RegistryObject<BlockEntityType<MixerBlockEntity>> MIXER = BLOCK_ENTITIES.register("mixer",
            () -> BlockEntityType.Builder.of(MixerBlockEntity::new, ModBlocks.MIXER_CORE.get()).build(null));
    public static final RegistryObject<BlockEntityType<PressBlockEntity>> PRESS = BLOCK_ENTITIES.register("press",
            () -> BlockEntityType.Builder.of(PressBlockEntity::new, ModBlocks.PRESS_HEAD.get()).build(null));
    public static final RegistryObject<BlockEntityType<SawBlockEntity>> SAW = BLOCK_ENTITIES.register("saw",
            () -> BlockEntityType.Builder.of(SawBlockEntity::new, ModBlocks.SAW.get()).build(null));
    public static final RegistryObject<BlockEntityType<GuillotineBlockEntity>> GUILLOTINE = BLOCK_ENTITIES.register("guillotine",
            () -> BlockEntityType.Builder.of(GuillotineBlockEntity::new, ModBlocks.GUILLOTINE.get()).build(null));

    private ModBlockEntities() {
    }
}
