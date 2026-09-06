package com.zenwayne.zenagent.tools

/**
 * Argument plucking for the constrained decoder's tool-call JSON.
 *
 * Args arrive as a small JSON object string (`{"path":"a.txt","content":"..."}`)
 * so a regex + decoder beats pulling in a JSON dependency. String values are
 * JSON-escaped on the wire and MUST be decoded before use — handing a literal
 * `\n` to a file write or to the Python interpreter is a bug.
 */

private fun stringField(key: String, argsJson: String): String? =
    Regex("\"$key\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"")
        .find(argsJson)?.groupValues?.get(1)?.let(::jsonUnescape)

internal fun extractPath(argsJson: String): String? = stringField("path", argsJson)

internal fun extractContent(argsJson: String): String? = stringField("content", argsJson)

internal fun extractCode(argsJson: String): String? = stringField("code", argsJson)

/** Decodes JSON string escapes; unknown escapes keep the escaped character. */
internal fun jsonUnescape(raw: String): String {
    if (!raw.contains('\\')) return raw
    val out = StringBuilder(raw.length)
    var i = 0
    while (i < raw.length) {
        val c = raw[i]
        if (c != '\\' || i == raw.lastIndex) {
            out.append(c)
            i++
            continue
        }
        when (val esc = raw[i + 1]) {
            '"' -> { out.append('"'); i += 2 }
            '\\' -> { out.append('\\'); i += 2 }
            '/' -> { out.append('/'); i += 2 }
            'n' -> { out.append('\n'); i += 2 }
            't' -> { out.append('\t'); i += 2 }
            'r' -> { out.append('\r'); i += 2 }
            'b' -> { out.append('\b'); i += 2 }
            'f' -> { out.append('\u000C'); i += 2 }
            'u' -> {
                val hex = raw.substring(i + 2, minOf(i + 6, raw.length))
                val code = if (hex.length == 4) hex.toIntOrNull(16) else null
                if (code != null) {
                    out.append(code.toChar())
                    i += 6
                } else {
                    out.append(esc)
                    i += 2
                }
            }
            else -> { out.append(esc); i += 2 }
        }
    }
    return out.toString()
}
