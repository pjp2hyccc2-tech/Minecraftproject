package com.clockworkdiver.registry;

import com.clockworkdiver.ClockworkDiver;
import com.clockworkdiver.block.MechanismBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, ClockworkDiver.MODID);

    @SuppressWarnings("DataFlowIssue")
    public static final RegistryObject<BlockEntityType<MechanismBlockEntity>> MECHANISM = BLOCK_ENTITIES.register("mechanism",
            () -> BlockEntityType.Builder.of(MechanismBlockEntity::new, ModBlocks.MECHANISM.get()).build(null));

    private ModBlockEntities() {}
}
