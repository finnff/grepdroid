package com.grepdroid.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FtsQueryTest {

    @Test
    fun `single word becomes a prefix query with trailing star`() {
        assertEquals("hello*", FtsQuery.sanitize("hello"))
    }

    @Test
    fun `only the last token gets the trailing star`() {
        assertEquals("hello world*", FtsQuery.sanitize("hello world"))
    }

    @Test
    fun `apostrophe is stripped from a token`() {
        assertEquals("dont*", FtsQuery.sanitize("don't"))
    }

    @Test
    fun `asterisk inside a token is stripped`() {
        assertEquals("ab*", FtsQuery.sanitize("a*b"))
    }

    @Test
    fun `parentheses are stripped`() {
        assertEquals("test*", FtsQuery.sanitize("(test)"))
    }

    @Test
    fun `quotes are stripped`() {
        assertEquals("q*", FtsQuery.sanitize("\"q\""))
    }

    @Test
    fun `special chars are stripped from every token`() {
        assertEquals("hello world*", FtsQuery.sanitize("hello (world)"))
    }

    @Test
    fun `empty input returns null`() {
        assertNull(FtsQuery.sanitize(""))
    }

    @Test
    fun `whitespace-only input returns null`() {
        assertNull(FtsQuery.sanitize("   "))
    }

    @Test
    fun `special-chars-only input returns null`() {
        assertNull(FtsQuery.sanitize("***"))
    }

    @Test
    fun `mixed special-chars-only input returns null`() {
        assertNull(FtsQuery.sanitize("(\""))
    }

    @Test
    fun `tabs and newlines collapse like whitespace`() {
        assertEquals("a b*", FtsQuery.sanitize("a\tb"))
    }

    @Test
    fun `multiple spaces between words collapse`() {
        assertEquals("a b*", FtsQuery.sanitize("a   b"))
    }
}
