package com.grepdroid.data

import org.junit.Assert.assertEquals
import org.junit.Test

class RepeatedLinesTest {

    private fun collapse(vararg lines: String) = RepeatedLines.collapse(lines.joinToString("\n"))

    @Test
    fun `repeated single line collapses`() {
        assertEquals("A\nB", collapse("A", "A", "A", "B"))
    }

    @Test
    fun `repeated multi-line block collapses`() {
        assertEquals("A\nB\nC\nD", collapse("A", "B", "C", "B", "C", "B", "C", "D"))
    }

    @Test
    fun `non-adjacent repeats are kept`() {
        assertEquals("A\nB\nA", collapse("A", "B", "A"))
    }

    @Test
    fun `partial trailing repeat is kept`() {
        assertEquals("A\nB\nC\nB", collapse("A", "B", "C", "B"))
    }

    @Test
    fun `blank lines are ignored`() {
        assertEquals("A\nB", collapse("A", "", "B", "", "A", "", "B", ""))
    }

    @Test
    fun `whitespace differences do not block a repeat`() {
        assertEquals("hello world", collapse("hello world", "  hello world "))
    }

    @Test
    fun `empty text stays empty`() {
        assertEquals("", RepeatedLines.collapse(""))
    }
}
