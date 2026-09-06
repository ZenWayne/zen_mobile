package com.zenwayne.zenagent.tools

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Tool arguments arrive as a JSON object string from the constrained decoder.
 * String values are JSON-escaped, so extraction must *decode* them — writing
 * a literal `\n` into a file (or handing it to Python) is a bug.
 */
class JsonArgsTest {

    @Test
    fun extractsAPlainPath() {
        assertEquals("notes.txt", extractPath("""{"path":"notes.txt"}"""))
    }

    @Test
    fun missingKeyYieldsNull() {
        assertNull(extractPath("""{"content":"x"}"""))
        assertNull(extractContent("""{"path":"x"}"""))
        assertNull(extractCode("""{"path":"x"}"""))
    }

    @Test
    fun decodesEscapedNewlinesInContent() {
        assertEquals("line1\nline2\n", extractContent("""{"content":"line1\nline2\n"}"""))
    }

    @Test
    fun decodesQuotesTabsAndBackslashes() {
        assertEquals(
            "say \"hi\"\tC:\\tmp",
            extractContent("""{"content":"say \"hi\"\tC:\\tmp"}"""),
        )
    }

    @Test
    fun decodesUnicodeEscapes() {
        // Built with an explicit backslash so the Kotlin lexer cannot eat the
        // \\uXXXX sequence before the decoder under test sees it.
        val bs = '\\'
        val args = "{\"content\":\"h${bs}u00e9llo ${bs}u4e16${bs}u754c\"}"
        assertEquals("héllo 世界", extractContent(args))
    }

    /** Literal (unescaped) UTF-8 passes through untouched. */
    @Test
    fun keepsLiteralUtf8() {
        assertEquals("héllo 世界", extractContent("""{"content":"héllo 世界"}"""))
    }

    @Test
    fun decodesEscapedForwardSlashesInPaths() {
        assertEquals("a/b.txt", extractPath("""{"path":"a\/b.txt"}"""))
    }

    @Test
    fun decodesCarriageReturnsAndFormFeeds() {
        assertEquals("a\r\nb\u000C", extractContent("""{"content":"a\r\nb\f"}"""))
    }

    @Test
    fun extractsMultiLinePythonCode() {
        val args = """{"code":"import math\nprint(math.sqrt(16))"}"""
        assertEquals("import math\nprint(math.sqrt(16))", extractCode(args))
    }

    /** An unknown escape keeps the escaped character rather than dropping it. */
    @Test
    fun unknownEscapeKeepsTheCharacter() {
        assertEquals("a?b", extractContent("""{"content":"a\?b"}"""))
    }

    /** Key order must not matter — `content` after `code`, `path` after both. */
    @Test
    fun keysAreFoundRegardlessOfOrder() {
        val args = """{"code":"pass","content":"c","path":"p"}"""
        assertEquals("pass", extractCode(args))
        assertEquals("c", extractContent(args))
        assertEquals("p", extractPath(args))
    }
}
