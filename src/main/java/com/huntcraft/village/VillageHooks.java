package com.huntcraft.village;

import com.huntcraft.network.HCScreens;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;

/**
 * Integration points of the village module with other modules. Every "open the quest board" in the village (the quest
 * board blocks in the gathering hall and the guild receptionist) goes through {@link #openQuestBoard(ServerPlayer)}.
 */
public final class VillageHooks {
    private VillageHooks() {
    }

    /**
     * Opens the quest board for a hunter.
     * <p>
     * TODO(integration): replace the body with {@code com.huntcraft.quest.QuestBoardService.open(player);}
     * once the quest module is merged (the quest module owns the quest board screen and its data). Until then the
     * generic screen id is used, which the quest module's client also registers.
     */
    public static void openQuestBoard(ServerPlayer player) {
        CompoundTag tag = new CompoundTag();
        tag.putString("source", "village");
        HCScreens.open(player, HCScreens.QUEST_BOARD, tag);
    }
}
