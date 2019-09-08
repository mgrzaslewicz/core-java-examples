package com.autocoin.cap;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicInteger;

import static java.util.stream.IntStream.range;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

public class AtomicWriteTest {
    private static final Logger logger = LoggerFactory.getLogger(AtomicWriteTest.class);
    private volatile int sum = 0;

    @Test
    public void shouldIncreasingBeBrokenWhenNotUsingAtomicInteger() throws InterruptedException {
        var sumHolder = new int[]{0};
        var numIterations = 15_000;
        var t1 = new Thread(() -> {
            for (var i = 0; i < numIterations; i++) {
                sumHolder[0]++;
            }
        });
        var t2 = new Thread(() -> {
            for (var i = 0; i < numIterations; i++) {
                sumHolder[0]++;
            }
        });
        t1.start();
        t2.start();
        t1.join();
        t2.join();

        logger.info("sum={}", sumHolder[0]);

        assertNotEquals(numIterations * 2, sumHolder[0]);
    }

    @Test
    public void shouldIncreaseProperlyWithAtomicInteger() throws InterruptedException {
        var sum = new AtomicInteger(0);
        var numIterations = 15_000;
        var t1 = new Thread(() -> range(0, numIterations).forEach((i) -> sum.incrementAndGet()));
        var t2 = new Thread(() -> range(0, numIterations).forEach((i) -> sum.incrementAndGet()));
        t1.start();
        t2.start();
        t1.join();
        t2.join();

        logger.info("sum={}", sum.get());

        assertEquals(numIterations * 2, sum.get());
    }

    @Test
    public void shouldUsingVolatileIntNotSolveTheAtomicityProblem() throws InterruptedException {
        var numIterations = 10_000;
        var t1 = new Thread(() -> range(0, numIterations).forEach((i) -> sum++));
        var t2 = new Thread(() -> range(0, numIterations).forEach((i) -> sum++));
        t1.start();
        t2.start();
        t1.join();
        t2.join();

        logger.info("sum={}", sum);

        assertNotEquals(numIterations * 2, sum);
    }

}
