package com.hitori.game;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * The cameras keep recording (docs/DESIGN.md §3.1): the client captures a frame of the feed it shows every few
 * seconds; at a key event in view of the watched camera (the tub empty, the doll at the fusuma, the woman, the stab,
 * the fire) the server asks for a frame right now, tagged with the event, for the good ending's playback.
 */
public final class Recording {
    /** Self test / debug: the events of the night (label @ clock). */
    public static final List<String> LOG = new ArrayList<>();

    private Recording() {}

    public static void event(ServerLevel level, String label, Vec3 at) {
        GameData d = GameData.get(level.getServer());
        LOG.add(label + "@" + Clock.hhmm(d.clock));
        if (LOG.size() > 200) LOG.remove(0);
        for (ServerPlayer p : GameManager.players(level)) {
            if (!GameManager.inGame(p)) continue;
            PlayerState ps = GameManager.state(p);
            if (ps.inApp() && Watch.cameraOn(p, at)) GameManager.event(p, GameManager.EV_RECORD, ps.appCamera, label);
        }
    }
}
