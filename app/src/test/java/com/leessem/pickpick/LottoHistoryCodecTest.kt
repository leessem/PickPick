package com.leessem.pickpick

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LottoHistoryCodecTest {

    @Test
    fun `decode of null returns empty list`() {
        assertTrue(LottoHistoryCodec.decode(null).isEmpty())
    }

    @Test
    fun `decode of empty string returns empty list`() {
        assertTrue(LottoHistoryCodec.decode("").isEmpty())
    }

    @Test
    fun `encode then decode round trips a single record`() {
        val record = LottoRecord(numbers = listOf(1, 12, 16, 18, 39, 45), timestamp = 1_700_000_000_000L)

        val decoded = LottoHistoryCodec.decode(LottoHistoryCodec.encode(listOf(record)))

        assertEquals(listOf(record), decoded)
    }

    @Test
    fun `encode then decode round trips multiple records preserving order`() {
        val records = listOf(
            LottoRecord(numbers = listOf(1, 2, 3, 4, 5, 6), timestamp = 300L),
            LottoRecord(numbers = listOf(10, 20, 30, 40, 41, 45), timestamp = 200L),
            LottoRecord(numbers = listOf(7, 8, 9, 10, 11, 12), timestamp = 100L)
        )

        val decoded = LottoHistoryCodec.decode(LottoHistoryCodec.encode(records))

        assertEquals(records, decoded)
    }

    @Test
    fun `encode of empty list decodes back to empty list`() {
        val decoded = LottoHistoryCodec.decode(LottoHistoryCodec.encode(emptyList()))

        assertTrue(decoded.isEmpty())
    }
}
