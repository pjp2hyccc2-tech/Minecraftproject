package com.clockworkdiver.client;

import com.clockworkdiver.client.gui.BenchScreen;
import com.clockworkdiver.client.gui.DialogueScreen;
import com.clockworkdiver.client.gui.RepairPromptScreen;
import com.clockworkdiver.clock.ClockData;
import com.clockworkdiver.network.S2C;
import com.clockworkdiver.registry.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;

/** Client side of the server messages. Only ever loaded on the physical client. */
public final class ClientPacketHandler {
    private ClientPacketHandler() {}

    public static void openDialogue(S2C.OpenDialogue msg) {
        ClockData clock = ClockData.load(msg.clock());
        Minecraft.getInstance().setScreen(new DialogueScreen(msg.entityId(), msg.mode(), msg.profile(), clock, msg.reward(), msg.extra()));
    }

    public static void openBench(S2C.OpenBench msg) {
        Minecraft.getInstance().setScreen(new BenchScreen(msg));
    }

    public static void objective(S2C.Objective msg) {
        if (!msg.key().equals(ClientState.objectiveKey) || msg.a() != ClientState.objectiveA || msg.sa() != ClientState.objectiveSubA) {
            ClientState.objectiveFlash = 20;
        }
        ClientState.objectiveKey = msg.key();
        ClientState.objectiveA = msg.a();
        ClientState.objectiveB = msg.b();
        ClientState.objectiveSub = msg.sub();
        ClientState.objectiveSubA = msg.sa();
        ClientState.objectiveSubB = msg.sb();
    }

    public static void hint(S2C.Hint msg) {
        ClientState.HINTS.addAll(msg.keys());
    }

    public static void diveState(S2C.DiveState msg) {
        if (msg.inDive()) {
            boolean fresh = !ClientState.inDive;
            ClientState.inDive = true;
            ClientState.defectType = msg.defectType();
            ClientState.partKey = msg.partKey();
            ClientState.period = Math.max(1, msg.period());
            ClientState.anchor = msg.anchor();
            ClientState.offset = msg.offset();
            ClientState.mods = msg.mods();
            ClientState.deadline = msg.deadline();
            ClientState.lensLevel = msg.lensLevel();
            ClientState.tutorialDive = msg.tutorial();
            if (fresh) {
                ClientState.lastBeat = Long.MIN_VALUE;
                // Fresh hints for the chamber follow this message; drop stale workshop hints.
                ClientState.HINTS.clear();
                ClientState.currentHint = null;
                ClientState.hintTicks = 0;
            }
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
        ClientState.card(msg.kind(), msg.title(), msg.subtitle());
        if (msg.kind() == S2C.TRANSITION_QUEST || msg.kind() == S2C.TRANSITION_CHAPTER) {
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(ModSounds.STORY_CARD.get(), 1.0f, 0.8f));
        }
    }

    public static void tempo(S2C.TempoSync msg) {
        ClientState.period = Math.max(1, msg.period());
        ClientState.anchor = msg.anchor();
        ClientState.offset = msg.offset();
    }

    public static void repairPrompt(S2C.RepairPrompt msg) {
        ClockData clock = ClockData.load(msg.clock());
        if (clock != null) Minecraft.getInstance().setScreen(new RepairPromptScreen(clock, false));
    }

    public static void memory(S2C.Memory msg) {
        ClientState.memoryKey = msg.key();
        ClientState.memoryIndex = msg.index();
        ClientState.memoryCount = msg.count();
        ClientState.memoryTicks = ClientState.MEMORY_DURATION;
    }

    public static void story(S2C.StorySync msg) {
        ClientState.storyIndex = msg.storyIndex();
        ClientState.keepsakes = msg.keepsakes();
        ClientState.repairedClocks = msg.repairedClocks();
        ClientState.reputation = msg.reputation();
    }
}
