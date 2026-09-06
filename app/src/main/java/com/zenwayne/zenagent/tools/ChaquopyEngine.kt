package com.zenwayne.zenagent.tools

import android.content.Context
import com.chaquo.python.PyException
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform

/**
 * [PythonEngine] backed by Chaquopy's embedded CPython (spec Q3).
 *
 * `Python.start` is deferred to the first execution rather than run in
 * `Application.onCreate`: starting the interpreter costs hundreds of
 * milliseconds and would tax every cold launch, while the first `python_run`
 * is already behind an approval gate on a background worker. It is called from
 * [PythonRunner]'s single worker thread, so no extra locking is needed.
 *
 * Stream capture lives in `app/src/main/python/zen_exec.py`: redirecting
 * `sys.stdout`/`sys.stderr` on the Python side keeps the whole exec + capture
 * + traceback dance in one place and one JNI round trip.
 */
class ChaquopyEngine(context: Context) : PythonEngine {
    private val appContext = context.applicationContext

    private val runner by lazy {
        if (!Python.isStarted()) Python.start(AndroidPlatform(appContext))
        Python.getInstance().getModule(MODULE)
    }

    override fun exec(code: String): PythonResult {
        val out = try {
            runner.callAttr(ENTRY, code).asList()
        } catch (e: PyException) {
            // The harness itself failed (not the user's snippet, which returns
            // its traceback through stderr) — surface it as the run's stderr.
            return PythonResult("", e.message ?: "python harness failed")
        }
        return PythonResult(
            stdout = out[0]?.toString().orEmpty(),
            stderr = out[1]?.toString().orEmpty(),
        )
    }

    private companion object {
        const val MODULE = "zen_exec"
        const val ENTRY = "run_code"
    }
}
