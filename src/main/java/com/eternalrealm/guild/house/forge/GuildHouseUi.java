package com.eternalrealm.guild.house.forge;

import com.eternalrealm.guild.Guild;
import com.eternalrealm.guild.GuildRank;
import com.eternalrealm.guild.house.BoardNote;
import com.eternalrealm.guild.house.GuildHouse;
import com.eternalrealm.guild.house.GuildHouseCatalog;
import com.eternalrealm.guild.house.GuildHouseDesign;
import com.eternalrealm.guild.house.GuildHouseRules;
import com.eternalrealm.guild.house.StorageLogEntry;
import com.eternalrealm.server.UiActions;
import com.eternalrealm.server.UiArgs;
import com.eternalrealm.server.UiData;
import com.eternalrealm.world.forge.AstoriaTravel;
import com.eternalrealm.world.forge.Capital;
import com.eternalrealm.world.forge.HubLayout;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * The guild houses' screens (docs/modules/guildhouse.md). Channel {@value #CATALOGUE}: the twenty designs with the
 * player's guild's state (owned design, fund, the player's rank and gold); opened by the broker and the gates'
 * plaques, focused on a design. Channel {@value #BOARD}: the guild board (message of the day, notes, storage log);
 * opened by the board in the house, also pushed to members when they arrive and whenever it changes (the board block
 * draws it). The server decides everything and sends the documents again after each action.
 */
public final class GuildHouseUi {
    public static final String CATALOGUE = "guildhouse.catalogue";
    public static final String BOARD = "guildhouse.board";
    /** How close to the broker or a gate's plaque a player must be to buy, switch or give up. */
    static final double REACH = 14.0;
    private static final int LOG_LINES = 60;

    private GuildHouseUi() {
    }

    static void register() {
        UiActions.register("guildhouse.view", (player, context, args) ->
                sendCatalogue(player, args.has("design") ? UiArgs.id(args, "design") : null, false));
        UiActions.register("guildhouse.buy", (player, context, args) -> {
            String design = UiArgs.id(args, "design");
            if (requireTerrace(player)) {
                GuildHouses.get().ifPresent(houses -> houses.buy(player, design));
            }
            sendCatalogue(player, design, false);
        });
        UiActions.register("guildhouse.switch", (player, context, args) -> {
            String design = UiArgs.id(args, "design");
            if (requireTerrace(player)) {
                GuildHouses.get().ifPresent(houses -> houses.switchTo(player, design));
            }
            sendCatalogue(player, design, false);
        });
        UiActions.register("guildhouse.sell", (player, context, args) -> {
            if (requireTerrace(player)) {
                GuildHouses.get().ifPresent(houses -> houses.sell(player));
            }
            sendCatalogue(player, null, false);
        });
        UiActions.register("guildhouse.donate", (player, context, args) -> {
            long amount = UiArgs.number(args, "amount", 1, GuildHouseRules.MAX_FUND);
            GuildHouses.get().ifPresent(houses -> houses.donate(player, amount));
            sendCatalogue(player, args.has("design") ? UiArgs.id(args, "design") : null, false);
        });
        UiActions.register("guildhouse.board", (player, context, args) -> sendBoard(player, false));
        UiActions.register("guildhouse.motd", (player, context, args) -> {
            String text = UiArgs.string(args, "text", 512);
            GuildHouses.get().ifPresent(houses -> houses.setMotd(player, text));
            sendBoard(player, false);
        });
        UiActions.register("guildhouse.note", (player, context, args) -> {
            String text = UiArgs.string(args, "text", 512);
            GuildHouses.get().ifPresent(houses -> houses.addNote(player, text));
            sendBoard(player, false);
        });
        UiActions.register("guildhouse.unpin", (player, context, args) -> {
            long id = UiArgs.number(args, "id", 1, Long.MAX_VALUE);
            GuildHouses.get().ifPresent(houses -> houses.removeNote(player, id));
            sendBoard(player, false);
        });
    }

    // ------------------------------------------------------------------ where things may be done

    /** Whether the player stands at the broker or by one of the gates on the Guild Gate Terrace. */
    public static boolean atTerrace(ServerPlayer player) {
        Optional<ServerLevel> astoria = AstoriaTravel.astoria(player.server);
        if (astoria.isEmpty() || player.serverLevel() != astoria.get()) {
            return false;
        }
        HubLayout layout = Capital.layout(astoria.get());
        double reach = REACH * REACH;
        Optional<BlockPos> broker = layout.named(GuildBroker.SPOT);
        if (broker.isPresent() && broker.get().distToCenterSqr(player.position()) <= reach) {
            return true;
        }
        for (BlockPos gate : layout.namedWithPrefix("guildgate.").values()) {
            if (gate.distToCenterSqr(player.position()) <= reach) {
                return true;
            }
        }
        return GuildBroker.near(player);
    }

    private static boolean requireTerrace(ServerPlayer player) {
        if (atTerrace(player)) {
            return true;
        }
        player.displayClientMessage(Component.translatable("message.eternalrealm.guildhouse.not_at_terrace")
                .withStyle(ChatFormatting.RED), true);
        return false;
    }

    // ------------------------------------------------------------------ the catalogue

    /** Opens the catalogue (broker, plaques), focused on {@code design} if given. */
    public static void openCatalogue(ServerPlayer player, String design) {
        sendCatalogue(player, design, true);
    }

    static void sendCatalogue(ServerPlayer player, String focus, boolean open) {
        UiData.send(player, CATALOGUE, catalogue(player, focus, open));
    }

    /** The catalogue document (public for the GameTests). */
    public static JsonObject catalogue(ServerPlayer player, String focus, boolean open) {
        JsonObject data = new JsonObject();
        data.addProperty("open", open);
        data.addProperty("nonce", System.nanoTime());
        GuildHouseCatalog catalog = GuildHouseCatalog.standard();
        data.addProperty("focus", focus != null && catalog.contains(focus) ? focus : "");
        Optional<GuildHouses> houses = GuildHouses.get();
        data.addProperty("available", houses.map(GuildHouses::available).orElse(false)
                && GuildHouses.estates().available());
        data.addProperty("gold", GuildHouses.gold(player));
        data.addProperty("atTerrace", atTerrace(player));
        data.addProperty("refundPercent", GuildHouseRules.REFUND_PERCENT);
        Optional<Guild> guild = GuildHouses.guildOf(player);
        GuildRank rank = GuildHouses.rankOf(guild, player.getUUID());
        GuildHouse house = guild.flatMap(g -> houses.map(h -> h.service().house(g.id()))).orElse(null);
        Optional<GuildHouseDesign> owned = house == null ? Optional.empty() : catalog.design(house.design());
        if (guild.isPresent()) {
            data.addProperty("guild", guild.get().name());
            data.addProperty("tag", guild.get().tag());
            data.addProperty("rank", rank == null ? "member" : rank.id());
            data.addProperty("fund", house == null ? 0 : house.fund());
            data.addProperty("owned", owned.map(GuildHouseDesign::id).orElse(""));
            data.addProperty("ready", owned.isPresent() && GuildHouses.estates().ready(guild.get().id()));
            data.addProperty("percent", owned.isPresent() ? GuildHouses.estates().percent(guild.get().id()) : 0);
        }
        JsonArray designs = new JsonArray();
        for (GuildHouseDesign design : catalog.designs()) {
            JsonObject json = new JsonObject();
            json.addProperty("id", design.id());
            json.addProperty("tier", design.tier());
            json.addProperty("price", design.price());
            json.addProperty("base", design.baseColor());
            json.addProperty("accent", design.accentColor());
            json.addProperty("w", design.width());
            json.addProperty("d", design.depth());
            json.addProperty("h", design.height());
            json.addProperty("storage", design.storageSlots());
            json.addProperty("vaults", GuildHouseRules.vaultChests(design.tier()) * 27);
            long cost = owned.map(current -> GuildHouseRules.switchBalance(current, design)).orElse(design.price());
            json.addProperty("cost", cost);
            designs.add(json);
        }
        data.add("designs", designs);
        owned.ifPresent(design -> data.addProperty("refund", GuildHouseRules.refund(design)));
        return data;
    }

    // ------------------------------------------------------------------ the board

    /** The board block in a house: open the board screen. */
    public static void openBoard(ServerPlayer player) {
        sendBoard(player, true);
    }

    /** Push the board (no screen) so the board in the house shows it. */
    public static void sendBoard(ServerPlayer player) {
        sendBoard(player, false);
    }

    static void sendBoard(ServerPlayer player, boolean open) {
        UiData.send(player, BOARD, board(player, open));
    }

    /** Every online member gets the board again (after a change). */
    static void refreshBoards(Guild guild) {
        GuildHouses.get().ifPresent(houses -> {
            for (UUID member : guild.members().keySet()) {
                ServerPlayer online = houses.server().getPlayerList().getPlayer(member);
                if (online != null) {
                    sendBoard(online, false);
                }
            }
        });
    }

    /** The board document (public for the GameTests). */
    public static JsonObject board(ServerPlayer player, boolean open) {
        JsonObject data = new JsonObject();
        data.addProperty("open", open);
        data.addProperty("nonce", System.nanoTime());
        Optional<Guild> guild = GuildHouses.guildOf(player);
        Optional<GuildHouses> houses = GuildHouses.get();
        if (guild.isEmpty() || houses.isEmpty()) {
            return data;
        }
        GuildRank rank = GuildHouses.rankOf(guild, player.getUUID());
        GuildHouse house = houses.get().service().house(guild.get().id());
        data.addProperty("guild", guild.get().name());
        data.addProperty("tag", guild.get().tag());
        data.addProperty("rank", rank == null ? "member" : rank.id());
        data.addProperty("moderator", rank != null && GuildHouseRules.mayModerate(rank));
        data.addProperty("design", house.design() == null ? "" : house.design());
        data.addProperty("motd", house.motd());
        JsonArray notes = new JsonArray();
        for (BoardNote note : house.notes()) {
            JsonObject json = new JsonObject();
            json.addProperty("id", note.id());
            json.addProperty("name", note.authorName());
            json.addProperty("text", note.text());
            json.addProperty("at", note.at().toEpochMilli());
            json.addProperty("mine", note.author().equals(player.getUUID()));
            notes.add(json);
        }
        data.add("notes", notes);
        JsonArray log = new JsonArray();
        List<StorageLogEntry> entries = house.log();
        for (int i = entries.size() - 1; i >= 0 && log.size() < LOG_LINES; i--) {
            StorageLogEntry entry = entries.get(i);
            JsonObject json = new JsonObject();
            json.addProperty("at", entry.at().toEpochMilli());
            json.addProperty("name", entry.playerName());
            json.addProperty("item", entry.itemId());
            json.addProperty("count", entry.count());
            json.addProperty("deposit", entry.deposit());
            json.addProperty("vault", GuildHouseRules.isVault(entry.piece()));
            log.add(json);
        }
        data.add("log", log);
        JsonArray members = new JsonArray();
        for (Map.Entry<UUID, com.eternalrealm.guild.GuildMember> member : guild.get().members().entrySet()) {
            JsonObject json = new JsonObject();
            json.addProperty("name", com.eternalrealm.social.forge.Names.of(player.server, member.getKey()));
            json.addProperty("rank", member.getValue().rank().id());
            json.addProperty("online", player.server.getPlayerList().getPlayer(member.getKey()) != null);
            members.add(json);
        }
        data.add("members", members);
        data.addProperty("lang", Locale.ROOT.toString());
        return data;
    }
}
