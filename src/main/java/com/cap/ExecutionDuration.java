package com.cap;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Future;

public class ExecutionDuration {

    public static Duration measureExecutionDuration(Runnable runAndMeasure) {
        var millisBefore = System.currentTimeMillis();
        runAndMeasure.run();
        return Duration.ofMillis(System.currentTimeMillis() - millisBefore);
    }

    public static void startThreadsAndWaitToFinish(List<Thread> threadsToStart) {
        threadsToStart.forEach(Thread::start);
        try {
            for (Thread thread : threadsToStart) {
                thread.join();
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    public static void waitToFinish(List<Future> jobs) {
        jobs.forEach(it -> {
            try {
                it.get();
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    public static Duration measureExecutionDuration(List<Thread> threadsToStart) {
        return measureExecutionDuration(() -> startThreadsAndWaitToFinish(threadsToStart));
    }

    public static Duration measureExecutionDuration(Thread... threadsToStart) {
        return measureExecutionDuration(Arrays.stream(threadsToStart).toList());
    }

}
