package com.zenwayne.zenagent.tools

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The `/shared` backend's traversal and policy decisions, driven through an
 * in-memory [DocNode] tree (spec §8-P3).
 *
 * This is the half that can go wrong: which segment has to be a directory,
 * when a missing one gets created, the read/list caps, binary detection, and
 * turning every provider failure into a result string the model can act on.
 * The `DocumentFile`/`ContentResolver` calls underneath are a thin adapter.
 */
class SafSharedStorageTest {

    /** In-memory document tree. A node is a directory iff [children] is non-null. */
    private class FakeDoc(
        override val name: String,
        var bytes: ByteArray? = null,
        val kids: MutableMap<String, FakeDoc>? = null,
        /** Overrides the reported size, to mimic a provider that misreports. */
        var reportedLength: Long? = null,
        var readable: Boolean = true,
        var writable: Boolean = true,
        var canCreate: Boolean = true,
        /** Forces the type flags to disagree with [kids], as a rogue provider can. */
        var claimsToBeADirectory: Boolean? = null,
    ) : DocNode {
        override val isFile get() = !isDirectory
        override val isDirectory get() = claimsToBeADirectory ?: (kids != null)
        override val length get() = reportedLength ?: (bytes?.size?.toLong() ?: 0L)

        override fun child(name: String) = kids?.get(name)
        override fun children() = kids?.values?.toList() ?: emptyList()

        override fun createFile(name: String): DocNode? {
            if (!canCreate || kids == null) return null
            return FakeDoc(name, bytes = ByteArray(0)).also { kids[name] = it }
        }

        override fun createDirectory(name: String): DocNode? {
            if (!canCreate || kids == null) return null
            return FakeDoc(name, kids = mutableMapOf()).also { kids[name] = it }
        }

        override fun readBytes(): ByteArray? = if (readable) bytes else null

        override fun writeBytes(bytes: ByteArray): Boolean {
            if (!writable) return false
            this.bytes = bytes
            return true
        }
    }

    private fun dir(name: String, vararg children: FakeDoc) = FakeDoc(
        name,
        kids = children.associateBy { it.name }.toMutableMap(),
    )

    private fun file(name: String, content: String) =
        FakeDoc(name, bytes = content.toByteArray(Charsets.UTF_8))

    private fun storage(root: FakeDoc?) = SafSharedStorage { root }

    // ── read ────────────────────────────────────────────────────────────────

    @Test
    fun readsAFileAtTheRoot() {
        val s = storage(dir("tree", file("a.txt", "hello")))
        assertEquals("""{"content":"hello","bytes":5}""", s.read("a.txt"))
    }

    @Test
    fun readsAFileNestedSeveralLevelsDown() {
        val s = storage(dir("tree", dir("docs", dir("sub", file("deep.txt", "found me")))))
        assertEquals("""{"content":"found me","bytes":8}""", s.read("docs/sub/deep.txt"))
    }

    @Test
    fun readEscapesJsonInTheContent() {
        val s = storage(dir("tree", file("a.txt", "line1\nsay \"hi\"")))
        assertEquals("""{"content":"line1\nsay \"hi\"","bytes":14}""", s.read("a.txt"))
    }

    @Test
    fun readCountsBytesNotCharacters() {
        val s = storage(dir("tree", file("a.txt", "世界")))
        assertEquals("""{"content":"世界","bytes":6}""", s.read("a.txt"))
    }

    @Test
    fun readingAMissingFileIsNotFound() {
        assertEquals("""{"error":"not_found"}""", storage(dir("tree")).read("nope.txt"))
    }

    @Test
    fun readingADirectoryIsNotAFile() {
        val s = storage(dir("tree", dir("docs")))
        assertEquals("""{"error":"not_a_file"}""", s.read("docs"))
    }

    @Test
    fun readOverTheCapIsRejectedByReportedLength() {
        val big = FakeDoc("big.txt", bytes = ByteArray(10), reportedLength = (FsLimits.READ_CAP_BYTES + 1).toLong())
        assertEquals("""{"error":"too_large"}""", storage(dir("tree", big)).read("big.txt"))
    }

