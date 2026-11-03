package com.reisatsu.registry;

import com.reisatsu.Reisatsu;
import com.reisatsu.block.entity.AutoDoorBlockEntity;
import com.reisatsu.block.entity.SwingDoorBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** Block entity types (owner: world). */
public final class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, Reisatsu.MODID);

    /** The bottom block of each automatic door panel (slide animation; the left panel runs the sensor). */
    @SuppressWarnings("DataFlowIssue")
    public static final RegistryObject<BlockEntityType<AutoDoorBlockEntity>> AUTO_DOOR = BLOCK_ENTITIES.register("auto_door",
            () -> BlockEntityType.Builder.of(AutoDoorBlockEntity::new, ModBlocks.AUTO_DOOR.get()).build(null));

    /** The lower block of each kitchen swing door leaf (the flapping leaf). */
    @SuppressWarnings("DataFlowIssue")
    public static final RegistryObject<BlockEntityType<SwingDoorBlockEntity>> SWING_DOOR = BLOCK_ENTITIES.register("swing_door",
            () -> BlockEntityType.Builder.of(SwingDoorBlockEntity::new, ModBlocks.SWING_DOOR.get()).build(null));

    private ModBlockEntities() {}
}
