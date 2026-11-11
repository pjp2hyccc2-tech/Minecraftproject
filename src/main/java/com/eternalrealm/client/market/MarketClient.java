package com.eternalrealm.client.market;

import com.eternalrealm.client.ClientChannels;
import com.eternalrealm.client.ClientKeyMappings;
import com.eternalrealm.core.ModInfo;
import com.eternalrealm.economy.forge.MarketScreens;
import com.eternalrealm.ui.market.AuctionScreen;
import com.eternalrealm.ui.market.BoardScreen;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import org.lwjgl.glfw.GLFW;

/**
 * The market on the client (mod bus): the auction hall key (O, in the game's key category) and the screens that open
 * when the server sends the board or the hall ({@link MarketScreens}).
 */
@Mod.EventBusSubscriber(modid = ModInfo.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class MarketClient {
    /** Opens the auction hall from anywhere. */
    public static final KeyMapping OPEN_AUCTION = new KeyMapping("key." + ModInfo.MOD_ID + ".open_auction",
            KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_O, ClientKeyMappings.CATEGORY);

    private MarketClient() {
    }

    @SubscribeEvent
    public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(OPEN_AUCTION);
    }

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            ClientChannels.register(MarketScreens.HALL_CHANNEL, AuctionScreen::accept);
            ClientChannels.register(MarketScreens.BOARD_CHANNEL, BoardScreen::accept);
        });
    }
}
