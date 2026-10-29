package com.huntcraft.core;

import com.huntcraft.network.HCNetwork;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/** Server helper to shake the camera of nearby players (client handles "core/shake"). */
public final class CameraShake {
    private CameraShake() {
    }

    public static void shake(ServerLevel level, Vec3 pos, double radius, float intensity, int ticks) {
        for (ServerPlayer p : level.players()) {
            double d = p.position().distanceTo(pos);
            if (d > radius) continue;
            float falloff = (float) (1.0 - d / radius);
            shake(p, intensity * (0.4F + 0.6F * falloff), ticks);
        }
    }

    public static void shake(ServerPlayer player, float intensity, int ticks) {
        CompoundTag t = new CompoundTag();
        t.putFloat("i", intensity);
        t.putInt("t", ticks);
        HCNetwork.sendToPlayer(player, "core/shake", t);
    }
}
