package com.birdiecraft.client;

import com.birdiecraft.client.screen.GolfMenuScreen;
import com.birdiecraft.config.GolfConfig;
import com.birdiecraft.entity.CpuGolferEntity;
import com.birdiecraft.match.MatchState;
import com.birdiecraft.match.MatchView;
import com.birdiecraft.network.S2CBallFlightPacket;
import com.birdiecraft.network.S2CHoleMapPacket;
import com.birdiecraft.network.S2CMatchStatePacket;
import com.birdiecraft.network.S2CMenuDataPacket;
import com.birdiecraft.network.S2CMessagePacket;
import com.birdiecraft.registry.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvent;

/** Client handlers for server packets (only ever loaded on the physical client). */
public final class ClientPacketHandler {
    private ClientPacketHandler() {
    }

    public static void onMatchState(S2CMatchStatePacket msg) {
        MatchView old = ClientGolfState.match;
        ClientGolfState.match = msg.view;
        if (msg.view == null) {
            ClientGolfState.previousState = null;
            if (ClientGolfState.holeMap != null) {
                ClientGolfState.holeMap.close();
                ClientGolfState.holeMap = null;
            }
            SwingController.cancel();
            return;
        }
        MatchState prev = old != null && old.matchId == msg.view.matchId ? old.state : null;
        ClientGolfState.previousState = prev;
        if (prev != msg.view.state) {
            ClientGolfState.stateSinceMs = System.currentTimeMillis();
            if (msg.view.state == MatchState.HOLE_END || msg.view.state == MatchState.FINISHED) {
                SwingController.cancel();
            }
        }
    }

    public static void onBallFlight(S2CBallFlightPacket msg) {
        MatchView m = ClientGolfState.match;
        boolean mine = msg.practice || (m != null && msg.participantId == m.myId);
        int delay = 0;
        if (!msg.practice && m != null) {
            MatchView.PView p = m.player(msg.participantId);
            if (p != null && p.cpu) delay = CpuGolferEntity.IMPACT_TICK;
        }
        BallFlight f = new BallFlight(msg, mine, delay);
        ClientGolfState.flights.add(f);
        GolfConfig.BallCamMode cam = GolfConfig.BALL_CAM.get();
        if (!com.birdiecraft.golf.engine.Club.byId(msg.club).isPutter() && (cam == GolfConfig.BallCamMode.ALL || (cam == GolfConfig.BallCamMode.MINE && mine))) {
            BallCam.follow(f);
        }
    }

    public static void onHoleMap(S2CHoleMapPacket msg) {
        if (ClientGolfState.holeMap != null) ClientGolfState.holeMap.close();
        ClientGolfState.holeMap = new HoleMapTexture(msg.map);
    }

    public static void onMessage(S2CMessagePacket msg) {
        HudMessages.add(msg.kind, msg.text, msg.color, msg.duration);
        SoundEvent sound = switch (msg.sound) {
            case S2CMessagePacket.SOUND_FANFARE_SMALL -> ModSounds.FANFARE_SMALL.get();
            case S2CMessagePacket.SOUND_FANFARE_BIG -> ModSounds.FANFARE_BIG.get();
            case S2CMessagePacket.SOUND_APPLAUSE -> ModSounds.APPLAUSE.get();
            case S2CMessagePacket.SOUND_GROAN -> ModSounds.GROAN.get();
            case S2CMessagePacket.SOUND_PERFECT -> ModSounds.PERFECT.get();
            case S2CMessagePacket.SOUND_TURN -> ModSounds.GAUGE_TICK.get();
            default -> null;
        };
        if (sound != null) {
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(sound, 1.0f, 0.9f));
            if (msg.sound == S2CMessagePacket.SOUND_FANFARE_SMALL || msg.sound == S2CMessagePacket.SOUND_FANFARE_BIG) {
                Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(ModSounds.APPLAUSE.get(), 1.0f, 0.7f));
            }
        }
    }

    public static void onMenuData(S2CMenuDataPacket msg) {
        ClientGolfState.menu = msg;
        if (Minecraft.getInstance().screen instanceof GolfMenuScreen screen) screen.refresh();
    }
}