    /** A provider that under-reports its size must not slip past the cap. */
    @Test
    fun readOverTheCapIsRejectedEvenWhenLengthUnderreports() {
        val lying = FakeDoc(
            "big.txt",
            bytes = ByteArray(FsLimits.READ_CAP_BYTES + 1) { 'a'.code.toByte() },
            reportedLength = 1L,
        )
        assertEquals("""{"error":"too_large"}""", storage(dir("tree", lying)).read("big.txt"))
    }

    @Test
    fun binaryContentIsRejected() {
        val bin = FakeDoc("b.bin", bytes = byteArrayOf(1, 2, 0, 3))
        assertEquals("""{"error":"binary_file"}""", storage(dir("tree", bin)).read("b.bin"))
    }

    @Test
    fun anUnreadableDocumentIsAReadFailure() {
        val f = file("a.txt", "hi").apply { readable = false }
        assertEquals("""{"error":"read_failed"}""", storage(dir("tree", f)).read("a.txt"))
    }

    @Test
    fun readingThroughAFileAsIfItWereADirectoryIsNotFound() {
        val s = storage(dir("tree", file("a.txt", "hi")))
        assertEquals("""{"error":"not_found"}""", s.read("a.txt/inner.txt"))
    }

    /**
     * SAF providers are third-party apps, so the type flags cannot be trusted
     * to agree with what the tree actually resolves. A node that denies being a
     * directory must not be traversed through, even if it hands back children.
     */
    @Test
    fun aNonDirectoryIsNotTraversedThroughEvenIfItResolvesChildren() {
        val rogue = FakeDoc(
            "a.txt",
            kids = mutableMapOf("inner.txt" to file("inner.txt", "secret")),
            claimsToBeADirectory = false,
        )
        val s = storage(dir("tree", rogue))
        assertEquals("""{"error":"not_found"}""", s.read("a.txt/inner.txt"))
    }

    @Test
    fun readRejectsAnEscapingPath() {
        assertEquals("""{"error":"invalid_path"}""", storage(dir("tree")).read("../outside.txt"))
    }

    /** A revoked grant makes the whole tree unresolvable, not a crash. */
    @Test
    fun readWithNoResolvableTreeIsNotFound() {
        assertEquals("""{"error":"not_found"}""", storage(null).read("a.txt"))
    }

    // ── write ───────────────────────────────────────────────────────────────

    @Test
    fun writesANewFile() {
        val root = dir("tree")
        assertEquals("""{"ok":true,"bytes":5}""", storage(root).write("a.txt", "hello"))
        assertEquals("hello", String(root.child("a.txt")!!.readBytes()!!, Charsets.UTF_8))
    }

    @Test
    fun writeOverwritesAnExistingFileInPlace() {
        val existing = file("a.txt", "old content")
        val root = dir("tree", existing)
        assertEquals("""{"ok":true,"bytes":3}""", storage(root).write("a.txt", "new"))
        assertEquals("new", String(existing.readBytes()!!, Charsets.UTF_8))
        // Overwrite, not a second document with the same name.
        assertEquals(1, root.children().size)
    }

    @Test
    fun writeCreatesMissingParentDirectories() {
        val root = dir("tree")
        assertEquals("""{"ok":true,"bytes":2}""", storage(root).write("a/b/c.txt", "hi"))
        val leaf = root.child("a")?.child("b")?.child("c.txt")
        assertEquals("hi", String(leaf!!.readBytes()!!, Charsets.UTF_8))
    }

    @Test
    fun writeReusesExistingParentDirectories() {
        val docs = dir("docs", file("keep.txt", "keep"))
        val root = dir("tree", docs)
        storage(root).write("docs/new.txt", "x")
        assertEquals(setOf("keep.txt", "new.txt"), docs.children().map { it.name }.toSet())
    }

    @Test
    fun writeCountsBytesNotCharacters() {
        assertEquals("""{"ok":true,"bytes":6}""", storage(dir("tree")).write("a.txt", "世界"))
    }

    @Test
    fun writingOntoADirectoryIsRejected() {
        val root = dir("tree", dir("docs"))
        assertEquals("""{"error":"not_a_file"}""", storage(root).write("docs", "x"))
    }

