package com.eternalrealm.core.concurrent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executor;

/**
 * Hand-off from worker threads to the server thread. Workers {@link #execute} tasks; the server thread runs them
 * from its tick via {@link #drain()}.
 *
 * <p>Why not {@code MinecraftServer#execute}? Once the server is stopping, vanilla runs submitted tasks
 * <em>immediately on the calling thread</em>. Persistence callbacks would then run on I/O threads concurrently with
 * the shutdown flush. With this queue, callbacks only ever run on the server thread; after the game loop has stopped
 * they are simply never run, and the shutdown code reads the I/O futures directly instead.</p>
 */
public final class MainThreadQueue implements Executor {
    private static final Logger LOG = LoggerFactory.getLogger(MainThreadQueue.class);
    /** Upper bound per drain so a flood of completions can never stall a single tick. */
    static final int MAX_TASKS_PER_DRAIN = 10_000;

    private final Queue<Runnable> tasks = new ConcurrentLinkedQueue<>();

    @Override
    public void execute(Runnable task) {
        tasks.add(Objects.requireNonNull(task, "task"));
    }

    /**
     * Runs queued tasks on the calling (server) thread. A failing task is logged and does not prevent the others.
     *
     * @return number of tasks run
     */
    public int drain() {
        int ran = 0;
        Runnable task;
        while (ran < MAX_TASKS_PER_DRAIN && (task = tasks.poll()) != null) {
            ran++;
            try {
                task.run();
            } catch (RuntimeException e) {
                LOG.error("Server-thread task failed", e);
            }
        }
        return ran;
    }

    public boolean isEmpty() {
        return tasks.isEmpty();
    }
}
