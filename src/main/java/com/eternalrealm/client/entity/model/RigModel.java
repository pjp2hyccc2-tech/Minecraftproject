package com.eternalrealm.client.entity.model;

import net.minecraft.client.animation.AnimationDefinition;
import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.entity.Entity;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Keyframe-animated model built from generated rig tables. Every frame: reset the pose, apply the variant's look
 * (visibility and rest adjustments), then the caller adds head look, walk cycle and the active animations.
 */
public abstract class RigModel<T extends Entity> extends HierarchicalModel<T> {
    protected static final float DEG = (float) (Math.PI / 180.0);

    protected final RigTables tables;
    private final ModelPart root;
    private final List<ModelPart> allParts;
    private final ModelPart[] toggled;
    private final ModelPart[] posed;
    private final AnimationDefinition[][] animations;
    private final Map<String, Optional<ModelPart>> partsByName = new HashMap<>();
    protected final ModelPart head;

    protected RigModel(ModelPart root, RigTables tables) {
        super(RenderType::entityCutoutNoCull);
        this.root = root;
        this.tables = tables;
        this.allParts = root.getAllParts().toList();
        this.toggled = new ModelPart[tables.toggledParts().length];
        for (int i = 0; i < this.toggled.length; i++) {
            this.toggled[i] = find(root, tables.toggledParts()[i]);
        }
        this.posed = new ModelPart[tables.posedParts().length];
        for (int i = 0; i < this.posed.length; i++) {
            this.posed[i] = find(root, tables.posedParts()[i]);
        }
        this.animations = new AnimationDefinition[tables.variants().length][];
        for (int i = 0; i < this.animations.length; i++) {
            this.animations[i] = tables.animations().apply(tables.variants()[i]);
        }
        this.head = findOrNull(root, "head");
    }

    @Override
    public ModelPart root() {
        return this.root;
    }

    /**
     * Cached: vanilla's keyframe player looks up every animated bone by name on every frame, which walks the whole
     * part tree each time.
     */
    @Override
    public Optional<ModelPart> getAnyDescendantWithName(String name) {
        Optional<ModelPart> part = this.partsByName.get(name);
        if (part == null) {
            part = super.getAnyDescendantWithName(name);
            this.partsByName.put(name, part);
        }
        return part;
    }

    /** Resets every part and applies the look of {@code variant}; returns the variant index. */
    protected int prepare(String variant) {
        for (ModelPart part : this.allParts) {
            part.resetPose();
            part.xScale = 1.0F;
            part.yScale = 1.0F;
            part.zScale = 1.0F;
        }
        int index = this.tables.indexOf(variant);
        boolean[] visible = this.tables.visible()[index];
        for (int i = 0; i < this.toggled.length; i++) {
            this.toggled[i].visible = visible[i];
        }
        float[][] poses = this.tables.poses()[index];
        for (int i = 0; i < this.posed.length; i++) {
            float[] pose = poses[i];
            if (pose == null) {
                continue;
            }
            ModelPart part = this.posed[i];
            part.x += pose[0];
            part.y -= pose[1];
            part.z += pose[2];
            part.xRot += pose[3] * DEG;
            part.yRot += pose[4] * DEG;
            part.zRot += pose[5] * DEG;
            part.xScale *= pose[6];
            part.yScale *= pose[7];
            part.zScale *= pose[8];
        }
        return index;
    }

    /** The animation of {@code slot} for the variant at {@code index}. */
    protected AnimationDefinition animation(int index, int slot) {
        return this.animations[index][slot];
    }

    protected void lookAt(float netHeadYaw, float headPitch, float factor) {
        if (this.head != null) {
            this.head.yRot += netHeadYaw * DEG * factor;
            this.head.xRot += headPitch * DEG * factor;
        }
    }

    protected float scaleOf(int index) {
        return this.tables.scales()[index];
    }

    private static ModelPart find(ModelPart root, String name) {
        ModelPart part = findOrNull(root, name);
        if (part == null) {
            throw new IllegalStateException("model part not found: " + name);
        }
        return part;
    }

    private static ModelPart findOrNull(ModelPart root, String name) {
        return root.getAllParts().filter(part -> part.hasChild(name)).findFirst().map(part -> part.getChild(name))
                .orElse(null);
    }
}
