package com.huntcraft.gear.weapon.client;

import com.huntcraft.HuntCraft;
import com.huntcraft.client.HCClientSetup;
import com.huntcraft.core.WeaponClass;
import com.huntcraft.gear.weapon.WeaponLine;
import com.huntcraft.gear.weapon.WeaponNet;
import com.huntcraft.gear.weapon.WeaponState;
import com.huntcraft.gear.weapon.WeaponsModule;
import com.huntcraft.network.HCNetwork;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import org.lwjgl.glfw.GLFW;

/** Client registration of the weapon module (mod bus): sharpen key, HUD overlay, arrow renderer, bow model state. */
@Mod.EventBusSubscriber(modid = HuntCraft.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class WeaponClient {
    public static final KeyMapping SHARPEN = new KeyMapping("key.huntcraft.sharpen", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_R, HCClientSetup.KEY_CATEGORY);

    private WeaponClient() {
    }

    @SubscribeEvent
    public static void keys(RegisterKeyMappingsEvent event) {
        event.register(SHARPEN);
    }

    @SubscribeEvent
    public static void overlays(RegisterGuiOverlaysEvent event) {
        event.registerAbove(VanillaGuiOverlay.HOTBAR.id(), "weapon_hud", WeaponHud::render);
    }

    @SubscribeEvent
    public static void renderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(WeaponsModule.HUNTER_ARROW.get(), HunterArrowRenderer::new);
    }

    @SubscribeEvent
    public static void setup(FMLClientSetupEvent event) {
        HCNetwork.registerClientAction(WeaponNet.STATE, tag -> {
            Player p = Minecraft.getInstance().player;
            if (p != null) WeaponState.get(p).readSyncTag(tag);
        });
        event.enqueueWork(() -> {
            ResourceLocation drawn = HuntCraft.id("drawn");
            for (WeaponLine l : WeaponLine.values()) {
                Item bow = WeaponsModule.weapon(WeaponClass.BOW, l).get();
                ItemProperties.register(bow, drawn, (stack, level, entity, seed) ->
                        entity != null && entity.isUsingItem() && entity.getUseItem() == stack ? 1.0F : 0.0F);
            }
        });
    }
}
