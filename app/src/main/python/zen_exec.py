"""Execution harness for the `python_run` host tool (spec §4.2).

The Kotlin side hands over a snippet and gets back its captured streams. All
of the redirection happens here so the tool costs exactly one JNI round trip,
and so a snippet that raises reports its traceback the way a user running it
in a terminal would — on stderr, not as a tool failure.
"""

import io
import sys
import traceback

_MAX_STREAM_CHARS = 128 * 1024


def _clip(text):
    """Keeps a runaway print loop from returning megabytes to the model."""
    if len(text) <= _MAX_STREAM_CHARS:
        return text
    return text[:_MAX_STREAM_CHARS] + "\n...[output truncated]"


def run_code(code):
    """Runs `code` as __main__, returning [stdout, stderr] as text.

    The snippet's own exceptions are caught and rendered into stderr: the model
    reads the traceback and can correct itself, which is more useful than the
    tool call failing outright. Only SystemExit is treated as a clean stop.
    """
    out, err = io.StringIO(), io.StringIO()
    saved_out, saved_err = sys.stdout, sys.stderr
    sys.stdout, sys.stderr = out, err
    try:
        exec(compile(code, "<zen_python_run>", "exec"), {"__name__": "__main__"})
    except SystemExit:
        pass
    except BaseException:
        traceback.print_exc(file=err)
    finally:
        sys.stdout, sys.stderr = saved_out, saved_err
    return [_clip(out.getvalue()), _clip(err.getvalue())]
