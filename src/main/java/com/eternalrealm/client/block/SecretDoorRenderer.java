package com.eternalrealm.client.block;

import com.eternalrealm.block.forge.SecretDoorBlock;
import com.eternalrealm.block.forge.SecretDoorBlockEntity;
import com.eternalrealm.world.town.SecretDoorMotion;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.client.RenderTypeHelper;
import net.minecraftforge.client.model.data.ModelData;

/**
 * The sliding panel of a secret door while it moves (the closed door is drawn with the chunk like any wall block). Each
 * half draws itself with the wall's own block model, lit and shaded from the world like the chunk does it, so the panel
 * looks exactly like the wall around it: it sinks a pixel back, then slides one block sideways into the wall (towards
 * the side with solid wall, the right as seen from the front if both have). A hair smaller than a block, so where the
 * chunk already shows the shut door again, the chunk wins and nothing flickers.
 */
public class SecretDoorRenderer implements BlockEntityRenderer<SecretDoorBlockEntity> {
    /** Ticks the shut panel is still drawn after the door closed, until the chunk shows the wall again. */
    private static final double GRACE_TICKS = 8.0;
    private static final float INSET = 0.0025F;

    private final RandomSource random = RandomSource.create();

    public SecretDoorRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(SecretDoorBlockEntity door, float partialTick, PoseStack pose, MultiBufferSource buffers,
                       int packedLight, int packedOverlay) {
        Level level = door.getLevel();
        BlockState state = door.getBlockState();
        if (level == null || !(state.getBlock() instanceof SecretDoorBlock)) {
            return;
        }
        boolean open = state.getValue(SecretDoorBlock.OPEN);
        double now = level.getGameTime() + partialTick;
        double progress = SecretDoorMotion.progress(door.anim(), door.start(), now, open);
        boolean draw = open ? progress < 1.0
                : door.anim() == SecretDoorMotion.IDLE && now - door.start() < GRACE_TICKS;
        if (!draw) {
            return;
        }
        if (!open) {
            progress = 0.0;
        }
        BlockPos pos = door.getBlockPos();
        Direction facing = state.getValue(SecretDoorBlock.FACING);
        if (door.clientSide == 0) {
            door.clientSide = chooseSide(level, SecretDoorBlock.lowerOf(state, pos), facing);
        }
        Direction right = facing.getCounterClockWise();
        double recess = SecretDoorMotion.recess(progress);
        double slide = SecretDoorMotion.slide(progress) * door.clientSide;
        pose.pushPose();
        pose.translate(-facing.getStepX() * recess + right.getStepX() * slide, 0.0,
                -facing.getStepZ() * recess + right.getStepZ() * slide);
        pose.translate(0.5F, 0.5F, 0.5F);
        pose.scale(1.0F - 2.0F * INSET, 1.0F - 2.0F * INSET, 1.0F - 2.0F * INSET);
        pose.translate(-0.5F, -0.5F, -0.5F);
        BlockRenderDispatcher blocks = Minecraft.getInstance().getBlockRenderer();
        BakedModel model = blocks.getBlockModel(state);
        long seed = state.getSeed(pos);
        random.setSeed(seed);
        for (RenderType type : model.getRenderTypes(state, random, ModelData.EMPTY)) {
            blocks.getModelRenderer().tesselateBlock(level, model, state, pos, pose,
                    buffers.getBuffer(RenderTypeHelper.getMovingBlockRenderType(type)), false, random, seed,
                    OverlayTexture.NO_OVERLAY, ModelData.EMPTY, type);
        }
        pose.popPose();
    }

    /** The side with solid wall on both levels to slide into (the right if both or neither have). */
    private static int chooseSide(Level level, BlockPos lower, Direction facing) {
        Direction right = facing.getCounterClockWise();
        Direction left = right.getOpposite();
        boolean rightSolid = solid(level, lower.relative(right)) && solid(level, lower.above().relative(right));
        boolean leftSolid = solid(level, lower.relative(left)) && solid(level, lower.above().relative(left));
        return SecretDoorMotion.slideSide(rightSolid, leftSolid);
    }

    private static boolean solid(Level level, BlockPos pos) {
        return level.getBlockState(pos).isSolidRender(level, pos);
    }

    @Override
    public int getViewDistance() {
        return 64;
    }
}
