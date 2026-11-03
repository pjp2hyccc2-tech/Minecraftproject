package com.hitori.game;

import com.hitori.world.FixtureEvents;
import com.hitori.world.Layout;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;

/** What the apartment tells the game (installed as {@link FixtureEvents#LISTENER} at startup). */
public final class GameFixtureListener implements FixtureEvents.Listener {
    public static final GameFixtureListener INSTANCE = new GameFixtureListener();

    private GameFixtureListener() {}

    @Override
    public void interact(ServerPlayer player, String key, BlockPos pos) {
        Ritual.interact(player, key, pos);
    }

    @Override
    public void noise(ServerLevel level, Vec3 pos, float radius, String cause, @Nullable Entity source) {
        NoiseSystem.emit(level, pos, radius, cause, source);
    }

    @Override
    public boolean hide(ServerPlayer player, BlockPos spot) {
        // outside the game a closet is just a closet
        if (!GameManager.inGame(player)) return true;
        String kind = isWestern(spot) ? "western" : "oshiire";
        return Hiding.enter(player, spot, kind);
    }

    @Override
    public void unhide(ServerPlayer player, BlockPos spot) {
        if (GameManager.inGame(player)) Hiding.exit(player, true);
    }

    @Override
    public void frontDoorOpened(ServerPlayer player) {
        if (!GameManager.inGame(player)) return;
        GameData d = GameData.get(player.server);
        if (d.ritualRunning()) {
            d.breakRule("door");
            Endings.begin(player.serverLevel(), player, GameData.ENDING_SOTO);
        } else {
            GameManager.thought(player, "thought.hitori.door_before");
        }
    }

    @Override
    public void note(ServerPlayer player, String noteId) {
        if (noteId.equals("howto") || noteId.equals("printout")) {
            GameManager.event(player, GameManager.EV_PRINTOUT, 0, "");
            Ritual.readPrintout(player);
        }
    }

    @Override
    public void switched(ServerLevel level, String key, boolean on) {
        if (!GameManager.isGameLevel(level)) return;
        if (on && !key.equals("tv") && !key.equals("breaker")) Ritual.lightUsed(level, key);
        if (on && key.equals("breaker")) Ritual.lightUsed(level, key);
    }

    private static boolean isWestern(BlockPos spot) {
        if (!Layout.has("western_closet")) return false;
        BlockPos w = Layout.marker("western_closet").pos();
        return Math.abs(w.getX() - spot.getX()) <= 1 && Math.abs(w.getZ() - spot.getZ()) <= 1;
    }
}
