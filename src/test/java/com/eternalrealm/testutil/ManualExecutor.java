package com.eternalrealm.testutil;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executor;

/**
 * Stands in for the Minecraft server thread: tasks posted from any thread are queued and only run when the test
 * calls {@link #runAll()} on the test thread.
 */
public final class ManualExecutor implements Executor {
    private final Queue<Runnable> queue = new ConcurrentLinkedQueue<>();

    @Override
    public void execute(Runnable command) {
        queue.add(command);
    }

    /** @return number of tasks executed */
    public int runAll() {
        int count = 0;
        Runnable task;
        while ((task = queue.poll()) != null) {
            task.run();
            count++;
        }
        return count;
    }

    public boolean isEmpty() {
        return queue.isEmpty();
    }
}
