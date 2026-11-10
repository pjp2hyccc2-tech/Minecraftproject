package com.eternalrealm.client.telegraph;

import com.eternalrealm.combat.Telegraph;
import com.eternalrealm.network.forge.TelegraphPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The ground telegraphs the client currently shows ({@link TelegraphPacket}). Times are client game ticks: a marker
 * fills up over its duration, flashes for {@link #FLASH_TICKS} when it fires and fades out when cancelled; markers whose
 * end was never announced disappear on their own. Client thread only.
 */
public final class ClientTelegraphs {
    static final int FLASH_TICKS = 7;
    static final int FADE_TICKS = 5;
    /** Without a FIRE/CANCEL a marker disappears this long after its duration. */
    private static final int GRACE_TICKS = 40;
    private static final int MAX_ACTIVE = 256;
    private static final Map<Integer, Active> ACTIVE = new LinkedHashMap<>();

    private ClientTelegraphs() {
    }

    /** One marker on screen. */
    public static final class Active {
        final Telegraph telegraph;
        final long start;
        long firedAt = -1;
        long cancelledAt = -1;

        Active(Telegraph telegraph, long start) {
            this.telegraph = telegraph;
            this.start = start;
        }

        public Telegraph telegraph() {
            return telegraph;
        }

        /** Charging (neither fired nor cancelled). */
        public boolean live() {
            return firedAt < 0 && cancelledAt < 0;
        }
    }

    public static void accept(TelegraphPacket packet) {
        long now = now();
        if (packet.action() == TelegraphPacket.SHOW && packet.telegraph() != null) {
            if (ACTIVE.size() >= MAX_ACTIVE) {
                Iterator<Integer> oldest = ACTIVE.keySet().iterator();
                oldest.next();
                oldest.remove();
            }
            ACTIVE.put(packet.id(), new Active(packet.telegraph(), now));
            return;
        }
        Active active = ACTIVE.get(packet.id());
        if (active == null) {
            return;
        }
        if (packet.action() == TelegraphPacket.FIRE) {
            active.firedAt = now;
        } else {
            active.cancelledAt = now;
        }
    }

    /** Client tick: drops finished markers. */
    public static void tick() {
        if (ACTIVE.isEmpty()) {
            return;
        }
        long now = now();
        ACTIVE.values().removeIf(active -> (active.firedAt >= 0 && now - active.firedAt > FLASH_TICKS)
                || (active.cancelledAt >= 0 && now - active.cancelledAt > FADE_TICKS)
                || now - active.start > active.telegraph.durationTicks() + GRACE_TICKS
                || now < active.start - 20); // the world's clock jumped back (new world)
    }

    public static boolean isEmpty() {
        return ACTIVE.isEmpty();
    }

    static Collection<Active> active() {
        return ACTIVE.values();
    }

    /**
     * Whether the local player stands in a charging marker that will hurt (drives the screen-edge warning). Standing in
     * a charging {@link Telegraph.Style#SAFE} marker is safe, even inside a danger area around it (safe zones).
     */
    public static boolean localPlayerInDanger() {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || ACTIVE.isEmpty()) {
            return false;
        }
        boolean danger = false;
        for (Active active : ACTIVE.values()) {
            if (active.live() && active.telegraph.contains(player.getX(), player.getY(), player.getZ())) {
                if (active.telegraph.style() == Telegraph.Style.SAFE) {
                    return false;
                }
                danger = danger || active.telegraph.style().warns();
            }
        }
        return danger;
    }

    /** Copy of the charging markers (for tests and debug views). */
    public static List<Telegraph> charging() {
        List<Telegraph> list = new ArrayList<>();
        for (Active active : ACTIVE.values()) {
            if (active.live()) {
                list.add(active.telegraph);
            }
        }
        return list;
    }

    public static void clear() {
        ACTIVE.clear();
    }

    static long now() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.level == null ? 0L : minecraft.level.getGameTime();
    }
}
