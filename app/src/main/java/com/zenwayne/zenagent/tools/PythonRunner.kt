package com.zenwayne.zenagent.tools

import java.util.concurrent.ExecutionException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/**
 * Serializes every Python execution through one worker thread (spec §4.2).
 *
 * There is a single embedded interpreter per process, so concurrent execution
 * is not an option — and the queue doubles as this tool's answer to the
 * parallel-dispatch concurrency contract (AGENTS.md): two tool calls in one
 * turn simply run one after the other.
 *
 * Timeouts are best-effort by construction. CPython cannot be interrupted
 * mid-`while True`, so a run that overruns is *abandoned*: the caller gets
 * `{"error":"timeout"}` and the wedged worker is detached (it keeps running as
 * a daemon thread) while a fresh worker takes over. The interpreter itself is
 * created once and shared across workers.
 */
class PythonRunner(
    private val engineFactory: () -> PythonEngine,
    private val defaultTimeoutMs: Long = PythonLimits.DEFAULT_TIMEOUT_MS,
) {
    private val workerLock = Any()
    private val engineLock = Any()

    private var worker: ExecutorService = newWorker()

    @Volatile
    private var engine: PythonEngine? = null

    /**
     * Runs [code], returning `{"stdout":…,"stderr":…}` or an `{"error":…}`
     * result string. [timeoutMs] covers queue wait *and* execution, so a call
     * queued behind a long-running one still returns within a bounded time.
     */
    fun run(code: String, timeoutMs: Long = defaultTimeoutMs): String {
        if (code.toByteArray(Charsets.UTF_8).size > PythonLimits.CODE_CAP_BYTES) {
            return """{"error":"code_too_large"}"""
        }
        val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs)
        val submitted = submit(code) ?: return errorResult("python_failed", "runner shut down")
        val (target, future) = submitted
        return try {
            val result = future.get(
                maxOf(0, deadline - System.nanoTime()),
                TimeUnit.NANOSECONDS,
            )
            """{"stdout":${FsWorkspace.jsonEscape(result.stdout)},""" +
                """"stderr":${FsWorkspace.jsonEscape(result.stderr)}}"""
        } catch (e: TimeoutException) {
            detach(target)
            """{"error":"timeout"}"""
        } catch (e: ExecutionException) {
            errorResult("python_failed", e.cause?.message ?: e.message ?: "unknown error")
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            """{"error":"cancelled"}"""
        }
    }

    /** Stops the current worker. The process-wide interpreter is not unloaded. */
    fun shutdown() {
        synchronized(workerLock) { worker.shutdownNow() }
    }

    /**
     * Submits onto the live worker, retrying once: a worker detached by a
     * concurrent timeout rejects late arrivals, and those callers belong on
     * the replacement rather than failing.
     */
    private fun submit(code: String): Pair<ExecutorService, java.util.concurrent.Future<PythonResult>>? {
        repeat(2) {
            val target = synchronized(workerLock) { worker }
            try {
                return target to target.submit<PythonResult> { engineOrCreate().exec(code) }
            } catch (e: RejectedExecutionException) {
                // Fall through and pick up whatever worker is current now.
            }
        }
        return null
    }

    /**
     * Replaces [stale] with a fresh worker. `shutdown()` (not `shutdownNow()`)
     * because interrupting will not free a thread stuck inside CPython, and a
     * hard stop would also discard work already queued behind it.
     */
    private fun detach(stale: ExecutorService) {
        synchronized(workerLock) {
            if (worker === stale) worker = newWorker()
        }
        stale.shutdown()
    }

    private fun engineOrCreate(): PythonEngine {
        engine?.let { return it }
        synchronized(engineLock) {
            engine?.let { return it }
            return engineFactory().also { engine = it }
        }
    }

    private fun newWorker(): ExecutorService =
        Executors.newSingleThreadExecutor { r ->
            Thread(r, "zen-python").apply { isDaemon = true }
        }

    private fun errorResult(code: String, detail: String) =
        """{"error":"$code","detail":${FsWorkspace.jsonEscape(detail)}}"""
}
