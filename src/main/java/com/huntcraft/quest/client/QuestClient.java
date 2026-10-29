package com.huntcraft.quest.client;

import com.huntcraft.HuntCraft;
import com.huntcraft.client.HCClientSetup;
import com.huntcraft.network.HCNetwork;
import com.huntcraft.network.HCScreens;
import com.huntcraft.quest.QuestHudState;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import org.lwjgl.glfw.GLFW;

/** Client registration of the quest module: screens, network handlers, quest HUD overlay. */
@Mod.EventBusSubscriber(modid = HuntCraft.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class QuestClient {
    /** Opens the quest map during a quest. */
    public static final KeyMapping QUEST_MAP = new KeyMapping("key.huntcraft.quest_map", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_M, HCClientSetup.KEY_CATEGORY);

    private QuestClient() {
    }

    @SubscribeEvent
    public static void keys(RegisterKeyMappingsEvent event) {
        event.register(QUEST_MAP);
    }

    @SubscribeEvent
    public static void setup(FMLClientSetupEvent event) {
        HCScreens.registerClient(HCScreens.QUEST_BOARD, tag -> Minecraft.getInstance().setScreen(new QuestBoardScreen(tag)));
        HCScreens.registerClient(HCScreens.QUEST_RESULT, tag -> Minecraft.getInstance().setScreen(new QuestResultScreen(tag)));
        HCNetwork.registerClientAction("quest/board_data", QuestBoardScreen::onData);
        HCNetwork.registerClientAction("quest/info", QuestHudState::readInfo);
        HCNetwork.registerClientAction("quest/hud", tag -> {
            Minecraft mc = Minecraft.getInstance();
            QuestHudState.readHud(tag, mc.level != null ? mc.level.getGameTime() : 0L);
        });
        HCNetwork.registerClientAction("quest/open_map", tag -> {
            if (QuestHudState.active) Minecraft.getInstance().setScreen(new QuestMapScreen());
        });
    }

    @SubscribeEvent
    public static void overlays(RegisterGuiOverlaysEvent event) {
        event.registerAbove(VanillaGuiOverlay.HOTBAR.id(), "quest_hud", QuestHudOverlay::render);
    }
}
