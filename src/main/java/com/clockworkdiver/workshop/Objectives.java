package com.clockworkdiver.workshop;

import com.clockworkdiver.clock.ClockData;
import com.clockworkdiver.data.PlayerProgress;
import com.clockworkdiver.data.WorldProgress;
import com.clockworkdiver.interior.DiveManager;
import com.clockworkdiver.interior.DiveSession;
import com.clockworkdiver.network.ModNetwork;
import com.clockworkdiver.network.S2C;
import com.clockworkdiver.registry.ModDimensions;
import com.clockworkdiver.story.StoryManager;
import com.clockworkdiver.story.StoryQuest;
import net.minecraft.server.level.ServerPlayer;

/** Works out the current objective from the player's state and sends it to the tracker on the HUD. */
public final class Objectives {
    private Objectives() {}

    public static void sync(ServerPlayer player) {
        if (player.connection == null) return;
        ModNetwork.send(player, current(player));
    }

    public static S2C.Objective current(ServerPlayer player) {
        PlayerProgress progress = WorldProgress.of(player);
        DiveSession s = DiveManager.session(player);
        if (s != null) {
            return new S2C.Objective(s.objectiveKey(), s.done, s.total, s.fragCount > 0 ? "objective.clockworkdiver.fragments" : "",
                    s.fragCollected, s.fragCount);
        }
        ClockData clock = progress.activeClock;
        boolean tower = StoryManager.isTower(clock);
        StoryQuest quest = StoryManager.of(clock);
        String sub = quest == null ? "" : quest.titleKey();
        int num = quest == null ? 0 : quest.number;
        if (clock != null && progress.accepted) {
            if (clock.allRepaired()) {
                return new S2C.Objective(tower ? "objective.clockworkdiver.deliver_tower" : "objective.clockworkdiver.deliver", 0, 0, sub, num, 0);
            }
            if (clock.foundCount() < clock.defects.size()) {
                return new S2C.Objective(tower ? "objective.clockworkdiver.find_tower" : "objective.clockworkdiver.find",
                        clock.foundCount(), clock.defects.size(), sub, num, 0);
            }
            return new S2C.Objective("objective.clockworkdiver.choose", clock.repairedCount(), clock.faultCount(), sub, num, 0);
        }
        if (clock != null) {
            return new S2C.Objective(tower ? "objective.clockworkdiver.talk_tower" : "objective.clockworkdiver.talk", 0, 0, sub, num, 0);
        }
        StoryQuest next = StoryManager.current(progress);
        if (ModDimensions.isWorkshop(player.level())) {
            if (next != null) return new S2C.Objective("objective.clockworkdiver.wait", 0, 0, next.titleKey(), next.number, 0);
            return new S2C.Objective("objective.clockworkdiver.wait", 0, 0);
        }
        if (!progress.tutorialDone) return new S2C.Objective("objective.clockworkdiver.start", 0, 0);
        if (next != null) return new S2C.Objective("objective.clockworkdiver.return", 0, 0, next.titleKey(), next.number, 0);
        return new S2C.Objective("", 0, 0);
    }
}
