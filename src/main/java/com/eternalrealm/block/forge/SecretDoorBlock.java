package com.eternalrealm.block.forge;

import com.eternalrealm.world.town.SecretDoorMotion;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * A two-high secret door that looks exactly like the wall it is set in ({@code look}: the block model of that wall is
 * used as is, so it is lit, shaded and textured like its neighbours). Using either half makes the panel sink a pixel
 * back and slide one block sideways into the wall with a stone-grinding sound; it is passable while open and slides
 * shut five seconds later once nobody stands in the doorway (someone stepping in while it closes opens it again).
 * Both halves always agree; removing one removes both. {@code facing} is the side it is found from (its front), the
 * passage lies behind. It drops nothing (creative tab only).
 *
 * <p>The closed door is an ordinary full block drawn with the chunk; while it moves or stands open the block has no
 * shape and the client draws the sliding panel ({@code SecretDoorRenderer}). The server drives the motion with
 * scheduled ticks on the lower half and keeps the times in {@link SecretDoorBlockEntity}.
 */
public class SecretDoorBlock extends Block implements EntityBlock {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final EnumProperty<DoubleBlockHalf> HALF = BlockStateProperties.DOUBLE_BLOCK_HALF;
    public static final BooleanProperty OPEN = BlockStateProperties.OPEN;
    public static final EnumProperty<Look> LOOK = EnumProperty.create("look", Look.class);

    /** The walls a secret door can hide in. */
    public enum Look implements StringRepresentable {
        BOOKSHELF("bookshelf", MapColor.WOOD, SoundType.WOOD),
        STONE_BRICKS("stone_bricks", MapColor.STONE, SoundType.STONE),
        CASTLE_BRICKS("castle_bricks", MapColor.QUARTZ, SoundType.STONE),
        WHITE_PLASTER("white_plaster", MapColor.SNOW, SoundType.CALCITE),
        MOSSY_STONE_BRICKS("mossy_stone_bricks", MapColor.STONE, SoundType.STONE),
        DARK_OAK_PLANKS("dark_oak_planks", MapColor.COLOR_BROWN, SoundType.WOOD),
        SPRUCE_PLANKS("spruce_planks", MapColor.PODZOL, SoundType.WOOD);

        private final String id;
        private final MapColor mapColor;
        private final SoundType sound;

        Look(String id, MapColor mapColor, SoundType sound) {
            this.id = id;
            this.mapColor = mapColor;
            this.sound = sound;
        }

        @Override
        public String getSerializedName() {
            return id;
        }

        public MapColor mapColor() {
            return mapColor;
        }

        public SoundType sound() {
            return sound;
        }

        /** Whether the panel is wood (a lighter scrape than stone). */
        public boolean wooden() {
            return sound == SoundType.WOOD;
        }

        /** The block whose looks the door copies. */
        public Block block() {
            return switch (this) {
                case BOOKSHELF -> Blocks.BOOKSHELF;
                case STONE_BRICKS -> Blocks.STONE_BRICKS;
                case CASTLE_BRICKS -> ERBlocks.CASTLE_BRICKS.get();
                case WHITE_PLASTER -> ERBlocks.WHITE_PLASTER.get();
                case MOSSY_STONE_BRICKS -> Blocks.MOSSY_STONE_BRICKS;
                case DARK_OAK_PLANKS -> Blocks.DARK_OAK_PLANKS;
                case SPRUCE_PLANKS -> Blocks.SPRUCE_PLANKS;
            };
        }

        /** The look of a wall block, or null if a secret door cannot copy it. */
        @Nullable
        public static Look of(Block block) {
            for (Look look : values()) {
                if (look.block() == block) {
                    return look;
                }
            }
            return null;
        }
    }

