package jp.satsujinki.block;

import net.minecraft.world.level.block.AbstractGlassBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

/** Reinforced mixer glass. Gets smeared with blood (0..3) every time the mixer kills. */
public class MixerGlassBlock extends AbstractGlassBlock {
    public static final IntegerProperty BLOOD = IntegerProperty.create("blood", 0, 3);

    public MixerGlassBlock(Properties props) {
        super(props);
        this.registerDefaultState(this.stateDefinition.any().setValue(BLOOD, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(BLOOD);
    }
}
