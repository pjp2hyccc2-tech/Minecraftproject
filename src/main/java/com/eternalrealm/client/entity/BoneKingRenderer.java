package com.eternalrealm.client.entity;

import com.eternalrealm.core.ModInfo;
import com.eternalrealm.job.forge.BoneKing;
import com.eternalrealm.job.fx.BoneKingFigure;
import com.eternalrealm.job.fx.Brush;
import com.eternalrealm.job.fx.Light;
import com.eternalrealm.job.fx.V3;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.slf4j.Logger;

/**
 * Draws 骸骨の騎士王, the necromancer finale's summon: the solid body of {@link BoneKingFigure} through the entity
 * pipeline (lit by the world like any creature, drawn with shader packs too, see-through while it rises and crumbles).
 * Its glow (the eyes, the fire in its ribs, the edge of its sword) is skill light, added by {@code SkillFx}.
 */
public final class BoneKingRenderer extends EntityRenderer<BoneKing> {
    private static final Logger LOG = LogUtils.getLogger();
    /** A plain white texture: the figure's colours are its vertex colours. */
    private static final ResourceLocation WHITE = new ResourceLocation(ModInfo.MOD_ID, "textures/entity/bone_king.png");
    /** It is made of mana: never quite as dark as the place it stands in (block light at least this). */
    private static final int MIN_GLOW = 6;
    private static boolean failed;

    public BoneKingRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 1.2F;
        this.shadowStrength = 0.7F;
    }

    /** The king at this frame, as the figure draws it. */
    public static BoneKingFigure.State state(BoneKing king, float partial) {
        Vec3 at = king.getPosition(partial);
        float yaw = Mth.rotLerp(partial, king.yBodyRotO, king.yBodyRot);
        return new BoneKingFigure.State(new V3(at.x, at.y, at.z), yaw, king.age(partial), king.swingAge(partial),
                king.swingKind(), king.fadeAge(partial), king.walkAnimation.position(partial),
                king.walkAnimation.speed(partial), king.getUUID().getLeastSignificantBits());
    }

    @Override
    public ResourceLocation getTextureLocation(BoneKing king) {
        return WHITE;
    }

    /** Its greatsword reaches far past its body: drawn while any of its reach is in view. */
    @Override
    public boolean shouldRender(BoneKing king, Frustum frustum, double x, double y, double z) {
        return king.shouldRender(x, y, z) && frustum.isVisible(king.getBoundingBox().inflate(4.0, 1.0, 4.0));
    }

    @Override
    public void render(BoneKing king, float yaw, float partial, PoseStack pose, MultiBufferSource buffers,
                       int packedLight) {
        super.render(king, yaw, partial, pose, buffers, packedLight);
        if (failed) {
            return;
        }
        try {
            int light = LightTexture.pack(Math.max(MIN_GLOW, LightTexture.block(packedLight)),
                    LightTexture.sky(packedLight));
            Vec3 at = king.getPosition(partial);
            Vec3 cam = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
            Sink sink = new Sink(buffers.getBuffer(RenderType.entityTranslucent(WHITE)), pose, at, light);
            BoneKingFigure.paint(new Brush(Brush.NOWHERE, sink, new V3(cam.x, cam.y, cam.z)), state(king, partial));
        } catch (RuntimeException e) {
            failed = true;
            LOG.error("The bone king's body is no longer drawn after a rendering error", e);
        }
    }

    /**
     * Writes the figure's solid quads (world positions, colours already shaded by the figure) as entity vertices
     * relative to the king. Every normal points up so the entity shader keeps the figure's own shading and adds only
     * the world's light.
     */
    private static final class Sink implements Light {
        private final VertexConsumer out;
        private final Matrix4f matrix;
        private final Matrix3f normal;
        private final double ox;
        private final double oy;
        private final double oz;
        private final int light;

        Sink(VertexConsumer out, PoseStack pose, Vec3 origin, int light) {
            this.out = out;
            this.matrix = pose.last().pose();
            this.normal = pose.last().normal();
            this.ox = origin.x;
            this.oy = origin.y;
            this.oz = origin.z;
            this.light = light;
        }

        @Override
        public void vertex(double x, double y, double z, int argb) {
            out.vertex(matrix, (float) (x - ox), (float) (y - oy), (float) (z - oz))
                    .color((argb >> 16) & 0xFF, (argb >> 8) & 0xFF, argb & 0xFF, argb >>> 24)
                    .uv(0.5F, 0.5F)
                    .overlayCoords(OverlayTexture.NO_OVERLAY)
                    .uv2(light)
                    .normal(normal, 0.0F, 1.0F, 0.0F)
                    .endVertex();
        }
    }
}
