package com.eternalrealm.client.entity.model;

import com.eternalrealm.npc.forge.NpcEntity;
import net.minecraft.client.animation.KeyframeAnimations;
import net.minecraft.client.model.geom.ModelPart;
import org.joml.Vector3f;

/**
 * Model of the town NPCs; the appearance selects parts, rest pose and animations. Layers, added every frame: head look,
 * the walk cycle while moving, the idle loop (breathing, weight shift, blinking), the appearance's work loop (hammering,
 * writing, sweeping...) faded out while a player stands close, while walking and while talking, and the talk gesture.
 */
public class NpcModel extends RigModel<NpcEntity> {
    /** Animation slots of the NPC rig (tools/art_entities/rig_npc.py). */
    public static final int IDLE = 0;
    public static final int WALK = 1;
    public static final int WORK = 2;
    public static final int TALK = 3;

    private final float walkSpeed;
    private final Vector3f cache = new Vector3f();

    /** @param walkSpeed walk cycle speed for {@code animateWalk}, divided by the appearance's scale */
    public NpcModel(ModelPart root, RigTables tables, float walkSpeed) {
        super(root, tables);
        this.walkSpeed = walkSpeed;
    }

    @Override
    public void setupAnim(NpcEntity npc, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw,
                          float headPitch) {
        int index = prepare(npc.getAppearance());
        lookAt(netHeadYaw, headPitch, 1.0F);
        float walking = Math.min(1.0F, limbSwingAmount * 2.5F);
        if (walking > 0.01F) {
            animateWalk(animation(index, WALK), limbSwing, limbSwingAmount, this.walkSpeed / scaleOf(index), 2.0F);
        }
        animate(npc.idleAnimation, animation(index, IDLE), ageInTicks);
        npc.workAnimation.updateTime(ageInTicks, 1.0F);
        float work = npc.workWeight(ageInTicks) * (1.0F - walking);
        if (work > 0.01F && npc.workAnimation.isStarted()) {
            KeyframeAnimations.animate(this, animation(index, WORK), npc.workAnimation.getAccumulatedTime(), work,
                    this.cache);
        }
        animate(npc.talkAnimation, animation(index, TALK), ageInTicks);
    }
}
