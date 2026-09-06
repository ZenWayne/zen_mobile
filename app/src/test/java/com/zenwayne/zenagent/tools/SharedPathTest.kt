package com.zenwayne.zenagent.tools

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Containment for the SAF root (spec §8-P3).
 *
 * `DocumentFile` has no canonical path to prefix-check against, so escapes
 * cannot be caught the way [FsWorkspace] catches them. Instead the path is
 * split into plain segments and anything that could walk upward — or that the
 * SAF tree would interpret structurally — is rejected before any traversal.
 */
class SharedPathTest {

    private fun ok(path: String): List<String> {
        val r = sharedSegments(path)
        assertTrue("expected $path to be accepted, got $r", r.isSuccess)
        return r.getOrThrow()
    }

    private fun rejected(path: String) {
        assertTrue("expected $path to be rejected", sharedSegments(path).isFailure)
    }

    @Test
    fun splitsAPlainRelativePath() {
        assertEquals(listOf("docs", "a.txt"), ok("docs/a.txt"))
    }

    @Test
    fun theRootIsAnEmptySegmentList() {
        assertEquals(emptyList<String>(), ok("."))
        assertEquals(emptyList<String>(), ok(""))
        assertEquals(emptyList<String>(), ok("/"))
    }

    @Test
    fun redundantSeparatorsAndDotsCollapse() {
        assertEquals(listOf("docs", "a.txt"), ok("/docs//./a.txt/"))
    }

    @Test
    fun parentTraversalIsRejected() {
        rejected("..")
        rejected("../a.txt")
        rejected("docs/../../a.txt")
        rejected("docs/..")
    }

    /** A literal `..` cannot be smuggled in as a name, either. */
    @Test
    fun encodedOrPaddedParentSegmentsAreRejected() {
        rejected("docs/ ../a.txt")
        rejected("docs/.. /a.txt")
    }

    @Test
    fun nulBytesAreRejected() {
        rejected("docs/a\u0000.txt")
    }

    @Test
    fun ordinaryDottedNamesAreStillFine() {
        assertEquals(listOf(".hidden"), ok(".hidden"))
        assertEquals(listOf("a..b.txt"), ok("a..b.txt"))
        assertEquals(listOf("...", "x"), ok(".../x"))
    }

    @Test
    fun deeplyNestedPathsSurviveIntact() {
        assertEquals(listOf("a", "b", "c", "d.txt"), ok("a/b/c/d.txt"))
    }
}
