package com.huntcraft.client.player;

import com.huntcraft.HuntCraft;
import com.huntcraft.client.model.HcModelLoader;
import com.huntcraft.client.model.HcModels;
import net.minecraft.client.Minecraft;
import net.minecraft.client.animation.AnimationDefinition;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import org.jetbrains.annotations.Nullable;

/** Mod-bus registration of the hunter rig layers + lazily baked models and animation lookup. */
@Mod.EventBusSubscriber(modid = HuntCraft.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class HunterModels {
    private static HunterModel wide;
    private static HunterModel slim;

    private HunterModels() {
    }

    @SubscribeEvent
    public static void layers(EntityRenderersEvent.RegisterLayerDefinitions event) {
        HcModels.register(event, "hunter");
        HcModels.register(event, "hunter_slim");
        wide = null;
        slim = null;
    }

    @SubscribeEvent
    public static void clientSetup(FMLClientSetupEvent event) {
        CombatClient.registerClientActions();
    }

    public static HunterModel get(boolean slimArms) {
        if (slimArms) {
            if (slim == null) slim = new HunterModel(Minecraft.getInstance().getEntityModels().bakeLayer(HcModels.layer("hunter_slim")), true);
            return slim;
        }
        if (wide == null) wide = new HunterModel(Minecraft.getInstance().getEntityModels().bakeLayer(HcModels.layer("hunter")), false);
        return wide;
    }

    @Nullable
    public static AnimationDefinition anim(String name) {
        return name == null ? null : HcModelLoader.get("hunter").animations.get(name);
    }
}
