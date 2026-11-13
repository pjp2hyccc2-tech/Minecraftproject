package com.eternalrealm.social.phone.forge;

import com.eternalrealm.core.ModInfo;
import com.eternalrealm.core.concurrent.KeyedSerialExecutor;
import com.eternalrealm.core.data.CorruptDataException;
import com.eternalrealm.core.data.DataAccessException;
import com.eternalrealm.network.forge.ERNetwork;
import com.eternalrealm.network.forge.PhotoDataPacket;
import com.eternalrealm.persistence.file.AtomicFileStore;
import com.eternalrealm.server.ServerContext;
import com.eternalrealm.server.UiActions;
import com.eternalrealm.server.UiArgs;
import com.eternalrealm.server.UiData;
import com.eternalrealm.social.forge.Names;
import com.eternalrealm.social.phone.FriendBook;
import com.eternalrealm.social.phone.Messages;
import com.eternalrealm.social.phone.PhoneData;
import com.eternalrealm.social.phone.PhoneRates;
import com.eternalrealm.social.phone.PhoneText;
import com.eternalrealm.social.phone.PhoneView;
import com.eternalrealm.social.phone.PhotoCodec;
import com.eternalrealm.social.phone.PhotoRules;
import com.eternalrealm.social.phone.Timeline;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * The 魔導スマホ on the server: friends, messages, the board (つぶやき) and photos ({@link PhoneData}), saved to
 * {@code <data>/phone.json} with the photos as {@code <data>/photos/<id>.jpg}. The phone's screens ask for views (UI
 * actions {@code phone.*}) and get them on UI channel {@value #CHANNEL}; what others do reaches a player as a line in
 * chat with a button, and as the badges. Server thread only (files are read and written on the IO threads).
 */
@Mod.EventBusSubscriber(modid = ModInfo.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class PhoneService {
    public static final String CHANNEL = "phone";
    /** Open the phone at a view (the /phone command, chat buttons). */
    public static final String OPEN_CHANNEL = "phone.open";
    /** A short line on the phone's screen (refusals, "sent"). */
    public static final String TOAST_CHANNEL = "phone.toast";
    /** The answer to a photo upload: {upload, ok, id}. */
    public static final String UPLOAD_CHANNEL = "phone.upload";
    /** The badges alone, when the phone is closed: {unread, requests, activity}. */
    public static final String BADGE_CHANNEL = "phone.badges";
    private static final Logger LOG = LogUtils.getLogger();
    private static final UUID IO_KEY = new UUID(0x7068_6f6e_6500_0000L, 1L);
    private static final int SAVE_TICKS = 1200;
    private static final int SWEEP_TICKS = 6000;
    private static PhoneService current;

    private final MinecraftServer server;
    private final Path file;
    private final Path photoDir;
    private final AtomicFileStore store;
    private final KeyedSerialExecutor<UUID> io;
    private final PhoneData data;
    private final boolean available;
    private final PhoneRates rates = new PhoneRates();
    private final SecureRandom random = new SecureRandom();
    private final Map<UUID, PhotoRules.Upload> uploads = new HashMap<>();
    /** What each player's open phone shows: {view, id, feed, before}. */
    private final Map<UUID, JsonObject> viewing = new HashMap<>();
    private long revision;
    private long savedRevision;
    private boolean saveInFlight;
    private long ticks;

    private PhoneService(MinecraftServer server, Path directory, KeyedSerialExecutor<UUID> io) {
        this.server = server;
        this.file = directory.resolve("phone.json");
        this.photoDir = directory.resolve("photos");
        this.store = new AtomicFileStore(directory.resolve("quarantine"), Clock.systemUTC());
        this.io = io;
        PhoneData loaded;
        boolean ok = true;
        try {
            loaded = store.read(file, PhoneService::parse).orElseGet(PhoneData::empty);
        } catch (DataAccessException e) {
            LOG.error("The phone's data ({}) cannot be read: friends, messages and posts start empty and are NOT saved "
                    + "until this is fixed (the file is left as it is)", file, e);
            loaded = PhoneData.empty();
            ok = false;
        }
        this.data = loaded;
        this.available = ok;
    }

    private static PhoneData parse(String content) throws CorruptDataException {
        try {
            JsonElement json = JsonParser.parseString(content);
            if (!json.isJsonObject()) {
                throw new CorruptDataException("phone.json is not an object");
            }
            return PhoneData.fromJson(json.getAsJsonObject());
        } catch (JsonParseException e) {
            throw new CorruptDataException("phone.json is not JSON: " + e.getMessage());
        }
    }

    public static Optional<PhoneService> current() {
        return Optional.ofNullable(current);
    }

    /** For tests and admins: the data (server thread only). */
    public PhoneData data() {
        return data;
    }

    private static long now() {
        return System.currentTimeMillis();
    }

    private void changed() {
        revision++;
    }

    // ------------------------------------------------------------------ the server's life

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        MinecraftServer server = event.getServer();
        Optional<ServerContext> context = ServerContext.current().filter(c -> c.server() == server);
        Path directory = context.map(ServerContext::dataDirectory)
                .orElse(server.getWorldPath(LevelResource.ROOT).resolve("eternalrealm"));
        current = new PhoneService(server, directory, context.map(ServerContext::ioExecutor).orElse(null));
        current.removeOrphans();
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        PhoneService service = current;
        if (service != null && service.server == event.getServer()) {
            service.flush();
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        current = null;
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        PhoneService service = current;
        if (event.phase != TickEvent.Phase.END || service == null || service.server != event.getServer()) {
            return;
        }
        service.ticks++;
        if (service.ticks % SWEEP_TICKS == 0) {
            service.sweep();
        }
        if (service.ticks % SAVE_TICKS == 0) {
            service.saveIfChanged();
        }
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (current != null && event.getEntity() instanceof ServerPlayer player) {
            current.login(player);
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (current != null) {
            current.viewing.remove(event.getEntity().getUUID());
            current.uploads.remove(event.getEntity().getUUID());
        }
    }

    private void login(ServerPlayer player) {
        UUID me = player.getUUID();
        data.friends().seen(me, player.getGameProfile().getName(), now());
        changed();
        int unread = data.messages().unreadTotal(me);
        int requests = data.friends().requestsTo(me).size();
        if (unread > 0 || requests > 0) {
            player.sendSystemMessage(Component.translatable("message.eternalrealm.phone.waiting", unread, requests)
                    .withStyle(ChatFormatting.AQUA).append(" ")
                    .append(Names.button("button.eternalrealm.phone.open", "/phone", ChatFormatting.GOLD)));
        }
        badges(player);
    }

    // ------------------------------------------------------------------ saving and files

    private void saveIfChanged() {
        if (!available || saveInFlight || revision == savedRevision) {
            return;
        }
        String snapshot = data.toJson().toString();
        long saving = revision;
        saveInFlight = true;
        background(() -> {
            boolean ok;
            try {
                store.write(file, snapshot);
                ok = true;
            } catch (DataAccessException | RuntimeException e) {
                LOG.error("Saving the phone's data failed; will retry", e);
                ok = false;
            }
            boolean saved = ok;
            server.execute(() -> {
                saveInFlight = false;
                if (saved) {
                    savedRevision = Math.max(savedRevision, saving);
                }
            });
        });
    }

    /** Shutdown: save now (the server loop has stopped). */
    private void flush() {
        if (!available || revision == savedRevision) {
            return;
        }
        try {
            store.write(file, data.toJson().toString());
            savedRevision = revision;
        } catch (DataAccessException | RuntimeException e) {
            LOG.error("The final save of the phone's data failed", e);
        }
    }

    private void background(Runnable task) {
        if (io == null) {
            task.run();
            return;
        }
        io.submit(IO_KEY, () -> {
            task.run();
            return null;
        });
    }

    private Path photoFile(String id) {
        return photoDir.resolve(id + ".jpg"); // ids are 16 hex digits: never a path of their own
    }

    private void deletePhotos(Collection<String> ids) {
        List<String> copy = new ArrayList<>();
        for (String id : ids) {
            if (PhotoRules.validId(id)) {
                data.photos().release(id);
                copy.add(id);
            }
        }
        if (!copy.isEmpty() && available) {
            changed();
            background(() -> {
                for (String id : copy) {
                    try {
                        Files.deleteIfExists(photoFile(id));
                    } catch (IOException e) {
                        LOG.warn("Could not delete photo {}", id, e);
                    }
                }
            });
        }
    }

    /** Every few minutes: photos nothing holds any more, and conversations quiet for months. */
    private void sweep() {
        List<String> gone = new ArrayList<>(data.messages().forgetQuiet(now()));
        gone.addAll(data.photos().sweep(now(), data.photosInUse()));
        if (!gone.isEmpty()) {
            deletePhotos(gone);
        }
    }

    /** At start: photo files the index does not know (an upload cut short by a crash) go, on the IO thread. */
    private void removeOrphans() {
        if (!available) {
            return;
        }
        Set<String> keep = new HashSet<>(data.photosInUse());
        keep.addAll(data.photos().ids());
        background(() -> {
            if (!Files.isDirectory(photoDir)) {
                return;
            }
            try (DirectoryStream<Path> files = Files.newDirectoryStream(photoDir, "*.jpg")) {
                for (Path path : files) {
                    String name = path.getFileName().toString();
                    if (!keep.contains(name.substring(0, name.length() - 4))) {
                        Files.deleteIfExists(path);
                    }
                }
            } catch (IOException | RuntimeException e) {
                LOG.warn("Could not tidy the phone's photos", e);
            }
        });
    }

    // ------------------------------------------------------------------ views

    private PhoneView view() {
        return new PhoneView(data, id -> server.getPlayerList().getPlayer(id) != null);
    }

    private static boolean moderator(ServerPlayer player) {
        return player.hasPermissions(2);
    }

    /** Sends the player's open phone what it shows now (the home screen if nothing else). */
    private void send(ServerPlayer player) {
        UUID me = player.getUUID();
        JsonObject state = viewing.get(me);
        String kind = state == null ? "home" : state.get("view").getAsString();
        PhoneView view = view();
        JsonObject out;
        switch (kind) {
            case "friends" -> out = view.friends(me);
            case "messages" -> out = view.messages(me);
            case "chat" -> {
                UUID other = UUID.fromString(state.get("id").getAsString());
                if (data.messages().unread(me, other) > 0) {
                    data.messages().markRead(me, other);
                    changed();
                }
                out = view.chat(me, other);
            }
            case "feed" -> out = view.feed(me, Timeline.Feed.valueOf(state.get("feed").getAsString()),
                    state.get("before").getAsLong(), moderator(player));
            case "post" -> out = view.post(me, state.get("id").getAsLong(), moderator(player));
            case "activity" -> {
                out = view.activity(me);
                if (data.timeline().unseenActivity(me) > 0) {
                    data.timeline().seeActivity(me);
                    changed();
                    out.addProperty("activity", 0);
                }
            }
            default -> out = view.home(me);
        }
        out.addProperty("me", me.toString());
        out.addProperty("mod", moderator(player));
        UiData.send(player, CHANNEL, out);
    }

    /** The badges (and the open phone's view, refreshed) of a player who is online. */
    private void badges(UUID id) {
        ServerPlayer player = server.getPlayerList().getPlayer(id);
        if (player != null) {
            badges(player);
        }
    }

    private void badges(ServerPlayer player) {
        if (viewing.containsKey(player.getUUID())) {
            send(player);
            return;
        }
        UUID me = player.getUUID();
        JsonObject out = new JsonObject();
        out.addProperty("unread", data.messages().unreadTotal(me));
        out.addProperty("requests", data.friends().requestsTo(me).size());
        out.addProperty("activity", data.timeline().unseenActivity(me));
        UiData.send(player, BADGE_CHANNEL, out);
    }

    /** Players looking at the board's first page or at one post see a change at once. */
    private void refreshWatchers(long postId) {
        for (Map.Entry<UUID, JsonObject> entry : List.copyOf(viewing.entrySet())) {
            String kind = entry.getValue().get("view").getAsString();
            boolean watching = ("feed".equals(kind) && entry.getValue().get("before").getAsLong() == 0L)
                    || ("post".equals(kind) && entry.getValue().get("id").getAsLong() == postId);
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (watching && player != null) {
                send(player);
            }
        }
    }

    private void toast(ServerPlayer player, String key, Object... args) {
        JsonObject out = new JsonObject();
        out.addProperty("message", Component.Serializer.toJson(Component.translatable(key, args)));
        UiData.send(player, TOAST_CHANNEL, out);
    }

    /** Opens the player's phone at a view (/phone, chat buttons). */
    public void open(ServerPlayer player, String view, String id) {
        JsonObject out = new JsonObject();
        out.addProperty("view", view);
        out.addProperty("id", id);
        UiData.send(player, OPEN_CHANNEL, out);
    }

    // ------------------------------------------------------------------ actions

    public static void register() {
        UiActions.register("phone.view", (player, context, args) -> service().view(player, args));
        UiActions.register("phone.close", (player, context, args) -> service().viewing.remove(player.getUUID()));
        UiActions.register("phone.friend", (player, context, args) -> service().friend(player, args));
        UiActions.register("phone.send", (player, context, args) -> service().message(player, args));
        UiActions.register("phone.post", (player, context, args) -> service().post(player, args));
        UiActions.register("phone.like", (player, context, args) -> service().like(player, args));
        UiActions.register("phone.comment", (player, context, args) -> service().comment(player, args));
        UiActions.register("phone.delete", (player, context, args) -> service().delete(player, args));
        UiActions.register("phone.uncomment", (player, context, args) -> service().uncomment(player, args));
        UiActions.register("phone.photo", (player, context, args) -> service().photo(player, args));
    }

    private static PhoneService service() {
        PhoneService service = current;
        if (service == null) {
            throw new IllegalStateException("the phone is not running");
        }
        return service;
    }

    private void view(ServerPlayer player, JsonObject args) {
        String kind = UiArgs.string(args, "view", 16);
        JsonObject state = new JsonObject();
        state.addProperty("view", kind);
        switch (kind) {
            case "home", "friends", "messages", "activity" -> {
            }
            case "chat" -> state.addProperty("id", UiArgs.uuid(args, "id").toString());
            case "feed" -> {
                String feed = UiArgs.string(args, "feed", 8).toUpperCase(Locale.ROOT);
                if (!List.of("ALL", "FRIENDS", "MINE").contains(feed)) {
                    throw new UiArgs.InvalidArgumentException("unknown feed");
                }
                state.addProperty("feed", feed);
                state.addProperty("before", UiArgs.number(args, "before", 0, Long.MAX_VALUE));
            }
            case "post" -> state.addProperty("id", UiArgs.number(args, "id", 1, Long.MAX_VALUE));
            default -> throw new UiArgs.InvalidArgumentException("unknown phone view");
        }
        viewing.put(player.getUUID(), state);
        send(player);
    }

    private void friend(ServerPlayer player, JsonObject args) {
        UUID me = player.getUUID();
        String op = UiArgs.string(args, "op", 10);
        FriendBook book = data.friends();
        FriendBook.Result result;
        UUID other;
        if ("request".equals(op)) {
            String name = UiArgs.string(args, "name", 16);
            if (!PhoneText.validName(name)) {
                toast(player, "phone.eternalrealm.toast.bad_name");
                return;
            }
            Optional<UUID> found = book.byName(name);
            if (found.isEmpty()) {
                toast(player, "phone.eternalrealm.toast.unknown", name);
                return;
            }
            if (!rates.tryUse(me, PhoneRates.Action.FRIEND, now())) {
                toast(player, "phone.eternalrealm.toast.slow");
                return;
            }
            other = found.get();
            result = book.request(me, other, now());
        } else {
            other = UiArgs.uuid(args, "id");
            result = switch (op) {
                case "accept" -> book.accept(me, other);
                case "decline" -> book.decline(me, other);
                case "remove" -> book.remove(me, other);
                case "block" -> book.block(me, other);
                case "unblock" -> book.unblock(me, other);
                default -> throw new UiArgs.InvalidArgumentException("unknown friend operation");
            };
        }
        String name = book.name(other);
        toast(player, resultKey(op, result), name);
        boolean changed = result == FriendBook.Result.OK || result == FriendBook.Result.BECAME_FRIENDS;
        if (!changed) {
            return;
        }
        changed();
        String meName = player.getGameProfile().getName();
        if ("request".equals(op) && result == FriendBook.Result.OK) {
            Names.send(server, other, Component.translatable("message.eternalrealm.phone.request", meName)
                    .withStyle(ChatFormatting.AQUA).append(" ")
                    .append(Names.button("button.eternalrealm.phone.accept", "/phone accept " + meName,
                            ChatFormatting.GREEN)).append(" ")
                    .append(Names.button("button.eternalrealm.phone.decline", "/phone decline " + meName,
                            ChatFormatting.RED)));
        } else if (result == FriendBook.Result.BECAME_FRIENDS || "accept".equals(op)) {
            Names.send(server, other, Component.translatable("message.eternalrealm.phone.friends_now", meName)
                    .withStyle(ChatFormatting.AQUA));
        }
        send(player);
        badges(other);
    }

    /** What to tell the player: "done" per operation, otherwise why not. */
    static String resultKey(String op, FriendBook.Result result) {
        return result == FriendBook.Result.OK ? "phone.eternalrealm.friend.done." + op
                : "phone.eternalrealm.friend." + result.name().toLowerCase(Locale.ROOT);
    }

    /** /phone accept|decline <name> (the chat buttons). */
    public void answer(ServerPlayer player, String name, boolean accept) {
        Optional<UUID> other = data.friends().byName(name);
        if (other.isEmpty()) {
            player.sendSystemMessage(Component.translatable("phone.eternalrealm.toast.unknown", name)
                    .withStyle(ChatFormatting.RED));
            return;
        }
        FriendBook.Result result = accept ? data.friends().accept(player.getUUID(), other.get())
                : data.friends().decline(player.getUUID(), other.get());
        player.sendSystemMessage(Component.translatable(resultKey(accept ? "accept" : "decline", result), name)
                .withStyle(result == FriendBook.Result.OK ? ChatFormatting.AQUA : ChatFormatting.RED));
        if (result == FriendBook.Result.OK) {
            changed();
            if (accept) {
                Names.send(server, other.get(), Component.translatable("message.eternalrealm.phone.friends_now",
                        player.getGameProfile().getName()).withStyle(ChatFormatting.AQUA));
            }
            badges(player);
            badges(other.get());
        }
    }

    /** A photo id the player may attach now, "" for none (anything else is no genuine client's). */
    private String attachable(ServerPlayer player, JsonObject args) {
        if (!args.has("photo")) {
            return "";
        }
        String photo = UiArgs.string(args, "photo", 16);
        if (photo.isEmpty()) {
            return "";
        }
        if (!PhotoRules.validId(photo)) {
            throw new UiArgs.InvalidArgumentException("bad photo id");
        }
        return data.photos().usableBy(photo, player.getUUID()) ? photo : null;
    }

    private void message(ServerPlayer player, JsonObject args) {
        UUID me = player.getUUID();
        UUID to = UiArgs.uuid(args, "to");
        String text = PhoneText.clean(UiArgs.string(args, "text", PhoneText.MESSAGE_MAX * 4), false);
        String photo = attachable(player, args);
        if (photo == null) {
            toast(player, "phone.eternalrealm.toast.photo_gone");
            return;
        }
        if (text.isEmpty() && photo.isEmpty()) {
            return;
        }
        if (!PhoneText.fits(text, PhoneText.MESSAGE_MAX)) {
            toast(player, "phone.eternalrealm.toast.too_long", PhoneText.MESSAGE_MAX);
            return;
        }
        if (!data.friends().areFriends(me, to) || data.friends().eitherBlocks(me, to)) {
            toast(player, "phone.eternalrealm.toast.not_friends");
            return;
        }
        if (!rates.tryUse(me, PhoneRates.Action.MESSAGE, now())) {
            toast(player, "phone.eternalrealm.toast.slow");
            return;
        }
        List<String> released = new ArrayList<>();
        data.messages().send(me, to, text, photo, now(), released);
        if (!photo.isEmpty()) {
            data.photos().use(photo);
        }
        changed();
        deletePhotos(released);
        send(player);
        ServerPlayer target = server.getPlayerList().getPlayer(to);
        if (target != null) {
            JsonObject theirs = viewing.get(to);
            boolean reading = theirs != null && "chat".equals(theirs.get("view").getAsString())
                    && me.toString().equals(theirs.get("id").getAsString());
            if (!reading) {
                String name = player.getGameProfile().getName();
                MutableComponent line = Component.translatable("message.eternalrealm.phone.message", name,
                        text.isEmpty() ? Component.translatable("phone.eternalrealm.photo")
                                : Component.literal(PhoneText.preview(text, 40))).withStyle(ChatFormatting.AQUA);
                target.sendSystemMessage(line.append(" ").append(Names.button("button.eternalrealm.phone.reply",
                        "/phone chat " + name, ChatFormatting.GOLD)));
            }
            badges(target);
        }
    }

    private void post(ServerPlayer player, JsonObject args) {
        UUID me = player.getUUID();
        String text = PhoneText.clean(UiArgs.string(args, "text", PhoneText.POST_MAX * 4), true);
        String photo = attachable(player, args);
        if (photo == null) {
            toast(player, "phone.eternalrealm.toast.photo_gone");
            return;
        }
        if (text.isEmpty() && photo.isEmpty()) {
            toast(player, "phone.eternalrealm.toast.empty");
            return;
        }
        if (!PhoneText.fits(text, PhoneText.POST_MAX)) {
            toast(player, "phone.eternalrealm.toast.too_long", PhoneText.POST_MAX);
            return;
        }
        if (!rates.tryUse(me, PhoneRates.Action.POST, now())) {
            toast(player, rates.dailyCapReached(me, PhoneRates.Action.POST, now())
                    ? "phone.eternalrealm.toast.daily" : "phone.eternalrealm.toast.slow");
            return;
        }
        Timeline.Posted posted = data.timeline().post(me, text, photo, now());
        if (!photo.isEmpty()) {
            data.photos().use(photo);
        }
        changed();
        deletePhotos(posted.released());
        toast(player, "phone.eternalrealm.toast.posted");
        JsonObject state = new JsonObject();
        state.addProperty("view", "feed");
        state.addProperty("feed", "ALL");
        state.addProperty("before", 0L);
        viewing.put(me, state);
        refreshWatchers(posted.post().id());
    }

    private void like(ServerPlayer player, JsonObject args) {
        long id = UiArgs.number(args, "id", 1, Long.MAX_VALUE);
        if (!visiblePost(player, id)) {
            send(player);
            return;
        }
        if (!rates.tryUse(player.getUUID(), PhoneRates.Action.LIKE, now())) {
            return;
        }
        Optional<Boolean> liked = data.timeline().toggleLike(player.getUUID(), id, now());
        if (liked.isEmpty()) {
            send(player);
            return;
        }
        changed();
        send(player);
        data.timeline().byId(id).ifPresent(post -> {
            if (liked.get() && !post.author().equals(player.getUUID())) {
                badges(post.author());
            }
        });
    }

    private boolean visiblePost(ServerPlayer player, long id) {
        return data.timeline().byId(id).map(post -> post.author().equals(player.getUUID())
                || !data.friends().eitherBlocks(player.getUUID(), post.author())).orElse(false);
    }

    private void comment(ServerPlayer player, JsonObject args) {
        UUID me = player.getUUID();
        long id = UiArgs.number(args, "id", 1, Long.MAX_VALUE);
        String text = PhoneText.clean(UiArgs.string(args, "text", PhoneText.COMMENT_MAX * 4), false);
        if (text.isEmpty()) {
            return;
        }
        if (!PhoneText.fits(text, PhoneText.COMMENT_MAX)) {
            toast(player, "phone.eternalrealm.toast.too_long", PhoneText.COMMENT_MAX);
            return;
        }
        if (!visiblePost(player, id)) {
            toast(player, "phone.eternalrealm.toast.post_gone");
            return;
        }
        if (!rates.tryUse(me, PhoneRates.Action.COMMENT, now())) {
            toast(player, "phone.eternalrealm.toast.slow");
            return;
        }
        Optional<Timeline.Comment> comment = data.timeline().comment(me, id, text, now());
        if (comment.isEmpty()) {
            toast(player, "phone.eternalrealm.toast.comments_full");
            return;
        }
        changed();
        refreshWatchers(id);
        send(player);
        data.timeline().byId(id).ifPresent(post -> {
            ServerPlayer author = server.getPlayerList().getPlayer(post.author());
            if (author != null && !author.getUUID().equals(me)) {
                author.sendSystemMessage(Component.translatable("message.eternalrealm.phone.comment",
                                player.getGameProfile().getName(), PhoneText.preview(text, 40))
                        .withStyle(ChatFormatting.AQUA).append(" ")
                        .append(Names.button("button.eternalrealm.phone.see", "/phone post " + id,
                                ChatFormatting.GOLD)));
                badges(author);
            }
        });
    }

    private void delete(ServerPlayer player, JsonObject args) {
        long id = UiArgs.number(args, "id", 1, Long.MAX_VALUE);
        Optional<String> photo = data.timeline().delete(id, player.getUUID(), moderator(player));
        if (photo.isEmpty()) {
            send(player);
            return;
        }
        changed();
        if (!photo.get().isEmpty()) {
            deletePhotos(List.of(photo.get()));
        }
        toast(player, "phone.eternalrealm.toast.deleted");
        refreshWatchers(id);
        send(player);
    }

    private void uncomment(ServerPlayer player, JsonObject args) {
        long id = UiArgs.number(args, "id", 1, Long.MAX_VALUE);
        long comment = UiArgs.number(args, "comment", 1, Long.MAX_VALUE);
        if (data.timeline().deleteComment(id, comment, player.getUUID(), moderator(player))) {
            changed();
            refreshWatchers(id);
        }
        send(player);
    }

    /** /phone admin: a moderator deletes a post or a comment by its number. */
    public boolean moderate(long postId, long commentId) {
        boolean done;
        if (commentId > 0) {
            done = data.timeline().deleteComment(postId, commentId, null, true);
        } else {
            Optional<String> photo = data.timeline().delete(postId, null, true);
            done = photo.isPresent();
            photo.filter(p -> !p.isEmpty()).ifPresent(p -> deletePhotos(List.of(p)));
        }
        if (done) {
            changed();
            refreshWatchers(postId);
        }
        return done;
    }

    // ------------------------------------------------------------------ photos

    private void photo(ServerPlayer player, JsonObject args) {
        String id = UiArgs.string(args, "id", 16);
        if (!PhotoRules.validId(id)) {
            throw new UiArgs.InvalidArgumentException("bad photo id");
        }
        UUID me = player.getUUID();
        if (!data.canSee(me, id)) {
            ERNetwork.sendToPlayer(player, new PhotoDataPacket(id, new byte[0]));
            return;
        }
        Path path = photoFile(id);
        background(() -> {
            byte[] bytes;
            try {
                bytes = Files.readAllBytes(path);
                if (bytes.length > PhotoRules.MAX_BYTES) {
                    bytes = new byte[0];
                }
            } catch (NoSuchFileException e) {
                bytes = new byte[0];
            } catch (IOException e) {
                LOG.warn("Could not read photo {}", id, e);
                bytes = new byte[0];
            }
            byte[] found = bytes;
            server.execute(() -> {
                ServerPlayer online = server.getPlayerList().getPlayer(me);
                if (online != null) {
                    ERNetwork.sendToPlayer(online, new PhotoDataPacket(id, found));
                }
            });
        });
    }

    /** A chunk of a photo (PhotoUploadPacket). */
    public void chunk(ServerPlayer player, int upload, int index, int total, byte[] bytes) {
        UUID me = player.getUUID();
        PhotoRules.Upload upl = uploads.get(me);
        if (index == 0) {
            if (!available || !rates.tryUse(me, PhoneRates.Action.PHOTO, now())) {
                uploads.remove(me);
                uploaded(player, upload, null, rates.dailyCapReached(me, PhoneRates.Action.PHOTO, now())
                        ? "phone.eternalrealm.toast.daily_photos" : "phone.eternalrealm.toast.slow");
                return;
            }
            upl = new PhotoRules.Upload(upload, total);
            uploads.put(me, upl);
        } else if (upl == null || upl.id() != upload) {
            return; // the rest of a refused upload
        }
        if (!upl.accept(index, bytes)) {
            uploads.remove(me);
            ServerContext.current().ifPresent(context -> com.eternalrealm.network.forge.ServerPacketGuard.violation(
                    context, player, "photo_upload: chunk out of order or of the wrong size"));
            return;
        }
        if (!upl.complete()) {
            return;
        }
        uploads.remove(me);
        byte[] photo = upl.bytes();
        String id = PhotoRules.newId(random);
        Path path = photoFile(id);
        background(() -> {
            String error = null;
            try {
                byte[] clean = PhotoCodec.normalize(photo);
                Files.createDirectories(photoDir);
                Files.write(path, clean);
            } catch (IOException | RuntimeException e) {
                LOG.debug("Refused a photo from {}: {}", player.getGameProfile().getName(), e.getMessage());
                error = "phone.eternalrealm.toast.bad_photo";
            }
            String refused = error;
            server.execute(() -> {
                ServerPlayer online = server.getPlayerList().getPlayer(me);
                if (refused != null) {
                    if (online != null) {
                        uploaded(online, upload, null, refused);
                    }
                    return;
                }
                deletePhotos(data.photos().add(id, me, now()));
                changed();
                if (online != null) {
                    uploaded(online, upload, id, null);
                }
            });
        });
    }

    private void uploaded(ServerPlayer player, int upload, String id, String error) {
        JsonObject out = new JsonObject();
        out.addProperty("upload", upload);
        out.addProperty("ok", id != null);
        if (id != null) {
            out.addProperty("id", id);
        } else {
            out.addProperty("message", Component.Serializer.toJson(Component.translatable(error)));
        }
        UiData.send(player, UPLOAD_CHANNEL, out);
    }

    // ------------------------------------------------------------------ for the commands

    /** Opens the conversation with a friend by name (/phone chat <name>). */
    public void openChat(ServerPlayer player, String name) {
        Optional<UUID> other = data.friends().byName(name);
        if (other.isEmpty()) {
            player.sendSystemMessage(Component.translatable("phone.eternalrealm.toast.unknown", name)
                    .withStyle(ChatFormatting.RED));
            return;
        }
        open(player, "chat", other.get().toString());
    }

    /** For GameTests: what a player's phone would show for {@code args} (as if they asked). */
    public JsonObject viewFor(ServerPlayer player, JsonObject args) {
        view(player, args);
        JsonObject state = viewing.get(player.getUUID());
        return state == null ? new JsonObject() : state.deepCopy();
    }

    /** For GameTests: send a message, post, like or comment as the player would (UI action arguments). */
    public void act(ServerPlayer player, String action, JsonObject args) {
        switch (action) {
            case "friend" -> friend(player, args);
            case "send" -> message(player, args);
            case "post" -> post(player, args);
            case "like" -> like(player, args);
            case "comment" -> comment(player, args);
            case "delete" -> delete(player, args);
            default -> throw new IllegalArgumentException(action);
        }
    }

    /** For GameTests: a player seen by the phone without logging in (fake players never do). */
    public void seen(ServerPlayer player) {
        data.friends().seen(player.getUUID(), player.getGameProfile().getName(), now());
    }

    /** For GameTests: unread messages. */
    public int unread(UUID me) {
        return data.messages().unreadTotal(me);
    }

    /** For GameTests: the conversation. */
    public List<Messages.Message> history(UUID me, UUID other) {
        return data.messages().history(me, other);
    }
}
