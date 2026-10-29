package com.huntcraft.village.client.model;

import com.huntcraft.client.model.HcModelLoader;
import com.huntcraft.village.HcAnimated;
import net.minecraft.client.animation.AnimationDefinition;
import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Animated hcmodel for non-monster entities (mofu companions / NPCs, training dummy):
 * loops "idle" + "walk"/"run", one action layer, head look, and accessory bones toggled per entity
 * ({@link HcAnimated#hcShown()}: toggle bones are hidden unless listed).
 */
@OnlyIn(Dist.CLIENT)
public class HcEntityModel<T extends Entity & HcAnimated> extends HierarchicalModel<T> {
    private static final float DEG = (float) (Math.PI / 180.0);
    private final ModelPart root;
    private final HcModelLoader.HcModelData data;
    private final Map<String, ModelPart> toggles = new HashMap<>();
    private final Map<String, ModelPart> cache = new HashMap<>();
    private final Set<String> missing = new HashSet<>();

    public HcEntityModel(ModelPart root, HcModelLoader.HcModelData data, String... toggleBones) {
        this.root = root;
        this.data = data;
        for (String n : toggleBones) {
            ModelPart p = bone(n);
            if (p != null) toggles.put(n, p);
        }
    }

    public HcModelLoader.HcModelData data() {
        return data;
    }

    @Override
    public ModelPart root() {
        return root;
    }

    @Nullable
    public ModelPart bone(String name) {
        ModelPart p = cache.get(name);
        if (p != null || missing.contains(name)) return p;
        p = this.getAnyDescendantWithName(name).orElse(null);
        if (p == null) missing.add(name);
        else cache.put(name, p);
        return p;
    }

    @Nullable
    private AnimationDefinition anim(@Nullable String name) {
        return name == null ? null : data.animations.get(name);
    }

    @Override
    public void setupAnim(T e, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
        this.root().getAllParts().forEach(ModelPart::resetPose);
        Set<String> shown = e.hcShown();
        for (Map.Entry<String, ModelPart> t : toggles.entrySet()) t.getValue().visible = shown.contains(t.getKey());
        for (String h : e.hcHidden()) {
            ModelPart p = bone(h);
            if (p != null) p.visible = false;
        }
        AnimationDefinition action = anim(e.hcActionName());
        boolean acting = action != null && e.hcAction().isStarted();
        if (acting && e.hcAction().getAccumulatedTime() > (long) (action.lengthInSeconds() * 1000.0F) + 100L) {
            e.hcAction().stop();
            acting = false;
        }
        AnimationDefinition walk = limbSwingAmount > 0.6F && anim("run") != null ? anim("run") : anim("walk");
        if (walk != null) this.animateWalk(walk, limbSwing, limbSwingAmount * (acting ? 0.4F : 1.0F), data.walkSpeed, data.walkScale);
        AnimationDefinition idle = anim("idle");
        if (idle != null) this.animate(e.hcIdle(), idle, ageInTicks);
        if (acting) this.animate(e.hcAction(), action, ageInTicks, data.actionSpeed);
        for (HcModelLoader.HeadBone hb : data.headBones) {
            ModelPart p = bone(hb.name());
            if (p != null) {
                p.yRot += netHeadYaw * DEG * hb.weight();
                p.xRot += headPitch * DEG * hb.weight();
            }
        }
    }
}
