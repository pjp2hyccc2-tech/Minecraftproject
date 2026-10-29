package com.huntcraft.client;

import com.huntcraft.HuntCraft;
import com.huntcraft.client.hud.HunterHud;
import com.huntcraft.client.render.MonsterProjectileRenderer;
import com.huntcraft.client.render.TrapRenderer;
import com.huntcraft.data.HunterData;
import com.huntcraft.network.HCNetwork;
import com.huntcraft.registry.HCEntities;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.NoopRenderer;
import net.minecraft.client.renderer.entity.ThrownItemRenderer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import org.lwjgl.glfw.GLFW;

/** Core client registration (mod bus). */
@Mod.EventBusSubscriber(modid = HuntCraft.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class HCClientSetup {
    public static final String KEY_CATEGORY = "key.categories.huntcraft";
    public static final KeyMapping DODGE = new KeyMapping("key.huntcraft.dodge", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_LEFT_ALT, KEY_CATEGORY);

    private HCClientSetup() {
    }

    @SubscribeEvent
    public static void keys(RegisterKeyMappingsEvent event) {
        event.register(DODGE);
    }

    @SubscribeEvent
    public static void renderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(HCEntities.TRAP.get(), TrapRenderer::new);
        event.registerEntityRenderer(HCEntities.THROWN_ITEM.get(), ThrownItemRenderer::new);
        event.registerEntityRenderer(HCEntities.MONSTER_PROJECTILE.get(), MonsterProjectileRenderer::new);
        event.registerEntityRenderer(HCEntities.HAZARD.get(), NoopRenderer::new);
    }

    @SubscribeEvent
    public static void overlays(RegisterGuiOverlaysEvent event) {
        event.registerAbove(VanillaGuiOverlay.HOTBAR.id(), "hunter_hud", HunterHud::render);
    }

    @SubscribeEvent
    public static void clientSetup(FMLClientSetupEvent event) {
        HCNetwork.registerClientAction("core/hunter_data", tag -> {
            Player p = Minecraft.getInstance().player;
            if (p != null) HunterData.get(p).load(tag);
        });
        HCNetwork.registerClientAction("core/stamina", tag -> {
            Player p = Minecraft.getInstance().player;
            if (p == null) return;
            HunterData d = HunterData.get(p);
            d.stamina = tag.getFloat("s");
            d.maxStamina = tag.getFloat("m");
            d.exhausted = tag.getBoolean("e");
        });
        HCNetwork.registerClientAction("core/dmg", tag -> ClientState.addDamageNumber(
                new Vec3(tag.getDouble("x"), tag.getDouble("y"), tag.getDouble("z")), tag.getFloat("a"), tag.getInt("c"), tag.getBoolean("k")));
        HCNetwork.registerClientAction("core/shake", tag -> ClientState.addShake(tag.getFloat("i"), tag.getInt("t")));
        HCNetwork.registerClientAction("core/dodge_anim", tag -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level == null) return;
            int id = tag.getInt("id");
            if (mc.player != null && id == mc.player.getId() && ClientState.DODGES.containsKey(id)) return;
            ClientState.DODGES.put(id, new ClientState.Dodge(mc.level.getGameTime(), tag.getInt("dir")));
        });
    }
}
