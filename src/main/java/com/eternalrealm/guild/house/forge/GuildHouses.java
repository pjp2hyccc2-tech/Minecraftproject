package com.eternalrealm.guild.house.forge;

import com.eternalrealm.core.concurrent.KeyedSerialExecutor;
import com.eternalrealm.core.data.DataAccessException;
import com.eternalrealm.guild.Guild;
import com.eternalrealm.guild.GuildRank;
import com.eternalrealm.guild.house.GuildHouse;
import com.eternalrealm.guild.house.GuildHouseCatalog;
import com.eternalrealm.guild.house.GuildHouseDesign;
import com.eternalrealm.guild.house.GuildHouseOutcome;
import com.eternalrealm.guild.house.GuildHouseRepository;
import com.eternalrealm.guild.house.GuildHouseResult;
import com.eternalrealm.guild.house.GuildHouseService;
import com.eternalrealm.persistence.file.AtomicFileStore;
import com.eternalrealm.persistence.file.FileGuildHouseRepository;
import com.eternalrealm.server.ServerContext;
import com.eternalrealm.social.forge.Names;
import com.mojang.logging.LogUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import org.slf4j.Logger;

import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * The guild houses of the running server (docs/modules/guildhouse.md): the pure {@link GuildHouseService} (owned
 * designs, house funds, boards, storage logs; saved as {@code guild_houses.json} on the I/O thread), the guilds' ranks
 * from the guild module, gold from the players' characters, the houses themselves from the estates
 * ({@link GuildEstates}). Server thread only. A failed load keeps the guild houses closed instead of starting empty.
 */
public final class GuildHouses {
    private static final Logger LOG = LogUtils.getLogger();
    private static final UUID IO_KEY = new UUID(0L, 0x67686f757365L);
    private static GuildHouses current;
    private static GuildEstates estates = GuildEstates.UNAVAILABLE;

    private final MinecraftServer server;
    private final GuildHouseRepository repository;
    private final KeyedSerialExecutor<UUID> io;
    private final GuildHouseService service;
    private final boolean available;
    /** The last known master of every guild with house data (the gold of a disbanded guild goes to them). */
    private final Map<UUID, UUID> masters = new HashMap<>();
    private boolean saveInFlight;

    private GuildHouses(MinecraftServer server, GuildHouseRepository repository, KeyedSerialExecutor<UUID> io) {
        this.server = server;
        this.repository = repository;
        this.io = io;
        GuildHouseService loaded;
        boolean ok;
        try {
            loaded = new GuildHouseService(GuildHouseCatalog.standard(), repository.loadAll());
            ok = true;
        } catch (DataAccessException | RuntimeException e) {
            LOG.error("Could not load the guild houses; they stay closed until the data is fixed", e);
            loaded = new GuildHouseService(GuildHouseCatalog.standard(), List.of());
            ok = false;
        }
        this.service = loaded;
        this.available = ok;
        LOG.info("Loaded the house data of {} guild(s)", service.houses().size());
    }

    // ------------------------------------------------------------------ lifecycle

    public static Optional<GuildHouses> get() {
        return Optional.ofNullable(current);
    }

    /** The estates the houses are built in (installed once by the estates bridge). */
    public static GuildEstates estates() {
        return estates;
    }

    public static void setEstates(GuildEstates bridge) {
        estates = bridge == null ? GuildEstates.UNAVAILABLE : bridge;
    }

