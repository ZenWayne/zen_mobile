package com.zenwayne.zenagent.tools

import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * P2 runner contract (spec §4.2): one interpreter → every execution serialized
 * through a single worker, best-effort timeout that detaches a wedged worker,
 * and a code size cap. Chaquopy is behind the [PythonEngine] seam so this runs
 * on the plain JVM.
 */
class PythonRunnerTest {

    private class FakeEngine(
        val body: (String) -> PythonResult = { PythonResult("out:$it", "") },
    ) : PythonEngine {
        val started = AtomicInteger(0)
        override fun exec(code: String): PythonResult {
            started.incrementAndGet()
            return body(code)
        }
    }

    @Test
    fun capturedStreamsRoundTripAsJson() {
        val runner = PythonRunner({ FakeEngine { PythonResult("hello\n", "warn") } })
        assertEquals("""{"stdout":"hello\n","stderr":"warn"}""", runner.run("print('hello')"))
        runner.shutdown()
    }

    @Test
    fun stdoutIsJsonEscaped() {
        val runner = PythonRunner({ FakeEngine { PythonResult("say \"hi\"\tnow", "") } })
        assertEquals("""{"stdout":"say \"hi\"\tnow","stderr":""}""", runner.run("x"))
        runner.shutdown()
    }

    /**
     * The concurrency-safety contract under parallel dispatch: two callers hit
     * the runner at once, the engine must never see overlapping executions.
     */
    @Test
    fun concurrentRunsAreSerialized() {
        val inFlight = AtomicInteger(0)
        val overlapped = AtomicBoolean(false)
        val engine = FakeEngine { code ->
            if (inFlight.incrementAndGet() > 1) overlapped.set(true)
            Thread.sleep(80)
            inFlight.decrementAndGet()
            PythonResult(code, "")
        }
        val runner = PythonRunner({ engine })
        val pool = Executors.newFixedThreadPool(4)
        val ready = CountDownLatch(4)
        val futures = (0 until 4).map { i ->
            pool.submit<String> {
                ready.countDown()
                ready.await()
                runner.run("code$i")
            }
        }
        futures.forEach { it.get(10, TimeUnit.SECONDS) }
        pool.shutdown()
        assertFalse("engine saw overlapping executions", overlapped.get())
        assertEquals(4, engine.started.get())
        runner.shutdown()
    }

    @Test
    fun timeoutReturnsErrorAndDoesNotWedgeTheRunner() {
        val release = CountDownLatch(1)
        // First call blocks past the timeout; the worker is detached, so the
        // next call must still be served by a fresh worker.
        val engine = FakeEngine { code ->
            if (code == "slow") release.await(10, TimeUnit.SECONDS)
            PythonResult(code, "")
        }
        val runner = PythonRunner({ engine })
        assertEquals("""{"error":"timeout"}""", runner.run("slow", timeoutMs = 150))
        assertEquals("""{"stdout":"fast","stderr":""}""", runner.run("fast", timeoutMs = 2_000))
        release.countDown()
        runner.shutdown()
    }

    @Test
    fun oversizedCodeIsRejectedWithoutTouchingTheEngine() {
        val engine = FakeEngine()
        val runner = PythonRunner({ engine })
        val huge = "#".repeat(PythonLimits.CODE_CAP_BYTES + 1)
        assertEquals("""{"error":"code_too_large"}""", runner.run(huge))
        assertEquals(0, engine.started.get())
        runner.shutdown()
    }

    @Test
    fun codeExactlyAtTheCapIsAccepted() {
        val runner = PythonRunner({ FakeEngine { PythonResult("ok", "") } })
        val atCap = "#".repeat(PythonLimits.CODE_CAP_BYTES)
        assertEquals("""{"stdout":"ok","stderr":""}""", runner.run(atCap))
        runner.shutdown()
    }

    @Test
    fun engineFailureBecomesAnErrorResult() {
        val runner = PythonRunner({ FakeEngine { throw IllegalStateException("interpreter gone") } })
        val result = runner.run("boom")
        assertTrue(result, result.startsWith("""{"error":"python_failed"""))
        assertTrue(result, result.contains("interpreter gone"))
        runner.shutdown()
    }

    /** Starting the interpreter is deferred to the first actual run. */
    @Test
    fun engineIsCreatedLazilyAndOnlyOnce() {
        val created = AtomicInteger(0)
        val runner = PythonRunner({
            created.incrementAndGet()
            FakeEngine { PythonResult("ok", "") }
        })
        assertEquals(0, created.get())
        runner.run("a")
        runner.run("b")
        assertEquals(1, created.get())
        runner.shutdown()
    }
}
