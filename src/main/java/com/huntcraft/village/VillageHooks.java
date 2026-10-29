package com.huntcraft.village;

import com.huntcraft.quest.QuestBoardService;
import net.minecraft.server.level.ServerPlayer;

/**
 * Integration points of the village module with other modules. Every "open the quest board" in the village (the quest
 * board blocks in the gathering hall and the guild receptionist) goes through {@link #openQuestBoard(ServerPlayer)}.
 */
public final class VillageHooks {
    private VillageHooks() {
    }

    /** Opens the quest board for a hunter (the quest module owns the board screen and its data). */
    public static void openQuestBoard(ServerPlayer player) {
        QuestBoardService.open(player);
    }
}
