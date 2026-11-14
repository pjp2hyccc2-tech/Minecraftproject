package com.eternalrealm.client.estate;

import com.eternalrealm.client.ClientChannels;
import com.eternalrealm.client.ClientKeyMappings;
import com.eternalrealm.core.ModInfo;
import com.eternalrealm.estate.RecallRules;
import com.eternalrealm.estate.forge.InnService;
import com.eternalrealm.estate.forge.LedgerService;
import com.eternalrealm.ui.estate.InnScreen;
import com.eternalrealm.ui.estate.LedgerScreen;
import com.eternalrealm.ui.estate.RecallOverlay;
import com.eternalrealm.ui.estate.RoomExitScreen;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import org.lwjgl.glfw.GLFW;

/**
 * The inn on the client (mod bus): the room key (Home, in the game's key category) that starts the way home and the
 * capital key (End) that starts the way to the royal capital, the
 * screens that open when the server sends the innkeeper's, the ledger's or the door's document, and the cast bar.
 */
@Mod.EventBusSubscriber(modid = ModInfo.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class EstateClient {
    /** Goes home to one's room (pressed again while casting: stops). */
    public static final KeyMapping RECALL = new KeyMapping("key." + ModInfo.MOD_ID + ".recall_room",
            KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_HOME, ClientKeyMappings.CATEGORY);
    /** Goes to the royal capital from anywhere (pressed again while casting: stops). */
    public static final KeyMapping TO_CAPITAL = new KeyMapping("key." + ModInfo.MOD_ID + ".recall_capital",
            KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_END, ClientKeyMappings.CATEGORY);

    private EstateClient() {
    }

    @SubscribeEvent
    public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(RECALL);
        event.register(TO_CAPITAL);
    }

    @SubscribeEvent
    public static void onRegisterOverlays(RegisterGuiOverlaysEvent event) {
        event.registerAbove(VanillaGuiOverlay.HOTBAR.id(), "estate_recall", new RecallOverlay());
    }

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            ClientChannels.register(InnService.CHANNEL, InnScreen::accept);
            ClientChannels.register(LedgerService.CHANNEL, LedgerScreen::accept);
            ClientChannels.register(InnService.EXIT_CHANNEL, RoomExitScreen::accept);
            ClientChannels.register(RecallRules.CHANNEL, RecallOverlay::accept);
        });
    }
}
