package com.zenwayne.zenagent.tools

/**
 * Presents one path space to the model over two roots (spec §8-P3).
 *
 * Everything is sandbox-relative by default; a path whose leading absolute
 * segment is `shared` addresses the directory tree the user authorized through
 * the system file picker. Defaulting to the sandbox is the safe direction: a
 * model that forgets the prefix writes somewhere harmless rather than into the
 * user's documents.
 *
 * The authorized tree is resolved per call, so revoking access in Settings
 * takes effect on the very next tool call, mid-run included.
 */
class FsRouter(
    private val workspace: FsBackend,
    private val sharedRoot: () -> FsBackend?,
) : FsBackend {

    override fun read(path: String): String {
        val (backend, p) = resolve(path) ?: return NOT_AUTHORIZED
        return backend.read(p)
    }

    override fun write(path: String, content: String): String {
        val (backend, p) = resolve(path) ?: return NOT_AUTHORIZED
        return backend.write(p, content)
    }

    override fun list(path: String): String {
        val (backend, p) = resolve(path) ?: return NOT_AUTHORIZED
        return backend.list(p)
    }

    /** null means `/shared` was addressed with no tree authorized. */
    private fun resolve(path: String): Pair<FsBackend, String>? {
        val p = path.trim()
        if (p != PREFIX && !p.startsWith("$PREFIX/")) return workspace to p
        val backend = sharedRoot() ?: return null
        val remainder = p.removePrefix(PREFIX).removePrefix("/")
        return backend to remainder.ifEmpty { "." }
    }

    companion object {
        /** Reserved absolute prefix for the authorized shared tree. */
        const val PREFIX = "/shared"
        private const val NOT_AUTHORIZED = """{"error":"shared_not_authorized"}"""
    }
}
