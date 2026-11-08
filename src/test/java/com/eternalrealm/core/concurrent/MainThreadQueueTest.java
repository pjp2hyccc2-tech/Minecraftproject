package com.eternalrealm.core.concurrent;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class MainThreadQueueTest {
    @Test
    public void tasksRunOnlyWhenDrainedAndOnTheDrainingThread() throws Exception {
        MainThreadQueue queue = new MainThreadQueue();
        List<String> threads = new ArrayList<>();
        CountDownLatch posted = new CountDownLatch(1);
        Thread worker = new Thread(() -> {
            queue.execute(() -> threads.add(Thread.currentThread().getName()));
            posted.countDown();
        }, "io-worker");
        worker.start();
        assertTrue(posted.await(5, TimeUnit.SECONDS));
        assertTrue("nothing runs before drain", threads.isEmpty());

        assertEquals(1, queue.drain());
        assertEquals(List.of(Thread.currentThread().getName()), threads);
        assertTrue(queue.isEmpty());
    }

    @Test
    public void failingTaskDoesNotStopTheOthers() {
        MainThreadQueue queue = new MainThreadQueue();
        List<Integer> ran = new ArrayList<>();
        queue.execute(() -> ran.add(1));
        queue.execute(() -> {
            throw new IllegalStateException("expected in test");
        });
        queue.execute(() -> ran.add(3));
        assertEquals(3, queue.drain());
        assertEquals(List.of(1, 3), ran);
    }

    @Test
    public void drainIsBoundedPerCall() {
        MainThreadQueue queue = new MainThreadQueue();
        for (int i = 0; i < MainThreadQueue.MAX_TASKS_PER_DRAIN + 5; i++) {
            queue.execute(() -> { });
        }
        assertEquals(MainThreadQueue.MAX_TASKS_PER_DRAIN, queue.drain());
        assertEquals(5, queue.drain());
    }
}
