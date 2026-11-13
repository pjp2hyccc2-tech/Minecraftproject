package com.eternalrealm.client.block;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BannerRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BannerBlockEntity;

/**
 * The game's banners, drawn by the game's own renderer, but hanging still when the waving of flags is switched off
 * ({@link Scenery#flagsWave()}; the capital has about four hundred of them). The game sways a banner by a phase of
 * (its position's offset + the game time + the partial tick) / 100 of a cycle; a partial tick that cancels the time
 * holds each banner at the angle it has at time 0, so a row of banners still hangs at slightly different angles.
 */
public class StillBannerRenderer implements BlockEntityRenderer<BannerBlockEntity> {
    private final BannerRenderer banner;

    public StillBannerRenderer(BlockEntityRendererProvider.Context context) {
        this.banner = new BannerRenderer(context);
    }

    @Override
    public void render(BannerBlockEntity entity, float partialTick, PoseStack pose, MultiBufferSource buffers,
                       int packedLight, int packedOverlay) {
        Level level = entity.getLevel();
        if (level == null || Scenery.flagsWave()) {
            banner.render(entity, partialTick, pose, buffers, packedLight, packedOverlay);
            return;
        }
        BlockPos pos = entity.getBlockPos();
        long offset = pos.getX() * 7 + pos.getY() * 9 + pos.getZ() * 13;
        float still = (float) (Math.floorMod(offset, 100L) - Math.floorMod(offset + level.getGameTime(), 100L));
        banner.render(entity, still, pose, buffers, packedLight, packedOverlay);
    }

    @Override
    public boolean shouldRenderOffScreen(BannerBlockEntity entity) {
        return banner.shouldRenderOffScreen(entity);
    }

    @Override
    public int getViewDistance() {
        return banner.getViewDistance();
    }
}
