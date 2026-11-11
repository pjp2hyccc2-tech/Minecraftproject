package com.eternalrealm.testkit.bots.forge;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.util.ReferenceCountUtil;
import net.minecraft.network.Connection;
import net.minecraft.network.ConnectionProtocol;
import net.minecraft.network.protocol.PacketFlow;

/**
 * The connection of a test bot ({@link TestBot}): a real {@link Connection} on an in-memory channel (as the game's own
 * test players use) that throws away everything the server sends, so the bot logs in, is saved and logs out through
 * the same code as a player (the server's login, the mod's login events, the tab list) and nothing piles up. The
 * server never ticks it (it is not one of its network connections): no keep-alive, no time-out; the bot ticks itself.
 * Being disconnected (a kick, the server stopping, {@code /er admin bots remove}) works as for a player.
 */
final class BotConnection extends Connection {
    private BotConnection() {
        super(PacketFlow.SERVERBOUND);
    }

    static BotConnection open() {
        BotConnection connection = new BotConnection();
        // the connection takes the channel when it becomes active (at once: an embedded channel is registered and
        // active when created); the discarder in front of it swallows every packet sent
        new EmbeddedChannel(new Discard(), connection);
        connection.setProtocol(ConnectionProtocol.PLAY);
        return connection;
    }

    /** Swallows outgoing packets: nobody reads them. */
    private static final class Discard extends ChannelOutboundHandlerAdapter {
        @Override
        public void write(ChannelHandlerContext context, Object message, ChannelPromise promise) {
            ReferenceCountUtil.release(message);
            promise.setSuccess();
        }
    }
}
