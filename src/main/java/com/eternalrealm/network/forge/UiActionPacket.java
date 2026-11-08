package com.eternalrealm.network.forge;

import com.eternalrealm.server.ServerContext;
import com.eternalrealm.server.UiActions;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.Objects;
import java.util.function.Supplier;
import java.util.regex.Pattern;

/**
 * C2S: a request from one of the mod's screens ("choose this job", "accept this quest"...). Only an intent: the
 * registered {@link UiActions} handler validates everything against the server state. Unknown actions and malformed
 * arguments are protocol violations (a genuine client never sends them).
 *
 * @param action {@code [a-z0-9_.]{1,64}}
 * @param args   a JSON object (at most {@link #MAX_ARGS} characters)
 */
public record UiActionPacket(String action, String args) {
    public static final int MAX_ARGS = 2048;
    static final int MAX_BYTES = 64 * 3 + MAX_ARGS * 3 + 16;
    private static final Pattern ACTION = Pattern.compile("[a-z0-9_.]{1,64}");

    public UiActionPacket {
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(args, "args");
    }

    public UiActionPacket(String action, JsonObject args) {
        this(action, args.toString());
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeUtf(action, 64);
        buffer.writeUtf(args, MAX_ARGS);
    }

    public static UiActionPacket decode(FriendlyByteBuf buffer) {
        PacketLimits.requireMaxSize(buffer, MAX_BYTES, "UiActionPacket");
        return new UiActionPacket(buffer.readUtf(64), buffer.readUtf(MAX_ARGS));
    }

    /** Server thread (via consumerMainThread). */
    public static void handle(UiActionPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        ServerPacketGuard.accept(contextSupplier, "ui_action", ServerContext::uiLimiter).ifPresent(accepted -> {
            if (!ACTION.matcher(packet.action()).matches()) {
                ServerPacketGuard.violation(accepted.context(), accepted.player(), "ui_action: bad action name");
                return;
            }
            JsonObject args;
            try {
                JsonElement parsed = JsonParser.parseString(packet.args());
                if (parsed == null || !parsed.isJsonObject()) {
                    throw new JsonParseException("not an object");
                }
                args = parsed.getAsJsonObject();
            } catch (JsonParseException e) {
                ServerPacketGuard.violation(accepted.context(), accepted.player(), "ui_action: malformed arguments");
                return;
            }
            UiActions.dispatch(accepted.player(), accepted.context(), packet.action(), args);
        });
    }
}
