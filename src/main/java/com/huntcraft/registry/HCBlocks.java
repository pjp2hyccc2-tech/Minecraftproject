package com.huntcraft.registry;

import com.huntcraft.HuntCraft;
import com.huntcraft.block.BarrelBombBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** Core blocks. Gathering nodes / item box / supply box live in their modules. */
public final class HCBlocks {
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, HuntCraft.MODID);

    private HCBlocks() {
    }

    public static final RegistryObject<Block> BARREL_BOMB = BLOCKS.register("barrel_bomb",
            () -> new BarrelBombBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_RED).strength(0.3F).sound(SoundType.WOOD).noOcclusion(), 70.0F, 3.0F));
    public static final RegistryObject<Block> BARREL_BOMB_PLUS = BLOCKS.register("barrel_bomb_plus",
            () -> new BarrelBombBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_RED).strength(0.3F).sound(SoundType.WOOD).noOcclusion(), 130.0F, 4.0F));
}
