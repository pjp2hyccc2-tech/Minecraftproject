package com.huntcraft.village.client;

import com.huntcraft.HuntCraft;
import com.huntcraft.client.model.HcModels;
import com.huntcraft.network.HCNetwork;
import com.huntcraft.network.HCScreens;
import com.huntcraft.village.VillageActions;
import com.huntcraft.village.VillageRegistry;
import com.huntcraft.village.client.render.VillageRenderers;
import com.huntcraft.village.client.screen.BlacksmithScreen;
import com.huntcraft.village.client.screen.CompanionScreen;
import com.huntcraft.village.client.screen.ElderScreen;
import com.huntcraft.village.client.screen.HunterNotesScreen;
import com.huntcraft.village.client.screen.ItemBoxScreen;
import com.huntcraft.village.client.screen.KitchenScreen;
import com.huntcraft.village.client.screen.MerchantScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.entity.ThrownItemRenderer;
import net.minecraft.nbt.CompoundTag;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

import java.util.function.Function;

/** Client registration of the village module (mod bus): models, renderers, screens, menus, HUD. */
@Mod.EventBusSubscriber(modid = HuntCraft.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class VillageClient {
    /** hcmodels of the village module (assets/huntcraft/hcmodels/NAME.json). */
    public static final String[] MODELS = {"mofu", "mofu_gear", "training_dummy", "npc_receptionist", "npc_blacksmith", "npc_merchant", "npc_elder"};

    private VillageClient() {
    }

    @SubscribeEvent
    public static void layers(EntityRenderersEvent.RegisterLayerDefinitions event) {
        for (String m : MODELS) HcModels.register(event, m);
    }

    @SubscribeEvent
    public static void renderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(VillageRegistry.HUMAN_NPC.get(), VillageRenderers.HumanNpc::new);
        event.registerEntityRenderer(VillageRegistry.MOFU_NPC.get(), VillageRenderers.MofuNpc::new);
        event.registerEntityRenderer(VillageRegistry.MOFU.get(), VillageRenderers.Mofu::new);
        event.registerEntityRenderer(VillageRegistry.TRAINING_DUMMY.get(), VillageRenderers.Dummy::new);
        event.registerEntityRenderer(VillageRegistry.MOFU_BOMB.get(), ThrownItemRenderer::new);
    }

    @SubscribeEvent
    public static void overlays(RegisterGuiOverlaysEvent event) {
        event.registerAbove(VanillaGuiOverlay.HOTBAR.id(), "village_hud", VillageHud::render);
    }

    @SubscribeEvent
    public static void clientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> MenuScreens.register(VillageRegistry.ITEM_BOX_MENU.get(), ItemBoxScreen::new));
        screen(HCScreens.BLACKSMITH, BlacksmithScreen::new);
        screen(HCScreens.MERCHANT, MerchantScreen::new);
        screen(HCScreens.KITCHEN, KitchenScreen::new);
        screen(HCScreens.COMPANION, CompanionScreen::new);
        screen(HCScreens.HUNTER_NOTES, HunterNotesScreen::new);
        screen(VillageActions.SCREEN_ELDER, ElderScreen::new);
        HCNetwork.registerClientAction("village/box", ClientVillage::loadBox);
    }

    private static void screen(String id, Function<CompoundTag, Screen> factory) {
        HCScreens.registerClient(id, tag -> Minecraft.getInstance().setScreen(factory.apply(tag)));
    }
}
