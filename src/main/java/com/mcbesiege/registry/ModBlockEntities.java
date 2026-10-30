package com.mcbesiege.registry;

import com.mcbesiege.MCBesiege;
import com.mcbesiege.block.MachinePartBlockEntity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModBlockEntities {
    private ModBlockEntities() {}

    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, MCBesiege.MODID);

    public static final RegistryObject<BlockEntityType<MachinePartBlockEntity>> MACHINE_PART = BLOCK_ENTITIES.register("machine_part",
            () -> BlockEntityType.Builder.of(MachinePartBlockEntity::new, ModBlocks.partBlocks().toArray(new Block[0])).build(null));
}
