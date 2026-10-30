package com.nihonisshu.tabi.registry;

import com.nihonisshu.tabi.TabiMod;
import com.nihonisshu.tabi.blockentity.TextPanelBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class TabiBlockEntities {
    private TabiBlockEntities() {}

    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, TabiMod.MODID);

    @SuppressWarnings("DataFlowIssue")
    public static final RegistryObject<BlockEntityType<TextPanelBlockEntity>> TEXT_PANEL = BLOCK_ENTITIES.register("text_panel",
            () -> BlockEntityType.Builder.of(TextPanelBlockEntity::new, TabiBlocks.textPanels()).build(null));
}
