package com.eternalrealm.client.entity;

import com.eternalrealm.core.ModInfo;
import com.eternalrealm.entity.BossRig;
import com.eternalrealm.entity.EnemyRig;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.resources.ResourceLocation;

/** Model layers: one per enemy rig, two per boss rig (body and the inflated shield overlay), one for the NPCs. */
public final class ERModelLayers {
    /** Extra size of every box of the shield overlay model. */
    public static final float SHIELD_INFLATE = 0.9F;

    private ERModelLayers() {
    }

    public static ModelLayerLocation enemy(EnemyRig rig) {
        return new ModelLayerLocation(new ResourceLocation(ModInfo.MOD_ID, rig.id() + "_enemy"), "main");
    }

    public static ModelLayerLocation boss(BossRig rig) {
        return new ModelLayerLocation(new ResourceLocation(ModInfo.MOD_ID, rig.id() + "_boss"), "main");
    }

    public static ModelLayerLocation bossShield(BossRig rig) {
        return new ModelLayerLocation(new ResourceLocation(ModInfo.MOD_ID, rig.id() + "_boss"), "shield");
    }

    /** The town NPC rig (all appearances share it). */
    public static ModelLayerLocation npc() {
        return new ModelLayerLocation(new ResourceLocation(ModInfo.MOD_ID, "npc"), "main");
    }
}
