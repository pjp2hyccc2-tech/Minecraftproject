package com.frostbound.network;

import com.frostbound.FrostboundMod;
import com.frostbound.server.CityService;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;

/** Dispatches client actions to the city service. */
public final class ServerActions {
    private ServerActions() {
    }

    public static void handle(ServerPlayer player, String op, CompoundTag args) {
        try {
            switch (op) {
                case "found" -> CityService.foundFromPlayer(player, args);
                case "management" -> CityService.setManaging(player, args.getBoolean("on"));
                case "heat_grid" -> CityService.sendHeatGrid(player);
                case "citizens" -> CityService.sendCitizens(player, args.getInt("page"));
                case "sync" -> CityService.sendSync(player);
                default -> CityService.cityAction(player, op, args);
            }
        } catch (Exception e) {
            FrostboundMod.LOGGER.error("[FROSTBOUND] action {} from {} failed", op, player.getGameProfile().getName(), e);
        }
    }
}
