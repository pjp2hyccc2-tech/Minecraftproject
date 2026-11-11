package com.eternalrealm.client.mount;

import com.eternalrealm.client.entity.model.RigModel;
import com.eternalrealm.client.entity.model.RigTables;
import com.eternalrealm.mount.MountKind;
import com.eternalrealm.mount.forge.MountEntity;
import net.minecraft.client.animation.AnimationDefinition;
import net.minecraft.client.animation.KeyframeAnimations;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import org.joml.Vector3f;

/**
 * Model of a mount (tools/art_entities/rig_mounts.py). The layers are blended by weight every frame from how the mount
 * moves ({@link MountEntity}'s look values):
 * <ul>
 *     <li>horse: idle; walk and gallop cycles following the distance run (mane and tail stream in the gallop); the
 *     jump pose in the air;</li>
 *     <li>griffin: idle; the folded wings on the ground, the walk; in the air the wing beat (phase from the beats,
 *     faster when climbing) blended with the glide; the body pitches with the climb;</li>
 *     <li>bike: the idle hum and the ride (banner and flag in the wind); the wheels turn with the distance (their
 *     glowing rims are the emissive layer) and the bike leans and steers into turns.</li>
 * </ul>
 * Each mount wears its coat (the model variant) and its shape: a horse's mane (the shards from the poll down the
 * crest and the locks hanging over the neck) cropped, flowing or long (with a longer tail); a griffin's wings broad,
 * swept (long narrow primaries folded back), fanned (short and spread) or royal (long plumes on the crest, the wing
 * tips and the tail).
 */
public class MountModel extends RigModel<MountEntity> {
    // animation slots (the order of SLOTS in rig_mounts.py)
    private static final int IDLE = 0;
    private static final int HORSE_WALK = 1;
    private static final int HORSE_GALLOP = 2;
    private static final int HORSE_JUMP = 3;
    private static final int GRIFFIN_WALK = 1;
    private static final int GRIFFIN_FOLD = 2;
    private static final int GRIFFIN_FLAP = 3;
    private static final int GRIFFIN_GLIDE = 4;
    private static final int BIKE_RIDE = 1;
    /** Blocks per cycle of each gait. */
    private static final float HORSE_WALK_STRIDE = 2.2F;
    private static final float HORSE_GALLOP_STRIDE = 5.6F;
    private static final float GRIFFIN_WALK_STRIDE = 2.0F;

    private final MountKind kind;
    private final Vector3f cache = new Vector3f();
    private final ModelPart base;
    private final ModelPart frontWheel;
    private final ModelPart rearWheel;
    private final ModelPart fork;
    private final java.util.List<ModelPart> mane = new java.util.ArrayList<>();
    private final java.util.List<ModelPart> locks = new java.util.ArrayList<>();
    private final java.util.List<ModelPart> tailEnds = new java.util.ArrayList<>();
    private final java.util.List<ModelPart> primaries = new java.util.ArrayList<>();
    private final java.util.List<ModelPart> hands = new java.util.ArrayList<>();
    private final java.util.List<ModelPart> crest = new java.util.ArrayList<>();
    private final java.util.List<ModelPart> hackles = new java.util.ArrayList<>();
    private final ModelPart tailTip;

    public MountModel(ModelPart root, RigTables tables, MountKind kind) {
        super(root, tables);
        this.kind = kind;
        this.base = findOrNull(root, "base");
        this.frontWheel = findOrNull(root, "front_wheel");
        this.rearWheel = findOrNull(root, "rear_wheel");
        this.fork = findOrNull(root, "fork");
        for (int i = 1; i <= 48; i++) {
            add(this.mane, findOrNull(root, "mane_" + i));
        }
        for (int i = 1; i <= 16; i++) {
            add(this.locks, findOrNull(root, "lock_" + i));
        }
        add(this.tailEnds, findOrNull(root, "tail3"));
        add(this.tailEnds, findOrNull(root, "tail4"));
        for (String side : new String[] {"r", "l"}) {
            for (int i = 1; i <= 12; i++) {
                add(this.primaries, findOrNull(root, "prim_" + side + i));
            }
            add(this.hands, findOrNull(root, "wing_" + side + "2"));
        }
        for (int i = 1; i <= 24; i++) {
            add(this.crest, findOrNull(root, "crest_" + i));
            add(this.hackles, findOrNull(root, "hackle_" + i));
        }
        this.tailTip = kind == MountKind.GRIFFIN ? findOrNull(root, "tail2") : null;
    }

    private static void add(java.util.List<ModelPart> parts, ModelPart part) {
        if (part != null) {
            parts.add(part);
        }
    }

    @Override
    public void setupAnim(MountEntity mount, float limbSwing, float limbSwingAmount, float ageInTicks,
                          float netHeadYaw, float headPitch) {
        int index = prepare(mount.look().coat());
        float partial = Mth.clamp(ageInTicks - mount.tickCount, 0.0F, 1.0F);
        long millis = (long) (ageInTicks * 50.0F);
        play(animation(index, IDLE), millis, 1.0F);
        switch (this.kind) {
            case HORSE -> horse(mount, index, partial);
            case GRIFFIN -> griffin(mount, index, partial);
            case BIKE -> bike(mount, index, partial, millis);
        }
        shape(mount.look().shape());
    }

