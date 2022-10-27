package com.autocoin.cap;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static com.autocoin.cap.TimeMeasure.startThreadsAndWaitToFinish;
import static java.util.stream.IntStream.range;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

public class AtomicWriteTest {
    private static final Logger logger = LoggerFactory.getLogger(AtomicWriteTest.class);
    private volatile int sum = 0;

    @Test
    public void shouldIncreasingBeBrokenWhenNotUsingAtomicInteger() {
        var sumHolder = new int[]{0};
        var numIterations = 15_000;
        Runnable increaseSum = () -> {
            for (var i = 0; i < numIterations; i++) {
                sumHolder[0]++;
            }
        };
        startThreadsAndWaitToFinish(List.of(new Thread(increaseSum), new Thread(increaseSum)));
        logger.info("sum={}", sumHolder[0]);
        assertNotEquals(numIterations * 2, sumHolder[0]);
    }

    @Test
    public void shouldIncreaseProperlyWithAtomicInteger() {
        var sum = new AtomicInteger(0);
        var numIterations = 15_000;
        var t1 = new Thread(() -> range(0, numIterations).forEach((i) -> sum.incrementAndGet()));
        var t2 = new Thread(() -> range(0, numIterations).forEach((i) -> sum.incrementAndGet()));
        startThreadsAndWaitToFinish(List.of(t1, t2));
        logger.info("sum={}", sum.get());
        assertEquals(numIterations * 2, sum.get());
    }

    @Test
    public void shouldUsingVolatileIntNotSolveTheAtomicityProblem() {
        var numIterations = 10_000;
        var t1 = new Thread(() -> range(0, numIterations).forEach((i) -> sum++));
        var t2 = new Thread(() -> range(0, numIterations).forEach((i) -> sum++));
        startThreadsAndWaitToFinish(List.of(t1, t2));

        logger.info("sum={}", sum);

        assertNotEquals(numIterations * 2, sum);
    }

}
