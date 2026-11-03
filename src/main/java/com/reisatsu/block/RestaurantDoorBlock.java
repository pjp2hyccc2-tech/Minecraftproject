package com.reisatsu.block;

import com.reisatsu.registry.ModSounds;
import com.reisatsu.world.FixtureEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockSetType;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nullable;

/**
 * The hinged doors of the restaurant (staff, office, restrooms, back door, walk-in fridge, toilet stalls). Opening
 * one is a noise the spirits hear (8 blocks, 3 when sneaking; the heavy fridge door 12). An open door leaves the
 * whole doorway free so the spirits never snag on the leaf.
 */
public class RestaurantDoorBlock extends DoorBlock {
    public enum Kind { STAFF, OFFICE, RESTROOM, BACK, FRIDGE, STALL }

    public final Kind kind;

    public RestaurantDoorBlock(Properties properties, BlockSetType type, Kind kind) {
        super(properties, type);
        this.kind = kind;
    }

    @Override
    @SuppressWarnings("deprecation")
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
        if (!canPlayerOpen(state, level, pos, player)) return InteractionResult.sidedSuccess(level.isClientSide);
        state = state.cycle(OPEN);
        level.setBlock(pos, state, 10);
        boolean open = state.getValue(OPEN);
        playDoorSound(level, pos, open, player.isShiftKeyDown());
        level.gameEvent(player, open ? GameEvent.BLOCK_OPEN : GameEvent.BLOCK_CLOSE, pos);
        if (level instanceof ServerLevel sl) {
            float radius = kind == Kind.FRIDGE ? 12.0f : 8.0f;
            if (player.isShiftKeyDown()) radius = 3.0f;
            FixtureEvents.LISTENER.noise(sl, Vec3.atCenterOf(pos), radius, kind == Kind.FRIDGE ? "fridge_door" : "door", player);
            if (kind == Kind.FRIDGE && player instanceof ServerPlayer sp) FixtureEvents.LISTENER.interact(sp, "fridge_door", pos);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /** Locks; called on both sides. */
    protected boolean canPlayerOpen(BlockState state, Level level, BlockPos pos, Player player) {
        return true;
    }

    @Override
    public void setOpen(@Nullable Entity entity, Level level, BlockState state, BlockPos pos, boolean open) {
        if (state.is(this) && state.getValue(OPEN) != open) {
            level.setBlock(pos, state.setValue(OPEN, open), 10);
            playDoorSound(level, pos, open, false);
            level.gameEvent(entity, open ? GameEvent.BLOCK_OPEN : GameEvent.BLOCK_CLOSE, pos);
            if (level instanceof ServerLevel sl) FixtureEvents.LISTENER.noise(sl, Vec3.atCenterOf(pos), 8.0f, "door", entity);
        }
    }

    /** Opens or shuts both halves without a player (reset, scripted events). */
    public static void setBoth(Level level, BlockPos lower, boolean open, boolean sound) {
        BlockState s = level.getBlockState(lower);
        if (!(s.getBlock() instanceof RestaurantDoorBlock door)) return;
        if (s.getValue(HALF) == DoubleBlockHalf.UPPER) {
            lower = lower.below();
            s = level.getBlockState(lower);
            if (!(s.getBlock() instanceof RestaurantDoorBlock)) return;
        }
        if (s.getValue(OPEN) == open) return;
        level.setBlock(lower, s.setValue(OPEN, open), 10);
        BlockState up = level.getBlockState(lower.above());
        if (up.is(s.getBlock())) level.setBlock(lower.above(), up.setValue(OPEN, open), 10);
        if (sound) door.playDoorSound(level, lower, open, false);
    }

    protected void playDoorSound(Level level, BlockPos pos, boolean open, boolean careful) {
        SoundEvent ev = switch (kind) {
            case FRIDGE -> open ? ModSounds.DOOR_FRIDGE_OPEN.get() : ModSounds.DOOR_FRIDGE_CLOSE.get();
            case STALL -> open || careful ? ModSounds.DOOR_OPEN.get() : ModSounds.DOOR_STALL_BANG.get();
            default -> open ? ModSounds.DOOR_OPEN.get() : ModSounds.DOOR_CLOSE.get();
        };
        float vol = careful ? 0.35f : 0.9f;
        float pitch = (open ? 1.0f : 0.9f) + level.getRandom().nextFloat() * 0.08f;
        level.playSound(null, pos, ev, SoundSource.BLOCKS, vol, pitch);
    }

    @Override
    @SuppressWarnings("deprecation")
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        if (state.getValue(OPEN)) return Shapes.empty();
        return super.getCollisionShape(state, level, pos, ctx);
    }

