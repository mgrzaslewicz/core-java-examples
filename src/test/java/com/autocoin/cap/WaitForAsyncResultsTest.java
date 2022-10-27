package com.autocoin.cap;

import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class WaitForAsyncResultsTest {
    private record TaskResult(boolean success) {
    }

    private CountDownLatch failedTaskLatch;
    private CountDownLatch successfulTaskLatch;
    private CountDownLatch allDoneLatch;
    private Callable<TaskResult> failedTask;
    private Callable<TaskResult> successfulTask;

    @BeforeEach
    public void setup() {
        failedTaskLatch = new CountDownLatch(1);
        successfulTaskLatch = new CountDownLatch(1);
        allDoneLatch = new CountDownLatch(2);
        failedTask = () -> {
            try {
                failedTaskLatch.await();
                allDoneLatch.countDown();
                return new TaskResult(false);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        };
        successfulTask = () -> {
            try {
                successfulTaskLatch.await();
                allDoneLatch.countDown();
                return new TaskResult(true);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        };
    }

    @Test
    public void shouldNotBeDoneWhenStillDoingSomething() {
        // given
        ExecutorService executor = Executors.newFixedThreadPool(2);
        // when
        Future<TaskResult> failedResult = executor.submit(failedTask);
        Future<TaskResult> successfulResult = executor.submit(successfulTask);
        // then
        assertThat(failedResult).isNotDone();
        assertThat(successfulResult).isNotDone();
        executor.shutdown();
    }

    @Test
    public void shouldWaitForAllAsyncResults() throws InterruptedException, ExecutionException {
        // given
        ExecutorService executor = Executors.newFixedThreadPool(2);
        // when
        Future<TaskResult> failedResult = executor.submit(failedTask);
        Future<TaskResult> successfulResult = executor.submit(successfulTask);
        successfulTaskLatch.countDown();
        failedTaskLatch.countDown();
        failedResult.get();
        successfulResult.get();
        // then
        assertThat(failedResult).isDone();
        assertThat(successfulResult).isDone();
    }

    @Test
    public void shouldGetFirstFinishedTask() throws InterruptedException, ExecutionException {
        // given
        final var executor = Executors.newFixedThreadPool(2);
        final var completionService = new ExecutorCompletionService<TaskResult>(executor);
        // when
        var submittedFailingFuture = completionService.submit(failedTask);
        completionService.submit(successfulTask);
        failedTaskLatch.countDown();
        // then
        var completedFuture = completionService.take();
        assertThat(completedFuture).isDone();
        assertThat(completedFuture.get().success).isFalse();
        assertThat(completedFuture).isEqualTo(submittedFailingFuture);

        executor.shutdown();
    }
}
