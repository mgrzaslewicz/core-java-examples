package com.autocoin.cap.completion;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FirstSuccessfulTaskCompletionService<T> {
    private static final Logger logger = LoggerFactory.getLogger(FirstSuccessfulTaskCompletionService.class);

    private final ExecutorService executor;
    private final Instant deadline;
    private final List<DescribedTask<T>> tasks;
    private final CurrentTimeProvider currentTimeProvider;
    private final TaskResultJudge<T> taskResultJudge;

    private FirstSuccessfulTaskCompletionService(ExecutorService executor,
                                                 Instant deadline,
                                                 List<DescribedTask<T>> tasks,
                                                 CurrentTimeProvider currentTimeProvider,
                                                 TaskResultJudge taskResultJudge) {
        this.executor = executor;
        this.deadline = deadline;
        this.tasks = tasks;
        this.currentTimeProvider = currentTimeProvider;
        this.taskResultJudge = taskResultJudge;
    }

    private final static Instant noDeadline = Instant.MAX;
    private boolean isWaitingForSuccessfulResult = true;
    private int numberOfTasksAlreadyFinished = 0;

    private Long millisecondsToDeadline() {
        if (deadline == noDeadline) {
            return Long.MAX_VALUE;
        } else {
            return Duration.between(currentTimeProvider.now(), deadline).toMillis();
        }
    }

    private Iterator<TaskResultJudgement<T>> toIterator(ExecutorCompletionService<T> completionService, Map<Future<T>, DescribedTask<T>> futuresWithTasks) {
        return new Iterator<>() {
            @Override
            public boolean hasNext() {
                return isWaitingForSuccessfulResult && numberOfTasksAlreadyFinished < tasks.size();
            }

            @Override
            public TaskResultJudgement<T> next() {
                try {
                    final var completedFuture = completionService.poll(millisecondsToDeadline(), TimeUnit.MILLISECONDS);
                    numberOfTasksAlreadyFinished++;
                    final var taskResult = completedFuture.get(millisecondsToDeadline(), TimeUnit.MILLISECONDS);
                    final var task = futuresWithTasks.get(completedFuture);
                    final var describedTaskResult = new DescribedTaskResult<T>(task, taskResult);
                    final var taskResultJudgement = new TaskResultJudgement<T>(taskResultJudge.isSuccessful(describedTaskResult), describedTaskResult.task, describedTaskResult.result);
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

    public TaskResults<T> waitForResults() {
        final var completionService = new ExecutorCompletionService<T>(executor);
        final Map<Future<T>, DescribedTask<T>> futuresWithTasks = submitTasks(completionService);

        final var taskResultJudgements = new ArrayList<TaskResultJudgement<T>>();
        toIterator(completionService, futuresWithTasks).forEachRemaining(taskResultJudgements::add);

        final var skippedTasks = getSkippedInSubmissionOrder(taskResultJudgements);
        logSkippedTasks(skippedTasks);

        return tasksResults(taskResultJudgements, skippedTasks);
    }

    private List<DescribedTask<T>> getSkippedInSubmissionOrder(List<TaskResultJudgement<T>> taskResultJudgements) {
        return tasks.stream()
                .filter(describedTask -> taskResultJudgements.stream().noneMatch(taskResultJudgement -> taskResultJudgement.task == describedTask))
                .collect(Collectors.toList());
    }

    private void logSkippedTasks(List<DescribedTask<T>> skippedTasks) {
        if (!skippedTasks.isEmpty()) {
            logger.info("Waiting for results of tasks [{}] skipped as there was already a successful one", skippedTasks.stream().map(it -> "'" + it.description + "'").collect(Collectors.joining(", ")));
        }
    }

    private Map<Future<T>, DescribedTask<T>> submitTasks(ExecutorCompletionService<T> completionService) {
        final var result = new LinkedHashMap<Future<T>, DescribedTask<T>>();
        tasks.stream().forEach(describedTask -> result.put(completionService.submit(describedTask.task), describedTask));
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

    public interface CurrentTimeProvider {
        Instant now();
    }

    public record TaskResultJudgement<T>(boolean isSuccessful, DescribedTask<T> task, T result) {

    }

    public record TaskResults<T>(TaskResultJudgement<T> firstSuccessfulTaskResult, List<TaskResultJudgement<T>> failed, List<DescribedTask<T>> skippedWaitingForResults) {
    }

    public record DescribedTaskResult<T>(DescribedTask<T> task, T result) {
    }

    public interface TaskResultJudge<T> {
        boolean isSuccessful(DescribedTaskResult<T> describedTaskResult);
    }

    public static final class Builder<T> {
        private final ExecutorService executor;
        private Instant deadline = noDeadline;
        private List<DescribedTask<T>> tasks = List.of();
        private CurrentTimeProvider currentTimeProvider = Instant::now;
        private TaskResultJudge taskResultJudge;

        public Builder(ExecutorService executor) {
            this.executor = executor;
        }

        public Builder<T> withDeadline(Instant deadline) {
            this.deadline = deadline;
            return this;
        }

        public Builder<T> withTasks(List<DescribedTask<T>> tasks) {
            this.tasks = tasks;
            return this;
        }

        public Builder<T> withCurrentTimeProvider(CurrentTimeProvider currentTimeProvider) {
            this.currentTimeProvider = currentTimeProvider;
            return this;
        }

        public Builder<T> withTaskResultJudge(TaskResultJudge<T> taskResultJudge) {
            this.taskResultJudge = taskResultJudge;
            return this;
        }

        public FirstSuccessfulTaskCompletionService<T> build() {
            assert taskResultJudge != null;
            return new FirstSuccessfulTaskCompletionService<T>(executor, deadline, tasks, currentTimeProvider, taskResultJudge);
        }
    }
}
