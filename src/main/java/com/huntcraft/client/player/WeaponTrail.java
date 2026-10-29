package com.huntcraft.client.player;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.joml.Matrix4f;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/**
 * Additive ribbon drawn through the recent positions of a swinging weapon (grip -> tip per frame).
 * Samples are stored in world space; rendered after particles with Catmull-Rom smoothing.
 */
@OnlyIn(Dist.CLIENT)
public final class WeaponTrail {
    private static final float LIFE = 5.0F;
    private static final int MAX = 48;

    private record Sample(Vec3 base, Vec3 tip, float time, int color) {
    }

    private final ArrayDeque<Sample> samples = new ArrayDeque<>();

    public void add(Vec3 base, Vec3 tip, float time, int color) {
        Sample last = samples.peekLast();
        if (last != null && last.time >= time) return;
        if (last != null && last.tip.distanceToSqr(tip) < 1.0E-4) return;
        // a jump of more than 3 blocks between consecutive samples is a teleport / camera swap, not a swing
        if (last != null && last.tip.distanceToSqr(tip) > 9.0) samples.clear();
        samples.addLast(new Sample(base, tip, time, color));
        while (samples.size() > MAX) samples.pollFirst();
    }

    public void stop() {
        // samples fade out naturally
    }

    public void tick(long gameTime) {
        while (!samples.isEmpty() && gameTime - samples.peekFirst().time > LIFE + 1) samples.pollFirst();
    }

    public boolean isEmpty() {
        return samples.isEmpty();
    }

    public void render(PoseStack ps, MultiBufferSource buffers, Vec3 cam, float now) {
        if (samples.size() < 2) return;
        List<Sample> list = new ArrayList<>(samples);
        // cut the ribbon where the swing paused (gap in time)
        VertexConsumer vc = buffers.getBuffer(RenderType.lightning());
        Matrix4f m = ps.last().pose();
        int n = list.size();
        for (int i = 0; i < n - 1; i++) {
            Sample a = list.get(i);
            Sample b = list.get(i + 1);
            if (b.time - a.time > 2.5F) continue;
            Sample p0 = list.get(Math.max(0, i - 1));
            Sample p3 = list.get(Math.min(n - 1, i + 2));
            int sub = 3;
            for (int k = 0; k < sub; k++) {
                float f0 = k / (float) sub;
                float f1 = (k + 1) / (float) sub;
                Vec3 b0 = catmull(p0.base, a.base, b.base, p3.base, f0);
                Vec3 t0 = catmull(p0.tip, a.tip, b.tip, p3.tip, f0);
                Vec3 b1 = catmull(p0.base, a.base, b.base, p3.base, f1);
                Vec3 t1 = catmull(p0.tip, a.tip, b.tip, p3.tip, f1);
                float age0 = now - (a.time + (b.time - a.time) * f0);
                float age1 = now - (a.time + (b.time - a.time) * f1);
                float al0 = alpha(age0);
                float al1 = alpha(age1);
                if (al0 <= 0 && al1 <= 0) continue;
                int c = b.color;
                float r = ((c >> 16) & 255) / 255F, g = ((c >> 8) & 255) / 255F, bl = (c & 255) / 255F;
                // outer coloured ribbon from 25% of the blade to the tip
                Vec3 i0 = b0.add(t0.subtract(b0).scale(0.25));
                Vec3 i1 = b1.add(t1.subtract(b1).scale(0.25));
                quad(vc, m, cam, i0, t0, t1, i1, r, g, bl, al0 * 0.12F, al0 * 0.55F, al1 * 0.55F, al1 * 0.12F);
                // bright core near the tip
                Vec3 c0 = b0.add(t0.subtract(b0).scale(0.6));
                Vec3 c1 = b1.add(t1.subtract(b1).scale(0.6));
                quad(vc, m, cam, c0, t0, t1, c1, 1, 1, 1, al0 * 0.08F, al0 * 0.45F, al1 * 0.45F, al1 * 0.08F);
            }
        }
    }

    private static float alpha(float age) {
        if (age < 0) return 1;
        float f = 1.0F - age / LIFE;
        return f <= 0 ? 0 : f * f;
    }

    private static Vec3 catmull(Vec3 p0, Vec3 p1, Vec3 p2, Vec3 p3, float t) {
        double t2 = t * t, t3 = t2 * t;
        double x = 0.5 * (2 * p1.x + (-p0.x + p2.x) * t + (2 * p0.x - 5 * p1.x + 4 * p2.x - p3.x) * t2 + (-p0.x + 3 * p1.x - 3 * p2.x + p3.x) * t3);
        double y = 0.5 * (2 * p1.y + (-p0.y + p2.y) * t + (2 * p0.y - 5 * p1.y + 4 * p2.y - p3.y) * t2 + (-p0.y + 3 * p1.y - 3 * p2.y + p3.y) * t3);
        double z = 0.5 * (2 * p1.z + (-p0.z + p2.z) * t + (2 * p0.z - 5 * p1.z + 4 * p2.z - p3.z) * t2 + (-p0.z + 3 * p1.z - 3 * p2.z + p3.z) * t3);
        return new Vec3(x, y, z);
    }

    /** Double sided quad (both windings) with per-corner alpha. */
    private static void quad(VertexConsumer vc, Matrix4f m, Vec3 cam, Vec3 a, Vec3 b, Vec3 c, Vec3 d, float r, float g, float bl,
                             float aa, float ab, float ac, float ad) {
        vert(vc, m, cam, a, r, g, bl, aa);
        vert(vc, m, cam, b, r, g, bl, ab);
        vert(vc, m, cam, c, r, g, bl, ac);
        vert(vc, m, cam, d, r, g, bl, ad);
        vert(vc, m, cam, d, r, g, bl, ad);
        vert(vc, m, cam, c, r, g, bl, ac);
        vert(vc, m, cam, b, r, g, bl, ab);
        vert(vc, m, cam, a, r, g, bl, aa);
    }

    private static void vert(VertexConsumer vc, Matrix4f m, Vec3 cam, Vec3 p, float r, float g, float b, float a) {
        vc.vertex(m, (float) (p.x - cam.x), (float) (p.y - cam.y), (float) (p.z - cam.z)).color(r, g, b, Math.max(0, Math.min(1, a))).endVertex();
    }

    /** Draws every player's trail (RenderLevelStageEvent AFTER_PARTICLES). */
    public static void renderAll(PoseStack ps, Vec3 cam, float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        float now = mc.level.getGameTime() + partialTick;
        MultiBufferSource.BufferSource buf = mc.renderBuffers().bufferSource();
        boolean any = false;
        for (net.minecraft.world.entity.player.Player p : mc.level.players()) {
            PlayerAnimState st = PlayerAnimState.get(p);
            if (st.trail.isEmpty()) continue;
            st.trail.render(ps, buf, cam, now);
            any = true;
        }
        if (any) buf.endBatch(RenderType.lightning());
    }
}
