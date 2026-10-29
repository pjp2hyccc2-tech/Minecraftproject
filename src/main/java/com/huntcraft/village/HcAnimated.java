package com.huntcraft.village;

import net.minecraft.world.entity.AnimationState;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.Set;

/** Entities of the village module that are drawn with an hcmodel (mofu companions / NPCs, training dummy). */
public interface HcAnimated {
    /** Always-on loop ("idle"). */
    AnimationState hcIdle();

    /** One-shot action layer. */
    AnimationState hcAction();

    /** Name of the action animation currently playing (or null). */
    @Nullable
    String hcActionName();

    /** Model bones that must be hidden for this entity (accessories). */
    default Set<String> hcHidden() {
        return Collections.emptySet();
    }

    /** Model bones that must be shown (accessories hidden by default). */
    default Set<String> hcShown() {
        return Collections.emptySet();
    }
}
