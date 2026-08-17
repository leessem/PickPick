package com.leessem.pickpick

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LottoDrawCodecTest {

    private fun draw(round: Int = 1234) = LottoDrawResult(
        round = round,
        drawDate = 1784937600000L,
        winningNumbers = listOf(1, 15, 19, 31, 35, 43),
        bonusNumber = 27
    )

    // 1: a single draw round-trips through encode/decode
    @Test
    fun `a single draw round-trips through encode and decode`() {
        val encoded = LottoDrawCodec.encode(listOf(draw()))
        val decoded = LottoDrawCodec.decode(encoded)

        assertEquals(listOf(draw()), decoded)
    }

    // 2: winningNumbers specifically survive the round-trip
    @Test
    fun `winningNumbers are restored correctly`() {
        val decoded = LottoDrawCodec.decode(LottoDrawCodec.encode(listOf(draw())))

        assertEquals(listOf(1, 15, 19, 31, 35, 43), decoded.single().winningNumbers)
    }

    // 3: bonusNumber specifically survives the round-trip
    @Test
    fun `bonusNumber is restored correctly`() {
        val decoded = LottoDrawCodec.decode(LottoDrawCodec.encode(listOf(draw())))

        assertEquals(27, decoded.single().bonusNumber)
    }

    // 4: round specifically survives the round-trip
    @Test
    fun `round is restored correctly`() {
        val decoded = LottoDrawCodec.decode(LottoDrawCodec.encode(listOf(draw(round = 1234))))

        assertEquals(1234, decoded.single().round)
    }

    // 5: multiple draws round-trip together
    @Test
    fun `multiple draws round-trip together`() {
        val draws = listOf(draw(1235), draw(1234), draw(1233))
        val decoded = LottoDrawCodec.decode(LottoDrawCodec.encode(draws))

        assertEquals(setOf(1235, 1234, 1233), decoded.map { it.round }.toSet())
    }

    // 12: null/empty raw input decodes to an empty list, no crash
    @Test
    fun `null or empty raw input decodes to an empty list`() {
        assertEquals(emptyList<LottoDrawResult>(), LottoDrawCodec.decode(null))
        assertEquals(emptyList<LottoDrawResult>(), LottoDrawCodec.decode(""))
    }

    // 13: a malformed entry is dropped instead of crashing the whole decode
    @Test
    fun `a malformed entry is dropped rather than crashing decode`() {
        val good = draw(1234)
        val raw = LottoDrawCodec.encode(listOf(good)) + ";" + "not-a-valid-entry-at-all"

        val decoded = LottoDrawCodec.decode(raw)

        assertEquals(listOf(good), decoded)
    }

    @Test
    fun `an entry whose numbers fail LottoDrawResult validation is dropped`() {
        // Hand-crafted entry with a duplicate winning number (6 fields but only 5 distinct) —
        // decodeOne's try/catch around the LottoDrawResult constructor must catch its init{}
        // IllegalArgumentException and drop this record instead of propagating.
        val invalidEntry = "1234:1784937600000:1,1,19,31,35,43:27"

        assertNull(LottoDrawCodec.decode(invalidEntry).firstOrNull { it.round == 1234 })
    }
}
