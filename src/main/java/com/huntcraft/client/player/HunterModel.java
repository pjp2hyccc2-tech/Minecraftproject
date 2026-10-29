package com.huntcraft.client.player;

import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.RenderType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The hunter rig (assets/huntcraft/hcmodels/hunter[_slim].json, built by tools/models/hunter.py):
 * root > hips > (leg_r > leg_r_lower, leg_l > leg_l_lower, chest > (head, arm_r > arm_r_lower > hand_r,
 * arm_l > arm_l_lower > hand_l)). Hand bones are wrist joints without cubes: held items are rendered from them.
 * Posing is done by {@link HunterAnimator}; {@link #setupAnim} is intentionally empty.
 */
@OnlyIn(Dist.CLIENT)
public class HunterModel extends HierarchicalModel<AbstractClientPlayer> {
    public static final String[] BONES = {"root", "hips", "leg_r", "leg_r_lower", "leg_l", "leg_l_lower", "chest", "head",
            "arm_r", "arm_r_lower", "hand_r", "arm_l", "arm_l_lower", "hand_l"};
    /** Parent of every bone (root has none). */
    public static final Map<String, String> PARENT = new HashMap<>();
    public static final Map<String, List<String>> CHILDREN = new HashMap<>();

    static {
        String[][] edges = {{"root", "hips"}, {"hips", "leg_r"}, {"leg_r", "leg_r_lower"}, {"hips", "leg_l"}, {"leg_l", "leg_l_lower"},
                {"hips", "chest"}, {"chest", "head"}, {"chest", "arm_r"}, {"arm_r", "arm_r_lower"}, {"arm_r_lower", "hand_r"},
                {"chest", "arm_l"}, {"arm_l", "arm_l_lower"}, {"arm_l_lower", "hand_l"}};
        for (String b : BONES) CHILDREN.put(b, new java.util.ArrayList<>());
        for (String[] e : edges) {
            PARENT.put(e[1], e[0]);
            CHILDREN.get(e[0]).add(e[1]);
        }
    }

    private final ModelPart root;
    private final Map<String, ModelPart> bones = new HashMap<>();
    public final ModelPart rootBone, hips, chest, head, armR, armRLower, handR, armL, armLLower, handL, legR, legRLower, legL, legLLower;
    public final boolean slim;

    public HunterModel(ModelPart root, boolean slim) {
        super(RenderType::entityTranslucent);
        this.root = root;
        this.slim = slim;
        rootBone = root.getChild("root");
        hips = rootBone.getChild("hips");
        legR = hips.getChild("leg_r");
        legRLower = legR.getChild("leg_r_lower");
        legL = hips.getChild("leg_l");
        legLLower = legL.getChild("leg_l_lower");
        chest = hips.getChild("chest");
        head = chest.getChild("head");
        armR = chest.getChild("arm_r");
        armRLower = armR.getChild("arm_r_lower");
        handR = armRLower.getChild("hand_r");
        armL = chest.getChild("arm_l");
        armLLower = armL.getChild("arm_l_lower");
        handL = armLLower.getChild("hand_l");
        ModelPart[] parts = {rootBone, hips, legR, legRLower, legL, legLLower, chest, head, armR, armRLower, handR, armL, armLLower, handL};
        for (int i = 0; i < BONES.length; i++) bones.put(BONES[i], parts[i]);
    }

    @Override
    public ModelPart root() {
        return root;
    }

    @Nullable
    public ModelPart bone(String name) {
        return bones.get(name);
    }

    public void resetAll() {
        root.getAllParts().forEach(ModelPart::resetPose);
    }

    @Override
    public void setupAnim(AbstractClientPlayer entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
    }

    /** Pushes the transforms of every bone from the root down to `bone` (inclusive) onto the stack. */
    public void translateTo(String bone, com.mojang.blaze3d.vertex.PoseStack ps) {
        java.util.ArrayDeque<String> chain = new java.util.ArrayDeque<>();
        String b = bone;
        while (b != null) {
            chain.push(b);
            b = PARENT.get(b);
        }
        for (String n : chain) bones.get(n).translateAndRotate(ps);
    }
}