    public SecretDoorBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH)
                .setValue(HALF, DoubleBlockHalf.LOWER).setValue(OPEN, false).setValue(LOOK, Look.STONE_BRICKS));
    }

    /** Map colour of the wall it copies (see {@code TownGimmickBlocks}). */
    static MapColor mapColor(BlockState state) {
        return state.getValue(LOOK).mapColor();
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, HALF, OPEN, LOOK);
    }

    /** The lower half of the door one of whose halves is at {@code pos}. */
    public static BlockPos lowerOf(BlockState state, BlockPos pos) {
        return state.getValue(HALF) == DoubleBlockHalf.LOWER ? pos : pos.below();
    }

    // ------------------------------------------------------------------------------------------ shape and looks

    @Override
    @SuppressWarnings("deprecation")
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(OPEN) ? Shapes.empty() : Shapes.block();
    }

    @Override
    @SuppressWarnings("deprecation")
    public RenderShape getRenderShape(BlockState state) {
        return state.getValue(OPEN) ? RenderShape.ENTITYBLOCK_ANIMATED : RenderShape.MODEL;
    }

    @Override
    @SuppressWarnings("deprecation")
    public SoundType getSoundType(BlockState state) {
        return state.getValue(LOOK).sound();
    }

    @Override
    @SuppressWarnings("deprecation")
    public boolean isPathfindable(BlockState state, BlockGetter level, BlockPos pos, PathComputationType type) {
        return type != PathComputationType.WATER && state.getValue(OPEN);
    }

    // ------------------------------------------------------------------------------------------ the two halves

    @Override
    @Nullable
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockPos pos = context.getClickedPos();
        Level level = context.getLevel();
        if (pos.getY() >= level.getMaxBuildHeight() - 1 || !level.getBlockState(pos.above()).canBeReplaced(context)) {
            return null;
        }
        Direction facing = context.getHorizontalDirection().getOpposite();
        Direction right = facing.getCounterClockWise();
        // copy the wall it is set into: the blocks beside the doorway, else the block clicked on
        BlockPos[] around = {pos.relative(right), pos.relative(right.getOpposite()), pos.above().relative(right),
                pos.above().relative(right.getOpposite()), pos.relative(context.getClickedFace().getOpposite())};
        Look look = Look.STONE_BRICKS;
        for (BlockPos at : around) {
            Look found = Look.of(level.getBlockState(at).getBlock());
            if (found != null) {
                look = found;
                break;
            }
        }
        return defaultBlockState().setValue(FACING, facing).setValue(HALF, DoubleBlockHalf.LOWER)
                .setValue(OPEN, false).setValue(LOOK, look);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        level.setBlock(pos.above(), state.setValue(HALF, DoubleBlockHalf.UPPER), Block.UPDATE_ALL);
    }

    @Override
    @SuppressWarnings("deprecation")
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighbour, LevelAccessor level,
                                  BlockPos pos, BlockPos neighbourPos) {
        DoubleBlockHalf half = state.getValue(HALF);
        if (direction.getAxis() == Direction.Axis.Y && (half == DoubleBlockHalf.LOWER) == (direction == Direction.UP)) {
            // the other half: follow it, or vanish with it
            return neighbour.is(this) && neighbour.getValue(HALF) != half
                    ? state.setValue(FACING, neighbour.getValue(FACING)).setValue(OPEN, neighbour.getValue(OPEN))
                    .setValue(LOOK, neighbour.getValue(LOOK))
                    : Blocks.AIR.defaultBlockState();
        }
        return super.updateShape(state, direction, neighbour, level, pos, neighbourPos);
    }

    @Override
    @Nullable
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SecretDoorBlockEntity(pos, state);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState old, boolean moving) {
        super.onPlace(state, level, pos, old, moving);
        // a door placed standing open (by a structure or the capital data) closes by itself like any other
        if (!level.isClientSide && !old.is(this) && state.getValue(HALF) == DoubleBlockHalf.LOWER
                && state.getValue(OPEN)) {
            level.scheduleTick(pos, this, SecretDoorMotion.STAY_OPEN_TICKS);
        }
    }

    // ------------------------------------------------------------------------------------------ opening and closing

    @Override
    @SuppressWarnings("deprecation")
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
                                 BlockHitResult hit) {
        if (state.getValue(OPEN)) {
            return InteractionResult.PASS;
        }
        if (level instanceof ServerLevel server && !open(server, lowerOf(state, pos), player)) {
            return InteractionResult.PASS;
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /**
     * Opens the closed door whose lower half is at {@code lower} (the panel starts to slide; the doorway is free at
     * once). Returns false if there is no complete closed door there.
     */
    public boolean open(ServerLevel level, BlockPos lower, @Nullable Entity who) {
        BlockState bottom = level.getBlockState(lower);
        BlockState top = level.getBlockState(lower.above());
        if (!bottom.is(this) || !top.is(this) || bottom.getValue(HALF) != DoubleBlockHalf.LOWER
                || top.getValue(HALF) != DoubleBlockHalf.UPPER || bottom.getValue(OPEN)) {
            return false;
        }
        long now = level.getGameTime();
        animate(level, lower, SecretDoorMotion.OPENING, now);      // the motion first, so clients never see a gap
        setOpen(level, lower, true);
        grind(level, lower, bottom, 1.0F);
        level.gameEvent(who, GameEvent.BLOCK_OPEN, lower);
        level.scheduleTick(lower, this, SecretDoorMotion.WATCH_TICKS);
        return true;
    }

    @Override
    @SuppressWarnings("deprecation")
    public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (state.getValue(HALF) != DoubleBlockHalf.LOWER
                || !(level.getBlockEntity(pos) instanceof SecretDoorBlockEntity door)) {
            return;
        }
        long now = level.getGameTime();
        switch (door.anim()) {
            case SecretDoorMotion.OPENING -> {
                if (!SecretDoorMotion.finished(door.start(), now)) {
                    dust(level, pos, state, random);
                    level.scheduleTick(pos, this, SecretDoorMotion.WATCH_TICKS);
                    return;
                }
                animate(level, pos, SecretDoorMotion.IDLE, now);
                thud(level, pos, state);
                level.scheduleTick(pos, this, SecretDoorMotion.STAY_OPEN_TICKS);
            }
            case SecretDoorMotion.CLOSING -> {
                if (occupied(level, pos)) {
                    // someone stepped in: open again from where the panel is
                    animate(level, pos, SecretDoorMotion.OPENING, SecretDoorMotion.reversedStart(door.start(), now));
                    grind(level, pos, state, 0.6F);
                    level.scheduleTick(pos, this, SecretDoorMotion.WATCH_TICKS);
                    return;
                }
                if (!SecretDoorMotion.finished(door.start(), now)) {
                    dust(level, pos, state, random);
                    level.scheduleTick(pos, this, SecretDoorMotion.WATCH_TICKS);
                    return;
                }
                animate(level, pos, SecretDoorMotion.IDLE, now);
                setOpen(level, pos, false);
                thud(level, pos, state);
                level.gameEvent((Entity) null, GameEvent.BLOCK_CLOSE, pos);
            }
            default -> {
                if (!state.getValue(OPEN)) {
                    return;
                }
                if (occupied(level, pos)) {
                    level.scheduleTick(pos, this, SecretDoorMotion.RETRY_TICKS);
                    return;
                }
                animate(level, pos, SecretDoorMotion.CLOSING, now);
                grind(level, pos, state, 1.0F);
                level.scheduleTick(pos, this, SecretDoorMotion.WATCH_TICKS);
            }
        }
    }

    /** Whether someone (a player, a townsperson, any creature) stands in the doorway. */
    public static boolean occupied(Level level, BlockPos lower) {
        AABB doorway = new AABB(lower.getX() + 0.12, lower.getY(), lower.getZ() + 0.12, lower.getX() + 0.88,
                lower.getY() + 2.0, lower.getZ() + 0.88);
        return !level.getEntitiesOfClass(LivingEntity.class, doorway, e -> e.isAlive() && !e.isSpectator()).isEmpty();
    }

    private void setOpen(ServerLevel level, BlockPos lower, boolean open) {
        for (BlockPos at : new BlockPos[]{lower, lower.above()}) {
            BlockState state = level.getBlockState(at);
            if (state.is(this) && state.getValue(OPEN) != open) {
                level.setBlock(at, state.setValue(OPEN, open), Block.UPDATE_ALL);
            }
        }
    }

    private static void animate(ServerLevel level, BlockPos lower, int anim, long start) {
        for (BlockPos at : new BlockPos[]{lower, lower.above()}) {
            if (level.getBlockEntity(at) instanceof SecretDoorBlockEntity door) {
                door.setAnim(anim, start);
            }
        }
    }

    /** The heavy grinding of the panel starting to move. */
    private static void grind(ServerLevel level, BlockPos lower, BlockState state, float volume) {
        Look look = state.getValue(LOOK);
        double x = lower.getX() + 0.5;
        double y = lower.getY() + 1.0;
        double z = lower.getZ() + 0.5;
        level.playSound(null, x, y, z, SoundEvents.GRINDSTONE_USE, SoundSource.BLOCKS, 0.85F * volume,
                look.wooden() ? 0.72F : 0.5F);
        level.playSound(null, x, y, z, SoundEvents.PISTON_CONTRACT, SoundSource.BLOCKS, 0.3F * volume, 0.5F);
        level.sendParticles(new BlockParticleOption(ParticleTypes.FALLING_DUST, look.block().defaultBlockState()),
                x, y + 0.95, z, 6, 0.3, 0.02, 0.3, 0.0);
    }

    /** Grit trickling from the seams while it moves. */
    private static void dust(ServerLevel level, BlockPos lower, BlockState state, RandomSource random) {
        Direction facing = state.getValue(FACING);
        double x = lower.getX() + 0.5 + facing.getStepX() * 0.52;
        double z = lower.getZ() + 0.5 + facing.getStepZ() * 0.52;
        level.sendParticles(new BlockParticleOption(ParticleTypes.FALLING_DUST,
                        state.getValue(LOOK).block().defaultBlockState()), x, lower.getY() + 0.3 + random.nextDouble() * 1.6,
                z, 2, Math.abs(facing.getStepZ()) * 0.45, 0.2, Math.abs(facing.getStepX()) * 0.45, 0.0);
    }

    /** The panel coming to rest. */
    private static void thud(ServerLevel level, BlockPos lower, BlockState state) {
        SoundType sound = state.getValue(LOOK).sound();
        level.playSound(null, lower.getX() + 0.5, lower.getY() + 1.0, lower.getZ() + 0.5, sound.getPlaceSound(),
                SoundSource.BLOCKS, 0.75F, 0.62F);
    }
}
