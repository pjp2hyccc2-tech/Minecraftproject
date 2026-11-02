package com.birdiecraft;

import com.birdiecraft.command.GolfCommand;
import com.birdiecraft.course.CourseBuilder;
import com.birdiecraft.match.MatchManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Server / common game events. */
@Mod.EventBusSubscriber(modid = BirdieCraft.MODID)
public final class CommonEvents {
    private CommonEvents() {
    }

    @SubscribeEvent
    public static void onServerStarting(ServerStartingEvent event) {
        MatchManager.onServerStart(event.getServer());
        CourseBuilder.clear();
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        MatchManager.onServerStop();
        CourseBuilder.clear();
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        MatchManager.tick();
        CourseBuilder.tick();
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        GolfCommand.register(event.getDispatcher());
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp) MatchManager.onLogout(sp);
    }
}