    @Override
    @SuppressWarnings("deprecation")
    public VoxelShape getVisualShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return getShape(state, level, pos, ctx);
    }

    // ------------------------------------------------------------------------------------------------------------

    /** The office door (office_key) and the back door (shop_key): locked until the game says the player may open them. */
    public static class Locked extends RestaurantDoorBlock {
        private final String lock;

        public Locked(Properties properties, BlockSetType type, Kind kind, String lock) {
            super(properties, type, kind);
            this.lock = lock;
            registerDefaultState(defaultBlockState().setValue(Props.LOCKED, true));
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
            super.createBlockStateDefinition(builder);
            builder.add(Props.LOCKED);
        }

        @Override
        protected boolean canPlayerOpen(BlockState state, Level level, BlockPos pos, Player player) {
            if (!state.getValue(Props.LOCKED)) return true;
            if (level.isClientSide) return false;
            if (player instanceof ServerPlayer sp && FixtureEvents.LISTENER.canUnlock(sp, lock)) {
                setLocked(level, pos, false);
                level.playSound(null, pos, ModSounds.DOOR_UNLOCK.get(), SoundSource.BLOCKS, 0.8f, 1.0f);
                return true;
            }
            level.playSound(null, pos, ModSounds.DOOR_LOCKED.get(), SoundSource.BLOCKS, 0.8f, 1.0f);
            if (level instanceof ServerLevel sl) FixtureEvents.LISTENER.noise(sl, Vec3.atCenterOf(pos), 4.0f, "door_locked", player);
            return false;
        }

        public static void setLocked(Level level, BlockPos pos, boolean locked) {
            BlockState s = level.getBlockState(pos);
            if (!s.hasProperty(Props.LOCKED)) return;
            BlockPos other = s.getValue(HALF) == DoubleBlockHalf.LOWER ? pos.above() : pos.below();
            level.setBlock(pos, s.setValue(Props.LOCKED, locked), 10);
            BlockState o = level.getBlockState(other);
            if (o.is(s.getBlock())) level.setBlock(other, o.setValue(Props.LOCKED, locked), 10);
        }
    }

    /** A toilet stall door: occupied = the red 使用中 indicator, locked for everyone. */
    public static class Stall extends RestaurantDoorBlock {
        public Stall(Properties properties, BlockSetType type) {
            super(properties, type, Kind.STALL);
            registerDefaultState(defaultBlockState().setValue(Props.OCCUPIED, false));
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
            super.createBlockStateDefinition(builder);
            builder.add(Props.OCCUPIED);
        }

        @Override
        protected boolean canPlayerOpen(BlockState state, Level level, BlockPos pos, Player player) {
            if (!state.getValue(Props.OCCUPIED)) return true;
            if (!level.isClientSide) {
                level.playSound(null, pos, ModSounds.DOOR_LOCKED.get(), SoundSource.BLOCKS, 0.8f, 1.0f);
                if (level instanceof ServerLevel sl) FixtureEvents.LISTENER.noise(sl, Vec3.atCenterOf(pos), 4.0f, "door_locked", player);
            }
            return false;
        }

        @Override
        public void setOpen(@Nullable Entity entity, Level level, BlockState state, BlockPos pos, boolean open) {
            if (state.is(this) && state.getValue(Props.OCCUPIED) && open) return;
            super.setOpen(entity, level, state, pos, open);
        }

        public static void setOccupied(Level level, BlockPos pos, boolean occupied) {
            BlockState s = level.getBlockState(pos);
            if (!s.hasProperty(Props.OCCUPIED)) return;
            BlockPos lower = s.getValue(HALF) == DoubleBlockHalf.LOWER ? pos : pos.below();
            for (BlockPos p : new BlockPos[]{lower, lower.above()}) {
                BlockState o = level.getBlockState(p);
                if (o.hasProperty(Props.OCCUPIED)) {
                    o = o.setValue(Props.OCCUPIED, occupied);
                    if (occupied) o = o.setValue(OPEN, false);
                    level.setBlock(p, o, 10);
                }
            }
        }
    }
}
