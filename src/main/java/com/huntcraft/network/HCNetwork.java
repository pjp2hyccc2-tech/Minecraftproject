package com.huntcraft.network;

import com.huntcraft.HuntCraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Network channel. Modules normally DO NOT register their own packet classes; instead they use the
 * generic string-keyed actions:
 * <ul>
 *   <li>client -> server: {@link #registerServerAction(String, BiConsumer)} + {@link #sendToServer(String, CompoundTag)}</li>
 *   <li>server -> client: {@link #registerClientAction(String, Consumer)} (register from client-only code) +
 *       {@link #sendToPlayer(ServerPlayer, String, CompoundTag)}</li>
 * </ul>
 * Handlers always run on the main thread of the receiving side.
 */
public final class HCNetwork {
    private static final String VERSION = "1";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(HuntCraft.id("main"), () -> VERSION, VERSION::equals, VERSION::equals);

    private static final Map<String, BiConsumer<ServerPlayer, CompoundTag>> SERVER_ACTIONS = new java.util.concurrent.ConcurrentHashMap<>();
    private static final Map<String, Consumer<CompoundTag>> CLIENT_ACTIONS = new java.util.concurrent.ConcurrentHashMap<>();
    private static int nextId = 0;
    private static boolean registered = false;

    private HCNetwork() {
    }

    public static <T> void registerPacket(Class<T> type, BiConsumer<T, FriendlyByteBuf> enc, Function<FriendlyByteBuf, T> dec, BiConsumer<T, Supplier<NetworkEvent.Context>> handler) {
        CHANNEL.registerMessage(nextId++, type, enc, dec, handler);
    }

    /** Registers the core packets. Called once from the mod constructor. */
    public static void init() {
        if (registered) return;
        registered = true;
        registerPacket(ActionPacket.class, ActionPacket::encode, ActionPacket::decode, ActionPacket::handle);
        registerPacket(ClientActionPacket.class, ClientActionPacket::encode, ClientActionPacket::decode, ClientActionPacket::handle);
    }

    // ------------------------------------------------------------------ generic action registry
    public static void registerServerAction(String id, BiConsumer<ServerPlayer, CompoundTag> handler) {
        SERVER_ACTIONS.put(id, handler);
    }

    public static void registerClientAction(String id, Consumer<CompoundTag> handler) {
        CLIENT_ACTIONS.put(id, handler);
    }

    static void dispatchServer(String id, ServerPlayer player, CompoundTag tag) {
        BiConsumer<ServerPlayer, CompoundTag> h = SERVER_ACTIONS.get(id);
        if (h != null) {
            try {
                h.accept(player, tag);
            } catch (Exception e) {
                HuntCraft.LOGGER.error("Error handling action {} from {}", id, player.getGameProfile().getName(), e);
            }
        } else {
            HuntCraft.LOGGER.warn("Unknown server action {}", id);
        }
    }

    static void dispatchClient(String id, CompoundTag tag) {
        Consumer<CompoundTag> h = CLIENT_ACTIONS.get(id);
        if (h != null) {
            try {
                h.accept(tag);
            } catch (Exception e) {
                HuntCraft.LOGGER.error("Error handling client action {}", id, e);
            }
        }
    }

    // ------------------------------------------------------------------ sending helpers
    public static void sendToServer(String id, CompoundTag tag) {
        CHANNEL.sendToServer(new ActionPacket(id, tag == null ? new CompoundTag() : tag));
    }

    public static void sendToPlayer(ServerPlayer player, String id, CompoundTag tag) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new ClientActionPacket(id, tag == null ? new CompoundTag() : tag));
    }

    public static void sendToTracking(Entity entity, String id, CompoundTag tag) {
        CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> entity), new ClientActionPacket(id, tag == null ? new CompoundTag() : tag));
    }

    public static void sendToAll(String id, CompoundTag tag) {
        CHANNEL.send(PacketDistributor.ALL.noArg(), new ClientActionPacket(id, tag == null ? new CompoundTag() : tag));
    }
}
