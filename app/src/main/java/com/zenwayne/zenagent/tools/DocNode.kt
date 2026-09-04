package com.zenwayne.zenagent.tools

import android.content.ContentResolver
import androidx.documentfile.provider.DocumentFile
import java.io.IOException

/**
 * The document-tree surface [SafSharedStorage] needs, and nothing more.
 *
 * SAF is only reachable through `DocumentFile` + `ContentResolver`, neither of
 * which exists on a plain JVM — so the traversal and policy decisions (which
 * segment must be a directory, when to create, the size caps, binary
 * detection) live above this seam and unit-test with an in-memory tree, the
 * same split that keeps [PythonEngine] testable.
 *
 * Failures are nulls and booleans rather than exceptions: every caller turns
 * them into an `{"error":…}` result string anyway.
 */
internal interface DocNode {
    val name: String
    val isFile: Boolean
    val isDirectory: Boolean

    /** Size in bytes as the provider reports it — may not match the real read. */
    val length: Long

    fun child(name: String): DocNode?
    fun children(): List<DocNode>
    fun createFile(name: String): DocNode?
    fun createDirectory(name: String): DocNode?

    /** null when the document could not be read. */
    fun readBytes(): ByteArray?

    /** false when the document could not be written. */
    fun writeBytes(bytes: ByteArray): Boolean
}

/** The real thing: one [DocNode] over one `DocumentFile` in the granted tree. */
internal class DocumentFileNode(
    private val resolver: ContentResolver,
    private val doc: DocumentFile,
) : DocNode {
    override val name: String get() = doc.name.orEmpty()
    override val isFile: Boolean get() = doc.isFile
    override val isDirectory: Boolean get() = doc.isDirectory
    override val length: Long get() = doc.length()

    override fun child(name: String): DocNode? =
        doc.findFile(name)?.let { DocumentFileNode(resolver, it) }

    override fun children(): List<DocNode> =
        doc.listFiles().map { DocumentFileNode(resolver, it) }

    override fun createFile(name: String): DocNode? =
        doc.createFile(TEXT_MIME, name)?.let { DocumentFileNode(resolver, it) }

    override fun createDirectory(name: String): DocNode? =
        doc.createDirectory(name)?.let { DocumentFileNode(resolver, it) }

    override fun readBytes(): ByteArray? = try {
        resolver.openInputStream(doc.uri)?.use { it.readBytes() }
    } catch (e: IOException) {
        null
    } catch (e: SecurityException) {
        // The grant can be revoked between the check and the read.
        null
    }

    override fun writeBytes(bytes: ByteArray): Boolean = try {
        // "wt" truncates: SAF has no rename-into-place, so unlike the sandbox
        // this write is not atomic (documented in AGENTS.md).
        val stream = resolver.openOutputStream(doc.uri, "wt") ?: return false
        stream.use { it.write(bytes) }
        true
    } catch (e: IOException) {
        false
    } catch (e: SecurityException) {
        false
    }

    private companion object {
        const val TEXT_MIME = "text/plain"
    }
}
