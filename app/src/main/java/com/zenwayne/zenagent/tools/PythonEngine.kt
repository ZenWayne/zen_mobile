package com.zenwayne.zenagent.tools

/** One execution's captured streams. */
data class PythonResult(val stdout: String, val stderr: String)

/**
 * Interpreter seam (spec §4.2). Chaquopy lives behind it so [PythonRunner]'s
 * queueing/timeout contract unit-tests on the plain JVM, with no Android or
 * native runtime on the test classpath.
 *
 * Implementations are only ever called from [PythonRunner]'s single worker,
 * so they need not be thread-safe themselves.
 */
interface PythonEngine {
    /** Executes [code], returning whatever it wrote to stdout/stderr. */
    fun exec(code: String): PythonResult
}

object PythonLimits {
    /** Code size cap — a guard against a runaway model, not a security boundary. */
    const val CODE_CAP_BYTES = 100 * 1024

    /** Best-effort wall clock per run (spec §4.2 — no hard kill is possible). */
    const val DEFAULT_TIMEOUT_MS = 30_000L
}
