package org.bohme.tracker.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FieldColorTest {
    @Test
    fun `parseColorHex six and eight digit tableau blue`() {
        val expected = 0xFF1F77B4.toInt()
        assertEquals(expected, parseColorHex("#1F77B4"))
        assertEquals(expected, parseColorHex("#FF1F77B4"))
        assertEquals(expected, parseColorHex("#1f77b4"))
        assertEquals(expected, parseColorHex("#ff1f77b4"))
    }

    @Test
    fun `parseColorHex rejects garbage empty and non hex`() {
        assertNull(parseColorHex(""))
        assertNull(parseColorHex("garbage"))
        assertNull(parseColorHex("#GG0000"))
        assertNull(parseColorHex("1F77B4"))
        assertNull(parseColorHex("#1F77B"))
        assertNull(parseColorHex("#1F77B4F"))
        assertNull(parseColorHex("#"))
        assertNull(parseColorHex("#1F77B4 "))
    }

    @Test
    fun `formatColorHex is six digit uppercase ignoring alpha`() {
        assertEquals("#1F77B4", formatColorHex(0xFF1F77B4.toInt()))
        assertEquals("#1F77B4", formatColorHex(0x801F77B4.toInt()))
        assertEquals("#FF0000", formatColorHex(0xFFFF0000.toInt()))
        assertEquals("#1F77B4", formatColorHex(parseColorHex("#1F77B4")!!))
    }

    @Test
    fun `lightenArgb default t makes red strictly lighter and keeps alpha`() {
        val input = 0xFFFF0000.toInt()
        val out = lightenArgb(input)
        assertNotEquals(input, out)
        assertEquals(255, (out ushr 24) and 0xFF)
        assertEquals(255, (out ushr 16) and 0xFF)
        assertTrue(((out ushr 8) and 0xFF) > 0)
        assertTrue((out and 0xFF) > 0)
        assertEquals(lightenArgb(input, 0.45f), out)
    }

    @Test
    fun `lightenArgb t 0 is identity and t 1 is white with same alpha`() {
        val red = 0xFFFF0000.toInt()
        assertEquals(red, lightenArgb(red, 0f))
        assertEquals(0xFFFFFFFF.toInt(), lightenArgb(red, 1f))
        val translucent = 0x80FF0000.toInt()
        assertEquals(translucent, lightenArgb(translucent, 0f))
        val whiteSameAlpha = lightenArgb(translucent, 1f)
        assertEquals(0x80, (whiteSameAlpha ushr 24) and 0xFF)
        assertEquals(255, (whiteSameAlpha ushr 16) and 0xFF)
        assertEquals(255, (whiteSameAlpha ushr 8) and 0xFF)
        assertEquals(255, whiteSameAlpha and 0xFF)
    }
}
