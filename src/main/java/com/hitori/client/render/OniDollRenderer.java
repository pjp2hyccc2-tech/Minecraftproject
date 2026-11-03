package com.hitori.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.hitori.Hitori;
import com.hitori.HitoriConfig;
import com.hitori.client.CameraApp;
import com.hitori.client.Tex;
import com.hitori.entity.FixedCamera;
import com.hitori.entity.OniDoll;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.EyesLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;

/**
 * The doll (docs/DESIGN.md §5): seen through a camera it is drawn in stop-motion — position, rotation and pose are
 * held for a few ticks and then jump (the camera app's uneven frame clock, {@link CameraApp#stopFrame()}); seen with
 * your own eyes it is simply still (the server freezes it). Its button eyes ({@code oni_doll_eyes.png}) glint on the IR
 * feed; to the naked eye they barely catch the light. The box stand-in until the models worker's OniDollModel is merged.
 */
public class OniDollRenderer extends MobRenderer<OniDoll, EntityModel<OniDoll>> {
    private static final ResourceLocation TEXTURE = Hitori.id("textures/entity/oni_doll.png");
    private static final ResourceLocation EYES = Hitori.id("textures/entity/oni_doll_eyes.png");
    private final RenderType eyes = RenderType.eyes(EYES);
    private final boolean placeholder;

    private record Snap(int frame, double x, double y, double z, float body, float head, float pitch, int tick) {}

    private static final Map<Integer, Snap> SNAPS = new HashMap<>();

    public OniDollRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, pick(ctx), 0.22f);
        this.placeholder = !Models.has("OniDollModel");
        addLayer(new EyesLayer<OniDoll, EntityModel<OniDoll>>(this) {
            @Override
            public RenderType renderType() {
                return eyes;
            }

            @Override
            public void render(PoseStack ps, MultiBufferSource buf, int light, OniDoll e, float limbSwing, float limbSwingAmount, float partial,
                               float age, float headYaw, float headPitch) {
                if (Tex.has(EYES)) super.render(ps, buf, light, e, limbSwing, limbSwingAmount, partial, age, headYaw, headPitch);
            }
        });
    }

    private static EntityModel<OniDoll> pick(EntityRendererProvider.Context ctx) {
        EntityModel<OniDoll> real = Models.create("OniDollModel", ctx);
        if (real != null) return real;
        return new HumanoidModel<>(ctx.bakeLayer(Models.PLACEHOLDER));
    }

    @Override
    public ResourceLocation getTextureLocation(OniDoll entity) {
        return TEXTURE;
    }

    @Nullable
    @Override
    protected RenderType getRenderType(OniDoll entity, boolean bodyVisible, boolean translucent, boolean glowing) {
        return RenderType.entityCutoutNoCull(TEXTURE);
    }

    static boolean throughCamera() {
        return Minecraft.getInstance().getCameraEntity() instanceof FixedCamera;
    }

    @Override
    public void render(OniDoll e, float yaw, float partial, PoseStack ps, MultiBufferSource buffer, int light) {
        boolean cam = throughCamera();
        // the glint of the button eyes: on the IR feed, not to the naked eye
        float glow = cam ? 1.0f : 0.12f;
        MultiBufferSource buf = new AlphaBuffers(buffer, 1.0f, glow, eyes);
        ps.pushPose();
        if (placeholder) placeholderPose(e, partial, ps);
        if (cam && CameraApp.isOpen() && !HitoriConfig.flashReduction()) {
            // stop-motion: hold, then jump
            int f = CameraApp.stopFrame();
            Snap s = SNAPS.get(e.getId());
            if (s == null || s.frame() != f) {
                s = new Snap(f, e.getX(), e.getY(), e.getZ(), e.yBodyRot, e.yHeadRot, e.getXRot(), e.tickCount);
                SNAPS.put(e.getId(), s);
                if (SNAPS.size() > 16) SNAPS.clear();
            }
            double lx = Mth.lerp(partial, e.xOld, e.getX()), ly = Mth.lerp(partial, e.yOld, e.getY()), lz = Mth.lerp(partial, e.zOld, e.getZ());
            ps.translate(s.x() - lx, s.y() - ly, s.z() - lz);
            float b0 = e.yBodyRot, b1 = e.yBodyRotO, h0 = e.yHeadRot, h1 = e.yHeadRotO, p0 = e.getXRot(), p1 = e.xRotO;
            int tick = e.tickCount;
            e.yBodyRot = e.yBodyRotO = s.body();
            e.yHeadRot = e.yHeadRotO = s.head();
            e.setXRot(s.pitch());
            e.xRotO = s.pitch();
            e.tickCount = s.tick();
            try {
                super.render(e, s.body(), 0.0f, ps, buf, light);
            } finally {
                e.yBodyRot = b0;
                e.yBodyRotO = b1;
                e.yHeadRot = h0;
                e.yHeadRotO = h1;
                e.setXRot(p0);
                e.xRotO = p1;
                e.tickCount = tick;
            }
        } else {
            super.render(e, yaw, partial, ps, buf, light);
        }
        ps.popPose();
    }

    /** The box stand-in has no poses of its own: lying in the water, sitting, limp. */
    private void placeholderPose(OniDoll e, float partial, PoseStack ps) {
        switch (e.pose()) {
            case OniDoll.FLOAT, OniDoll.LIMP -> {
                ps.translate(0.0, 0.1, 0.0);
                ps.mulPose(Axis.YP.rotationDegrees(-Mth.lerp(partial, e.yBodyRotO, e.yBodyRot)));
                ps.mulPose(Axis.XP.rotationDegrees(e.pose() == OniDoll.FLOAT ? 90.0f : -90.0f));
                ps.mulPose(Axis.YP.rotationDegrees(Mth.lerp(partial, e.yBodyRotO, e.yBodyRot)));
            }
            case OniDoll.SIT -> ps.translate(0.0, -0.08, 0.0);
            case OniDoll.BURN -> {
                float t = e.tickCount + partial;
                ps.translate(Mth.sin(t * 2.1f) * 0.02, 0.0, Mth.cos(t * 2.7f) * 0.02);
            }
            default -> {
            }
        }
    }

    @Override
    protected void scale(OniDoll entity, PoseStack ps, float partial) {
        if (placeholder) ps.scale(0.32f, 0.32f, 0.32f);
    }

    @Override
    protected float getFlipDegrees(OniDoll entity) {
        return 0.0f;
    }
}
