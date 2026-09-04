package com.zenwayne.zenagent.tools

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * P3 routing (spec §8-P3): one flat path space for the model, two very
 * different backends underneath. Anything under `/shared` goes to the
 * user-authorized SAF tree; everything else stays in the sandbox.
 */
class FsRouterTest {

    private class Spy(val tag: String) : FsBackend {
        val calls = mutableListOf<String>()
        override fun read(path: String): String {
            calls += "read:$path"
            return """{"backend":"$tag","path":"$path"}"""
        }
        override fun write(path: String, content: String): String {
            calls += "write:$path"
            return """{"backend":"$tag","path":"$path","content":"$content"}"""
        }
        override fun list(path: String): String {
            calls += "list:$path"
            return """{"backend":"$tag","path":"$path"}"""
        }
    }

    private fun router(shared: FsBackend? = null): Triple<FsRouter, Spy, Spy?> {
        val ws = Spy("ws")
        return Triple(FsRouter(ws) { shared }, ws, shared as? Spy)
    }

    @Test
    fun plainPathsGoToTheSandbox() {
        val (r, ws, _) = router()
        assertEquals("""{"backend":"ws","path":"notes.txt"}""", r.read("notes.txt"))
        assertEquals(listOf("read:notes.txt"), ws.calls)
    }

    @Test
    fun absolutePathsStillGoToTheSandbox() {
        val (r, ws, _) = router()
        r.read("/notes.txt")
        assertEquals(listOf("read:/notes.txt"), ws.calls)
    }

    @Test
    fun sharedPrefixIsStrippedAndRoutedToTheSafTree() {
        val shared = Spy("shared")
        val (r, ws, _) = router(shared)
        assertEquals("""{"backend":"shared","path":"docs/a.txt"}""", r.read("/shared/docs/a.txt"))
        assertEquals(listOf("read:docs/a.txt"), shared.calls)
        assertEquals(emptyList<String>(), ws.calls)
    }

    @Test
    fun bareSharedRootListsTheTreeItself() {
        val shared = Spy("shared")
        val (r, _, _) = router(shared)
        r.list("/shared")
        r.list("/shared/")
        assertEquals(listOf("list:.", "list:."), shared.calls)
    }

    @Test
    fun writesRouteAndCarryTheirContent() {
        val shared = Spy("shared")
        val (r, _, _) = router(shared)
        assertEquals(
            """{"backend":"shared","path":"a.txt","content":"hi"}""",
            r.write("/shared/a.txt", "hi"),
        )
    }

    /** Without an authorized tree the model gets a self-explanatory error. */
    @Test
    fun sharedWithoutAuthorizationIsAnErrorResult() {
        val (r, ws, _) = router(shared = null)
        assertEquals("""{"error":"shared_not_authorized"}""", r.read("/shared/a.txt"))
        assertEquals("""{"error":"shared_not_authorized"}""", r.write("/shared/a.txt", "x"))
        assertEquals("""{"error":"shared_not_authorized"}""", r.list("/shared"))
        assertEquals(emptyList<String>(), ws.calls)
    }

    /** `shared` is only a root when it is the leading absolute segment. */
    @Test
    fun aSandboxFolderNamedSharedIsNotTheSharedRoot() {
        val sharedBackend = Spy("shared")
        val (r, ws, _) = router(sharedBackend)
        r.read("shared/a.txt")
        r.read("/sharedstuff/a.txt")
        r.read("docs/shared/a.txt")
        assertEquals(
            listOf("read:shared/a.txt", "read:/sharedstuff/a.txt", "read:docs/shared/a.txt"),
            ws.calls,
        )
        assertEquals(emptyList<String>(), sharedBackend.calls)
    }

    @Test
    fun surroundingWhitespaceIsIgnoredWhenRouting() {
        val shared = Spy("shared")
        val (r, _, _) = router(shared)
        r.read("  /shared/a.txt  ")
        assertEquals(listOf("read:a.txt"), shared.calls)
    }

    /** The tree is looked up per call, so revoking access takes effect at once. */
    @Test
    fun authorizationIsResolvedPerCall() {
        var backend: FsBackend? = null
        val r = FsRouter(Spy("ws")) { backend }
        assertEquals("""{"error":"shared_not_authorized"}""", r.list("/shared"))
        backend = Spy("shared")
        assertEquals("""{"backend":"shared","path":"."}""", r.list("/shared"))
        backend = null
        assertEquals("""{"error":"shared_not_authorized"}""", r.list("/shared"))
    }
}
