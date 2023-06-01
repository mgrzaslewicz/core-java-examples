package com.cap;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

import static com.cap.ExecutionDuration.measureExecutionDuration;
import static java.util.stream.IntStream.range;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

public class SynchronizedVsLocksTests {
    private static final Logger logger = LoggerFactory.getLogger(SynchronizedVsLocksTests.class);

    @Test
    public void shouldLockBeSlowerThanSynchronized() {
        var iterationsCount = 5_000_000;
        var threadsCount = 2;

        AtomicInteger sumWithSynchronized = new AtomicInteger();
        Runnable increaseSumWithSynchronized = () -> {
            for (int i = 0; i < iterationsCount; i++) {
                synchronized (this) {
                    sumWithSynchronized.getAndIncrement();
                }
            }
        };
        var threadsUsingSynchronized = range(0, threadsCount)
                .mapToObj((i) -> new Thread(increaseSumWithSynchronized))
                .collect(Collectors.toList());

        AtomicInteger sumWithLock = new AtomicInteger();
        var reentrantLock = new ReentrantLock();
        Runnable increaseSumWithLock = () -> {
            for (int i = 0; i < iterationsCount; i++) {
                reentrantLock.lock();
                sumWithLock.getAndIncrement();
                reentrantLock.unlock();
            }
        };
        var threadsUsingLock = range(0, threadsCount)
                .mapToObj((i) -> new Thread(increaseSumWithLock))
                .collect(Collectors.toList());


        var durationSynchronized = measureExecutionDuration(threadsUsingSynchronized);
        var durationLock = measureExecutionDuration(threadsUsingLock);

        logger.info("sumWithSynchronized={}, took {} ms", sumWithSynchronized, durationSynchronized);
        logger.info("sumWithLock={}, took {} ms", sumWithLock, durationLock);

        assertThat(sumWithSynchronized.get()).isEqualTo(iterationsCount * threadsCount);
        assertThat(sumWithLock.get()).isEqualTo(iterationsCount * threadsCount);
        assertThat(durationLock).isGreaterThan(durationSynchronized);
    }

}
