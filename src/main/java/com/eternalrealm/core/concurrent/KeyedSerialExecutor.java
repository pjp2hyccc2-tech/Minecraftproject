package com.eternalrealm.core.concurrent;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Runs tasks on a bounded thread pool while guaranteeing that tasks submitted with the <em>same key</em>
 * execute strictly one after another, in submission order. Tasks with different keys may run in parallel.
 *
 * <p>Used for per-player persistence I/O: a player's "save on logout" always finishes before the
 * "load on login" of the same player starts, even when the player reconnects immediately.
 * This keeps blocking I/O off the Minecraft server thread without introducing stale reads.</p>
 */
public final class KeyedSerialExecutor<K> implements AutoCloseable {
    private static final Duration DEFAULT_CLOSE_TIMEOUT = Duration.ofSeconds(30);

    private final ExecutorService pool;
    private final Object lock = new Object();
    private final Map<K, CompletableFuture<?>> tails = new HashMap<>();
    private boolean closed;

    public KeyedSerialExecutor(int threads, String threadNamePrefix) {
        if (threads < 1) {
            throw new IllegalArgumentException("threads must be >= 1: " + threads);
        }
        Objects.requireNonNull(threadNamePrefix, "threadNamePrefix");
        AtomicInteger counter = new AtomicInteger();
        ThreadFactory factory = runnable -> {
            Thread thread = new Thread(runnable, threadNamePrefix + "-" + counter.incrementAndGet());
            // Daemon so that a hung task can never keep the JVM alive; shutdown() drains explicitly.
            thread.setDaemon(true);
            return thread;
        };
        this.pool = Executors.newFixedThreadPool(threads, factory);
    }

    /**
     * Queues {@code task} behind every task previously submitted with {@code key}.
     * A failure of an earlier task does not prevent later tasks from running.
     *
     * @return a future completed with the task result; completed exceptionally with
     *         {@link RejectedExecutionException} if the executor is already closed
     */
    public <T> CompletableFuture<T> submit(K key, Callable<T> task) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(task, "task");
        synchronized (lock) {
            if (closed) {
                return CompletableFuture.failedFuture(new RejectedExecutionException("executor is closed"));
            }
            CompletableFuture<?> previous = tails.get(key);
            CompletableFuture<?> start = previous == null
                    ? CompletableFuture.completedFuture(null)
                    : previous.handle((result, error) -> null);
            CompletableFuture<T> next = start.thenApplyAsync(ignored -> call(task), pool);
            tails.put(key, next);
            next.whenComplete((result, error) -> {
                synchronized (lock) {
                    tails.remove(key, next);
                }
            });
            return next;
        }
    }

    /** @return number of keys that still have unfinished work */
    public int pendingKeys() {
        synchronized (lock) {
            return (int) tails.values().stream().filter(future -> !future.isDone()).count();
        }
    }

    /**
     * Blocks until every task submitted so far (and anything they chain) has finished, or the timeout elapses.
     *
     * @return {@code true} if all work finished in time
     */
    public boolean awaitQuiescence(Duration timeout) throws InterruptedException {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (true) {
            List<CompletableFuture<?>> pending;
            synchronized (lock) {
                pending = tails.values().stream().filter(future -> !future.isDone()).toList();
            }
            if (pending.isEmpty()) {
                return true;
            }
            long remaining = deadline - System.nanoTime();
            if (remaining <= 0) {
                return false;
            }
            CompletableFuture<?>[] guarded = pending.stream()
                    .map(future -> future.handle((result, error) -> null))
                    .toArray(CompletableFuture[]::new);
            try {
                CompletableFuture.allOf(guarded).get(remaining, TimeUnit.NANOSECONDS);
            } catch (TimeoutException e) {
                return false;
            } catch (ExecutionException e) {
                // Not reachable: handle() swallows task failures.
                throw new IllegalStateException(e);
            }
        }
    }

    /**
     * Stops accepting work, waits for queued work to finish and releases the threads.
     *
     * @return {@code true} if all queued work finished before the timeout
     */
    public boolean shutdown(Duration timeout) {
        synchronized (lock) {
            closed = true;
        }
        boolean drained;
        try {
            drained = awaitQuiescence(timeout);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            drained = false;
        }
        if (drained) {
            pool.shutdown();
        } else {
            pool.shutdownNow();
        }
        return drained;
    }

    public boolean isClosed() {
        synchronized (lock) {
            return closed;
        }
    }

    @Override
    public void close() {
        shutdown(DEFAULT_CLOSE_TIMEOUT);
    }

    private static <T> T call(Callable<T> task) {
        try {
            return task.call();
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new CompletionException(e);
        }
    }
}
