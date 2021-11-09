package com.autocoin.cap;

import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;

import static com.autocoin.cap.TimeMeasure.waitToFinish;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

public class VolatileVsSynchronized {

    private class WithVolatileField {
        private volatile long counter = 0;
        private volatile long counterB = 0;
    }

    @Test
    public void shouldShowIncreasingVolatileIsNotAtomic() {
        // given
        final var withVolatileField = new WithVolatileField();
        final int expectedSum = 100_000;
        final var jobs = new ArrayList<Future>();
        ExecutorService executorService = Executors.newFixedThreadPool(10);
        // when
        for (int i = 0; i < expectedSum; i++) {
            jobs.add(
                    executorService.submit(() -> {
                        withVolatileField.counter++;
                    })
            );
        }
        waitToFinish(jobs);
        // then
        assertNotEquals(expectedSum, withVolatileField.counter);
    }
}
