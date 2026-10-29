package com.huntcraft.client.render;

import com.huntcraft.HuntCraft;
import com.huntcraft.entity.misc.TrapEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/** Draws a placed trap as a textured disc on the ground plus a small raised frame. */
@OnlyIn(Dist.CLIENT)
public class TrapRenderer extends EntityRenderer<TrapEntity> {
    private static final ResourceLocation PITFALL = HuntCraft.id("textures/entity/trap_pitfall.png");
    private static final ResourceLocation SHOCK = HuntCraft.id("textures/entity/trap_shock.png");

    public TrapRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
    }

    @Override
    public void render(TrapEntity e, float yaw, float partialTick, PoseStack ps, MultiBufferSource buffers, int light) {
        ps.pushPose();
        float size = 1.8F;
        PoseStack.Pose pose = ps.last();
        Matrix4f m = pose.pose();
        Matrix3f n = pose.normal();
        VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(getTextureLocation(e)));
        float y = 0.02F;
        float h = size / 2;
        int l = e.getKind() == TrapEntity.TrapKind.SHOCK && (e.tickCount / 4) % 2 == 0 ? 0xF000F0 : light;
        quad(vc, m, n, l, -h, y, -h, h, y, h);
        ps.popPose();
        super.render(e, yaw, partialTick, ps, buffers, light);
    }

    private static void quad(VertexConsumer vc, Matrix4f m, Matrix3f n, int light, float x0, float y, float z0, float x1, float y1, float z1) {
        vc.vertex(m, x0, y, z0).color(255, 255, 255, 255).uv(0, 0).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(n, 0, 1, 0).endVertex();
        vc.vertex(m, x0, y, z1).color(255, 255, 255, 255).uv(0, 1).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(n, 0, 1, 0).endVertex();
        vc.vertex(m, x1, y, z1).color(255, 255, 255, 255).uv(1, 1).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(n, 0, 1, 0).endVertex();
        vc.vertex(m, x1, y, z0).color(255, 255, 255, 255).uv(1, 0).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(n, 0, 1, 0).endVertex();
    }

    @Override
    public ResourceLocation getTextureLocation(TrapEntity e) {
        return e.getKind() == TrapEntity.TrapKind.SHOCK ? SHOCK : PITFALL;
    }
}
