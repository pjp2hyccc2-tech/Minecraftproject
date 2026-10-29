package com.huntcraft.client.render;

import com.huntcraft.HuntCraft;
import com.huntcraft.entity.misc.MonsterProjectile;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
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

/** Camera-facing glowing sprite for monster projectiles (texture per kind). */
@OnlyIn(Dist.CLIENT)
public class MonsterProjectileRenderer extends EntityRenderer<MonsterProjectile> {
    private static final ResourceLocation[] TEXTURES = new ResourceLocation[MonsterProjectile.Kind.values().length];

    static {
        for (MonsterProjectile.Kind k : MonsterProjectile.Kind.values()) {
            TEXTURES[k.ordinal()] = HuntCraft.id("textures/entity/projectile/" + k.name().toLowerCase() + ".png");
        }
    }

    public MonsterProjectileRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
    }

    @Override
    public void render(MonsterProjectile e, float yaw, float partialTick, PoseStack ps, MultiBufferSource buffers, int light) {
        ps.pushPose();
        float s = Math.max(0.4F, e.getSize()) * 1.4F;
        float spin = (e.tickCount + partialTick) * 12.0F;
        ps.translate(0, e.getBbHeight() * 0.5, 0);
        ps.scale(s, s, s);
        ps.mulPose(this.entityRenderDispatcher.cameraOrientation());
        ps.mulPose(Axis.YP.rotationDegrees(180.0F));
        ps.mulPose(Axis.ZP.rotationDegrees(spin));
        PoseStack.Pose pose = ps.last();
        Matrix4f m = pose.pose();
        Matrix3f n = pose.normal();
        VertexConsumer vc = buffers.getBuffer(RenderType.entityTranslucent(getTextureLocation(e)));
        int full = 0xF000F0;
        vertex(vc, m, n, full, -0.5F, -0.5F, 0, 1);
        vertex(vc, m, n, full, 0.5F, -0.5F, 1, 1);
        vertex(vc, m, n, full, 0.5F, 0.5F, 1, 0);
        vertex(vc, m, n, full, -0.5F, 0.5F, 0, 0);
        ps.popPose();
        super.render(e, yaw, partialTick, ps, buffers, light);
    }

    private static void vertex(VertexConsumer vc, Matrix4f m, Matrix3f n, int light, float x, float y, int u, int v) {
        vc.vertex(m, x, y, 0.0F).color(255, 255, 255, 255).uv((float) u, (float) v).overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(light).normal(n, 0.0F, 1.0F, 0.0F).endVertex();
    }

    @Override
    public ResourceLocation getTextureLocation(MonsterProjectile e) {
        return TEXTURES[e.getKind().ordinal()];
    }
}
