package com.cap.completion;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FirstSuccessfulTaskCompletionService<T> {
    private static final Logger logger = LoggerFactory.getLogger(FirstSuccessfulTaskCompletionService.class);

    private final Supplier<ExecutorService> executorProvider;
    private final Function<ExecutorService, CompletionService<T>> completionServiceProvider;
    private final Instant deadline;
    private final Clock clock;
    private final Predicate<DescribedTaskResult<T>> taskResultJudge;

    private FirstSuccessfulTaskCompletionService(Supplier<ExecutorService> executorProvider,
                                                 Function<ExecutorService, CompletionService<T>> completionServiceProvider,
                                                 Instant deadline,
                                                 Clock clock,
                                                 Predicate<DescribedTaskResult<T>> taskResultJudge) {
        this.executorProvider = executorProvider;
        this.completionServiceProvider = completionServiceProvider;
        this.deadline = deadline;
        this.clock = clock;
        this.taskResultJudge = taskResultJudge;
    }


    private Long millisecondsToDeadline() {
        if (deadline == null) {
            return Long.MAX_VALUE;
        } else {
            return Duration.between(clock.instant(), deadline).toMillis();
        }
    }

    private Iterator<TaskResultJudgement<T>> toIterator(CompletionService<T> completionService, Map<Future<T>, SubmittedTask<T>> futuresWithTasks) {
        return new Iterator<>() {
            private boolean isWaitingForSuccessfulResult = true;
            private int numberOfTasksAlreadyFinished = 0;

            @Override
            public boolean hasNext() {
                return isWaitingForSuccessfulResult && numberOfTasksAlreadyFinished < futuresWithTasks.size();
            }

            @Override
            public TaskResultJudgement<T> next() {
                try {
                    final var completedFuture = completionService.poll(millisecondsToDeadline(), TimeUnit.MILLISECONDS);
                    numberOfTasksAlreadyFinished++;
                    if (completedFuture == null) {
                        return null;
                    }
                    final var taskResult = completedFuture.get();
                    final var submittedTask = futuresWithTasks.get(completedFuture);
                    final var describedTaskResult = new DescribedTaskResult<T>(submittedTask.describedTask, taskResult);
                    final var taskResultJudgement = new TaskResultJudgement<T>(taskResultJudge.test(describedTaskResult), submittedTask.describedTask, taskResult, submittedTask.submitOrder);
                    if (!taskResultJudgement.isSuccessful) {
                        logger.info("Task '{}' failed", taskResultJudgement.task.description);
                    }
                    if (isWaitingForSuccessfulResult && taskResultJudgement.isSuccessful) {
                        isWaitingForSuccessfulResult = false;
                    }
                    return taskResultJudgement;
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        };
    }

    /**
     * Shuts down the executor when not waiting for a successful result anymore or all tasks have finished.
     *
     * @param tasks
     * @return tasks and their results
     */
    public TaskResults<T> waitForResults(List<DescribedTask<T>> tasks) {
        if (tasks.isEmpty()) {
            return new TaskResults<>(null, List.of(), List.of());
        }
        final var executor = executorProvider.get();
        final var completionService = completionServiceProvider.apply(executor);
        final Map<Future<T>, SubmittedTask<T>> futuresWithTasks = submitTasks(tasks, completionService);

        final var taskResultJudgements = new ArrayList<TaskResultJudgement<T>>();
        toIterator(completionService, futuresWithTasks).forEachRemaining(it -> {
            if (it != null) taskResultJudgements.add(it);
        });
        executor.shutdown();

        final var skippedTasks = getSkippedInSubmissionOrder(tasks, taskResultJudgements);
        logSkippedTasks(skippedTasks);

        return tasksResults(taskResultJudgements, skippedTasks);
    }

    private List<DescribedTask<T>> getSkippedInSubmissionOrder(List<DescribedTask<T>> tasks, List<TaskResultJudgement<T>> taskResultJudgements) {
        return tasks.stream()
                .filter(describedTask -> taskResultJudgements.stream().noneMatch(taskResultJudgement -> taskResultJudgement.task == describedTask))
                .collect(Collectors.toList());
    }

    private void logSkippedTasks(List<DescribedTask<T>> skippedTasks) {
        if (!skippedTasks.isEmpty()) {
            logger.info("Waiting for results of tasks [{}] skipped as there was already a successful one", skippedTasks.stream().map(it -> "'" + it.description + "'").collect(Collectors.joining(", ")));
        }
    }

    private Map<Future<T>, SubmittedTask<T>> submitTasks(List<DescribedTask<T>> tasks, CompletionService<T> completionService) {
        final var result = new LinkedHashMap<Future<T>, SubmittedTask<T>>();
        var taskIndex = 0;
        for (final var task : tasks) {
            final var future = completionService.submit(task.task);
            result.put(future, new SubmittedTask<>(task, taskIndex++));
        }
        return result;
    }

    private TaskResults<T> tasksResults(List<TaskResultJudgement<T>> taskResultJudgements, List<DescribedTask<T>> skippedWaitingForResults) {
        final var firstSuccessfulTaskResult = taskResultJudgements.stream().filter(TaskResultJudgement::isSuccessful).findFirst();
        return new TaskResults<>(
                firstSuccessfulTaskResult.orElse(null),
                taskResultJudgements.stream().filter(it -> !it.isSuccessful).collect(Collectors.toList()),
                skippedWaitingForResults
        );
    }

    public record DescribedTask<T>(String description, Callable<T> task) {
    }

    private record SubmittedTask<T>(DescribedTask<T> describedTask, int submitOrder) {
    }


    public record TaskResultJudgement<T>(boolean isSuccessful, DescribedTask<T> task, T result, int submitOrder) {
    }

    public record TaskResults<T>(TaskResultJudgement<T> firstSuccessfulTaskResult, List<TaskResultJudgement<T>> failed, List<DescribedTask<T>> skippedWaitingForResults) {
    }

    public record DescribedTaskResult<T>(DescribedTask<T> task, T result) {
    }

    public static final class Builder<T> {
        private Supplier<ExecutorService> executorProvider = Executors::newCachedThreadPool;
        private Function<ExecutorService, CompletionService<T>> completionProvider = ExecutorCompletionService::new;
        private Instant deadline = null;
        private Clock clock = Clock.systemDefaultZone();
        private Predicate<DescribedTaskResult<T>> taskResultJudge;


        public Builder<T> withDeadline(Instant deadline) {
            assert deadline != null;
            this.deadline = deadline;
            return this;
        }

        public Builder<T> withExecutorProvider(Supplier<ExecutorService> executorProvider) {
            assert executorProvider != null;
            this.executorProvider = executorProvider;
            return this;
        }

        public Builder<T> withCompletionProvider(Function<ExecutorService, CompletionService<T>> completionProvider) {
            assert completionProvider != null;
            this.completionProvider = completionProvider;
            return this;
        }

        public Builder<T> noDeadline() {
            this.deadline = null;
            return this;
        }

        public Builder<T> withClock(Clock clock) {
            this.clock = clock;
            return this;
        }

        public Builder<T> withTaskResultJudge(Predicate<DescribedTaskResult<T>> taskResultJudge) {
            this.taskResultJudge = taskResultJudge;
            return this;
        }

        public FirstSuccessfulTaskCompletionService<T> build() {
            assert taskResultJudge != null;
            return new FirstSuccessfulTaskCompletionService<>(executorProvider, completionProvider, deadline, clock, taskResultJudge);
        }
    }
}
