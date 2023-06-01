package com.cap;

import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class CountDownLatchTest {

    @Test
    public void shouldWaitForAllProducersToFinishWithinTimeout() {
        // given
        final int requiredWrites = 2;
        final var countDownLatch = new CountDownLatch(requiredWrites);
        final var tasks = new ArrayList<Future>();
        var finishedOnTime = new AtomicBoolean(false);

        ExecutorService executorService = Executors.newFixedThreadPool(2);
        // when
        final var producerTask = new Runnable() {
            @Override
            public void run() {
                try {
                    Thread.sleep(200);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
                countDownLatch.countDown();
            }
        };
        final var consumerTask = new Runnable() {
            @Override
            public void run() {
                try {
                    finishedOnTime.set(countDownLatch.await(300, TimeUnit.MILLISECONDS));
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
        };
        tasks.add(executorService.submit(producerTask));
        tasks.add(executorService.submit(producerTask));
        tasks.add(executorService.submit(consumerTask));
        TimeMeasure.waitToFinish(tasks);
        // then
        assertEquals(0, countDownLatch.getCount());
        assertTrue(finishedOnTime.get());
    }

}
