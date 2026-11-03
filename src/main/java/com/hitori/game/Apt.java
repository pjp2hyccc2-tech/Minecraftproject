package com.hitori.game;

import com.hitori.debug.Greybox;
import com.hitori.world.Fixtures;
import com.hitori.world.Layout;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The apartment's fixtures as the game uses them: {@link Fixtures} (owner: world) in the real apartment, or a small
 * state of its own in the greybox stand-in ({@link Greybox}: the game branch before the world is merged), so the
 * ritual, the doll and the tests work either way. Everything the game does to the apartment goes through here.
 */
public final class Apt {
    /** The rooms whose lights count for "every light off" (docs §1 step 13). */
    public static final String[] LIT_ROOMS = {"genkan", "hall", "toilet", "bath", "washroom", "dk", "tatami", "western"};
    private static final Map<String, Integer> GREY = new HashMap<>();
    private static int bathFill;

    private Apt() {}

    public static boolean grey(ServerLevel level) {
        return level.dimension() != Layout.DIMENSION;
    }

    private static int g(String key, int def) {
        return GREY.getOrDefault(key, def);
    }

    private static void sg(ServerLevel level, String key, int v) {
        GREY.put(key, v);
        Greybox.show(level, key, v);
    }

    // ------------------------------------------------------------------------------------------------ setup
    public static void ensureBuilt(ServerLevel level) {
        if (grey(level)) Greybox.ensure(level);
        else Fixtures.ensureBuilt(level);
    }

    /** The evening's start state: the western room's light on, TV off, the bath empty, the plush on its shelf... */
    public static void resetAll(ServerLevel level) {
        if (grey(level)) {
            GREY.clear();
            bathFill = 0;
            for (String r : LIT_ROOMS) sg(level, "light_" + r, r.equals("western") || r.equals("hall") ? 1 : 0);
            sg(level, "tv", 0);
            sg(level, "fusuma", 0);
            sg(level, "bath", 0);
            sg(level, "breaker", 1);
            return;
        }
        Fixtures.resetAll(level);
    }

    /** Per tick (the greybox's bath fills by itself). */
    public static void tick(ServerLevel level) {
        if (!grey(level)) return;
        if (g("bath_tap", 0) == 1) {
            if (++bathFill % 200 == 0) {
                int w = Math.min(2, g("bath", 0) + 1);
                sg(level, "bath", w);
                if (w >= 2) GREY.put("bath_tap", 0);
            }
        }
    }

    // ------------------------------------------------------------------------------------------------ lights
    public static void setLights(ServerLevel level, String room, boolean on) {
        if (grey(level)) sg(level, "light_" + room, on ? 1 : 0);
        else Fixtures.setLights(level, room, on);
    }

    public static boolean lightsOn(ServerLevel level, String room) {
        if (grey(level)) return g("breaker", 1) == 1 && g("light_" + room, 0) == 1;
        return Fixtures.lightsOn(level, room);
    }

    public static boolean anyLightOn(ServerLevel level) {
        for (String r : LIT_ROOMS) if (lightsOn(level, r)) return true;
        return false;
    }

    public static void allLights(ServerLevel level, boolean on) {
        for (String r : LIT_ROOMS) setLights(level, r, on);
    }

    public static void setBreaker(ServerLevel level, boolean on) {
        if (grey(level)) {
            sg(level, "breaker", on ? 1 : 0);
            if (!on) sg(level, "tv", 0);
        } else {
            Fixtures.setBreaker(level, on);
        }
    }

    public static boolean breakerOn(ServerLevel level) {
        return grey(level) ? g("breaker", 1) == 1 : Fixtures.breakerOn(level);
    }

    public static void flicker(ServerLevel level, String room, int ticks) {
        if (!grey(level)) Fixtures.flicker(level, room, ticks);
    }

    public static void swingLamp(ServerLevel level, int ticks) {
        if (!grey(level)) Fixtures.swingLamp(level, ticks);
    }

    // ------------------------------------------------------------------------------------------------ the TV
    public static void setTv(ServerLevel level, boolean on) {
        if (grey(level)) sg(level, "tv", on ? 1 : 0);
        else Fixtures.setTv(level, on);
    }

    public static boolean tvOn(ServerLevel level) {
        return grey(level) ? g("tv", 0) == 1 : Fixtures.tvOn(level);
    }

    public static void setTvChannel(ServerLevel level, int channel) {
        if (grey(level)) GREY.put("tv_channel", channel);
        else Fixtures.setTvChannel(level, channel);
    }

    // ------------------------------------------------------------------------------------------------ water
    public static void setBathTap(ServerLevel level, boolean running) {
        if (grey(level)) {
            GREY.put("bath_tap", running ? 1 : 0);
            bathFill = 0;
        } else {
            Fixtures.setBathTap(level, running);
        }
    }

    public static boolean bathTapRunning(ServerLevel level) {
        return grey(level) && g("bath_tap", 0) == 1;
    }

    public static int bathWater(ServerLevel level) {
        return grey(level) ? g("bath", 0) : Fixtures.bathWater(level);
    }

    public static void setBathWater(ServerLevel level, int water) {
        if (grey(level)) sg(level, "bath", water);
        else Fixtures.setBathWater(level, water);
    }

    public static void setTap(ServerLevel level, String which, boolean running) {
        if (!grey(level)) Fixtures.setTap(level, which, running);
    }

    // ------------------------------------------------------------------------------------------------ doors
    public static void setClosetFusuma(ServerLevel level, int open) {
        if (grey(level)) sg(level, "fusuma", open);
        else Fixtures.setClosetFusuma(level, open);
    }

    public static int closetFusuma(ServerLevel level) {
        return grey(level) ? g("fusuma", 0) : Fixtures.closetFusuma(level);
    }

    public static void setDoor(ServerLevel level, String door, boolean open) {
        if (grey(level)) GREY.put("door_" + door, open ? 1 : 0);
        else Fixtures.setDoor(level, door, open);
    }

    public static boolean doorOpen(ServerLevel level, String door) {
        return grey(level) ? g("door_" + door, 0) == 1 : Fixtures.doorOpen(level, door);
    }

    // ------------------------------------------------------------------------------------------------ devices, items
    public static void setDevice(ServerLevel level, String key, boolean on) {
        if (grey(level)) GREY.put("dev_" + key, on ? 1 : 0);
        else Fixtures.setDevice(level, key, on);
    }

    public static boolean deviceOn(ServerLevel level, String key) {
        return grey(level) ? g("dev_" + key, 0) == 1 : Fixtures.deviceOn(level, key);
    }

    public static void setPresent(ServerLevel level, String key, boolean present) {
        if (grey(level)) GREY.put("present_" + key, present ? 1 : 0);
        else Fixtures.setPresent(level, key, present);
    }

    public static boolean present(ServerLevel level, String key) {
        return grey(level) ? g("present_" + key, 1) == 1 : Fixtures.present(level, key);
    }

    public static void dropSomething(ServerLevel level, BlockPos pos) {
        if (!grey(level)) Fixtures.dropSomething(level, pos);
    }

    public static void wetMark(ServerLevel level, BlockPos pos) {
        if (!grey(level)) Fixtures.wetMark(level, pos);
    }

    public static List<BlockPos> lightsIn(ServerLevel level, String room) {
        return grey(level) ? List.of() : Fixtures.lightsIn(level, room);
    }
}
