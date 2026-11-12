package com.eternalrealm.network.forge;

import com.eternalrealm.job.SkillTheme;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * S2C: light a job skill leaves where it lands, drawn by every client near it (docs/modules/skills.md, "The light of
 * the skills"): a ground area for its whole life ({@link #ZONE}: radius, ticks before the first pulse, pulses and the
 * ticks between them, whether it heals or harms) or the burst of an exploding spell or arrow ({@link #BLAST}).
 * {@code theme} is the skill's {@link SkillTheme} ordinal, {@code yaw} the caster's facing, {@code seed} makes every
 * client draw the same details.
 */
public record SkillFxPacket(int kind, int theme, float x, float y, float z, float yaw, float radius, int delay,
                            int pulses, int interval, int flags, int seed) {
    public static final int ZONE = 0;
    public static final int BLAST = 1;
    /** {@link #flags}: the area heals the caster's side. */
    public static final int HEALS = 1;
    /** {@link #flags}: the area hurts enemies. */
    public static final int HARMS = 2;
    static final int MAX_BYTES = 40;
    static final float MAX_RADIUS = 16.0F;
    static final int MAX_TICKS = 600;
    static final int MAX_PULSES = 64;

    public SkillFxPacket {
        if (!valid(kind, theme, x, y, z, yaw, radius, delay, pulses, interval, flags)) {
            throw new IllegalArgumentException("skill fx " + kind + "/" + theme);
        }
    }

    /** The light of a ground area. */
    public static SkillFxPacket zone(SkillTheme theme, double x, double y, double z, float yaw, double radius,
                                     int delay, int pulses, int interval, boolean heals, boolean harms, int seed) {
        return new SkillFxPacket(ZONE, theme.ordinal(), (float) x, (float) y, (float) z, wrap(yaw),
                (float) Math.min(MAX_RADIUS, Math.max(0.5, radius)), Math.min(MAX_TICKS, Math.max(0, delay)),
                Math.min(MAX_PULSES, Math.max(1, pulses)), Math.min(MAX_TICKS, Math.max(1, interval)),
                (heals ? HEALS : 0) | (harms ? HARMS : 0), seed);
    }

    /** The burst of an exploding spell or arrow. */
    public static SkillFxPacket blast(SkillTheme theme, double x, double y, double z, float yaw, double radius,
                                      int seed) {
        return new SkillFxPacket(BLAST, theme.ordinal(), (float) x, (float) y, (float) z, wrap(yaw),
                (float) Math.min(MAX_RADIUS, Math.max(0.5, radius)), 0, 1, 1, HARMS, seed);
    }

    public SkillTheme themeValue() {
        return SkillTheme.values()[theme];
    }

    public boolean heals() {
        return (flags & HEALS) != 0;
    }

    public boolean harms() {
        return (flags & HARMS) != 0;
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeByte(kind);
        buffer.writeByte(theme);
        buffer.writeFloat(x);
        buffer.writeFloat(y);
        buffer.writeFloat(z);
        buffer.writeFloat(yaw);
        buffer.writeFloat(radius);
        buffer.writeShort(delay);
        buffer.writeByte(pulses);
        buffer.writeShort(interval);
        buffer.writeByte(flags);
        buffer.writeInt(seed);
    }

    public static SkillFxPacket decode(FriendlyByteBuf buffer) {
        PacketLimits.requireMaxSize(buffer, MAX_BYTES, "SkillFxPacket");
        int kind = buffer.readUnsignedByte();
        int theme = buffer.readUnsignedByte();
        float x = buffer.readFloat();
        float y = buffer.readFloat();
        float z = buffer.readFloat();
        float yaw = buffer.readFloat();
        float radius = buffer.readFloat();
        int delay = buffer.readUnsignedShort();
        int pulses = buffer.readUnsignedByte();
        int interval = buffer.readUnsignedShort();
        int flags = buffer.readUnsignedByte();
        int seed = buffer.readInt();
        if (!valid(kind, theme, x, y, z, yaw, radius, delay, pulses, interval, flags)) {
            throw new DecoderException("Invalid SkillFxPacket");
        }
        return new SkillFxPacket(kind, theme, x, y, z, yaw, radius, delay, pulses, interval, flags, seed);
    }

    static boolean valid(int kind, int theme, float x, float y, float z, float yaw, float radius, int delay,
                         int pulses, int interval, int flags) {
        return (kind == ZONE || kind == BLAST) && theme >= 0 && theme < SkillTheme.values().length
                && Float.isFinite(x) && Float.isFinite(y) && Float.isFinite(z) && Math.abs(x) < 3.0E7F
                && Math.abs(y) < 1.0E5F && Math.abs(z) < 3.0E7F && Float.isFinite(yaw) && Math.abs(yaw) <= 360.0F
                && Float.isFinite(radius) && radius >= 0.5F && radius <= MAX_RADIUS && delay >= 0
                && delay <= MAX_TICKS && pulses >= 1 && pulses <= MAX_PULSES && interval >= 1 && interval <= MAX_TICKS
                && (flags & ~(HEALS | HARMS)) == 0;
    }

    private static float wrap(float yaw) {
        float w = yaw % 360.0F;
        return Float.isFinite(w) ? w : 0.0F;
    }

    /** Client thread (via consumerMainThread). */
    public static void handle(SkillFxPacket packet, Supplier<NetworkEvent.Context> context) {
        ClientboundHandlers.onSkillFx(packet);
    }
}
