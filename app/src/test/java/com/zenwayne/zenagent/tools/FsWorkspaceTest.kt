package com.zenwayne.zenagent.tools

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.rules.TemporaryFolder

class FsWorkspaceTest {
    private val rootDir: File = TemporaryFolder().apply { create() }.root

    private fun ws() = FsWorkspace(rootDir)

    @Test
    fun pathEscapeIsRejected() {
        val r = ws().resolve("../../etc/passwd")
        assertTrue(r.isFailure)
    }

    @Test
    fun absolutePathResolvesInsideRoot() {
        val f = ws().resolve("/tmp/evil.txt")
        assertTrue(f.isSuccess)
        assertTrue(f.getOrThrow().path.startsWith(rootDir.canonicalPath))
    }

    @Test
    fun writeThenReadRoundTrips() {
        val w = ws().write("a/b.txt", "hello")
        assertTrue("write should succeed: $w", w.contains("\"ok\""))
        val r = ws().read("a/b.txt")
        assertTrue("read should return content: $r", r.contains("hello"))
    }

    @Test
    fun binaryReadIsRejected() {
        File(rootDir, "bin.dat").writeBytes(byteArrayOf(0, 1, 2, 3))
        assertTrue(ws().read("bin.dat").contains("binary"))
    }

    @Test
    fun readOverCapIsRejected() {
        File(rootDir, "big.txt").writeBytes(ByteArray(FsLimits.READ_CAP_BYTES + 1) { 'a'.code.toByte() })
        assertTrue(ws().read("big.txt").contains("too_large"))
    }

    @Test
    fun listReturnsEntries() {
        File(rootDir, "x.txt").writeText("x")
        File(rootDir, "sub").mkdirs()
        val out = ws().list(".")
        assertTrue("list should include both: $out", out.contains("x.txt") && out.contains("sub"))
    }
}
