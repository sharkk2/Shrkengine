package org.sharkk2.shrkengine.engine.helpers;

import java.util.Queue;
import java.util.concurrent.*;
import java.util.function.Consumer;

public class ThreadManager {
    private static final ExecutorService executor = Executors.newCachedThreadPool();
    private static Thread mainThread;

    public static void init() {
        mainThread = Thread.currentThread();
    }

    public static Task run(Runnable runnable) {
        Future<?> future = executor.submit(runnable);
        return new Task(future);
    }

    public static <T> TaskWithResult<T> supply(Callable<T> callable) {
        Future<T> future = executor.submit(callable);
        return new TaskWithResult<>(future);
    }

    public static void shutdown() {
        executor.shutdown();
    }

    public static class Task {
        private final Future<?> future;

        public Task(Future<?> future) {
            this.future = future;
        }

        public boolean isDone() {
            return future.isDone();
        }

        public void waitUntilDone() {
            try {
                future.get();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        public void onComplete(Runnable callback) {
            executor.submit(() -> {
                try {
                    future.get();
                    callback.run();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
        }
    }

    public static class MainThread {
        private static final Queue<Runnable> queue = new ConcurrentLinkedQueue<>();

        public static void run(Runnable runnable) {
            if (Thread.currentThread() == mainThread) {
                System.out.println("a");
                runnable.run();
                return;
            }
            queue.add(runnable);
        }

        public static void execute() {
            Runnable task;
            while ((task = queue.poll()) != null) {
                try {
                    task.run();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }

        public static <T> T submit(Callable<T> task) {
            if (Thread.currentThread() == mainThread) {
                try {
                    return task.call();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }

            CompletableFuture<T> future = new CompletableFuture<>();
            queue.add(() -> {
                try {
                    future.complete(task.call());
                } catch (Exception e) {
                    future.completeExceptionally(e);
                }
            });

            try {
                return future.get();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

    public static class TaskWithResult<T> {
        private final Future<T> future;

        public TaskWithResult(Future<T> future) {
            this.future = future;
        }

        public boolean isDone() {
            return future.isDone();
        }

        public T get() {
            try {
                return future.get();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        public void onComplete(Consumer<T> callback) {
            executor.submit(() -> {
                try {
                    T result = future.get();
                    callback.accept(result);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
        }
    }
}