package jp.satsujinki.block;

import jp.satsujinki.game.GoreHandler;
import jp.satsujinki.network.GoreFxPacket;
import jp.satsujinki.registry.ModDamageTypes;
import jp.satsujinki.registry.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.pathfinder.BlockPathTypes;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nullable;

/** 剣山: a bed of steel spikes. Hurts and slows anything (except the host) and gets bloodier with each victim. */
public class SpikeBedBlock extends Block {
    public static final IntegerProperty BLOOD = IntegerProperty.create("blood", 0, 2);
    private static final VoxelShape OUTLINE = Block.box(0.0D, 0.0D, 0.0D, 16.0D, 12.0D, 16.0D);
    private static final VoxelShape COLLISION = Block.box(0.0D, 0.0D, 0.0D, 16.0D, 3.0D, 16.0D);

    public SpikeBedBlock(Properties props) {
        super(props);
        this.registerDefaultState(this.stateDefinition.any().setValue(BLOOD, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(BLOOD);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return OUTLINE;
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return COLLISION;
    }

    @Override
    public void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
        if (entity instanceof Player) {
            entity.makeStuckInBlock(state, new Vec3(0.9D, 1.0D, 0.9D));
            return;
        }
        if (!(entity instanceof LivingEntity le) || !le.isAlive()) return;
        double drop = entity.yo - entity.getY();
        le.makeStuckInBlock(state, new Vec3(0.5D, 0.9D, 0.5D));
        if (level.isClientSide) return;
        if (drop > 0.3D) {
            // landed on the spikes from a height: impaled
            level.playSound(null, pos, ModSounds.SPIKE_IMPALE.get(), SoundSource.HOSTILE, 1.3F, 0.85F + level.random.nextFloat() * 0.3F);
            if (level instanceof ServerLevel sl) GoreHandler.fx(sl, entity.getX(), entity.getY() + 0.3D, entity.getZ(), GoreFxPacket.BLEED, 4.0F);
            le.invulnerableTime = 0;
            le.hurt(ModDamageTypes.source(level, ModDamageTypes.SPIKES, GoreHandler.creditFor(le)), (float) (5.0D + drop * 12.0D));
        } else if (level.getGameTime() % 15L == 0L) {
            le.hurt(ModDamageTypes.source(level, ModDamageTypes.SPIKES, GoreHandler.creditFor(le)), 2.0F);
        }
    }

    @Override
    public void fallOn(Level level, BlockState state, BlockPos pos, Entity entity, float fallDistance) {
        if (entity instanceof LivingEntity le && !(entity instanceof Player) && !level.isClientSide) {
            float dmg = 4.0F + fallDistance * 3.0F;
            level.playSound(null, pos, ModSounds.SPIKE_IMPALE.get(), SoundSource.HOSTILE, 1.2F, 0.9F + level.random.nextFloat() * 0.2F);
            if (level instanceof ServerLevel sl) GoreHandler.fx(sl, entity.getX(), entity.getY() + 0.3D, entity.getZ(), GoreFxPacket.BLEED, 3.0F);
            le.hurt(ModDamageTypes.source(level, ModDamageTypes.SPIKES, GoreHandler.creditFor(le)), dmg);
            return;
        }
        super.fallOn(level, state, pos, entity, fallDistance);
    }

    @Override
    public BlockPathTypes getBlockPathType(BlockState state, BlockGetter level, BlockPos pos, @Nullable Mob mob) {
        return BlockPathTypes.DAMAGE_OTHER;
    }
}
