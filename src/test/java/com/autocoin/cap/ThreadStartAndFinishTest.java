package com.autocoin.cap;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicInteger;

import static java.util.stream.IntStream.range;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

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

}
