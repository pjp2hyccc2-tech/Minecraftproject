package com.sounan.game;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/** Paces the night: ambient scares, monster appearances, scripted moments. */
public final class Director {
    private Director() {}

    public static void tickLevel(ServerLevel level, GameData data) {
    }

    public static void onAreaEntered(ServerLevel level, ServerPlayer p, PlayerState ps, GameData data, int area) {
    }

    public static void clearFor(ServerPlayer p) {
    }

    public static void calmDown(ServerPlayer p, int ticks) {
        PlayerState ps = GameManager.state(p);
        ps.nextMonster = Math.max(ps.nextMonster, ticks);
        ps.nextMajor = Math.max(ps.nextMajor, ticks);
    }

    public static void onPhoto(ServerLevel level, ServerPlayer p) {
    }

    public static void onBell(ServerLevel level, ServerPlayer p) {
    }

    public static void onFlare(ServerLevel level, ServerPlayer p) {
    }

    public static void onCall(ServerLevel level, ServerPlayer p) {
    }

    public static void answerPayphone(ServerLevel level, ServerPlayer p) {
        GameManager.thought(p, "thought.sounan.payphone_dead");
    }

    public static void onBagOpened(ServerLevel level, ServerPlayer p) {
    }

    public static void onKeyTaken(ServerLevel level, ServerPlayer p) {
    }

    public static void clearAll() {
    }
}
