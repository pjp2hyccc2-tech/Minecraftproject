package com.nihonisshu.tabi.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.math.Axis;
import com.nihonisshu.tabi.data.CarDesign;
import com.nihonisshu.tabi.entity.VehicleEntity;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.client.model.data.ModelData;

import java.util.HashMap;
import java.util.Map;

/** Draws every car of a train (or a bus) from its voxel design using normal block models. */
public class VehicleRenderer extends EntityRenderer<VehicleEntity> {
    private final BlockRenderDispatcher blocks;
    private final Map<String, BlockState[]> palettes = new HashMap<>();

    public VehicleRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.blocks = ctx.getBlockRenderDispatcher();
        this.shadowRadius = 0f;
    }

    private BlockState[] palette(CarDesign d) {
        return palettes.computeIfAbsent(d.id, k -> {
            BlockState[] out = new BlockState[d.palette.size()];
            for (int i = 0; i < out.length; i++) {
                try {
                    out[i] = BlockStateParser.parseForBlock(BuiltInRegistries.BLOCK.asLookup(), d.palette.get(i), false).blockState();
                } catch (CommandSyntaxException e) {
                    out[i] = Blocks.WHITE_CONCRETE.defaultBlockState();
                }
            }
            return out;
        });
    }

    @Override
    public void render(VehicleEntity e, float yaw, float pt, PoseStack ps, MultiBufferSource buffers, int light) {
        CarDesign d = e.design();
        if (d == null) return;
        BlockState[] pal = palette(d);
        double s = Mth.lerp(pt, e.prevS, e.curS);
        double bx = Mth.lerp(pt, e.xo, e.getX()), by = Mth.lerp(pt, e.yo, e.getY()), bz = Mth.lerp(pt, e.zo, e.getZ());
        boolean open = e.doorsOpen();
        for (int i = 0; i < e.carCount(); i++) {
            VehicleEntity.CarPose p = e.carPose(s, i);
            ps.pushPose();
            ps.translate(p.center.x - bx, p.center.y - by, p.center.z - bz);
            ps.mulPose(Axis.YP.rotation((float) p.yaw));
            ps.mulPose(Axis.XP.rotation((float) -p.pitch));
            float sc = (float) d.scale;
            ps.scale(sc, sc, sc);
            int packed = LevelRenderer.getLightColor(e.level(), BlockPos.containing(p.center.x, p.center.y + 2.5, p.center.z));
            int carLight = LightTexture.pack(Math.max(LightTexture.block(packed), 11), LightTexture.sky(packed));
            for (double[] b : d.blocks) {
                if (open && b[4] == 1) continue;
                BlockState st = pal[(int) b[3]];
                ps.pushPose();
                ps.translate(b[0], b[1], b[2]);
                blocks.renderSingleBlock(st, ps, buffers, carLight, OverlayTexture.NO_OVERLAY, ModelData.EMPTY, null);
                ps.popPose();
            }
            ps.popPose();
        }
    }

    @Override
    public ResourceLocation getTextureLocation(VehicleEntity e) {
        return InventoryMenu.BLOCK_ATLAS;
    }
}
