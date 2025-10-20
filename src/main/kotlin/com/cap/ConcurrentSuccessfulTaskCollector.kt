package com.cap

import mu.KotlinLogging
import java.time.Duration
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger


/**
 * A thread-safe collector that executes tasks concurrently and collects count of successful and failed results.
 *
 * It continues executing tasks until either the desired number of successful results is achieved
 * or the maximum number of attempts is reached.
 *
 * Example use cases:
 * - Load test keeping `maxConcurrency` tasks
 * - Dataset generation via API to have desired number of entities, where concurrent tasks make it faster than linear processing
 *
 * @param T The type of results produced by the tasks
 * @param maxConcurrency The maximum number of tasks that can execute concurrently
 */
class ConcurrentSuccessfulTaskCollector<T>(
    private val maxConcurrency: Int,
    private val terminationTimeout: Duration = Duration.ofSeconds(10),
) {
    init {
        require(maxConcurrency > 0) { "maxConcurrency must be greater than 0" }
    }

    private companion object {
        private val logger = KotlinLogging.logger {}
        private val poolCount = AtomicInteger(0)
    }

    data class TasksCount(val successful: Int, val failed: Int) {
        fun attempts() = successful + failed
    }

    /**
     * Executes tasks concurrently until the desired number of successful results is collected
     * or maximum attempts is reached.
     *
     * @param taskSupplier A function that supplies the task logic and returns result or null on failure
     * @param onSuccess Optional callback function to be executed for each successful result
     * @param minNumberOfResults Minimum number of successful results needed
     * @param maxAttempts Maximum number of task execution attempts allowed
     * @return [TasksCount] containing the number of successful and failed attempts
     */
    fun countSuccessfulResults(
        taskSupplier: () -> T?,
        onSuccess: (T) -> Unit = {},
        minNumberOfResults: Int,
        maxAttempts: Int,
    ): TasksCount {
        require(maxAttempts > 0) { "maxAttempts must be greater than 0" }
        require(minNumberOfResults > 0) { "minNumberOfResults must be greater than 0" }
        require(minNumberOfResults <= maxAttempts) { "minNumberOfResults must be less than or equal to maxAttempts" }

        val successfulResults = AtomicInteger(0)
        val attemptCount = AtomicInteger(0)
        val shouldStop = AtomicBoolean(false)

        val poolName = "concurrent-successful-task-collector-pool-${poolCount.incrementAndGet()}"
        var threadCount = 1
        val executor = Executors.newFixedThreadPool(maxConcurrency) {
            Thread(it).apply { name = "$poolName-thread-${threadCount++}" }
        }
        val completionService = ExecutorCompletionService<T>(executor)
        val futures = mutableListOf<Future<T>>()
        val finishLatch = CountDownLatch(maxConcurrency)

        val semaphore = Semaphore(maxConcurrency)
        try {
            repeat(maxConcurrency) {
                futures += completionService.submit {
                    while (successfulResults.get() < minNumberOfResults && attemptCount.get() < maxAttempts) {
                        if (!shouldStop.get()) {
                            try {
                                semaphore.acquire()
                                attemptCount.incrementAndGet()
                                val taskResult = taskSupplier()
                                if (taskResult != null) {
                                    onSuccess(taskResult)
                                    successfulResults.incrementAndGet()
                                }
                            } catch (e: Exception) {
                                logger.debug(e) { "Executing task failed" }
                            } finally {
                                semaphore.release()
                            }
                        }
                    }
                    shouldStop.set(true)
                    logger.info { "Latch: ${finishLatch.count}" }
                    finishLatch.countDown()
                    null
                }
            }
            finishLatch.await()
            futures.forEach { it.get(terminationTimeout.toMillis(), TimeUnit.MILLISECONDS) }

        } finally {
            executor.shutdown()
            try {
                if (!executor.awaitTermination(terminationTimeout.toMillis(), TimeUnit.MILLISECONDS)) {
                    executor.shutdownNow()
                }
            } catch (e: InterruptedException) {
                executor.shutdownNow()
                Thread.currentThread().interrupt()
            }
        }
        return TasksCount(
            successful = successfulResults.get(),
            failed = attemptCount.get() - successfulResults.get(),
        )
    }

}
