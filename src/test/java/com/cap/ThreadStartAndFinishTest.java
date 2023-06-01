package com.cap;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static com.cap.ExecutionDuration.measureExecutionDuration;
import static java.util.stream.IntStream.range;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assertions.*;

public class ThreadStartAndFinishTest {
    private static final Logger logger = LoggerFactory.getLogger(ThreadStartAndFinishTest.class);

    @Test
    public void shouldThreadNotDoAllTheWorkWhenNotWaitingForFinish() {
        var sum = new AtomicInteger(0);
        var numIterations = 1_000;
        var t = new Thread(() -> range(0, numIterations).forEach((i) -> {
            sum.incrementAndGet();
            if (i == 0) {
                try {
                    Thread.sleep(1);
                } catch (InterruptedException e) {
                }
            }
        }));

        t.start();
        // no t1.join() - not waiting for thread finish
        var sumWhenThreadPossiblyNotFinishedOrStarted = sum.get();
        logger.info("sum={}", sumWhenThreadPossiblyNotFinishedOrStarted);
        assertNotEquals(numIterations, sumWhenThreadPossiblyNotFinishedOrStarted);
    }

    @Test
    public void shouldThreadDoAllTheWorkWhenWaitingForFinish() throws InterruptedException {
        var sum = new AtomicInteger(0);
        var numIterations = 1_000;
        var t = new Thread(() -> range(0, numIterations).forEach((i) -> sum.incrementAndGet()));
        t.start();
        t.join();
        logger.info("sum={}", sum.get());
        assertEquals(numIterations, sum.get());
    }

    @Test
    @DisplayName("Cost of synchronization should kill benefits of using threads when working on task which is not parallelized")
    public void shouldThreadsBeSlowerThanNoThreadsBecauseOfSynchronizationCost() {
        var sumWithThreads = new AtomicInteger(0);
        var sumWithoutThreads = new AtomicInteger(0);
        var numIterations = 1_000_000;
        Runnable increaseInteger = () -> range(0, numIterations).forEach((i) -> sumWithThreads.incrementAndGet());
        var durationWith2Threads = measureExecutionDuration(new Thread(increaseInteger), new Thread(increaseInteger));
        var durationWithoutThreads = ExecutionDuration.measureExecutionDuration(() -> range(0, numIterations).forEach((i) -> sumWithoutThreads.incrementAndGet()));
        logger.info("millisWith2Threads={}", durationWith2Threads);
        logger.info("millisWithoutThreads={}", durationWithoutThreads);
        assertThat(durationWith2Threads).isGreaterThan(durationWithoutThreads);
    }

    @Test
    public void shouldUseUncaughtExceptionHandlerToPreventStoppingThread() throws InterruptedException {
        // given
        var t1 = new Thread(() -> {
            throw new RuntimeException("Failing on purpose");
        }, "Thread throwing an exception during running");
        var latch = new CountDownLatch(1);
        t1.setUncaughtExceptionHandler((thread, throwable) -> {
            logger.error("Exception occured in thread", throwable);
            assertTrue(thread.getState() == Thread.State.RUNNABLE);
            latch.countDown();
        });
        // when
        t1.start();
        // then
        latch.await(100, TimeUnit.MILLISECONDS);
    }

}
