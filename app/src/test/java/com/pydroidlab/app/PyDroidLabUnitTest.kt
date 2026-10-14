package com.pydroidlab.app

import com.pydroidlab.app.data.SampleRepository
import com.pydroidlab.terminal.TerminalEmulator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.concurrent.thread

class PyDroidLabUnitTest {

    @Test
    fun verifyTwelveBuiltInSamplesExist() {
        assertEquals(12, SampleRepository.samples.size)
        assertTrue(SampleRepository.samples.any { it.usesInput })
        assertTrue(SampleRepository.samples.any { it.code.contains("import requests") })
    }

    @Test
    fun verifyInteractiveInputQueueAndStdout() {
        val emulator = TerminalEmulator()
        emulator.resetForNewSession("test_input.py")
        emulator.writeStdout("Hello World\n")

        var receivedInput: String? = null
        val worker = thread {
            receivedInput = emulator.requestLineInput("Enter name: ")
        }

        Thread.sleep(50)
        assertTrue(emulator.snapshot.value.isAwaitingInput)
        emulator.submitUserInput("Alice")
        worker.join(1000)

        assertEquals("Alice", receivedInput)
        val allText = emulator.snapshot.value.lines.joinToString("\n") { it.plainText }
        assertTrue(allText.contains("Hello World"))
        assertTrue(allText.contains("Enter name: Alice"))
    }
}
