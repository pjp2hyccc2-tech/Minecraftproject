package com.mcbesiege.client;

import com.mcbesiege.entity.MachineEntity;
import com.mcbesiege.entity.MachineView;
import com.mcbesiege.registry.ModBlocks;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.client.model.data.ModelData;
import org.joml.Quaternionf;

/** Draws every piece of a simulating machine with its normal block model, at its rigid body's interpolated pose. */
public class MachineRenderer extends EntityRenderer<MachineEntity> {
    private final BlockRenderDispatcher blocks;

    public MachineRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.blocks = ctx.getBlockRenderDispatcher();
        this.shadowRadius = 0f;
    }

    @Override
    public boolean shouldRender(MachineEntity e, Frustum frustum, double cx, double cy, double cz) {
        AABB box = bounds(e);
        e.setCullBox(box);
        return box == null || frustum.isVisible(box);
    }

    static AABB bounds(MachineEntity e) {
        MachineView v = e.view;
        if (!v.hasState) return null;
        double x0 = Double.MAX_VALUE, y0 = Double.MAX_VALUE, z0 = Double.MAX_VALUE, x1 = -Double.MAX_VALUE, y1 = -Double.MAX_VALUE, z1 = -Double.MAX_VALUE;
        for (int i = 0; i < v.bodyCount && (i + 1) * 7 <= v.cur.length; i++) {
            double x = v.cur[i * 7], y = v.cur[i * 7 + 1], z = v.cur[i * 7 + 2];
            x0 = Math.min(x0, x);
            y0 = Math.min(y0, y);
            z0 = Math.min(z0, z);
            x1 = Math.max(x1, x);
            y1 = Math.max(y1, y);
            z1 = Math.max(z1, z);
        }
        if (x0 == Double.MAX_VALUE) return null;
        return new AABB(x0, y0, z0, x1, y1, z1).inflate(12);
    }

    @Override
    public void render(MachineEntity e, float yaw, float pt, PoseStack ps, MultiBufferSource buffers, int light) {
        MachineView v = e.view;
        if (!v.hasState) return;
        double ex = Mth.lerp(pt, e.xo, e.getX()), ey = Mth.lerp(pt, e.yo, e.getY()), ez = Mth.lerp(pt, e.zo, e.getZ());
        double[][] poses = new double[v.bodyCount][];
        Level level = e.level();
        for (MachineView.SubView s : v.subs) {
            if (s.body < 0 || s.body >= poses.length) continue;
            if (poses[s.body] == null) {
                double[] p = new double[7];
                if (!v.pose(s.body, pt, p)) continue;
                poses[s.body] = p;
            }
            double[] pose = poses[s.body];
            double[] w = MachineView.transform(pose, s.ox, s.oy, s.oz);
            int packed = LevelRenderer.getLightColor(level, BlockPos.containing(w[0], w[1], w[2]));
            if (s.charred) packed = LightTexture.pack(0, Math.min(3, LightTexture.sky(packed)));
            int flags = v.partFlags(s.part);
            if ((flags & 1) != 0) packed = LightTexture.pack(15, LightTexture.sky(packed));
            int overlay = (flags & 2) != 0 ? OverlayTexture.pack(0, 3) : OverlayTexture.NO_OVERLAY;
            ps.pushPose();
            ps.translate(pose[0] - ex, pose[1] - ey, pose[2] - ez);
            ps.mulPose(new Quaternionf((float) pose[4], (float) pose[5], (float) pose[6], (float) pose[3]));
            ps.translate(s.ox - 0.5, s.oy - 0.5, s.oz - 0.5);
            blocks.renderSingleBlock(s.state, ps, buffers, packed, overlay, ModelData.EMPTY, null);
            ps.popPose();
        }
        for (MachineView.ConView c : v.cons) {
            if (c.ba < 0 || c.ba >= poses.length || c.bb < 0 || c.bb >= poses.length) continue;
            double[] pa = poses[c.ba], pb = poses[c.bb];
            if (pa == null) {
                pa = new double[7];
                if (!v.pose(c.ba, pt, pa)) continue;
            }
            if (pb == null) {
                pb = new double[7];
                if (!v.pose(c.bb, pt, pb)) continue;
            }
            double[] a = MachineView.transform(pa, c.ax, c.ay, c.az);
            double[] b = MachineView.transform(pb, c.bx, c.by, c.bz);
            BlockState st = visual(c.kind);
            int packed = LevelRenderer.getLightColor(level, BlockPos.containing((a[0] + b[0]) / 2, (a[1] + b[1]) / 2, (a[2] + b[2]) / 2));
            ps.pushPose();
            ps.translate(-ex, -ey, -ez);
            drawBeam(blocks, ps, buffers, st, a[0], a[1], a[2], b[0], b[1], b[2], packed);
            ps.popPose();
        }
    }

    static BlockState visual(String kind) {
        return switch (kind) {
            case "spring" -> ModBlocks.SPRING_VISUAL.get().defaultBlockState();
            case "rope" -> ModBlocks.ROPE_VISUAL.get().defaultBlockState();
            default -> ModBlocks.BRACE_VISUAL.get().defaultBlockState();
        };
    }

    /** Renders a beam model (built along +Y) stretched from point a to point b (pose stack in world-relative space). */
    static void drawBeam(BlockRenderDispatcher blocks, PoseStack ps, MultiBufferSource buffers, BlockState st,
                         double ax, double ay, double az, double bx, double by, double bz, int light) {
        double dx = bx - ax, dy = by - ay, dz = bz - az;
        double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len < 1e-3) return;
        ps.pushPose();
        ps.translate(ax, ay, az);
        ps.mulPose(new Quaternionf().rotationTo(0f, 1f, 0f, (float) (dx / len), (float) (dy / len), (float) (dz / len)));
        ps.scale(1f, (float) len, 1f);
        ps.translate(-0.5, 0, -0.5);
        blocks.renderSingleBlock(st, ps, buffers, light, OverlayTexture.NO_OVERLAY, ModelData.EMPTY, null);
        ps.popPose();
    }

    @Override
    public ResourceLocation getTextureLocation(MachineEntity e) {
        return InventoryMenu.BLOCK_ATLAS;
    }
}
