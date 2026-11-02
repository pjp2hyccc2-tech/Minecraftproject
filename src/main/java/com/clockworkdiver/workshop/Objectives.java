package com.clockworkdiver.workshop;

import com.clockworkdiver.clock.ClockData;
import com.clockworkdiver.data.PlayerProgress;
import com.clockworkdiver.data.WorldProgress;
import com.clockworkdiver.interior.DiveManager;
import com.clockworkdiver.interior.DiveSession;
import com.clockworkdiver.network.ModNetwork;
import com.clockworkdiver.network.S2C;
import com.clockworkdiver.registry.ModDimensions;
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
        if (s != null) return new S2C.Objective(s.objectiveKey(), s.done, s.total);
        ClockData clock = progress.activeClock;
        if (clock != null && progress.accepted) {
            if (clock.allRepaired()) return new S2C.Objective("objective.clockworkdiver.deliver", 0, 0);
            if (clock.foundCount() < clock.defects.size()) {
                return new S2C.Objective("objective.clockworkdiver.find", clock.foundCount(), clock.defects.size());
            }
            return new S2C.Objective("objective.clockworkdiver.choose", clock.repairedCount(), clock.defects.size());
        }
        if (clock != null) return new S2C.Objective("objective.clockworkdiver.talk", 0, 0);
        if (ModDimensions.isWorkshop(player.level())) return new S2C.Objective("objective.clockworkdiver.wait", 0, 0);
        if (!progress.tutorialDone) return new S2C.Objective("objective.clockworkdiver.start", 0, 0);
        return new S2C.Objective("", 0, 0);
    }
}
