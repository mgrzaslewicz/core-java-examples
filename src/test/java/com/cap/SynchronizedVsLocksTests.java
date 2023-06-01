package com.cap;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.locks.ReentrantLock;

import static com.cap.ExecutionDuration.measureExecutionDuration;
import static java.util.stream.IntStream.range;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class SynchronizedVsLocksTests {
    private static final Logger logger = LoggerFactory.getLogger(SynchronizedVsLocksTests.class);
    private volatile int sumWithSynchronized = 0;
    private volatile int sumWithLock = 0;
    private ReentrantLock reentrantLock = new ReentrantLock();

    @Test
    public void shouldLockBeSlowerThanSynchronized() {
        var numIterations = 5_000_000;
        Runnable increaseSumWithSynchronized = () -> range(0, numIterations).forEach((i) -> {
            synchronized (this) {
                sumWithSynchronized++;
            }
        });
        var t1Synchronized = new Thread(increaseSumWithSynchronized);
        var t2Synchronized = new Thread(increaseSumWithSynchronized);

        Runnable increaseSumWithLock = () -> range(0, numIterations).forEach((i) -> {
            reentrantLock.lock();
            sumWithLock++;
            reentrantLock.unlock();
        });
        var t3Lock = new Thread(increaseSumWithLock);
        var t4Lock = new Thread(increaseSumWithLock);


        var durationSynchronized = measureExecutionDuration(t1Synchronized, t2Synchronized);
        var durationLock = measureExecutionDuration(t3Lock, t4Lock);

        logger.info("sumWithSynchronized={}, took {} ms", sumWithSynchronized, durationSynchronized);
        logger.info("sumWithLock={}, took {} ms", sumWithLock, durationLock);

        assertEquals(numIterations * 2, sumWithSynchronized);
        assertEquals(numIterations * 2, sumWithLock);
        assertThat(durationLock).isGreaterThan(durationSynchronized);
    }

}
