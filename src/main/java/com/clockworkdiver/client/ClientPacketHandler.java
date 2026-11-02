package com.clockworkdiver.client;

import com.clockworkdiver.network.S2C;

/** Client side of the server messages. Only ever loaded on the physical client. */
public final class ClientPacketHandler {
    private ClientPacketHandler() {}

    public static void openDialogue(S2C.OpenDialogue msg) {
    }

    public static void openBench(S2C.OpenBench msg) {
    }

    public static void objective(S2C.Objective msg) {
    }

    public static void hint(S2C.Hint msg) {
    }

    public static void diveState(S2C.DiveState msg) {
    }

    public static void combat(S2C.Combat msg) {
    }

    public static void transition(S2C.Transition msg) {
    }

    public static void tempo(S2C.TempoSync msg) {
    }

    public static void repairPrompt(S2C.RepairPrompt msg) {
    }
}
