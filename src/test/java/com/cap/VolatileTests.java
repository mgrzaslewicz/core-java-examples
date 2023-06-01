package com.cap;

import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertNotEquals;

public class VolatileTests {
    private static final Logger logger = LoggerFactory.getLogger(VolatileTests.class);

    private class WithVolatileField {
        private volatile long counterA = 0;
        private long counterB = 0;
    }

    @Test
    public void shouldUsingVolatileIntNotSolveTheAtomicityProblem() {
        // given
        final var withVolatileField = new WithVolatileField();
        final int expectedSum = 1_000_000;
        final var jobs = new ArrayList<Future>();
        ExecutorService executorService = Executors.newFixedThreadPool(10);
        // when
        for (int i = 0; i < expectedSum; i++) {
            jobs.add(
                    executorService.submit(() -> {
                        withVolatileField.counterA++; // changing the order does not change the result, always B > A
                        withVolatileField.counterB++;
                    })
            );
        }
        TimeMeasure.waitToFinish(jobs);
        // then
        logger.info("counterA={}", withVolatileField.counterA);
        logger.info("counterB={}", withVolatileField.counterB); // TODO find out why always counterB > counterA
        assertNotEquals(expectedSum, withVolatileField.counterA);
    }

}
