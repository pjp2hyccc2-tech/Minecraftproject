package com.sounan.block;

import com.sounan.game.NoiseSystem;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockSetType;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.BlockHitResult;

import javax.annotation.Nullable;
import java.util.function.Supplier;

/**
 * Sliding doors of the mansion (fusuma, shoji, closet, storehouse). Opening one makes noise the bride can hear.
 * The BlockSetType decides whether mobs may open it (OAK = yes, IRON = no).
 */
public class SounanDoorBlock extends DoorBlock {
    private final Supplier<SoundEvent> sound;
    private final float noiseRadius;

    public SounanDoorBlock(Properties properties, BlockSetType type, Supplier<SoundEvent> sound, float noiseRadius) {
        super(properties, type);
        this.sound = sound;
        this.noiseRadius = noiseRadius;
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (!canPlayerOpen(state, level, pos, player)) {
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        state = state.cycle(OPEN);
        level.setBlock(pos, state, 10);
        boolean open = state.getValue(OPEN);
        playDoorSound(level, pos, open, player);
        level.gameEvent(player, open ? GameEvent.BLOCK_OPEN : GameEvent.BLOCK_CLOSE, pos);
        if (level instanceof ServerLevel sl && noiseRadius > 0) {
            NoiseSystem.emit(sl, pos, noiseRadius, NoiseSystem.Source.DOOR);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /**
     * Fusuma and shoji slide into the wall: an open one leaves the whole doorway free. (The swinging vanilla panel
     * left a 13/16 gap that a 0.6 wide mob only fits through when it is centred to within a hundredth of a block,
     * so the bride kept getting stuck on the edge of open doors.)
     */
    @Override
    @SuppressWarnings("deprecation")
    public net.minecraft.world.phys.shapes.VoxelShape getCollisionShape(BlockState state, net.minecraft.world.level.BlockGetter level,
                                                                        BlockPos pos, net.minecraft.world.phys.shapes.CollisionContext ctx) {
        if (state.getValue(OPEN)) return net.minecraft.world.phys.shapes.Shapes.empty();
        return super.getCollisionShape(state, level, pos, ctx);
    }

    /** Hook for locked doors; called on both sides. */
    protected boolean canPlayerOpen(BlockState state, Level level, BlockPos pos, Player player) {
        return true;
    }

    @Override
    public void setOpen(@Nullable Entity entity, Level level, BlockState state, BlockPos pos, boolean open) {
        if (state.is(this) && state.getValue(OPEN) != open) {
            level.setBlock(pos, state.setValue(OPEN, open), 10);
            playDoorSound(level, pos, open, null);
            level.gameEvent(entity, open ? GameEvent.BLOCK_OPEN : GameEvent.BLOCK_CLOSE, pos);
        }
    }

    protected void playDoorSound(Level level, BlockPos pos, boolean open, @Nullable Player player) {
        float pitch = (open ? 1.0f : 0.86f) + level.getRandom().nextFloat() * 0.1f;
        level.playSound(player, pos, sound.get(), SoundSource.BLOCKS, 0.9f, pitch);
    }
}
