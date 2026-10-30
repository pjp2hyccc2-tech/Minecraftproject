package com.nihonisshu.tabi.game;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.nihonisshu.tabi.data.TabiData;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

/**
 * Elevators and lifts (Port Tower observation deck...). meta.json "warps":
 * [{"pos":[x,y,z], "to":[x,y,z,yaw], "fee":500, "label":"展望フロアへ", "journal":"spot.port_tower_top"}]
 */
public final class Warps {
    private Warps() {}

    public static void use(ServerPlayer p, BlockPos pos) {
        JsonArray arr = TabiData.meta().getAsJsonArray("warps");
        if (arr == null) return;
        JsonObject best = null;
        double bestD = 9;
        for (JsonElement e : arr) {
            JsonObject o = e.getAsJsonObject();
            JsonArray a = o.getAsJsonArray("pos");
            double d = pos.distSqr(new BlockPos(a.get(0).getAsInt(), a.get(1).getAsInt(), a.get(2).getAsInt()));
            if (d <= bestD) {
                bestD = d;
                best = o;
            }
        }
        if (best == null) {
            p.displayClientMessage(Component.literal("（点検中）"), true);
            return;
        }
        int fee = TabiData.integer(best, "fee", 0);
        if (fee > 0 && !PlayerData.spend(p, fee)) return;
        JsonArray to = best.getAsJsonArray("to");
        double x = to.get(0).getAsDouble(), y = to.get(1).getAsDouble(), z = to.get(2).getAsDouble();
        float yaw = to.size() > 3 ? to.get(3).getAsFloat() : p.getYRot();
        p.teleportTo(p.serverLevel(), x, y, z, yaw, 0f);
        p.level().playSound(null, BlockPos.containing(x, y, z), SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.BLOCKS, 0.8f, 1.5f);
        String label = TabiData.str(best, "label", "");
        if (!label.isEmpty()) p.displayClientMessage(Component.literal(label).withStyle(ChatFormatting.AQUA), true);
        String j = TabiData.str(best, "journal", "");
        if (!j.isEmpty()) PlayerData.unlock(p, j);
    }
}
