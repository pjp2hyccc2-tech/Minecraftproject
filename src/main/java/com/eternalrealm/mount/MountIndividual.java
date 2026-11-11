package com.eternalrealm.mount;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.Objects;
import java.util.Optional;
import java.util.SplittableRandom;
import java.util.UUID;

/**
 * One mount (docs/modules/mounts.md): its own id, its kind, its look and its individual values. A horse or griffin
 * moves by its kind's numbers scaled by its values ({@link #stats()}); the arcbike is a machine, always standard.
 * Immutable.
 */
public record MountIndividual(UUID id, MountKind kind, MountLook look, MountTraits traits) {
    public MountIndividual {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(kind, "kind");
        look = MountLooks.orClassic(kind, look);
        traits = MountLooks.varies(kind) ? Objects.requireNonNull(traits, "traits") : MountTraits.STANDARD;
    }

    /** The classic look with the standard values (mounts bought before individuality, operators' gifts). */
    public static MountIndividual standard(UUID id, MountKind kind) {
        return new MountIndividual(id, kind, MountLooks.classic(kind), MountTraits.STANDARD);
    }

    /** A new mount as the stable breeds them: a rolled look and rolled values. */
    public static MountIndividual roll(UUID id, MountKind kind, SplittableRandom random) {
        if (!MountLooks.varies(kind)) {
            return standard(id, kind);
        }
        return new MountIndividual(id, kind, MountLooks.roll(kind, random), MountTraits.roll(random));
    }

    /** The kind's numbers scaled by the values: speed → top and ground speed, acceleration, lift → jump, climb, glide. */
    public MountKind.MountStats stats() {
        return scaled(kind, traits);
    }

    /** {@code kind}'s numbers scaled by {@code traits} (the arcbike's are never scaled). */
    public static MountKind.MountStats scaled(MountKind kind, MountTraits traits) {
        MountKind.MountStats base = kind.stats();
        if (!MountLooks.varies(kind)) {
            return base;
        }
        double speed = MountTraits.factor(traits.speed());
        double acceleration = MountTraits.factor(traits.acceleration());
        double lift = MountTraits.factor(traits.lift());
        return new MountKind.MountStats(base.topSpeed() * speed, Math.min(1.0, base.acceleration() * acceleration),
                base.jump() * lift, base.backFactor(), base.strafeFactor(), base.coast(), base.groundSpeed() * speed,
                base.climb() * lift, base.descend(), base.glide() / lift);
    }

    public MountTraits.Grade grade() {
        return traits.grade();
    }

    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("id", id.toString());
        json.addProperty("kind", kind.id());
        json.addProperty("coat", look.coat());
        json.addProperty("shape", look.shape());
        json.addProperty("speed", traits.speed());
        json.addProperty("accel", traits.acceleration());
        json.addProperty("lift", traits.lift());
        return json;
    }

    /**
     * Lenient: an unknown kind or a missing id gives empty; an unknown look becomes the classic one and values out of
     * range are clamped.
     */
    public static Optional<MountIndividual> fromJson(JsonElement element) {
        if (element == null || !element.isJsonObject()) {
            return Optional.empty();
        }
        JsonObject json = element.getAsJsonObject();
        Optional<MountKind> kind = MountKind.byId(string(json, "kind"));
        Optional<UUID> id = uuid(string(json, "id"));
        if (kind.isEmpty() || id.isEmpty()) {
            return Optional.empty();
        }
        MountTraits traits = new MountTraits(MountTraits.clamp(number(json, "speed")),
                MountTraits.clamp(number(json, "accel")), MountTraits.clamp(number(json, "lift")));
        return Optional.of(new MountIndividual(id.get(), kind.get(),
                new MountLook(string(json, "coat"), string(json, "shape")), traits));
    }

    static Optional<UUID> uuid(String text) {
        try {
            return text.isEmpty() ? Optional.empty() : Optional.of(UUID.fromString(text));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    private static String string(JsonObject json, String key) {
        JsonElement element = json.get(key);
        return element != null && element.isJsonPrimitive() ? element.getAsString() : "";
    }

    private static int number(JsonObject json, String key) {
        JsonElement element = json.get(key);
        if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
            return MountTraits.STANDARD_VALUE;
        }
        double value = element.getAsDouble();
        return Double.isFinite(value) ? (int) Math.max(Integer.MIN_VALUE, Math.min(Integer.MAX_VALUE, value))
                : MountTraits.STANDARD_VALUE;
    }
}
