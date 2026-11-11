package com.eternalrealm.client.guildhouse;

import com.eternalrealm.client.ClientChannels;
import com.eternalrealm.core.ModInfo;
import com.eternalrealm.guild.house.forge.GuildHouseBlocks;
import com.eternalrealm.guild.house.forge.GuildHouseUi;
import com.eternalrealm.guild.house.forge.GuildPortalBlock;
import com.eternalrealm.ui.guildhouse.GuildBoardScreen;
import com.eternalrealm.ui.guildhouse.GuildHouseScreen;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterColorHandlersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/**
 * Client wiring of the guild houses: the portal's two tint layers take its theme's colours, the board draws the guild's
 * notes, and the catalogue and board documents open their screens.
 */
@Mod.EventBusSubscriber(modid = ModInfo.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class GuildHouseClient {
    private GuildHouseClient() {
    }

    @SubscribeEvent
    public static void onBlockColors(RegisterColorHandlersEvent.Block event) {
        event.register((state, level, pos, tint) -> {
            int[] colors = GuildPortalBlock.colors(state.getValue(GuildPortalBlock.THEME));
            return tint == 1 ? colors[1] : colors[0];
        }, GuildHouseBlocks.PORTAL.get());
    }

    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(GuildHouseBlocks.BOARD_ENTITY.get(), GuildBoardRenderer::new);
    }

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            ClientChannels.register(GuildHouseUi.CATALOGUE, GuildHouseScreen::accept);
            ClientChannels.register(GuildHouseUi.BOARD, GuildBoardScreen::accept);
        });
    }
}
