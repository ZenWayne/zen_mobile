package com.zenwayne.zenagent.tools

/**
 * What the three fs tools need from a storage root: the same three operations,
 * each returning a JSON result string (`{"error":"..."}` on failure).
 *
 * Two implementations sit behind it — [FsWorkspace] over `java.io` for the
 * sandbox, and `SafSharedStorage` over a user-authorized SAF tree (P3) — and
 * [FsRouter] picks between them by path prefix. Implementations must be
 * concurrency-safe (AGENTS.md: parallel dispatch may call them at once).
 */
interface FsBackend {
    fun read(path: String): String
    fun write(path: String, content: String): String
    fun list(path: String): String
}
