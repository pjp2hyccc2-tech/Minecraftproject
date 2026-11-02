package com.clockworkdiver.network;

import com.clockworkdiver.client.ClientPacketHandler;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.List;
import java.util.function.Supplier;

/** Server to client messages. Handlers hop to {@link ClientPacketHandler}, which is only loaded on the client. */
public final class S2C {
    private S2C() {}

    private static CompoundTag orEmpty(CompoundTag tag) {
        return tag == null ? new CompoundTag() : tag;
    }

    /** Modes for {@link OpenDialogue}. */
    public static final int DIALOGUE_OFFER = 0;
    public static final int DIALOGUE_WAITING = 1;
    public static final int DIALOGUE_DELIVER = 2;
    public static final int DIALOGUE_THANKS = 3;

    public record OpenDialogue(int entityId, int mode, String profile, CompoundTag clock, int reward) {
        public void encode(FriendlyByteBuf buf) {
            buf.writeVarInt(entityId);
            buf.writeVarInt(mode);
            buf.writeUtf(profile);
            buf.writeNbt(clock);
            buf.writeVarInt(reward);
        }

        public static OpenDialogue decode(FriendlyByteBuf buf) {
            return new OpenDialogue(buf.readVarInt(), buf.readVarInt(), buf.readUtf(), orEmpty(buf.readNbt()), buf.readVarInt());
        }

        public void handle(Supplier<NetworkEvent.Context> ctx) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPacketHandler.openDialogue(this));
        }
    }

    public record OpenBench(CompoundTag clock, int coins, int wrenchLevel, int steamLevel, int lensLevel,
                            boolean glassesAnimation, boolean tutorial, int reputation) {
        public void encode(FriendlyByteBuf buf) {
            buf.writeNbt(clock);
            buf.writeVarInt(coins);
            buf.writeVarInt(wrenchLevel);
            buf.writeVarInt(steamLevel);
            buf.writeVarInt(lensLevel);
            buf.writeBoolean(glassesAnimation);
            buf.writeBoolean(tutorial);
            buf.writeVarInt(reputation);
        }

        public static OpenBench decode(FriendlyByteBuf buf) {
            return new OpenBench(orEmpty(buf.readNbt()), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
                    buf.readBoolean(), buf.readBoolean(), buf.readVarInt());
        }

        public void handle(Supplier<NetworkEvent.Context> ctx) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPacketHandler.openBench(this));
        }
    }

    /** The objective tracker line. An empty key clears it. */
    public record Objective(String key, int a, int b) {
        public void encode(FriendlyByteBuf buf) {
            buf.writeUtf(key);
            buf.writeVarInt(a);
            buf.writeVarInt(b);
        }

        public static Objective decode(FriendlyByteBuf buf) {
            return new Objective(buf.readUtf(), buf.readVarInt(), buf.readVarInt());
        }

        public void handle(Supplier<NetworkEvent.Context> ctx) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPacketHandler.objective(this));
        }
    }

    /** Tutorial hint cards, shown one after another. */
    public record Hint(List<String> keys) {
        public void encode(FriendlyByteBuf buf) {
            buf.writeCollection(keys, FriendlyByteBuf::writeUtf);
        }

        public static Hint decode(FriendlyByteBuf buf) {
            return new Hint(buf.readList(FriendlyByteBuf::readUtf));
        }

        public void handle(Supplier<NetworkEvent.Context> ctx) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPacketHandler.hint(this));
        }
    }

    public record DiveState(boolean inDive, String defectType, String partKey, int period, long anchor, int lensLevel, boolean tutorial) {
        public void encode(FriendlyByteBuf buf) {
            buf.writeBoolean(inDive);
            buf.writeUtf(defectType);
            buf.writeUtf(partKey);
            buf.writeVarInt(period);
            buf.writeLong(anchor);
            buf.writeVarInt(lensLevel);
            buf.writeBoolean(tutorial);
        }

        public static DiveState decode(FriendlyByteBuf buf) {
            return new DiveState(buf.readBoolean(), buf.readUtf(), buf.readUtf(), buf.readVarInt(), buf.readLong(), buf.readVarInt(),
                    buf.readBoolean());
        }

        public void handle(Supplier<NetworkEvent.Context> ctx) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPacketHandler.diveState(this));
        }
    }

    /** Steam pressure, combo and the judgement of the last hit (-1 = none). */
    public record Combat(float steam, float maxSteam, int combo, int quality) {
        public void encode(FriendlyByteBuf buf) {
            buf.writeFloat(steam);
            buf.writeFloat(maxSteam);
            buf.writeVarInt(combo);
            buf.writeByte(quality);
        }

        public static Combat decode(FriendlyByteBuf buf) {
            return new Combat(buf.readFloat(), buf.readFloat(), buf.readVarInt(), buf.readByte());
        }

        public void handle(Supplier<NetworkEvent.Context> ctx) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPacketHandler.combat(this));
        }
    }

    public static final int TRANSITION_ARRIVE = 0;
    public static final int TRANSITION_RETURN = 1;
    public static final int TRANSITION_REPAIRED = 2;
    public static final int TRANSITION_FAILED = 3;
    public static final int TRANSITION_WORKSHOP = 4;

    /** Full screen fades with a title, used when shrinking into or growing out of a clock. */
    public record Transition(int kind, String title, String subtitle) {
        public void encode(FriendlyByteBuf buf) {
            buf.writeVarInt(kind);
            buf.writeUtf(title);
            buf.writeUtf(subtitle);
        }

        public static Transition decode(FriendlyByteBuf buf) {
            return new Transition(buf.readVarInt(), buf.readUtf(), buf.readUtf());
        }

        public void handle(Supplier<NetworkEvent.Context> ctx) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPacketHandler.transition(this));
        }
    }

    public record TempoSync(int period, long anchor) {
        public void encode(FriendlyByteBuf buf) {
            buf.writeVarInt(period);
            buf.writeLong(anchor);
        }

        public static TempoSync decode(FriendlyByteBuf buf) {
            return new TempoSync(buf.readVarInt(), buf.readLong());
        }

        public void handle(Supplier<NetworkEvent.Context> ctx) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPacketHandler.tempo(this));
        }
    }

    /** Opens the screen with the big "Repair Bench" button. */
    public record RepairPrompt(CompoundTag clock) {
        public void encode(FriendlyByteBuf buf) {
            buf.writeNbt(clock);
        }

        public static RepairPrompt decode(FriendlyByteBuf buf) {
            return new RepairPrompt(orEmpty(buf.readNbt()));
        }

        public void handle(Supplier<NetworkEvent.Context> ctx) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPacketHandler.repairPrompt(this));
        }
    }
}
