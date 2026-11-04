package jp.satsujinki.event;

import jp.satsujinki.Satsujinki;
import jp.satsujinki.command.SatsujinkiCommand;
import jp.satsujinki.entity.ContestantEntity;
import jp.satsujinki.game.DeathGameManager;
import jp.satsujinki.game.GoreHandler;
import jp.satsujinki.game.KillStyle;
import jp.satsujinki.game.ScoreManager;
import jp.satsujinki.game.TrapRegistry;
import jp.satsujinki.item.HostHandItem;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.server.ServerLifecycleHooks;

import javax.annotation.Nullable;
import java.util.UUID;

@Mod.EventBusSubscriber(modid = Satsujinki.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class CommonEvents {
    private CommonEvents() {
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ContestantEntity c) || !(c.level() instanceof ServerLevel level)) return;
        DamageSource src = event.getSource();
        KillStyle style = c.forcedStyle != null ? c.forcedStyle : KillStyle.fromSource(src, c.wasThrownRecently());
        HostHandItem.forgetEntity(c);
        if (!c.goreHandled) {
            c.goreHandled = true;
            GoreHandler.spawnRemains(level, c, style, Double.NaN, false, null);
        }
        ServerPlayer killer = resolveKiller(level, c, src);
        if (killer != null) ScoreManager.onKill(killer, c, style);
        DeathGameManager.onContestantDeath(c);
    }

    @Nullable
    private static ServerPlayer resolveKiller(ServerLevel level, ContestantEntity c, DamageSource src) {
        if (src.getEntity() instanceof ServerPlayer p) return p;
        if (c.wasThrownRecently() && c.getThrowerId() != null && level.getPlayerByUUID(c.getThrowerId()) instanceof ServerPlayer p) return p;
        if (c.getLastHurtByMob() instanceof ServerPlayer p) return p;
        UUID host = DeathGameManager.hostOf(c);
        if (host != null && level.getPlayerByUUID(host) instanceof ServerPlayer p) return p;
        Player near = level.getNearestPlayer(c.getX(), c.getY(), c.getZ(), 48.0D, false);
        return near instanceof ServerPlayer sp ? sp : null;
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;
        ScoreManager.tick(server);
        DeathGameManager.tick(server);
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp) ScoreManager.onLogin(sp);
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        HostHandItem.releaseAll(event.getEntity());
        ScoreManager.forget(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onCommands(RegisterCommandsEvent event) {
        SatsujinkiCommand.register(event.getDispatcher());
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        TrapRegistry.clear();
        ScoreManager.clear();
        DeathGameManager.clear();
        HostHandItem.clear();
    }
}