    @Test
    fun writeToTheRootItselfIsRejected() {
        assertEquals("""{"error":"not_a_file"}""", storage(dir("tree")).write(".", "x"))
    }

    @Test
    fun writeRejectsAnEscapingPath() {
        assertEquals("""{"error":"invalid_path"}""", storage(dir("tree")).write("../evil.txt", "x"))
    }

    @Test
    fun aProviderThatRefusesToCreateIsAWriteFailure() {
        val root = dir("tree").apply { canCreate = false }
        assertEquals("""{"error":"write_failed"}""", storage(root).write("a.txt", "x"))
    }

    @Test
    fun aProviderThatRefusesToWriteIsAWriteFailure() {
        val f = file("a.txt", "old").apply { writable = false }
        assertEquals("""{"error":"write_failed"}""", storage(dir("tree", f)).write("a.txt", "new"))
    }

    @Test
    fun writeWithNoResolvableTreeIsInvalidPath() {
        assertEquals("""{"error":"invalid_path"}""", storage(null).write("a.txt", "x"))
    }

    // ── list ────────────────────────────────────────────────────────────────

    @Test
    fun listsEntriesWithTypeAndSize() {
        val root = dir("tree", file("a.txt", "hello"), dir("docs"))
        val out = root.let { storage(it).list(".") }
        assertTrue(out, out.contains("""{"name":"a.txt","type":"file","size":5}"""))
        assertTrue(out, out.contains("""{"name":"docs","type":"dir","size":0}"""))
    }

    @Test
    fun listsASubdirectory() {
        val root = dir("tree", dir("docs", file("x.txt", "xy")))
        assertEquals("""{"entries":[{"name":"x.txt","type":"file","size":2}]}""", storage(root).list("docs"))
    }

    @Test
    fun anEmptyDirectoryListsAsNoEntries() {
        assertEquals("""{"entries":[]}""", storage(dir("tree")).list("."))
    }

    @Test
    fun listIsCappedAtTheEntryLimit() {
        val many = (1..FsLimits.LIST_CAP + 50).map { file("f$it.txt", "x") }.toTypedArray()
        val out = storage(dir("tree", *many)).list(".")
        assertEquals(FsLimits.LIST_CAP, out.split("\"name\"").size - 1)
    }

    @Test
    fun listingAFileIsNotADirectory() {
        val s = storage(dir("tree", file("a.txt", "hi")))
        assertEquals("""{"error":"not_a_directory"}""", s.list("a.txt"))
    }

    @Test
    fun listingAMissingDirectoryIsNotFound() {
        assertEquals("""{"error":"not_found"}""", storage(dir("tree")).list("nope"))
    }

    @Test
    fun listRejectsAnEscapingPath() {
        assertEquals("""{"error":"invalid_path"}""", storage(dir("tree")).list(".."))
    }

    @Test
    fun listWithNoResolvableTreeIsNotFound() {
        assertEquals("""{"error":"not_found"}""", storage(null).list("."))
    }

    // ── the tree is resolved per call ───────────────────────────────────────

    /** Revoking the grant must take effect on the very next call, not later. */
    @Test
    fun theTreeIsResolvedOnEveryCall() {
        var root: FakeDoc? = dir("tree", file("a.txt", "hi"))
        val s = SafSharedStorage { root }
        assertEquals("""{"content":"hi","bytes":2}""", s.read("a.txt"))
        root = null
        assertEquals("""{"error":"not_found"}""", s.read("a.txt"))
    }

    @Test
    fun listDoesNotCacheDirectoryContents() {
        val root = dir("tree")
        val s = storage(root)
        assertEquals("""{"entries":[]}""", s.list("."))
        s.write("a.txt", "x")
        assertEquals("""{"entries":[{"name":"a.txt","type":"file","size":1}]}""", s.list("."))
    }

    @Test
    fun writeThenReadRoundTripsThroughTheTree() {
        val s = storage(dir("tree"))
        s.write("notes/todo.txt", "buy milk")
        assertEquals("""{"content":"buy milk","bytes":8}""", s.read("notes/todo.txt"))
    }
}
