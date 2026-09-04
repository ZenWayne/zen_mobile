package com.zenwayne.zenagent.tools

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import java.io.IOException

/**
 * Splits a `/shared`-relative path into plain child names (spec §8-P3).
 *
 * [FsWorkspace] can canonicalize and prefix-check because it holds real files;
 * a SAF tree has no canonical path, so containment has to be structural
 * instead: `.` and empty segments collapse, and anything that could walk out of
 * the tree — or that a provider might treat specially — is refused outright.
 */
internal fun sharedSegments(path: String): Result<List<String>> {
    val segments = mutableListOf<String>()
    for (raw in path.split('/')) {
        val trimmed = raw.trim()
        if (trimmed.isEmpty() || trimmed == ".") continue
        if (trimmed == "..") {
            return Result.failure(SecurityException("path escapes the shared tree"))
        }
        if (raw.any { it.isISOControl() }) {
            return Result.failure(IllegalArgumentException("illegal character in path"))
        }
        segments += raw
    }
    return Result.success(segments)
}

/**
 * [FsBackend] over a directory tree the user granted with
 * `ACTION_OPEN_DOCUMENT_TREE` (spec §8-P3).
 *
 * Two differences from the sandbox are deliberate and visible to callers:
 * writes are **not** atomic (SAF offers no rename-into-place, so a truncating
 * write is the only option) and traversal costs a `listFiles` per level, which
 * is why paths stay shallow in practice. Reads and listings honour the same
 * caps as the sandbox so a huge shared folder cannot flood the model.
 */
class SafSharedStorage(
    context: Context,
    private val treeUri: Uri,
) : FsBackend {

    private val appContext = context.applicationContext

    private fun root(): DocumentFile? = DocumentFile.fromTreeUri(appContext, treeUri)

    override fun read(path: String): String {
        val doc = walk(path).getOrElse { return err(INVALID) } ?: return err(NOT_FOUND)
        if (!doc.isFile) return err("not_a_file")
        if (doc.length() > FsLimits.READ_CAP_BYTES) return err("too_large")
        val bytes = try {
            appContext.contentResolver.openInputStream(doc.uri)?.use { it.readBytes() }
        } catch (e: IOException) {
            null
        } ?: return err("read_failed")
        if (bytes.size > FsLimits.READ_CAP_BYTES) return err("too_large")
        if (bytes.any { it == 0.toByte() }) return err("binary_file")
        val text = String(bytes, Charsets.UTF_8)
        return """{"content":${FsWorkspace.jsonEscape(text)},"bytes":${bytes.size}}"""
    }

    override fun write(path: String, content: String): String {
        val segments = sharedSegments(path).getOrElse { return err(INVALID) }
        val name = segments.lastOrNull() ?: return err("not_a_file")
        val parent = descend(segments.dropLast(1), create = true) ?: return err("invalid_path")
        val target = parent.findFile(name)
            ?: parent.createFile(TEXT_MIME, name)
            ?: return err("write_failed")
        if (target.isDirectory) return err("not_a_file")
        return try {
            // "wt" truncates: SAF has no rename-into-place, so unlike the
            // sandbox this write is not atomic (documented in AGENTS.md).
            appContext.contentResolver.openOutputStream(target.uri, "wt")?.use {
                it.write(content.toByteArray(Charsets.UTF_8))
            } ?: return err("write_failed")
            """{"ok":true,"bytes":${content.toByteArray(Charsets.UTF_8).size}}"""
        } catch (e: IOException) {
            err("write_failed")
        }
    }

    override fun list(path: String): String {
        val doc = walk(path).getOrElse { return err(INVALID) } ?: return err(NOT_FOUND)
        if (!doc.isDirectory) return err("not_a_directory")
        val body = doc.listFiles().take(FsLimits.LIST_CAP).joinToString(",") { f ->
            val type = if (f.isDirectory) "\"dir\"" else "\"file\""
            val size = if (f.isFile) f.length() else 0
            """{"name":${FsWorkspace.jsonEscape(f.name.orEmpty())},"type":$type,"size":$size}"""
        }
        return """{"entries":[$body]}"""
    }

    /** Resolves an existing document, or null when a segment is missing. */
    private fun walk(path: String): Result<DocumentFile?> {
        val segments = sharedSegments(path).getOrElse { return Result.failure(it) }
        return Result.success(descend(segments, create = false))
    }

    /**
     * Walks [segments] from the tree root. Every segment but the last must be
     * a directory; with [create] set, missing directories are made along the
     * way (the last segment is left to the caller to create as a file).
     */
    private fun descend(segments: List<String>, create: Boolean): DocumentFile? {
        var current = root() ?: return null
        segments.forEachIndexed { index, name ->
            val existing = current.findFile(name)
            val next = existing ?: if (create) current.createDirectory(name) else null
            if (next == null) return null
            if (index != segments.lastIndex && !next.isDirectory) return null
            current = next
        }
        return current
    }

    private fun err(code: String) = """{"error":"$code"}"""

    private companion object {
        const val TEXT_MIME = "text/plain"
        const val NOT_FOUND = "not_found"
        const val INVALID = "invalid_path"
    }
}
