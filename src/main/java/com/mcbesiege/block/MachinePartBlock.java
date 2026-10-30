package com.mcbesiege.block;

import com.mcbesiege.machine.MachineManager;
import com.mcbesiege.part.Orient;
import com.mcbesiege.part.PartType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** One block class for every machine part; behaviour comes from {@link PartType}. */
public class MachinePartBlock extends Block implements EntityBlock {
    public enum SimPart implements StringRepresentable {
        FULL("full"), BASE("base"), HEAD("head");

        private final String name;

        SimPart(String name) { this.name = name; }

        @Override
        public String getSerializedName() { return name; }
    }

    public static final DirectionProperty FACING = BlockStateProperties.FACING;
    public static final BooleanProperty TWIST = BooleanProperty.create("twist");
    public static final EnumProperty<SimPart> SIM_PART = EnumProperty.create("sim_part", SimPart.class);

    public final PartType type;
    private final VoxelShape[] shapes = new VoxelShape[12];

    public MachinePartBlock(PartType type, Properties props) {
        super(props);
        this.type = type;
        registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.UP).setValue(TWIST, false).setValue(SIM_PART, SimPart.FULL));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, TWIST, SIM_PART);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        Direction facing = type.place == PartType.Place.PLAYER_H ? ctx.getHorizontalDirection() : ctx.getClickedFace();
        boolean twist = false;
        if (type.twistable && facing.getAxis().isVertical()) twist = ctx.getHorizontalDirection().getAxis() == Direction.Axis.X;
        return defaultBlockState().setValue(FACING, facing).setValue(TWIST, twist);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        int idx = state.getValue(FACING).get3DDataValue() * 2 + (state.getValue(TWIST) ? 1 : 0);
        VoxelShape s = shapes[idx];
        if (s == null) {
            s = buildShape(state.getValue(FACING), state.getValue(TWIST));
            shapes[idx] = s;
        }
        return s;
    }

    private VoxelShape buildShape(Direction f, boolean twist) {
        if (type.headShape != PartType.HeadShape.BOX || type.sphereRadius > 0) return Shapes.block();
        VoxelShape s = box(type, type.baseBox, f, twist);
        if (type.headBox != null) s = Shapes.or(s, box(type, type.headBox, f, twist));
        return s;
    }

    private static VoxelShape box(PartType t, double[] px, Direction f, boolean twist) {
        double[] b = Orient.box(t, px, f, twist);
        double x0 = clamp(b[0]), y0 = clamp(b[1]), z0 = clamp(b[2]), x1 = clamp(b[3]), y1 = clamp(b[4]), z1 = clamp(b[5]);
        if (x1 - x0 < 0.01 || y1 - y0 < 0.01 || z1 - z0 < 0.01) return Shapes.block();
        return Shapes.box(x0, y0, z0, x1, y1, z1);
    }

    private static double clamp(double v) { return Math.max(0, Math.min(1, v)); }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (type == PartType.STARTING_BLOCK && player.getItemInHand(hand).isEmpty()) {
            if (!level.isClientSide && player instanceof ServerPlayer sp) {
                MachineManager.startSimulation((ServerLevel) level, pos, sp);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        return InteractionResult.PASS;
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rot) {
        return state.setValue(FACING, rot.rotate(state.getValue(FACING)));
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new MachinePartBlockEntity(pos, state);
    }
}