    /** The individual's mane or wings, over the pose of this frame. */
    private void shape(String shape) {
        switch (shape) {
            case "cropped" -> {
                for (ModelPart part : this.mane) {
                    part.zScale *= 0.55F;
                    part.yScale *= 0.85F;
                }
                // the hanging locks hang along y
                for (ModelPart part : this.locks) {
                    part.yScale *= 0.5F;
                }
            }
            case "long" -> {
                for (ModelPart part : this.mane) {
                    part.zScale *= 1.45F;
                }
                for (ModelPart part : this.locks) {
                    part.yScale *= 1.4F;
                }
                for (ModelPart part : this.tailEnds) {
                    part.yScale *= 1.3F;
                }
            }
            case "swept" -> {
                for (ModelPart part : this.primaries) {
                    part.zScale *= 1.3F;
                    part.xScale *= 0.75F;
                    part.yRot *= 0.55F;
                }
                for (ModelPart part : this.hands) {
                    part.xScale *= 1.1F;
                }
            }
            case "fan" -> {
                for (ModelPart part : this.primaries) {
                    part.zScale *= 0.82F;
                    part.yRot *= 1.35F;
                }
                for (ModelPart part : this.hands) {
                    part.xScale *= 0.9F;
                }
            }
            case "royal" -> {
                for (ModelPart part : this.crest) {
                    part.zScale *= 1.6F;
                }
                for (ModelPart part : this.hackles) {
                    part.zScale *= 1.3F;
                }
                int perWing = this.primaries.size() / 2;
                for (int i = 0; i < this.primaries.size(); i++) {
                    if (perWing > 0 && i % perWing >= perWing / 3) {
                        this.primaries.get(i).zScale *= 1.3F;
                    }
                }
                if (this.tailTip != null) {
                    this.tailTip.zScale *= 1.6F;
                }
            }
            default -> {
                // the classic flowing mane, the broad wings
            }
        }
    }

    private void horse(MountEntity mount, int index, float partial) {
        float speed = mount.speed();
        float air = mount.airTicks() > 2 ? Math.min(1.0F, (mount.airTicks() - 2) / 3.0F) : 0.0F;
        float gallop = smooth(speed, 0.2F, 0.38F) * (1.0F - air);
        float walk = smooth(speed, 0.015F, 0.08F) * (1.0F - gallop) * (1.0F - air);
        float distance = mount.distance(partial);
        cycle(animation(index, HORSE_WALK), distance / HORSE_WALK_STRIDE, walk);
        cycle(animation(index, HORSE_GALLOP), distance / HORSE_GALLOP_STRIDE, gallop);
        play(animation(index, HORSE_JUMP), (long) (Math.min(mount.airTicks(), 12) * 50L), air);
    }

    private void griffin(MountEntity mount, int index, float partial) {
        float wings = mount.wings(partial);
        float grounded = 1.0F - wings;
        cycle(animation(index, GRIFFIN_WALK), mount.distance(partial) / GRIFFIN_WALK_STRIDE,
                smooth(mount.speed(), 0.015F, 0.08F) * grounded);
        play(animation(index, GRIFFIN_FOLD), 0L, grounded);
        float beat = Mth.clamp(mount.beat(), 0.0F, 1.0F);
        cycle(animation(index, GRIFFIN_FLAP), mount.flap(partial), wings * beat);
        play(animation(index, GRIFFIN_GLIDE), (long) ((mount.tickCount + partial) * 50.0F), wings * (1.0F - beat));
        if (this.base != null) {
            // nose up while climbing, down while diving
            this.base.xRot += Mth.clamp(-mount.climb() * 70.0F, -22.0F, 26.0F) * DEG * wings;
        }
    }

    private void bike(MountEntity mount, int index, float partial, long millis) {
        play(animation(index, BIKE_RIDE), millis, smooth(mount.speed(), 0.02F, 0.2F));
        float wheel = mount.wheel(partial);
        if (this.frontWheel != null) {
            this.frontWheel.xRot += wheel;
        }
        if (this.rearWheel != null) {
            this.rearWheel.xRot += wheel;
        }
        float lean = mount.lean(partial);
        if (this.base != null) {
            this.base.zRot -= lean * DEG; // -X is the bike's right side
        }
        if (this.fork != null) {
            this.fork.yRot += lean * 0.5F * DEG;
        }
    }

    private void play(AnimationDefinition definition, long millis, float weight) {
        if (weight > 0.01F) {
            KeyframeAnimations.animate(this, definition, millis, weight, this.cache);
        }
    }

    /** A looping cycle at {@code phase} (cycles; the fraction is used). */
    private void cycle(AnimationDefinition definition, float phase, float weight) {
        if (weight > 0.01F) {
            long millis = (long) (Mth.frac(phase) * definition.lengthInSeconds() * 1000.0F);
            KeyframeAnimations.animate(this, definition, millis, weight, this.cache);
        }
    }

    private static float smooth(float value, float from, float to) {
        float t = Mth.clamp((value - from) / (to - from), 0.0F, 1.0F);
        return t * t * (3.0F - 2.0F * t);
    }
}
