package com.huntcraft.client.model;

import com.huntcraft.entity.monster.HuntMonster;
import com.huntcraft.entity.monster.PartDef;
import net.minecraft.client.animation.AnimationDefinition;
import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.HashMap;
import java.util.Map;

/**
 * Generic animated model for every HuntCraft monster built from an hcmodel JSON.
 * <ul>
 *   <li>loops: "idle" (always), "walk"/"run" (by movement), "fly" (while flying)</li>
 *   <li>state poses: "death", "trapped", "sleep", "stun" (replace everything else)</li>
 *   <li>actions: any animation named by {@link HuntMonster#getActionAnimName()} (attacks, roar, flinch...)</li>
 *   <li>head look on configured head bones; broken parts hide their bones</li>
 * </ul>
 */
@OnlyIn(Dist.CLIENT)
public class HcModel<T extends HuntMonster> extends HierarchicalModel<T> {
    private static final float DEG = (float) (Math.PI / 180.0);
    private final ModelPart root;
    private final HcModelLoader.HcModelData data;
    private final Map<String, ModelPart> byName = new HashMap<>();
    private final java.util.Set<String> missing = new java.util.HashSet<>();

    public HcModel(ModelPart root, HcModelLoader.HcModelData data) {
        this.root = root;
        this.data = data;
    }

    public HcModelLoader.HcModelData data() {
        return data;
    }

    @Override
    public ModelPart root() {
        return root;
    }

    public ModelPart bone(String name) {
        ModelPart p = byName.get(name);
        if (p != null || missing.contains(name)) return p;
        p = this.getAnyDescendantWithName(name).orElse(null);
        if (p == null) missing.add(name);
        else byName.put(name, p);
        return p;
    }

    private AnimationDefinition anim(String name) {
        return name == null ? null : data.animations.get(name);
    }

    @Override
    public void setupAnim(T e, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
        this.root().getAllParts().forEach(ModelPart::resetPose);
        for (PartDef pd : e.profile().parts.values()) {
            for (String b : pd.hideBones) {
                ModelPart p = bone(b);
                if (p != null) p.visible = true;
            }
        }
        String state = e.getStateAnimName();
        String action = e.getActionAnimName();
        if (state != null && anim(state) != null) {
            this.animate(e.stateAnim, anim(state), ageInTicks);
        } else {
            boolean acting = action != null && anim(action) != null;
            if (e.isFlying() && anim("fly") != null) {
                this.animate(e.flyAnim, anim("fly"), ageInTicks);
            } else {
                AnimationDefinition walk = e.isRunning() && anim("run") != null ? anim("run") : anim("walk");
                if (walk != null) {
                    float speed = data.walkSpeed * (e.isRunning() ? data.runSpeed : 1.0F) / Math.max(0.5F, e.getMonsterScale());
                    if (e.isLimping()) speed *= 0.6F;
                    this.animateWalk(walk, limbSwing, limbSwingAmount * (acting ? 0.2F : 1.0F), speed, data.walkScale);
                }
                if (anim("idle") != null) this.animate(e.idleAnim, anim("idle"), ageInTicks);
            }
            if (acting) {
                this.animate(e.actionAnim, anim(action), ageInTicks, data.actionSpeed);
            } else {
                for (HcModelLoader.HeadBone hb : data.headBones) {
                    ModelPart p = bone(hb.name());
                    if (p != null) {
                        p.yRot += netHeadYaw * DEG * hb.weight();
                        p.xRot += headPitch * DEG * hb.weight();
                    }
                }
            }
        }
        // hide broken parts
        int mask = e.getBrokenMask();
        if (mask != 0) {
            int i = 0;
            for (PartDef pd : e.profile().parts.values()) {
                if ((mask & (1 << i)) != 0) {
                    for (String b : pd.hideBones) {
                        ModelPart p = bone(b);
                        if (p != null) p.visible = false;
                    }
                }
                i++;
            }
        }
    }
}
