package com.huntcraft.network;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;

import java.util.function.Consumer;

/**
 * Opening client screens from the server across module boundaries.
 * <p>Client side (in a module's client setup): {@code HCScreens.registerClient(HCScreens.QUEST_BOARD, tag -> mc.setScreen(...))}
 * <p>Server side: {@code HCScreens.open(player, HCScreens.QUEST_BOARD, data)}
 */
public final class HCScreens {
    public static final String QUEST_BOARD = "quest_board";     // quest module
    public static final String BLACKSMITH = "blacksmith";       // village module
    public static final String MERCHANT = "merchant";           // village module
    public static final String KITCHEN = "kitchen";             // village module
    public static final String COMPANION = "companion";         // village module
    public static final String HUNTER_NOTES = "hunter_notes";   // village module
    public static final String QUEST_RESULT = "quest_result";   // quest module

    private HCScreens() {
    }

    public static void registerClient(String screenId, Consumer<CompoundTag> opener) {
        HCNetwork.registerClientAction("screen/" + screenId, opener);
    }

    public static void open(ServerPlayer player, String screenId, CompoundTag data) {
        HCNetwork.sendToPlayer(player, "screen/" + screenId, data == null ? new CompoundTag() : data);
    }
}
