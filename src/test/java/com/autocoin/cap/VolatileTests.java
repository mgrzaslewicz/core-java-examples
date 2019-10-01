package com.autocoin.cap;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

import static com.autocoin.cap.TimeMeasure.startThreadsAndWaitToFinish;
import static java.util.stream.IntStream.range;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

public class VolatileTests {
    private static final Logger logger = LoggerFactory.getLogger(VolatileTests.class);
    private volatile int sum = 0;

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
