package com.autocoin.cap;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class WaitNotifyTest {
    private static final Logger logger = LoggerFactory.getLogger(WaitNotifyTest.class);

    class LackingSynchronizeForMonitor {
        private Object lock = new Object();

        public void doSomethingWithoutSynchronizedBlockAroundNotify() {
            lock.notify(); // will throw exception
        }
    }

    class SimpleBlockingQueue {
        private Object lock = new Object();
        private ConcurrentLinkedDeque<String> list = new ConcurrentLinkedDeque<>(); // non-concurrent LinkedList will fail
        private CountDownLatch startedWaitingLatch;
        private CountDownLatch gotResultLatch;

        public SimpleBlockingQueue(CountDownLatch startedWaitingLatch, CountDownLatch gotResultLatch) {
            this.startedWaitingLatch = startedWaitingLatch;
            this.gotResultLatch = gotResultLatch;
        }

        public void add(String value) {
            logger.info("Adding element to the list: {}", value);
            list.add(value);
            synchronized (lock) {
                lock.notify();
            }
        }

        public String offer() {
            while (list.isEmpty()) {
                synchronized (lock) {
                    startedWaitingLatch.countDown();
                    logger.info("Waiting for any element on the list");
                    try {
                        lock.wait();
                    } catch (InterruptedException e) {
                        logger.error("Error during wait", e);
                    }
                }
            }
            var result = list.removeFirst();
            logger.info("Returning element: {}", result);
            gotResultLatch.countDown();
            return result;
        }
    }

    @Test
    public void shouldThrowExceptionBecauseThreadDoesNotOwnTheMonitor() {
        var tested = new LackingSynchronizeForMonitor();
        assertThrows(IllegalMonitorStateException.class, () -> tested.doSomethingWithoutSynchronizedBlockAroundNotify());
    }

    @Test
    public void shouldWaitForProducerToNotify() throws InterruptedException {
        for (int i = 0; i < 1_000; i++) {
            logger.info("Iteration: {}", i);
            var startedWaitingLatch = new CountDownLatch(2);
            var gotResultLatch = new CountDownLatch(2);

            var tested = new SimpleBlockingQueue(startedWaitingLatch, gotResultLatch);
            var threadConsumer1 = new Thread(() -> tested.offer());
            var threadConsumer2 = new Thread(() -> tested.offer());
            threadConsumer1.start();
            threadConsumer2.start();
            startedWaitingLatch.await(); // make sure consumers have already started waiting to avoid missing lock.notify()

            tested.add("some string1");
            tested.add("some string2");
            gotResultLatch.await(500, TimeUnit.MILLISECONDS);

            assertEquals(0, gotResultLatch.getCount());
        }
    }
}
