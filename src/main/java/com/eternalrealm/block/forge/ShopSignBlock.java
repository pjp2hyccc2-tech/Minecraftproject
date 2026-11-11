package com.eternalrealm.block.forge;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/**
 * A hanging shop board with a painted picture of the trade ({@code icon}) on a wooden board in an iron frame.
 * {@code attach=wall}: the block is the air cell in front of a wall and {@code facing} points away from the wall; a
 * wrought-iron bracket comes out of the wall along the top of the cell and the board hangs under it, edge-on to the
 * wall so people walking along the street see its face. {@code attach=ceiling}: the board hangs on two short chains
 * from the block above, its face towards {@code facing}. The board swings gently in the wind (drawn by the client,
 * {@code ShopSignRenderer}; the bracket is the block model). In creative mode an operator cycles the picture by using
 * the sign with an empty hand.
 */
public class ShopSignBlock extends HorizontalDirectionalBlock implements EntityBlock {
    public static final EnumProperty<Attach> ATTACH = EnumProperty.create("attach", Attach.class);
    public static final EnumProperty<Icon> ICON = EnumProperty.create("icon", Icon.class);
    // the board and its bracket/chains: wall boards lie in the plane of facing, ceiling boards across it
    private static final VoxelShape WALL_NS = Block.box(7.0, 0.0, 0.0, 9.0, 15.0, 16.0);
    private static final VoxelShape WALL_EW = Block.box(0.0, 0.0, 7.0, 16.0, 15.0, 9.0);
    private static final VoxelShape CEILING_NS = Block.box(0.0, 0.0, 7.0, 16.0, 16.0, 9.0);
    private static final VoxelShape CEILING_EW = Block.box(7.0, 0.0, 0.0, 9.0, 16.0, 16.0);

    /** How the board is hung. */
    public enum Attach implements StringRepresentable {
        WALL,
        CEILING;

        @Override
        public String getSerializedName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /** The trades (textures {@code block/shop_sign/<id>}). */
    public enum Icon implements StringRepresentable {
        SWORD, SHIELD, POTION, BREAD, ANVIL, BOOK, GEM, FLOWER, SHIRT, KEY, FISH, MUG, SCROLL, MAP, HAMMER, RING;

        @Override
        public String getSerializedName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    public ShopSignBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(ATTACH, Attach.WALL)
                .setValue(ICON, Icon.SWORD));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, ATTACH, ICON);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction face = context.getClickedFace();
        if (face.getAxis().isHorizontal()) {
            return defaultBlockState().setValue(FACING, face).setValue(ATTACH, Attach.WALL);
        }
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite())
                .setValue(ATTACH, face == Direction.DOWN ? Attach.CEILING : Attach.WALL);
    }

    @Override
    @SuppressWarnings("deprecation")
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        boolean northSouth = state.getValue(FACING).getAxis() == Direction.Axis.Z;
        if (state.getValue(ATTACH) == Attach.WALL) {
            return northSouth ? WALL_NS : WALL_EW;
        }
        return northSouth ? CEILING_NS : CEILING_EW;
    }

    @Override
    @SuppressWarnings("deprecation")
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) {
        return true;
    }

    @Override
    @SuppressWarnings("deprecation")
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
                                 BlockHitResult hit) {
        if (!player.isCreative() || !player.hasPermissions(2) || !player.getItemInHand(hand).isEmpty()) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide) {
            BlockState next = state.cycle(ICON);
            level.setBlock(pos, next, Block.UPDATE_ALL);
            player.displayClientMessage(Component.translatable("message.eternalrealm.shop_sign.icon",
                    Component.translatable("shop_sign.eternalrealm.icon." + next.getValue(ICON).getSerializedName())),
                    true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    @Nullable
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ShopSignBlockEntity(pos, state);
    }
}
