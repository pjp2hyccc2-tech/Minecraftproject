package com.eternalrealm.core.concurrent;

import org.junit.After;
import org.junit.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class KeyedSerialExecutorTest {
    private final KeyedSerialExecutor<String> executor = new KeyedSerialExecutor<>(4, "test-io");

    @After
    public void tearDown() {
        executor.shutdown(Duration.ofSeconds(5));
    }

    @Test
    public void tasksWithSameKeyRunInSubmissionOrder() throws Exception {
        List<Integer> order = Collections.synchronizedList(new ArrayList<>());
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            int value = i;
            futures.add(executor.submit("player", () -> {
                Thread.sleep(ThreadLocalRandom.current().nextInt(0, 3));
                order.add(value);
                return null;
            }));
        }
        CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).get(10, TimeUnit.SECONDS);
        assertEquals(IntStream.range(0, 50).boxed().collect(Collectors.toList()), order);
    }

    @Test
    public void tasksWithSameKeyNeverOverlap() throws Exception {
        AtomicInteger running = new AtomicInteger();
        AtomicInteger maxRunning = new AtomicInteger();
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            futures.add(executor.submit("player", () -> {
                int now = running.incrementAndGet();
                maxRunning.accumulateAndGet(now, Math::max);
                Thread.sleep(1);
                running.decrementAndGet();
                return null;
            }));
        }
        CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).get(10, TimeUnit.SECONDS);
        assertEquals(1, maxRunning.get());
    }

    @Test
    public void differentKeysRunInParallel() throws Exception {
        CyclicBarrier barrier = new CyclicBarrier(2);
        CompletableFuture<Integer> a = executor.submit("a", () -> barrier.await(5, TimeUnit.SECONDS));
        CompletableFuture<Integer> b = executor.submit("b", () -> barrier.await(5, TimeUnit.SECONDS));
        // Would time out (BrokenBarrier/Timeout) if the two keys were serialised.
        a.get(10, TimeUnit.SECONDS);
        b.get(10, TimeUnit.SECONDS);
    }

    @Test
    public void failedTaskDoesNotBlockLaterTasksOfSameKey() throws Exception {
        CompletableFuture<Object> failing = executor.submit("player", () -> {
            throw new IllegalStateException("boom");
        });
        CompletableFuture<String> next = executor.submit("player", () -> "ran");
        assertEquals("ran", next.get(5, TimeUnit.SECONDS));
        ExecutionException error = assertThrows(ExecutionException.class, failing::get);
        assertTrue(error.getCause() instanceof IllegalStateException);
    }

    @Test
    public void checkedExceptionsAreReportedThroughTheFuture() {
        CompletableFuture<Object> failing = executor.submit("player", () -> {
            throw new java.io.IOException("disk");
        });
        ExecutionException error = assertThrows(ExecutionException.class, () -> failing.get(5, TimeUnit.SECONDS));
        assertTrue(error.getCause() instanceof java.io.IOException);
    }

    @Test
    public void awaitQuiescenceWaitsForBlockedWork() throws Exception {
        CountDownLatch release = new CountDownLatch(1);
        executor.submit("player", () -> release.await(5, TimeUnit.SECONDS));
        assertFalse(executor.awaitQuiescence(Duration.ofMillis(100)));
        assertEquals(1, executor.pendingKeys());
        release.countDown();
        assertTrue(executor.awaitQuiescence(Duration.ofSeconds(5)));
        assertEquals(0, executor.pendingKeys());
    }

    @Test
    public void closedExecutorRejectsNewWork() {
        assertTrue(executor.shutdown(Duration.ofSeconds(5)));
        CompletableFuture<String> rejected = executor.submit("player", () -> "never");
        ExecutionException error = assertThrows(ExecutionException.class, rejected::get);
        assertTrue(error.getCause() instanceof RejectedExecutionException);
    }

    @Test
    public void shutdownDrainsQueuedWork() throws Exception {
        AtomicInteger done = new AtomicInteger();
        for (int i = 0; i < 20; i++) {
            executor.submit("k" + (i % 3), () -> {
                Thread.sleep(2);
                return done.incrementAndGet();
            });
        }
        assertTrue(executor.shutdown(Duration.ofSeconds(10)));
        assertEquals(20, done.get());
    }
}
