package com.zenwayne.zenagent.inference

import com.zenwayne.zenagent.data.Conversation
import com.zenwayne.zenagent.data.Role
import com.zenwayne.zenagent.data.RunState
import com.zenwayne.zenagent.data.SampleData
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        kotlinx.coroutines.Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        kotlinx.coroutines.Dispatchers.resetMain()
    }

    private fun fakeClient(tokens: List<String>): InferenceClient = object : InferenceClient {
        override fun verify(): Boolean = true
        override fun streamTokens(query: String): Flow<String> = flow {
            tokens.forEach { emit(it) }
        }
    }

    @Test
    fun `send appends user message and streams deltas into agent bubble`() = runTest(dispatcher) {
        val vm = ChatViewModel(
            fakeClient(listOf("Hel", "lo ", "world")),
            initialConversation = emptyConversation(),
            runDispatcher = dispatcher,
        )
        vm.send("hi")

        val state = vm.selected.value
        // user + streaming agent bubble exist, run is Running
        assertEquals(RunState.Running, state.runState)
        assertEquals(2, state.messages.size)
        assertEquals("hi", state.messages[0].text)
        assertEquals(Role.User, state.messages[0].role)
        assertTrue(state.messages[1].streaming)

        advanceUntilIdle()

        val done = vm.selected.value
        assertEquals(RunState.Succeeded, done.runState)
        assertEquals("Hello world", done.messages.last { it.role == Role.Agent }.text)
        assertFalse(done.messages.last { it.role == Role.Agent }.streaming)
    }

    @Test
    fun `stop cancels streaming and shows stopped banner`() = runTest(dispatcher) {
        val vm = ChatViewModel(
            fakeClient(listOf("a", "b", "c")),
            initialConversation = emptyConversation(),
            runDispatcher = dispatcher,
        )
        vm.send("hi")
        vm.stop()
        advanceUntilIdle()

        val state = vm.selected.value
        assertEquals(RunState.Stopped, state.runState)
        assertTrue(state.messages.any { it.failure?.canResume == true })
    }

    @Test
    fun `native error maps to failed state`() = runTest(dispatcher) {
        val failing = object : InferenceClient {
            override fun verify(): Boolean = true
            override fun streamTokens(query: String): Flow<String> = flow {
                throw InferenceError.EngineInit(RuntimeException("boom"))
            }
        }
        val vm = ChatViewModel(
            failing,
            initialConversation = emptyConversation(),
            runDispatcher = dispatcher,
        )
        vm.send("hi")
        advanceUntilIdle()

        val state = vm.selected.value
        assertEquals(RunState.Failed, state.runState)
        assertTrue(state.messages.any { it.failure != null })
    }

    @Test
    fun `model missing reflected after verify`() = runTest {
        val absent = object : InferenceClient {
            override fun verify(): Boolean = false
            override fun streamTokens(query: String): Flow<String> = flow { emit("x") }
        }
        val vm = ChatViewModel(
            absent,
            initialConversation = emptyConversation(),
            runDispatcher = dispatcher,
        )
        vm.verifyModel()
        assertTrue(vm.modelMissing)
        assertFalse(vm.modelReady)
    }

    private fun emptyConversation(): Conversation {
        val sample = SampleData.conversations.first()
        return sample.copy(messages = emptyList())
    }
}
