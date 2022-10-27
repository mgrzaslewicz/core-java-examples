package com.autocoin.cap;

import java.util.HashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class LocksTests {
    private static final Logger logger = LoggerFactory.getLogger(LocksTests.class);

    @Test
    public void shouldReadersMissSomeValuesProducedByWriters() throws InterruptedException {
        ExecutorService executorService = Executors.newCachedThreadPool();
        var numIterations = 100_000;
        ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
        var sum = new AtomicInteger(0);
        var sumReadHowManyTimes = new HashMap<Integer, Integer>();
        Runnable writer = () -> {
            lock.writeLock().lock();
            sum.incrementAndGet();
            lock.writeLock().unlock();
        };
        Runnable reader = () -> {
            lock.readLock().lock();
            sumReadHowManyTimes.putIfAbsent(sum.get(), 0);
            sumReadHowManyTimes.put(sum.get(), sumReadHowManyTimes.get(sum.get()) + 1);
            lock.readLock().unlock();
        };
        for (var i = 0; i < numIterations; i++) {
            executorService.submit(writer);
            executorService.submit(reader);
        }
        executorService.awaitTermination(10, TimeUnit.SECONDS);

        logger.info("sum={}", sum.get());
        logger.info("sumReadHowManyTimes.value().size()={}", sumReadHowManyTimes.values().size());
        var howManyValuesMissedByReaders = sum.get() - sumReadHowManyTimes.values().size();
        logger.info("howManyValuesMissedByReaders={}, which is {}%", howManyValuesMissedByReaders, howManyValuesMissedByReaders * 100 / sum.get());

        assertTrue(sumReadHowManyTimes.values().stream().anyMatch(it -> it > 1)); // multiple readers have read the same value
        assertTrue(sumReadHowManyTimes.values().size() < numIterations); // some values written by writers were missed
    }

}
