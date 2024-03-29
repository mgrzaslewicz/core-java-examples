package com.cap;

import com.cap.completion.FirstSuccessfulTaskCompletionService;
import com.cap.completion.FirstSuccessfulTaskCompletionService.DescribedTask;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;


public class FirstSuccessfulTaskCompletionServiceTest {
    private record SampleResult(boolean success) {
    }

    @Test
    public void shouldGetFirstTaskJudgedAsSuccessful() {
        // given
        final var task1Blocker = new CountDownLatch(1);
        final var task2Blocker = new CountDownLatch(1);
        final var task1Result = new SampleResult(true);
        final var task2Result = new SampleResult(true);
        final var tasks = List.of(
                new DescribedTask<>("task1 - successful", () -> {
                    task1Blocker.await();
                    return task1Result;
                }),
                new DescribedTask<>("task2 - successful", () -> {
                    task2Blocker.await();
                    return task2Result;
                })
        );
        final var tested = new FirstSuccessfulTaskCompletionService.Builder<SampleResult>()
                .withTaskResultJudge(describedTaskResult -> describedTaskResult.result().success)
                .build();
        final Runnable task2FinishesFirst = () -> {
            try {
                Thread.sleep(500);
                task2Blocker.countDown();
                Thread.sleep(500);
                task1Blocker.countDown();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        };
        new Thread(task2FinishesFirst).start();
        // when
        final var result = tested.waitForResults(tasks);
        // then
        assertThat(result.firstSuccessfulTaskResult().result()).isEqualTo(task2Result);
        assertThat(result.failed()).isEmpty();
        assertThat(result.skippedWaitingForResults()).containsOnly(tasks.get(0));
    }

    @Test
    public void shouldGetAllFailed() {
        // given
        final var task1Result = new SampleResult(false);
        final var task2Result = new SampleResult(false);
        final var tasks = List.of(
                new DescribedTask<>("task1 - failed", () -> task1Result),
                new DescribedTask<>("task2 - failed", () -> task2Result)
        );
        final var tested = new FirstSuccessfulTaskCompletionService.Builder<SampleResult>()
                .withTaskResultJudge(describedTaskResult -> describedTaskResult.result().success)
                .build();
        // when
        final var result = tested.waitForResults(tasks);
        // then
        assertThat(result.firstSuccessfulTaskResult()).isNull();
        assertThat(result.failed()).hasSize(tasks.size());
        assertThat(result.skippedWaitingForResults()).isEmpty();
    }
}
