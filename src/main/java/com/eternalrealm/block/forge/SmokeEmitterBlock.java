package com.eternalrealm.block.forge;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.entity.player.Player;
import net.minecraft.util.RandomSource;

import com.eternalrealm.combat.forge.ERParticles;

/**
 * Chimney smoke: put it in the air block right above a chimney. Soft grey wisps rise and drift away and can be seen
 * from across the town; {@code heavy=true} gives the thick dark smoke of a forge.
 */
public class SmokeEmitterBlock extends InvisibleEmitterBlock<SmokeEmitterBlock.Entity> {
    public static final BooleanProperty HEAVY = BooleanProperty.create("heavy");
    private static final double SEEN_FROM = 160.0;

    public SmokeEmitterBlock(Properties properties) {
        super(properties, () -> ERBlocks.SMOKE_EMITTER_ENTITY.get(), SmokeEmitterBlock::clientTick);
        registerDefaultState(stateDefinition.any().setValue(HEAVY, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(HEAVY);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new Entity(pos, state);
    }

    private static void clientTick(Level level, BlockPos pos, BlockState state, Entity entity) {
        boolean heavy = state.getValue(HEAVY);
        RandomSource random = level.getRandom();
        float chance = heavy ? 0.22F : 0.07F;
        if (random.nextFloat() >= chance) {
            return;
        }
        double x = pos.getX() + 0.5;
        double y = pos.getY() + 0.1;
        double z = pos.getZ() + 0.5;
        Player near = level.getNearestPlayer(x, y, z, SEEN_FROM, false);
        if (near == null || (near.distanceToSqr(x, y, z) > 96.0 * 96.0 && random.nextBoolean())) {
            return;
        }
        level.addAlwaysVisibleParticle(heavy ? ERParticles.FORGE_SMOKE.get() : ERParticles.CHIMNEY_SMOKE.get(), true,
                x + (random.nextDouble() - 0.5) * 0.35, y, z + (random.nextDouble() - 0.5) * 0.35,
                (random.nextDouble() - 0.5) * 0.004, 0.035 + random.nextDouble() * 0.02,
                (random.nextDouble() - 0.5) * 0.004);
    }

    /** No data: it only exists so the client can tick the emitter. */
    public static class Entity extends BlockEntity {
        public Entity(BlockPos pos, BlockState state) {
            super(ERBlocks.SMOKE_EMITTER_ENTITY.get(), pos, state);
        }
    }
}
