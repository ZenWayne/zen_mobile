package com.zenwayne.zenagent.tools

import java.io.File
import java.io.IOException

object FsLimits {
    const val READ_CAP_BYTES = 512 * 1024
    const val LIST_CAP = 200
}

/**
 * Sandboxed filesystem view over a workspace root (spec §4.2). Pure
 * java.io — no Android dependencies — so it unit-tests on the JVM. Paths are
 * canonicalized and prefix-checked against the root: `..` and absolute paths
 * resolve inside the workspace; escapes are errors.
 */
class FsWorkspace(private val root: File) : FsBackend {
    private val rootCanonical: String = root.canonicalPath

    fun resolve(path: String): Result<File> {
        val p = path.trim().ifEmpty { "." }
        val raw = if (p.startsWith("/")) File(root, p.removePrefix("/")) else File(root, p)
        return try {
            val canon = raw.canonicalFile
            val ok = canon.path == rootCanonical ||
                canon.path.startsWith(rootCanonical + File.separator)
            if (ok) Result.success(canon)
            else Result.failure(SecurityException("path escapes workspace"))
        } catch (e: IOException) {
            Result.failure(e)
        }
    }

    override fun read(path: String): String {
        val f = resolve(path).getOrElse { return err("invalid_path") }
        if (!f.isFile) return err("not_a_file")
        if (f.length() > FsLimits.READ_CAP_BYTES) return err("too_large")
        val bytes = f.readBytes()
        bytes.forEach { b -> if (b == 0.toByte()) return err("binary_file") }
        val text = String(bytes, Charsets.UTF_8)
        return """{"content":${jsonEscape(text)},"bytes":${bytes.size}}"""
    }

    override fun write(path: String, content: String): String {
        val f = resolve(path).getOrElse { return err("invalid_path") }
        f.parentFile?.mkdirs()
        val tmp = File(f.parentFile, "${f.name}.tmp-${System.nanoTime()}")
        return try {
            tmp.writeText(content)
            if (!tmp.renameTo(f)) {
                tmp.copyTo(f, overwrite = true)
                tmp.delete()
            }
            """{"ok":true,"bytes":${content.toByteArray(Charsets.UTF_8).size}}"""
        } catch (e: IOException) {
            tmp.delete()
            err("write_failed")
        }
    }

    override fun list(path: String): String {
        val d = resolve(path).getOrElse { return err("invalid_path") }
        if (!d.isDirectory) return err("not_a_directory")
        val entries = d.listFiles()?.take(FsLimits.LIST_CAP) ?: emptyList()
        val body = entries.joinToString(",") { f ->
            val type = if (f.isDirectory) "\"dir\"" else "\"file\""
            val size = if (f.isFile) f.length() else 0
            """{"name":${jsonEscape(f.name)},"type":$type,"size":$size}"""
        }
        return """{"entries":[$body]}"""
    }

    private fun err(code: String) = """{"error":"$code"}"""

    companion object {
        fun jsonEscape(s: String): String = buildString {
            append('"')
            s.forEach { c ->
                when (c) {
                    '"' -> append("\\\"")
                    '\\' -> append("\\\\")
                    '\n' -> append("\\n")
                    '\t' -> append("\\t")
                    else -> if (c.code < 0x20) append(String.format("\\u%04x", c.code)) else append(c)
                }
            }
            append('"')
        }
    }
}
