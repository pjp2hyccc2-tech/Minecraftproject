package com.hitori.block;

import com.hitori.registry.ModSounds;
import com.hitori.world.FixtureEvents;
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
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
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
 * The hinged doors of コーポ白樺 (docs/DESIGN.md §7): 203's steel front door (peephole: sneak + right-click), the cheap
 * interior doors, the toilet door, the folding glass bath door, and the locked steel doors of the other units.
 * Opening one is a noise (8 blocks, 3 when sneaking). An open door leaves the whole doorway free so nothing snags on
 * the leaf. Opening the front door from inside tells the game ({@link FixtureEvents.Listener#frontDoorOpened}).
 */
public class HitoriDoorBlock extends DoorBlock {
    public enum Kind { FRONT, INTERIOR, TOILET, BATH, UNIT }

    public final Kind kind;

    public HitoriDoorBlock(Properties properties, BlockSetType type, Kind kind) {
        super(properties, type);
        this.kind = kind;
    }

    @Override
    @SuppressWarnings("deprecation")
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
        BlockPos lower = state.getValue(HALF) == DoubleBlockHalf.LOWER ? pos : pos.below();
        if (kind == Kind.UNIT) {
            if (!level.isClientSide) {
                level.playSound(null, pos, ModSounds.DOOR_LOCKED.get(), SoundSource.BLOCKS, 0.8f, 0.95f + level.random.nextFloat() * 0.1f);
                if (level instanceof ServerLevel sl) FixtureEvents.LISTENER.noise(sl, Vec3.atCenterOf(pos), 4.0f, "door_locked", player);
                if (player instanceof ServerPlayer sp) FixtureEvents.LISTENER.interact(sp, "locked_door", lower);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        if (kind == Kind.FRONT && player.isShiftKeyDown() && !state.getValue(OPEN)) {
            if (player instanceof ServerPlayer sp) FixtureEvents.LISTENER.interact(sp, "peephole", lower);
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        state = state.cycle(OPEN);
        level.setBlock(pos, state, 10);
        boolean open = state.getValue(OPEN);
        playDoorSound(level, pos, open, player.isShiftKeyDown());
        level.gameEvent(player, open ? GameEvent.BLOCK_OPEN : GameEvent.BLOCK_CLOSE, pos);
        if (level instanceof ServerLevel sl) {
            FixtureEvents.LISTENER.noise(sl, Vec3.atCenterOf(pos), player.isShiftKeyDown() ? 3.0f : 8.0f, kind == Kind.FRONT ? "front_door" : "door", player);
            if (player instanceof ServerPlayer sp) {
                FixtureEvents.LISTENER.interact(sp, kind == Kind.FRONT ? "front_door" : "door", lower);
                if (kind == Kind.FRONT && open && insideOf(state, lower, player)) FixtureEvents.LISTENER.frontDoorOpened(sp);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /** True if the player stands on the inner side of the door (the side its closed leaf faces away from). */
    static boolean insideOf(BlockState state, BlockPos lower, Player player) {
        // the front door faces SOUTH (into the apartment): inside is the facing side
        var f = state.getValue(FACING);
        double d = (player.getX() - (lower.getX() + 0.5)) * f.getStepX() + (player.getZ() - (lower.getZ() + 0.5)) * f.getStepZ();
        return d > 0;
    }

    @Override
    public void setOpen(@Nullable Entity entity, Level level, BlockState state, BlockPos pos, boolean open) {
        if (kind == Kind.UNIT) return;
        if (state.is(this) && state.getValue(OPEN) != open) {
            level.setBlock(pos, state.setValue(OPEN, open), 10);
            playDoorSound(level, pos, open, false);
            level.gameEvent(entity, open ? GameEvent.BLOCK_OPEN : GameEvent.BLOCK_CLOSE, pos);
            if (level instanceof ServerLevel sl) FixtureEvents.LISTENER.noise(sl, Vec3.atCenterOf(pos), 8.0f, "door", entity);
        }
    }

    /** Opens or shuts both halves without a player (reset, the game). Returns true if it changed. */
    public static boolean setBoth(Level level, BlockPos pos, boolean open, boolean sound) {
        BlockState s = level.getBlockState(pos);
        if (!(s.getBlock() instanceof HitoriDoorBlock door)) return false;
        BlockPos lower = s.getValue(HALF) == DoubleBlockHalf.UPPER ? pos.below() : pos;
        s = level.getBlockState(lower);
        if (!(s.getBlock() instanceof HitoriDoorBlock) || s.getValue(OPEN) == open) return false;
        level.setBlock(lower, s.setValue(OPEN, open), 10);
        BlockState up = level.getBlockState(lower.above());
        if (up.is(s.getBlock())) level.setBlock(lower.above(), up.setValue(OPEN, open), 10);
        if (sound) door.playDoorSound(level, lower, open, false);
        return true;
    }

    public static boolean isOpen(Level level, BlockPos pos) {
        BlockState s = level.getBlockState(pos);
        return s.getBlock() instanceof HitoriDoorBlock && s.getValue(OPEN);
    }

    protected void playDoorSound(Level level, BlockPos pos, boolean open, boolean careful) {
        SoundEvent ev = switch (kind) {
            case FRONT -> open ? ModSounds.DOOR_FRONT_OPEN.get() : ModSounds.DOOR_FRONT_CLOSE.get();
            case BATH -> ModSounds.DOOR_BATH_FOLD.get();
            default -> open ? ModSounds.DOOR_INTERIOR_OPEN.get() : ModSounds.DOOR_INTERIOR_CLOSE.get();
        };
        float vol = careful ? 0.35f : 0.9f;
        float pitch = (kind == Kind.BATH ? (open ? 1.0f : 0.92f) : 1.0f) + level.getRandom().nextFloat() * 0.06f;
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
        // the frosted bath door hides what is behind it, like every other door
        return getShape(state, level, pos, ctx);
    }
}
