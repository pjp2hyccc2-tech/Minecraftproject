package com.eternalrealm.guild.house.forge;

import com.eternalrealm.estate.forge.EstateProtection;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;

/**
 * Wiring of the guild houses (docs/modules/guildhouse.md), called once from the mod's constructor: blocks and the
 * training dummy, the bridge to the estates core (member rule, "house ready"), the broker, the screens' actions and
 * the server events. Bells may be rung inside estates (the rally bell).
 */
public final class GuildHouseModule {
    private GuildHouseModule() {
    }

    public static void register(IEventBus modBus) {
        GuildHouseBlocks.register(modBus);
        EstateGuildEstates.install();
        modBus.addListener((FMLCommonSetupEvent event) -> event.enqueueWork(
                () -> EstateProtection.allowUse(Blocks.BELL)));
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
