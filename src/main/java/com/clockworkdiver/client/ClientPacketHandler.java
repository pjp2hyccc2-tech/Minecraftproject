package com.clockworkdiver.client;

import com.clockworkdiver.client.gui.BenchScreen;
import com.clockworkdiver.client.gui.DialogueScreen;
import com.clockworkdiver.client.gui.RepairPromptScreen;
import com.clockworkdiver.clock.ClockData;
import com.clockworkdiver.network.S2C;
import net.minecraft.client.Minecraft;

/** Client side of the server messages. Only ever loaded on the physical client. */
public final class ClientPacketHandler {
    private ClientPacketHandler() {}

    public static void openDialogue(S2C.OpenDialogue msg) {
        ClockData clock = ClockData.load(msg.clock());
        Minecraft.getInstance().setScreen(new DialogueScreen(msg.entityId(), msg.mode(), msg.profile(), clock, msg.reward()));
    }

    public static void openBench(S2C.OpenBench msg) {
        Minecraft.getInstance().setScreen(new BenchScreen(msg));
    }

    public static void objective(S2C.Objective msg) {
        if (!msg.key().equals(ClientState.objectiveKey) || msg.a() != ClientState.objectiveA) ClientState.objectiveFlash = 20;
        ClientState.objectiveKey = msg.key();
        ClientState.objectiveA = msg.a();
        ClientState.objectiveB = msg.b();
    }

    public static void hint(S2C.Hint msg) {
        ClientState.HINTS.addAll(msg.keys());
    }

    public static void diveState(S2C.DiveState msg) {
        if (msg.inDive()) {
            ClientState.inDive = true;
            ClientState.defectType = msg.defectType();
            ClientState.partKey = msg.partKey();
            ClientState.period = Math.max(1, msg.period());
            ClientState.anchor = msg.anchor();
            ClientState.lensLevel = msg.lensLevel();
            ClientState.tutorialDive = msg.tutorial();
            ClientState.lastBeat = Long.MIN_VALUE;
            // Fresh hints for the chamber follow this message; drop stale workshop hints.
            ClientState.HINTS.clear();
            ClientState.currentHint = null;
            ClientState.hintTicks = 0;
        } else {
            ClientState.leaveDive();
        }
    }

    public static void combat(S2C.Combat msg) {
        ClientState.steam = msg.steam();
        ClientState.maxSteam = Math.max(1.0f, msg.maxSteam());
        ClientState.combo = msg.combo();
    }

    public static void transition(S2C.Transition msg) {
        ClientState.transitionKind = msg.kind();
        ClientState.transitionTicks = 0;
        ClientState.transitionTitle = msg.title();
        ClientState.transitionSubtitle = msg.subtitle();
    }

    public static void tempo(S2C.TempoSync msg) {
        ClientState.period = Math.max(1, msg.period());
        ClientState.anchor = msg.anchor();
    }

    public static void repairPrompt(S2C.RepairPrompt msg) {
        ClockData clock = ClockData.load(msg.clock());
        if (clock != null) Minecraft.getInstance().setScreen(new RepairPromptScreen(clock, false));
    }
}
