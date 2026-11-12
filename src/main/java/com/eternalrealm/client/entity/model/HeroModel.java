package com.eternalrealm.client.entity.model;

import com.eternalrealm.npc.HeroBodies;
import com.eternalrealm.npc.forge.TravellerEntity;
import net.minecraft.client.animation.AnimationDefinition;
import net.minecraft.client.animation.KeyframeAnimations;
import net.minecraft.client.model.geom.ModelPart;
import org.joml.Vector3f;

/**
 * Model of the four legendary travellers (tools/art_entities/rig_hero.py). Layers, blended by weight every frame:
 * the idle loop or the fighting stance (as the traveller is at ease or fighting), the walk or the run while moving
 * (fading under a move), the move being played (each authored as a whole pose, so it takes over the pose while it
 * plays and hands it back over a few ticks), the fallen pose, and the talk gesture. A few parts follow the moment
 * rather than the look: the swordsman's blade is in his hand while he fights or moves and in its scabbard otherwise,
 * his phantom blades only show in a fight.
 */
public class HeroModel extends RigModel<TravellerEntity> {
    private final ModelPart swordInHand;
    private final ModelPart swordSheathed;
    private final ModelPart phantoms;
    private final Vector3f cache = new Vector3f();

    public HeroModel(ModelPart root, RigTables tables) {
        super(root, tables);
        this.swordInHand = findOrNull(root, "sword_rune");
        this.swordSheathed = findOrNull(root, "sheathed");
        this.phantoms = findOrNull(root, "phantoms");
    }

    @Override
    public void setupAnim(TravellerEntity hero, float limbSwing, float limbSwingAmount, float ageInTicks,
                         float netHeadYaw, float headPitch) {
        int index = prepare(hero.heroAppearance());
        float action = hero.actionWeight(ageInTicks);
        float down = hero.downWeight(ageInTicks);
        float ready = hero.readyWeight(ageInTicks);
        int slot = hero.actionSlot();
        boolean kaylen = "traveller_kaylen".equals(hero.heroAppearance());
        if (kaylen) {
            boolean drawn = ready > 0.05F || (slot >= 0 && slot != HeroBodies.TALK);
            if (slot == HeroBodies.ULTIMATE && hero.actionAnimation.getAccumulatedTime() > 3150L) {
                drawn = false; // the blade has gone home
            }
            setVisible(this.swordInHand, drawn);
            setVisible(this.swordSheathed, !drawn);
            setVisible(this.phantoms, ready > 0.3F || slot == HeroBodies.SKILL_B || slot == HeroBodies.ULTIMATE);
        }
        float free = (1.0F - down) * (1.0F - action);
        lookAt(netHeadYaw, headPitch, free);
        hero.idleAnimation.updateTime(ageInTicks, 1.0F);
        long idleTime = hero.idleAnimation.getAccumulatedTime();
        weighted(animation(index, HeroBodies.IDLE), idleTime, free * (1.0F - ready));
        weighted(animation(index, HeroBodies.READY), idleTime, free * ready);
        float moving = Math.min(1.0F, limbSwingAmount * 2.2F) * (1.0F - down) * (1.0F - 0.75F * action);
        if (moving > 0.01F) {
            float scale = scaleOf(index);
            float run = Math.max(0.0F, Math.min(1.0F, (limbSwingAmount - 0.45F) * 3.0F));
            long walkTime = (long) (limbSwing * 50.0F * 2.0F / scale);
            weighted(animation(index, HeroBodies.WALK), walkTime, moving * (1.0F - run));
            long runTime = (long) (limbSwing * 50.0F * 1.6F / scale);
            weighted(animation(index, HeroBodies.RUN), runTime, moving * run);
        }
        if (slot >= 0 && action > 0.01F) {
            hero.actionAnimation.updateTime(ageInTicks, 1.0F);
            weighted(animation(index, slot), hero.actionAnimation.getAccumulatedTime(), action * (1.0F - down));
        }
        if (down > 0.01F) {
            weighted(animation(index, HeroBodies.DOWN), idleTime, down);
        }
        hero.talkAnimation.updateTime(ageInTicks, 1.0F);
        if (hero.talkAnimation.isStarted() && free > 0.01F) {
            weighted(animation(index, HeroBodies.TALK), hero.talkAnimation.getAccumulatedTime(), free);
        }
    }

    private void weighted(AnimationDefinition definition, long millis, float weight) {
        if (weight > 0.01F) {
            KeyframeAnimations.animate(this, definition, millis, weight, this.cache);
        }
    }

    private static void setVisible(ModelPart part, boolean visible) {
        if (part != null) {
            part.visible = visible;
        }
    }
}
