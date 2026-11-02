package com.clockworkdiver.util;

import com.clockworkdiver.ClockworkDiver;
import net.minecraft.server.MinecraftServer;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/** Runs code a number of server ticks later (used to wait for a dimension change to finish on the client). */
public final class DelayedTasks {
    private record Task(int at, Runnable action) {}

    private static final List<Task> TASKS = new ArrayList<>();

    private DelayedTasks() {}

    public static void schedule(MinecraftServer server, int delayTicks, Runnable action) {
        TASKS.add(new Task(server.getTickCount() + Math.max(1, delayTicks), action));
    }

    public static void tick(MinecraftServer server) {
        if (TASKS.isEmpty()) return;
        int now = server.getTickCount();
        List<Task> due = new ArrayList<>();
        Iterator<Task> it = TASKS.iterator();
        while (it.hasNext()) {
            Task t = it.next();
            if (t.at() <= now) {
                due.add(t);
                it.remove();
            }
        }
        for (Task t : due) {
            try {
                t.action().run();
            } catch (Exception e) {
                ClockworkDiver.LOGGER.error("Clockwork Diver delayed task failed", e);
            }
        }
    }

    public static void clear() {
        TASKS.clear();
    }
}
