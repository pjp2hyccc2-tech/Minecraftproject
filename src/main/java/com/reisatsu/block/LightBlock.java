package com.reisatsu.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The restaurant's lights: {@code ceiling_light} (recessed fluorescent panel, lit + broken = flickers),
 * {@code downlight} (also in the eave outside: fascia = true) and {@code pendant_lamp}. Which circuit a light belongs to follows from where it is
 * ({@link com.reisatsu.world.Fixtures}); the flicker of broken tubes is driven by the world ticker.
 */
public class LightBlock extends Block {
    private final boolean hasBroken;
    private final VoxelShape shape;

    public LightBlock(Properties properties, boolean hasBroken, VoxelShape shape) {
        super(properties);
        this.hasBroken = hasBroken;
        this.shape = shape;
        BlockState s = stateDefinition.any().setValue(Props.LIT, false);
        if (hasBroken) s = s.setValue(Props.BROKEN, false);
        if (this instanceof Down) s = s.setValue(Props.FASCIA, false);
        registerDefaultState(s);
    }

    /** Light level of a lit light of this kind. */
    public static int level(BlockState state, int lit) {
        return state.getValue(Props.LIT) ? lit : 0;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(Props.LIT);
        // the panel lights have a "broken" (flickering) state; the stateDefinition is built before our fields are set
        if (this instanceof Panel) builder.add(Props.BROKEN);
        if (this instanceof Down) builder.add(Props.FASCIA);
    }

    public boolean hasBroken() {
        return hasBroken;
    }

    @Override
    @SuppressWarnings("deprecation")
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return shape;
    }

    @Override
    @SuppressWarnings("deprecation")
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return shape == Shapes.block() ? shape : Shapes.empty();
    }

    @Override
    @SuppressWarnings("deprecation")
    public VoxelShape getVisualShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return shape == Shapes.block() ? shape : Shapes.empty();
    }

    /** The downlight: in a ceiling, or set into the eave's fascia band outside ({@link Props#FASCIA}). */
    public static class Down extends LightBlock {
        public Down(Properties properties) {
            super(properties, false, Shapes.block());
        }
    }

    /** The ceiling panel light (has the broken state). */
    public static class Panel extends LightBlock {
        public Panel(Properties properties) {
            super(properties, true, Shapes.block());
        }
    }
}