    public static void onServerStarted(ServerStartedEvent event) {
        ServerContext.current().filter(context -> context.server() == event.getServer()).ifPresent(context -> {
            AtomicFileStore store = new AtomicFileStore(context.dataDirectory().resolve("quarantine"),
                    Clock.systemUTC());
            current = new GuildHouses(event.getServer(), new FileGuildHouseRepository(context.dataDirectory(), store),
                    context.ioExecutor());
        });
    }

    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END && current != null && event.getServer() == current.server
                && event.getServer().getTickCount() % 20 == 7) {
            try {
                current.tickSecond();
            } catch (RuntimeException e) {
                LOG.error("Guild house tick failed", e);
            }
        }
    }

    public static void onServerStopping(ServerStoppingEvent event) {
        if (current != null && current.server == event.getServer()) {
            current.flush();
            current = null;
        }
    }

    public GuildHouseService service() {
        return service;
    }

    public boolean available() {
        return available;
    }

    public MinecraftServer server() {
        return server;
    }

    // ------------------------------------------------------------------ guild and gold

    public static Optional<Guild> guildOf(ServerPlayer player) {
        return ServerContext.current().flatMap(context -> context.game().guilds().service().guildOf(player.getUUID()));
    }

    public static GuildRank rankOf(Optional<Guild> guild, UUID player) {
        return guild.flatMap(g -> g.rankOf(player)).orElse(null);
    }

    static long gold(ServerPlayer player) {
        return ServerContext.current().map(context -> context.playerData().gold(player.getUUID())).orElse(0L);
    }

    private static boolean spend(ServerPlayer player, long amount) {
        return amount <= 0 || ServerContext.current().map(context -> context.playerData()
                .spendGold(player.getUUID(), amount)).orElse(false);
    }

    /** Gold to a player: straight into an online character's purse, the rest (or all, when away) by mail. */
    private void give(UUID player, long amount, String reason) {
        if (amount <= 0) {
            return;
        }
        ServerContext.current().ifPresent(context -> {
            long added = server.getPlayerList().getPlayer(player) == null ? 0L
                    : context.playerData().grantGold(player, amount).orElse(0L);
            if (added < amount) {
                context.game().market().house().sendGold(player, amount - added, reason);
            }
        });
    }

    // ------------------------------------------------------------------ operations of the catalogue screen

    /** The master buys {@code design}: fund first, then their gold; the house starts building. */
    public GuildHouseOutcome buy(ServerPlayer player, String design) {
        Optional<Guild> guild = guildOf(player);
        GuildRank rank = rankOf(guild, player.getUUID());
        if (!available || !estates.available()) {
            return fail(player, GuildHouseOutcome.UNAVAILABLE);
        }
        if (guild.isEmpty()) {
            return fail(player, GuildHouseOutcome.NOT_IN_GUILD);
        }
        UUID guildId = guild.get().id();
        GuildHouseResult check = service.checkBuy(guildId, rank, design, gold(player));
        if (!check.isSuccess()) {
            return fail(player, check.outcome());
        }
        if (!spend(player, check.fromWallet())) {
            return fail(player, GuildHouseOutcome.NOT_ENOUGH_GOLD);
        }
        GuildEstates.Result claimed = estates.claim(guildId, design);
        if (claimed != GuildEstates.Result.OK) {
            give(player.getUUID(), check.fromWallet(), "guild house purchase refund");
            LOG.warn("Claiming the guild house {} of {} failed: {}", design, guildId, claimed);
            return fail(player, GuildHouseOutcome.UNAVAILABLE);
        }
        GuildHouseResult done = service.buy(guildId, rank, design, check.fromWallet(), Instant.now());
        masters.put(guildId, guild.get().master());
        paid(player, done);
        toGuild(guild.get(), Component.translatable("message.eternalrealm.guildhouse.bought", player.getDisplayName(),
                name(design)).withStyle(ChatFormatting.GOLD));
        player.playNotifySound(SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.8F, 1.2F);
        return GuildHouseOutcome.OK;
    }

    /** The master switches the house to {@code design} (the trade-in counts, the rest is paid like a purchase). */
    public GuildHouseOutcome switchTo(ServerPlayer player, String design) {
        Optional<Guild> guild = guildOf(player);
        GuildRank rank = rankOf(guild, player.getUUID());
        if (!available || !estates.available()) {
            return fail(player, GuildHouseOutcome.UNAVAILABLE);
        }
        if (guild.isEmpty()) {
            return fail(player, GuildHouseOutcome.NOT_IN_GUILD);
        }
        UUID guildId = guild.get().id();
        GuildHouseResult check = service.checkSwitch(guildId, rank, design, gold(player));
        if (!check.isSuccess()) {
            return fail(player, check.outcome());
        }
        if (!spend(player, check.fromWallet())) {
            return fail(player, GuildHouseOutcome.NOT_ENOUGH_GOLD);
        }
        GuildEstates.Result moved = estates.redesign(guildId, design);
        if (moved != GuildEstates.Result.OK) {
            give(player.getUUID(), check.fromWallet(), "guild house switch refund");
            LOG.warn("Redesigning the guild house of {} as {} failed: {}", guildId, design, moved);
            return fail(player, moved == GuildEstates.Result.BUILDING ? GuildHouseOutcome.BUILDING
                    : GuildHouseOutcome.UNAVAILABLE);
        }
        GuildHouseResult done = service.switchTo(guildId, rank, design, check.fromWallet(), Instant.now());
        paid(player, done);
        if (done.toFund() > 0) {
            player.sendSystemMessage(Component.translatable("message.eternalrealm.guildhouse.refund_fund",
                    String.format("%,d", done.toFund())).withStyle(ChatFormatting.YELLOW));
        }
        toGuild(guild.get(), Component.translatable("message.eternalrealm.guildhouse.switched",
                player.getDisplayName(), name(design)).withStyle(ChatFormatting.GOLD));
        return GuildHouseOutcome.OK;
    }

    /** The master gives the house up; half its price goes into the fund. */
    public GuildHouseOutcome sell(ServerPlayer player) {
        Optional<Guild> guild = guildOf(player);
        GuildRank rank = rankOf(guild, player.getUUID());
        if (!available || !estates.available()) {
            return fail(player, GuildHouseOutcome.UNAVAILABLE);
        }
        if (guild.isEmpty()) {
            return fail(player, GuildHouseOutcome.NOT_IN_GUILD);
        }
        UUID guildId = guild.get().id();
        GuildHouseResult check = service.checkSell(guildId, rank);
        if (!check.isSuccess()) {
            return fail(player, check.outcome());
        }
        GuildEstates.Result released = estates.release(guildId);
        if (released != GuildEstates.Result.OK && released != GuildEstates.Result.NOT_CLAIMED) {
            return fail(player, GuildHouseOutcome.UNAVAILABLE);
        }
        GuildHouseResult done = service.sell(guildId, rank);
        toGuild(guild.get(), Component.translatable("message.eternalrealm.guildhouse.sold", player.getDisplayName(),
                String.format("%,d", done.toFund())).withStyle(ChatFormatting.YELLOW));
        return GuildHouseOutcome.OK;
    }

    /** Any member pays gold into the house fund. */
    public GuildHouseOutcome donate(ServerPlayer player, long amount) {
        Optional<Guild> guild = guildOf(player);
        if (!available) {
            return fail(player, GuildHouseOutcome.UNAVAILABLE);
        }
        if (guild.isEmpty()) {
            return fail(player, GuildHouseOutcome.NOT_IN_GUILD);
        }
        UUID guildId = guild.get().id();
        GuildRank rank = rankOf(guild, player.getUUID());
        GuildHouse house = service.house(guildId);
        if (amount <= 0 || amount > gold(player) || amount > com.eternalrealm.guild.house.GuildHouseRules.MAX_FUND
                - house.fund()) {
            return fail(player, GuildHouseOutcome.INVALID_AMOUNT);
        }
        if (!spend(player, amount)) {
            return fail(player, GuildHouseOutcome.INVALID_AMOUNT);
        }
        GuildHouseResult done = service.donate(guildId, rank, amount, amount);
        if (!done.isSuccess()) {
            give(player.getUUID(), amount, "guild house fund refund");
            return fail(player, done.outcome());
        }
        masters.put(guildId, guild.get().master());
        toGuild(guild.get(), Component.translatable("message.eternalrealm.guildhouse.donated", player.getDisplayName(),
                String.format("%,d", amount), String.format("%,d", done.house().fund())).withStyle(ChatFormatting.GREEN));
        player.playNotifySound(SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.7F, 1.0F);
        return GuildHouseOutcome.OK;
    }

    // ------------------------------------------------------------------ the board

    public GuildHouseOutcome setMotd(ServerPlayer player, String text) {
        Optional<Guild> guild = guildOf(player);
        if (guild.isEmpty()) {
            return fail(player, GuildHouseOutcome.NOT_IN_GUILD);
        }
        GuildHouseResult done = service.setMotd(guild.get().id(), rankOf(guild, player.getUUID()), text);
        if (!done.isSuccess()) {
            return fail(player, done.outcome());
        }
        if (!done.house().motd().isEmpty()) {
            toGuild(guild.get(), Component.translatable("message.eternalrealm.guildhouse.board_updated",
                    done.house().motd()).withStyle(ChatFormatting.AQUA));
        }
        GuildHouseUi.refreshBoards(guild.get());
        return GuildHouseOutcome.OK;
    }

    public GuildHouseOutcome addNote(ServerPlayer player, String text) {
        Optional<Guild> guild = guildOf(player);
        if (guild.isEmpty()) {
            return fail(player, GuildHouseOutcome.NOT_IN_GUILD);
        }
        GuildHouseResult done = service.addNote(guild.get().id(), rankOf(guild, player.getUUID()), player.getUUID(),
                player.getGameProfile().getName(), text, Instant.now());
        if (!done.isSuccess()) {
            return fail(player, done.outcome());
        }
        GuildHouseUi.refreshBoards(guild.get());
        return GuildHouseOutcome.OK;
    }

    public GuildHouseOutcome removeNote(ServerPlayer player, long noteId) {
        Optional<Guild> guild = guildOf(player);
        if (guild.isEmpty()) {
            return fail(player, GuildHouseOutcome.NOT_IN_GUILD);
        }
        GuildHouseResult done = service.removeNote(guild.get().id(), player.getUUID(), rankOf(guild, player.getUUID()),
                noteId);
        if (!done.isSuccess()) {
            return fail(player, done.outcome());
        }
        GuildHouseUi.refreshBoards(guild.get());
        return GuildHouseOutcome.OK;
    }

    // ------------------------------------------------------------------ messages

    static MutableComponent name(String design) {
        return GuildHouseCatalog.standard().design(design).map(d -> Component.translatable(d.nameKey()))
                .orElse(Component.literal(design));
    }

    private static void paid(ServerPlayer player, GuildHouseResult done) {
        if (done.fromFund() > 0 || done.fromWallet() > 0) {
            player.sendSystemMessage(Component.translatable("message.eternalrealm.guildhouse.paid",
                    String.format("%,d", done.fromFund()), String.format("%,d", done.fromWallet()))
                    .withStyle(ChatFormatting.GRAY));
        }
    }

    static GuildHouseOutcome fail(ServerPlayer player, GuildHouseOutcome outcome) {
        if (outcome == GuildHouseOutcome.BUILDING) {
            player.displayClientMessage(Component.translatable("message.eternalrealm.guildhouse.error.building",
                    guildOf(player).map(g -> estates.percent(g.id())).orElse(0)).withStyle(ChatFormatting.YELLOW), true);
        } else {
            outcome.messageKey().ifPresent(key -> player.displayClientMessage(Component.translatable(key)
                    .withStyle(ChatFormatting.RED), true));
        }
        return outcome;
    }

    void toGuild(Guild guild, Component message) {
        for (UUID member : guild.members().keySet()) {
            Names.send(server, member, message);
        }
    }

    /** A house finished building (first build or redesign): the guild hears of it. */
    public void onReady(UUID guildId) {
        ServerContext.current().flatMap(context -> context.game().guilds().service().guild(guildId)).ifPresent(guild ->
                service.design(guildId).ifPresent(design -> toGuild(guild, Component.translatable(
                        "message.eternalrealm.guildhouse.ready", Component.translatable(design.nameKey()))
                        .withStyle(ChatFormatting.GOLD))));
    }

    // ------------------------------------------------------------------ every second

    void tickSecond() {
        if (!available) {
            return;
        }
        releaseDisbanded();
        saveIfDirty();
    }

    /** Guilds that no longer exist: their house is released and the gold goes to the last master. */
    private void releaseDisbanded() {
        ServerContext.current().ifPresent(context -> {
            var guilds = context.game().guilds();
            if (!guilds.available()) {
                return;
            }
            for (GuildHouse house : service.houses()) {
                Optional<Guild> guild = guilds.service().guild(house.guildId());
                if (guild.isPresent()) {
                    masters.put(house.guildId(), guild.get().master());
                    continue;
                }
                GuildHouseService.Release release = service.disband(house.guildId());
                if (release.design() != null) {
                    estates.release(house.guildId());
                }
                UUID master = masters.remove(house.guildId());
                if (master != null && release.gold() > 0) {
                    give(master, release.gold(), "guild house of a disbanded guild");
                    Names.send(server, master, Component.translatable("message.eternalrealm.guildhouse.released",
                            String.format("%,d", release.gold())).withStyle(ChatFormatting.YELLOW));
                }
                LOG.info("Guild {} is gone: its house ({}) was released, {} G returned to {}", house.guildId(),
                        release.design(), release.gold(), master);
            }
        });
    }

    private void saveIfDirty() {
        if (saveInFlight || !service.isDirty()) {
            return;
        }
        List<GuildHouse> snapshot = service.houses();
        long revision = service.revision();
        saveInFlight = true;
        io.submit(IO_KEY, () -> {
            boolean ok;
            try {
                repository.saveAll(snapshot);
                ok = true;
            } catch (DataAccessException | RuntimeException e) {
                LOG.error("Saving the guild houses failed; will retry", e);
                ok = false;
            }
            boolean saved = ok;
            server.execute(() -> {
                saveInFlight = false;
                if (saved) {
                    service.markSaved(revision);
                }
            });
            return null;
        });
    }

    private void flush() {
        if (!available || !service.isDirty()) {
            return;
        }
        try {
            repository.saveAll(service.houses());
            service.markSaved(service.revision());
        } catch (DataAccessException | RuntimeException e) {
            LOG.error("Final guild house save failed", e);
        }
    }

    /** The design a guild owns (convenience for the UI). */
    public Optional<GuildHouseDesign> design(UUID guildId) {
        return service.design(guildId);
    }
}
