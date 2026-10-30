package com.nihonisshu.tabi.block;

import com.nihonisshu.tabi.blockentity.TextPanelBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.Map;

/**
 * Signboards whose text (Japanese) is drawn by a block entity renderer: station name boards, shop signs, info boards.
 * Geometry is described in the NORTH orientation (front face looks north = -Z): frontDepth/backDepth are the pixel
 * z-positions of the front and back faces, textCenterY/textHeight the text area in block units.
 */
public class TextPanelBlock extends FacingBlock implements EntityBlock {
    private final double frontDepth, backDepth;
    private final float textCenterY, textHeight;

    public TextPanelBlock(Properties props, Map<Direction, VoxelShape> shapes, boolean noCollision,
                          double frontDepth, double backDepth, float textCenterY, float textHeight) {
        super(props, shapes, noCollision);
        this.frontDepth = frontDepth;
        this.backDepth = backDepth;
        this.textCenterY = textCenterY;
        this.textHeight = textHeight;
    }

    public TextPanelBlock(Properties props, Map<Direction, VoxelShape> shapes, boolean noCollision) {
        this(props, shapes, noCollision, 7, 9, 0.5f, 0.8f);
    }

    public double frontDepth() { return frontDepth; }
    public double backDepth() { return backDepth; }
    public float textCenterY() { return textCenterY; }
    public float textHeight() { return textHeight; }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TextPanelBlockEntity(pos, state);
    }
}
