package com.grepdroid.data

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class ResamplerTest {

    private fun resampleInChunks(input: FloatArray, from: Int, to: Int, chunk: Int): FloatArray {
        val r = AudioDecoder.Resampler(from, to)
        val out = mutableListOf<Float>()
        var i = 0
        while (i < input.size) {
            val n = minOf(chunk, input.size - i)
            out += r.process(input.copyOfRange(i, i + n), n).toList()
            i += n
        }
        return out.toFloatArray()
    }

    @Test
    fun `same rate passes samples through`() {
        val input = floatArrayOf(0.1f, 0.2f, 0.3f)
        assertArrayEquals(input, AudioDecoder.Resampler(16000, 16000).process(input, 3), 0f)
    }

    @Test
    fun `downsampling by an integer factor averages each group`() {
        val input = floatArrayOf(1f, 3f, 5f, 7f, 2f, 4f)
        assertArrayEquals(floatArrayOf(2f, 6f, 3f), resampleInChunks(input, 32000, 16000, 6), 1e-6f)
    }

    @Test
    fun `chunk boundaries don't change the output`() {
        val input = FloatArray(44100) { kotlin.math.sin(it / 7.0).toFloat() }
        val whole = resampleInChunks(input, 44100, 16000, input.size)
        for (chunk in listOf(1, 13, 1024, 4097)) {
            assertArrayEquals("chunk=$chunk", whole, resampleInChunks(input, 44100, 16000, chunk), 1e-6f)
        }
        assertEquals(16000f, whole.size.toFloat(), 1f)
    }

    @Test
    fun `upsampling interpolates between neighbours`() {
        val out = resampleInChunks(floatArrayOf(0f, 1f, 2f, 3f), 8000, 16000, 1)
        assertArrayEquals(floatArrayOf(0f, 0.5f, 1f, 1.5f, 2f, 2.5f), out, 1e-6f)
    }
}
