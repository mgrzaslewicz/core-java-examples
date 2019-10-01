package com.autocoin.cap;

import java.util.List;

public class TimeMeasure {

    public static long measureTimeMillis(Runnable runAndMeasure) {
        var millisBefore = System.currentTimeMillis();
        runAndMeasure.run();
        return System.currentTimeMillis() - millisBefore;
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

    public static long measureThreadsExecutionTimeMillis(List<Thread> threadsToStart) {
        return measureTimeMillis(() -> startThreadsAndWaitToFinish(threadsToStart));
    }

}
