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
    /** A friend gathered for a story scene says a line ({@code extra} is its language key). */
    public static final int DIALOGUE_CHATTER = 4;

    public record OpenDialogue(int entityId, int mode, String profile, CompoundTag clock, int reward, String extra) {
        public void encode(FriendlyByteBuf buf) {
            buf.writeVarInt(entityId);
            buf.writeVarInt(mode);
            buf.writeUtf(profile);
            buf.writeNbt(clock);
            buf.writeVarInt(reward);
            buf.writeUtf(extra);
        }

        public static OpenDialogue decode(FriendlyByteBuf buf) {
            return new OpenDialogue(buf.readVarInt(), buf.readVarInt(), buf.readUtf(), orEmpty(buf.readNbt()), buf.readVarInt(), buf.readUtf());
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

    /** The objective tracker: a main line and an optional second line (memory fragments...). An empty key clears it. */
    public record Objective(String key, int a, int b, String sub, int sa, int sb) {
        public Objective(String key, int a, int b) {
            this(key, a, b, "", 0, 0);
        }

        public void encode(FriendlyByteBuf buf) {
            buf.writeUtf(key);
            buf.writeVarInt(a);
            buf.writeVarInt(b);
            buf.writeUtf(sub);
            buf.writeVarInt(sa);
            buf.writeVarInt(sb);
        }

        public static Objective decode(FriendlyByteBuf buf) {
            return new Objective(buf.readUtf(), buf.readVarInt(), buf.readVarInt(), buf.readUtf(), buf.readVarInt(), buf.readVarInt());
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

    /**
     * Entering or leaving a clock. {@code offset} shifts the judged beat (off-beat), {@code deadline} is the game time
     * when a timed repair fails (0 = none).
     */
    public record DiveState(boolean inDive, String defectType, String partKey, int period, long anchor, int lensLevel, boolean tutorial,
                            int mods, int offset, long deadline) {
        public void encode(FriendlyByteBuf buf) {
            buf.writeBoolean(inDive);
            buf.writeUtf(defectType);
            buf.writeUtf(partKey);
            buf.writeVarInt(period);
            buf.writeLong(anchor);
            buf.writeVarInt(lensLevel);
            buf.writeBoolean(tutorial);
            buf.writeVarInt(mods);
            buf.writeVarInt(offset);
            buf.writeLong(deadline);
        }

        public static DiveState decode(FriendlyByteBuf buf) {
            return new DiveState(buf.readBoolean(), buf.readUtf(), buf.readUtf(), buf.readVarInt(), buf.readLong(), buf.readVarInt(),
                    buf.readBoolean(), buf.readVarInt(), buf.readVarInt(), buf.readLong());
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
    /** Story quest title card: title = quest id. */
    public static final int TRANSITION_QUEST = 5;
    /** Chapter title card: title = chapter number. */
    public static final int TRANSITION_CHAPTER = 6;
    /** A boss phase change or similar dramatic moment inside a clock. */
    public static final int TRANSITION_ALERT = 7;
    /** End of a chapter (shown by the client after the last quest's thanks): title = chapter number. */
    public static final int TRANSITION_CHAPTER_END = 8;

    /** Full screen fades with a title, used when shrinking into or growing out of a clock and for story cards. */
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

    public record TempoSync(int period, long anchor, int offset) {
        public void encode(FriendlyByteBuf buf) {
            buf.writeVarInt(period);
            buf.writeLong(anchor);
            buf.writeVarInt(offset);
        }

        public static TempoSync decode(FriendlyByteBuf buf) {
            return new TempoSync(buf.readVarInt(), buf.readLong(), buf.readVarInt());
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

    /** A memory fragment was picked up inside a clock: show its text. */
    public record Memory(String key, int index, int count) {
        public void encode(FriendlyByteBuf buf) {
            buf.writeUtf(key);
            buf.writeVarInt(index);
            buf.writeVarInt(count);
        }

        public static Memory decode(FriendlyByteBuf buf) {
            return new Memory(buf.readUtf(), buf.readVarInt(), buf.readVarInt());
        }

        public void handle(Supplier<NetworkEvent.Context> ctx) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPacketHandler.memory(this));
        }
    }

    /** Story progress for the journal: quests delivered and keepsakes received. */
    public record StorySync(int storyIndex, int keepsakes, int repairedClocks, int reputation) {
        public void encode(FriendlyByteBuf buf) {
            buf.writeVarInt(storyIndex);
            buf.writeVarInt(keepsakes);
            buf.writeVarInt(repairedClocks);
            buf.writeVarInt(reputation);
        }

        public static StorySync decode(FriendlyByteBuf buf) {
            return new StorySync(buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt());
        }

        public void handle(Supplier<NetworkEvent.Context> ctx) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPacketHandler.story(this));
        }
    }
}
