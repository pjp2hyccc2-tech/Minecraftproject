package com.eternalrealm.client.entity.layer;

import com.eternalrealm.client.entity.model.BossModel;
import com.eternalrealm.entity.BossAnimation;
import com.eternalrealm.entity.BossRig;
import com.eternalrealm.entity.ERBoss;
import com.eternalrealm.entity.RigData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayDeque;
import java.util.Iterator;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Weapon trails: while a strike swings through its impact frame, the blade, axe, fists, claws or tail tip of the boss
 * (the rig's trail bones) leave a glowing ribbon in the colour of its form, fading within a few ticks. Drawn in model
 * space from the bones' own transforms, additive, both sides.
 */
public class BossTrailLayer extends RenderLayer<ERBoss, BossModel> {
    /** Seconds before the impact frame the trail fades in, and after it is gone. */
    private static final float BEFORE = 0.42F;
    private static final float AFTER = 0.3F;
    /** Age in ticks of the oldest point of a ribbon. */
    private static final float LIFE_TICKS = 3.5F;
    private static final BossAnimation[] STRIKES = {BossAnimation.MELEE_SLAM, BossAnimation.MELEE_SWEEP,
            BossAnimation.CHARGE, BossAnimation.SUMMON};

    /** One recorded position of a trail bone's segment. */
    private record Sample(float time, Vector3f base, Vector3f tip) {
    }

    private final BossRig rig;
    private final Map<ERBoss, ArrayDeque<Sample>[]> trails = new WeakHashMap<>();

    public BossTrailLayer(RenderLayerParent<ERBoss, BossModel> parent, BossRig rig) {
        super(parent);
        this.rig = rig;
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource buffers, int packedLight, ERBoss boss, float limbSwing,
                       float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
        String look = boss.lookKey();
        String[] bones = RigData.trailBones(this.rig.id(), look);
        float strength = boss.isInvisible() || boss.isDefeatPlaying() ? 0.0F : strength(boss, look);
        if (bones.length == 0 || strength <= 0.0F) {
            this.trails.remove(boss);
            return;
        }
        float[][] points = RigData.trailPoints(this.rig.id(), look);
        ArrayDeque<Sample>[] ribbons = this.trails.computeIfAbsent(boss, key -> newRibbons(bones.length));
        if (ribbons.length != bones.length) {
            ribbons = newRibbons(bones.length);
            this.trails.put(boss, ribbons);
        }
        float time = boss.tickCount + partialTick;
        for (int i = 0; i < bones.length && i < points.length; i++) {
            Matrix4f bone = boneMatrix(RigData.trailChain(this.rig.id(), bones[i]));
            if (bone == null) {
                continue;
            }
            float[] p = points[i];
            Vector3f base = bone.transformPosition(new Vector3f(p[0] / 16.0F, p[1] / 16.0F, p[2] / 16.0F));
            Vector3f tip = bone.transformPosition(new Vector3f(p[3] / 16.0F, p[4] / 16.0F, p[5] / 16.0F));
            ArrayDeque<Sample> ribbon = ribbons[i];
            if (ribbon.isEmpty() || time > ribbon.peekLast().time() + 0.05F) {
                ribbon.addLast(new Sample(time, base, tip));
            }
            Iterator<Sample> old = ribbon.iterator();
            while (old.hasNext() && time - old.next().time() > LIFE_TICKS) {
                old.remove();
            }
        }
        float[] rgb = BossVisuals.formColor(boss.bossId(), Math.min(3, boss.getPhase()));
        VertexConsumer consumer = buffers.getBuffer(RenderType.lightning());
        Matrix4f pose = poseStack.last().pose();
        for (ArrayDeque<Sample> ribbon : ribbons) {
            Sample previous = null;
            for (Sample sample : ribbon) {
                if (previous != null) {
                    float a0 = strength * fade(time - previous.time());
                    float a1 = strength * fade(time - sample.time());
                    quad(consumer, pose, previous, sample, rgb, a0, a1);
                }
                previous = sample;
            }
        }
    }

    /** 0..1: how strongly a strike shows its trail now (around the impact frame of the action playing). */
    private float strength(ERBoss boss, String look) {
        float best = 0.0F;
        for (BossAnimation action : STRIKES) {
            var state = boss.animationState(action);
            if (!state.isStarted()) {
                continue;
            }
            float elapsed = state.getAccumulatedTime() / 1000.0F;
            float impact = RigData.impact(this.rig.id(), look, action.ordinal());
            float t = elapsed - impact;
            float in = Mth.clamp((t + BEFORE) / 0.12F, 0.0F, 1.0F);
            float out = Mth.clamp((AFTER - t) / 0.15F, 0.0F, 1.0F);
            best = Math.max(best, Math.min(in, out));
        }
        return best;
    }

    private static float fade(float age) {
        float life = Mth.clamp(1.0F - age / LIFE_TICKS, 0.0F, 1.0F);
        return life * life;
    }

    /** Transform of a bone in the model's space, walking its chain of parts from the root. */
    private Matrix4f boneMatrix(String[] chain) {
        if (chain.length == 0) {
            return null;
        }
        PoseStack local = new PoseStack();
        ModelPart part = getParentModel().root();
        for (String name : chain) {
            if (!part.hasChild(name)) {
                return null;
            }
            part = part.getChild(name);
            part.translateAndRotate(local);
        }
        return new Matrix4f(local.last().pose());
    }

    /** One stretch of the ribbon between two samples, on both faces. */
    private static void quad(VertexConsumer consumer, Matrix4f pose, Sample from, Sample to, float[] rgb, float a0,
                             float a1) {
        float r = Math.min(1.0F, rgb[0] * 0.6F + 0.4F);
        float g = Math.min(1.0F, rgb[1] * 0.6F + 0.4F);
        float b = Math.min(1.0F, rgb[2] * 0.6F + 0.4F);
        vertex(consumer, pose, from.base(), r, g, b, a0 * 0.2F);
        vertex(consumer, pose, from.tip(), r, g, b, a0);
        vertex(consumer, pose, to.tip(), r, g, b, a1);
        vertex(consumer, pose, to.base(), r, g, b, a1 * 0.2F);
        vertex(consumer, pose, to.base(), r, g, b, a1 * 0.2F);
        vertex(consumer, pose, to.tip(), r, g, b, a1);
        vertex(consumer, pose, from.tip(), r, g, b, a0);
        vertex(consumer, pose, from.base(), r, g, b, a0 * 0.2F);
    }

    private static void vertex(VertexConsumer consumer, Matrix4f pose, Vector3f at, float r, float g, float b,
                               float a) {
        consumer.vertex(pose, at.x(), at.y(), at.z()).color(r, g, b, a * 0.7F).endVertex();
    }

    @SuppressWarnings("unchecked")
    private static ArrayDeque<Sample>[] newRibbons(int count) {
        ArrayDeque<Sample>[] ribbons = new ArrayDeque[count];
        for (int i = 0; i < count; i++) {
            ribbons[i] = new ArrayDeque<>();
        }
        return ribbons;
    }
}
