package com.zenwayne.zenagent.tools

import agentflow.dsl.CancellationSignal
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `python_run` tool surface: approval gate (spec §4.2 — code execution is
 * side-effecting), argument extraction, and the error taxonomy the model sees.
 */
class PythonRunToolTest {

    private class Signal(@Volatile var flag: Boolean = false) : CancellationSignal {
        override val cancelled get() = flag
    }

    private fun tool(
        gates: ConcurrentHashMap<String, ApprovalGate> = ConcurrentHashMap(),
        body: (String) -> PythonResult = { PythonResult("ran:$it", "") },
    ) = PythonRunTool(PythonRunner({ object : PythonEngine {
        override fun exec(code: String) = body(code)
    } }), gates)

    @Test
    fun declaresApprovalRequiredAndItsSchema() {
        val t = tool()
        assertEquals("python_run", t.name)
        assertTrue("code execution must be gated", t.requiresApproval)
        assertTrue(t.paramsJsonSchema, t.paramsJsonSchema.contains("\"code\""))
    }

    @Test
    fun approvedCallExecutesAndReturnsStreams() {
        val gates = ConcurrentHashMap<String, ApprovalGate>()
        val t = tool(gates)
        val pool = Executors.newSingleThreadExecutor()
        val fut = pool.submit<String> {
            t.invoke("call-1", """{"code":"print(1)"}""", Signal())
        }
        awaitGate(gates, "call-1").approve()
        assertEquals("""{"stdout":"ran:print(1)","stderr":""}""", fut.get(5, TimeUnit.SECONDS))
        pool.shutdown()
    }

    @Test
    fun deniedCallNeverReachesTheInterpreter() {
        val gates = ConcurrentHashMap<String, ApprovalGate>()
        var executed = false
        val t = tool(gates) { executed = true; PythonResult("", "") }
        val pool = Executors.newSingleThreadExecutor()
        val fut = pool.submit<String> {
            t.invoke("call-2", """{"code":"import os"}""", Signal())
        }
        awaitGate(gates, "call-2").deny()
        assertEquals("""{"error":"user_denied"}""", fut.get(5, TimeUnit.SECONDS))
        assertEquals(false, executed)
        pool.shutdown()
    }

    @Test
    fun cancelledRunReleasesTheGateAsDenied() {
        val gates = ConcurrentHashMap<String, ApprovalGate>()
        val t = tool(gates)
        val sig = Signal()
        val pool = Executors.newSingleThreadExecutor()
        val fut = pool.submit<String> { t.invoke("call-3", """{"code":"x"}""", sig) }
        awaitGate(gates, "call-3")
        sig.flag = true
        assertEquals("""{"error":"user_denied"}""", fut.get(5, TimeUnit.SECONDS))
        pool.shutdown()
    }

    @Test
    fun missingCodeArgumentIsAnErrorResult() {
        assertEquals("""{"error":"missing_code"}""", tool().invoke("c", """{"path":"a"}""", Signal()))
    }

    /** Multi-line snippets arrive JSON-escaped and must reach Python unescaped. */
    @Test
    fun escapedNewlinesAreDecodedBeforeExecution() {
        val gates = ConcurrentHashMap<String, ApprovalGate>()
        var seen: String? = null
        val t = tool(gates) { seen = it; PythonResult("ok", "") }
        val pool = Executors.newSingleThreadExecutor()
        val fut = pool.submit<String> {
            t.invoke("call-4", """{"code":"a = 1\nprint(a)"}""", Signal())
        }
        awaitGate(gates, "call-4").approve()
        fut.get(5, TimeUnit.SECONDS)
        assertEquals("a = 1\nprint(a)", seen)
        pool.shutdown()
    }

    private fun awaitGate(
        gates: ConcurrentHashMap<String, ApprovalGate>,
        id: String,
    ): ApprovalGate {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
        while (System.nanoTime() < deadline) {
            gates[id]?.let { return it }
            Thread.sleep(10)
        }
        throw AssertionError("gate $id never registered")
    }
}
