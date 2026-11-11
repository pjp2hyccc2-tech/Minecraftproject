package com.eternalrealm.testkit.bots.forge;

import com.eternalrealm.character.CharacterSnapshot;
import com.eternalrealm.core.ModInfo;
import com.eternalrealm.estate.EstateKey;
import com.eternalrealm.estate.forge.EstateService;
import com.eternalrealm.job.JobType;
import com.eternalrealm.server.ServerContext;
import com.eternalrealm.testkit.bots.BotIds;
import com.eternalrealm.world.forge.AstoriaTravel;
import com.eternalrealm.world.forge.Capital;
import com.eternalrealm.world.forge.HubLayout;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.logging.LogUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.TreeMap;
import java.util.UUID;

/**
 * The test bots (docs/modules/testkit.md §4): AI players for trying the game as a crowd. Operators only
 * (permission level 2), {@code /eternalrealm admin bots ...} (alias {@code /er}):
 * <ul>
 *     <li>{@code spawn [<count>]}: bots 1..count (default {@value #DEFAULT_COUNT}, at most {@link BotIds#MAX}) that
 *     are not online yet log in, one every {@value #ARRIVAL_GAP} ticks. Each keeps its character from earlier
 *     sessions.</li>
 *     <li>{@code remove}: every bot logs out (their characters stay for next time).</li>
 *     <li>{@code purge}: as remove, and their traces go: rented inn rooms are given up, their ranking entries
 *     removed.</li>
 *     <li>{@code list}: who is online and what each is doing.</li>
 *     <li>{@code send <activity>}: every bot goes to town, the shop, the inn, a hunt, or home now.</li>
 *     <li>{@code chat on|off}: whether they say things in the chat now and then.</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = ModInfo.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class TestBots {
    public static final int DEFAULT_COUNT = 30;
    /** Ticks between two bots logging in: a crowd arriving, not all at once. */
    public static final int ARRIVAL_GAP = 5;
    private static final int GAMEMASTER = 2;
    private static final int HUB_REFRESH_TICKS = 20 * 60 * 5;
    private static final Logger LOG = LogUtils.getLogger();

    private static final Map<Integer, TestBot> ONLINE = new TreeMap<>();
    private static final Deque<Integer> ARRIVING = new ArrayDeque<>();
    private static final Map<Integer, Long> EXPIRES = new HashMap<>();
    private static MinecraftServer owner;
    private static HubLayout hub;
    private static long hubAt;
    private static volatile boolean chat = true;

    private TestBots() {
    }

    // ------------------------------------------------------------------ registry

    public static Collection<TestBot> online() {
        return List.copyOf(ONLINE.values());
    }

    public static Optional<TestBot> byId(UUID id) {
        OptionalInt number = BotIds.numberOf(id);
        return number.isPresent() ? Optional.ofNullable(ONLINE.get(number.getAsInt())) : Optional.empty();
    }

    public static int count() {
        return ONLINE.size();
    }

    public static boolean chatEnabled() {
        return chat;
    }

    /** The capital's layout (cached for a few minutes); empty without the realm's dimension. */
    static Optional<HubLayout> hub(MinecraftServer server) {
        ServerLevel astoria = AstoriaTravel.astoria(server).orElse(null);
        if (astoria == null) {
            return Optional.empty();
        }
        long now = server.getTickCount();
        if (hub == null || now - hubAt > HUB_REFRESH_TICKS) {
            hub = Capital.layout(astoria);
            hubAt = now;
        }
        return Optional.of(hub);
    }

    /** Logs bot {@code number} in now (or returns it if it is online already). */
    public static TestBot spawn(MinecraftServer server, int number) {
        claim(server);
        TestBot online = ONLINE.get(number);
        if (online != null && server.getPlayerList().getPlayer(online.getUUID()) == online) {
            return online;
        }
        if (server.getPlayerList().getPlayer(BotIds.idOf(number)) != null) {
            throw new IllegalStateException(BotIds.nameOf(number) + " is online but not as a test bot");
        }
        TestBot bot = new TestBot(server, server.overworld(), number);
        ONLINE.put(number, bot);
        try {
            server.getPlayerList().placeNewPlayer(BotConnection.open(), bot);
        } catch (RuntimeException e) {
            ONLINE.remove(number, bot);
            throw e;
        }
        return bot;
    }

    /**
     * A held bot for a test ({@link BotBrain#hold()}: it follows orders only), logged out by itself after
     * {@code lifetime} ticks should the test not do it.
     */
    public static TestBot spawnHeld(MinecraftServer server, int number, int lifetime) {
        TestBot bot = spawn(server, number);
        bot.mind().hold();
        EXPIRES.put(number, (long) server.getTickCount() + lifetime);
        return bot;
    }

    /** Logs {@code bot} out, as a player's disconnection does. */
    public static void remove(TestBot bot) {
        ONLINE.remove(bot.number(), bot);
        EXPIRES.remove(bot.number());
        if (bot.server.getPlayerList().getPlayer(bot.getUUID()) == bot) {
            bot.connection.disconnect(Component.translatable("message.eternalrealm.bots.removed"));
        }
    }

    /** Logs every bot out; @return how many there were */
    public static int removeAll() {
        ARRIVING.clear();
        List<TestBot> all = new ArrayList<>(ONLINE.values());
        all.forEach(TestBots::remove);
        return all.size();
    }

    private static void claim(MinecraftServer server) {
        if (owner != server) {
            owner = server;
            ONLINE.clear();
            ARRIVING.clear();
            EXPIRES.clear();
            hub = null;
        }
    }

    // ------------------------------------------------------------------ events

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        MinecraftServer server = event.getServer();
        if (event.phase != TickEvent.Phase.END || server == null) {
            return;
        }
        if (owner != server) {
            if (ARRIVING.isEmpty() && ONLINE.isEmpty()) {
                return;
            }
            claim(server);
        }
        long now = server.getTickCount();
        if (!ARRIVING.isEmpty() && now % ARRIVAL_GAP == 0) {
            int number = ARRIVING.poll();
            try {
                spawn(server, number);
            } catch (RuntimeException e) {
                LOG.error("Test bot {} could not log in", BotIds.nameOf(number), e);
            }
        }
        // logged out by something else (a kick, the server's own clean-up): forgotten
        ONLINE.values().removeIf(bot -> server.getPlayerList().getPlayer(bot.getUUID()) != bot);
        for (TestBot bot : ONLINE.values()) {
            bot.track(now);
        }
        for (Map.Entry<Integer, Long> entry : new ArrayList<>(EXPIRES.entrySet())) {
            TestBot bot = ONLINE.get(entry.getKey());
            if (bot == null) {
                EXPIRES.remove(entry.getKey());
            } else if (now >= entry.getValue()) {
                LOG.warn("Test bot {} outlived its test; logging it out", bot.getGameProfile().getName());
                remove(bot);
            }
        }
    }

    /**
     * A bot that would die (outside the realm's dimensions nothing catches the fall, and a bot cannot press the
     * respawn button): it is set back on its feet and goes home instead.
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof TestBot bot)) {
            return;
        }
        event.setCanceled(true);
        bot.setHealth(bot.getMaxHealth());
        bot.clearFire();
        bot.removeAllEffects();
        bot.setDeltaMovement(Vec3.ZERO);
        bot.fallDistance = 0.0F;
        bot.mind().knockedOut();
    }

    @SubscribeEvent
    public static void onLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof TestBot bot) {
            ONLINE.remove(bot.number(), bot);
            EXPIRES.remove(bot.number());
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        ARRIVING.clear(); // the server logs everyone out itself
        EXPIRES.clear();
    }

    // ------------------------------------------------------------------ commands

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("eternalrealm")
                .then(Commands.literal("admin").requires(source -> source.hasPermission(GAMEMASTER))
                        .then(Commands.literal("bots").requires(source -> source.hasPermission(GAMEMASTER))
                                .then(Commands.literal("spawn")
                                        .executes(ctx -> spawnCommand(ctx.getSource(), DEFAULT_COUNT))
                                        .then(Commands.argument("count", IntegerArgumentType.integer(1, BotIds.MAX))
                                                .executes(ctx -> spawnCommand(ctx.getSource(),
                                                        IntegerArgumentType.getInteger(ctx, "count")))))
                                .then(Commands.literal("remove")
                                        .executes(ctx -> removeCommand(ctx.getSource())))
                                .then(Commands.literal("purge")
                                        .executes(ctx -> purgeCommand(ctx.getSource())))
                                .then(Commands.literal("list")
                                        .executes(ctx -> listCommand(ctx.getSource())))
                                .then(Commands.literal("send")
                                        .then(Commands.argument("activity", StringArgumentType.word())
                                                .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(
                                                        List.of("town", "shop", "inn", "hunt", "return"), builder))
                                                .executes(ctx -> sendCommand(ctx.getSource(),
                                                        StringArgumentType.getString(ctx, "activity")))))
                                .then(Commands.literal("chat")
                                        .then(Commands.literal("on")
                                                .executes(ctx -> chatCommand(ctx.getSource(), true)))
                                        .then(Commands.literal("off")
                                                .executes(ctx -> chatCommand(ctx.getSource(), false)))))));
    }

    static int spawnCommand(CommandSourceStack source, int count) {
        MinecraftServer server = source.getServer();
        claim(server);
        int queued = 0;
        int already = 0;
        for (int number = 1; number <= count; number++) {
            TestBot online = ONLINE.get(number);
            if (online != null && server.getPlayerList().getPlayer(online.getUUID()) == online) {
                already++;
            } else if (server.getPlayerList().getPlayer(BotIds.idOf(number)) == null && !ARRIVING.contains(number)) {
                ARRIVING.add(number);
                queued++;
            }
        }
        int arriving = queued;
        int present = already;
        source.sendSuccess(() -> Component.translatable("commands.eternalrealm.bots.spawning", arriving, present,
                (arriving * ARRIVAL_GAP + 19) / 20), true);
        return queued;
    }

    static int removeCommand(CommandSourceStack source) {
        claim(source.getServer());
        int removed = removeAll();
        source.sendSuccess(() -> Component.translatable("commands.eternalrealm.bots.removed", removed), true);
        return removed;
    }

    static int purgeCommand(CommandSourceStack source) {
        claim(source.getServer());
        int removed = removeAll();
        int rooms = 0;
        int entries = 0;
        Optional<ServerContext> context = ServerContext.current();
        for (int number = 1; number <= BotIds.MAX; number++) {
            UUID id = BotIds.idOf(number);
            Optional<EstateKey> room = EstateService.estate(EstateKey.ROOM, id);
            if (room.isPresent() && EstateService.release(room.get()).ok()) {
                rooms++;
            }
            if (context.isPresent() && context.get().game().rankings().board().removePlayer(id)) {
                entries++;
            }
        }
        int roomCount = rooms;
        int entryCount = entries;
        source.sendSuccess(() -> Component.translatable("commands.eternalrealm.bots.purged", removed, roomCount,
                entryCount), true);
        return removed;
    }

    static int listCommand(CommandSourceStack source) {
        claim(source.getServer());
        if (ONLINE.isEmpty()) {
            source.sendSuccess(() -> Component.translatable("commands.eternalrealm.bots.none"), false);
            return 0;
        }
        Optional<ServerContext> context = ServerContext.current();
        source.sendSuccess(() -> Component.translatable("commands.eternalrealm.bots.list_header", ONLINE.size(),
                ARRIVING.size()).withStyle(ChatFormatting.GOLD), false);
        for (TestBot bot : ONLINE.values()) {
            Optional<CharacterSnapshot> me = context.flatMap(c -> c.playerData().snapshot(bot.getUUID()));
            int level = me.map(CharacterSnapshot::level).orElse(0);
            JobType job = me.map(CharacterSnapshot::jobId).map(JobType::fromStored).orElse(JobType.NONE);
            Component jobName = Component.translatable(job.translationKey());
            Component doing = Component.translatable("bot.eternalrealm.activity."
                    + bot.mind().activity().name().toLowerCase(Locale.ROOT));
            String where = String.format(Locale.ROOT, "%s %d, %d, %d", bot.level().dimension().location().getPath(),
                    bot.getBlockX(), bot.getBlockY(), bot.getBlockZ());
            source.sendSuccess(() -> Component.translatable("commands.eternalrealm.bots.list_line",
                    bot.getGameProfile().getName(), level, jobName, doing, where), false);
        }
        return ONLINE.size();
    }

    static int sendCommand(CommandSourceStack source, String activity) {
        claim(source.getServer());
        BotBrain.Activity next = switch (activity.toLowerCase(Locale.ROOT)) {
            case "town" -> BotBrain.Activity.TOWN;
            case "shop" -> BotBrain.Activity.SHOP;
            case "inn" -> BotBrain.Activity.INN;
            case "hunt" -> BotBrain.Activity.HUNT;
            case "return" -> BotBrain.Activity.RETURN;
            default -> null;
        };
        if (next == null) {
            source.sendFailure(Component.translatable("commands.eternalrealm.bots.unknown_activity", activity));
            return 0;
        }
        int sent = 0;
        for (TestBot bot : online()) {
            if (bot.mind().activity() != BotBrain.Activity.SETUP && bot.mind().activity() != BotBrain.Activity.DOWN) {
                bot.mind().send(next);
                sent++;
            }
        }
        int count = sent;
        Component doing = Component.translatable("bot.eternalrealm.activity." + next.name().toLowerCase(Locale.ROOT));
        source.sendSuccess(() -> Component.translatable("commands.eternalrealm.bots.sent", count, doing), true);
        return sent;
    }

    static int chatCommand(CommandSourceStack source, boolean on) {
        chat = on;
        source.sendSuccess(() -> Component.translatable(on ? "commands.eternalrealm.bots.chat_on"
                : "commands.eternalrealm.bots.chat_off"), true);
        return 1;
    }
}
