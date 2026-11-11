package com.eternalrealm.guild.house.forge;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;

/**
 * Wiring of the guild houses (docs/modules/guildhouse.md), called once from the mod's constructor: blocks, the broker,
 * the screens' actions and the server events.
 */
public final class GuildHouseModule {
    private GuildHouseModule() {
    }

    public static void register(IEventBus modBus) {
        GuildHouseBlocks.register(modBus);
        GuildHouseUi.register();
        GuildBroker.register();
        IEventBus forgeBus = MinecraftForge.EVENT_BUS;
        forgeBus.addListener(GuildHouses::onServerStarted);
        forgeBus.addListener(GuildHouses::onServerTick);
        forgeBus.addListener(GuildHouses::onServerStopping);
        forgeBus.addListener(GuildHouseEvents::onRightClickBlock);
        forgeBus.addListener(GuildHouseEvents::onContainerOpen);
        forgeBus.addListener(GuildHouseEvents::onContainerClose);
        forgeBus.addListener(GuildHouseEvents::onLogout);
    }
}
