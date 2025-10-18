package com.cap.sample

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test


class GreeterTest {

    @Test
    fun shouldGreet() {
        val greeter = Greeter()
        val greeting = greeter.greet("Kotlin")
        assertThat(greeting).isEqualTo("Hello, Kotlin!")
    }
}
