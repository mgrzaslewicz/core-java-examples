package com.cap

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.RepeatedTest
import java.time.Duration

class ConcurrentSuccessfulTaskCollectorTest {

    @RepeatedTest(100)
    fun shouldCollectSuccessfulResults() {
        // given
        val concurrency = 5
        val runner = ConcurrentSuccessfulTaskCollector<Int>(maxConcurrency = concurrency)

        var iteration = 0
        val taskSupplier = {
            if (iteration++ % 2 == 0) {
                Thread.sleep((Math.random() * 10).toLong())
                (Math.random() * 1000).toInt()
            } else {
                null
            }
        }

        // when
        val minNumberOfResults = 10
        val results = runner.countSuccessfulResults(
            taskSupplier = taskSupplier,
            minNumberOfResults = minNumberOfResults,
            maxAttempts = 50,
        )

        // then
        assertThat(results.successful).isBetween(minNumberOfResults, minNumberOfResults + concurrency)
        assertThat(results.failed).isBetween(results.successful - 1, results.successful + 1)
    }

    @RepeatedTest(100)
    fun shouldNotFinishTooEarly() {
        // given
        val concurrency = 20
        val runner = ConcurrentSuccessfulTaskCollector<Int>(maxConcurrency = concurrency, terminationTimeout = Duration.ofMillis(1))

        val taskSupplier = {
            Thread.sleep(30 + (Math.random() * 10).toLong())
            1
        }

        // when
        val minNumberOfResults = 40
        val millisBefore = System.currentTimeMillis()
        val results = runner.countSuccessfulResults(
            taskSupplier = taskSupplier,
            minNumberOfResults = minNumberOfResults,
            maxAttempts = 40,
        )
        val millisAfter = System.currentTimeMillis()
        val durationMillis = millisAfter - millisBefore

        // then
        assertThat(results.successful).isEqualTo(40)
        assertThat(durationMillis).isGreaterThan(60)
    }

    @RepeatedTest(100)
    fun shouldCollectSuccessfulResultsWhenMoreThreadsThanAttempts() {
        // given
        val concurrency = 20
        val runner = ConcurrentSuccessfulTaskCollector<Int>(maxConcurrency = concurrency)

        var iteration = 0
        val taskSupplier = {
            if (iteration++ % 2 == 0) {
                Thread.sleep((Math.random() * 10).toLong())
                (Math.random() * 1000).toInt()
            } else {
                null
            }
        }

        // when
        val minNumberOfResults = 10
        val results = runner.countSuccessfulResults(
            taskSupplier = taskSupplier,
            minNumberOfResults = minNumberOfResults,
            maxAttempts = 10,
        )

        // then
        assertThat(results.successful).isEqualTo(5)
        assertThat(results.failed).isEqualTo(5)
    }

    @RepeatedTest(100)
    fun shouldExceedMaxAttempts() {
        // given
        val concurrency = 10
        val runner = ConcurrentSuccessfulTaskCollector<Int>(maxConcurrency = concurrency)

        var iteration = 0
        val taskSupplier = {
            if (++iteration % 15 == 0) {
                Thread.sleep((Math.random() * 10).toLong())
                (Math.random() * 1000).toInt()
            } else {
                null
            }
        }

        // when
        val results = runner.countSuccessfulResults(
            taskSupplier = taskSupplier,
            minNumberOfResults = 10,
            maxAttempts = 20,
        )

        // then
        assertThat(results.successful).isEqualTo(1)
        assertThat(results.failed).isEqualTo(19)
    }

}
