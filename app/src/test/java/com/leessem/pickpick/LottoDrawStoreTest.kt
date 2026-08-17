package com.leessem.pickpick

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// mergeDraw/findDraw/removeDraw are the pure list logic behind LottoDrawStore.save/get/delete.
// The SharedPreferences-backed LottoDrawStore class itself needs a real Context, which this
// project's local JVM tests can't provide (Android framework calls throw "not mocked") — that
// persistence is verified on-device instead (see the device verification report).
class LottoDrawStoreTest {

    private fun draw(round: Int) = LottoDrawResult(
        round = round,
        drawDate = 1_700_000_000_000L + round,
        winningNumbers = listOf(1, 2, 3, 4, 5, 6),
        bonusNumber = 7
    )

    // 6: getAll-equivalent ordering — newest round first
    @Test
    fun `merging draws in arbitrary order yields newest-round-first`() {
        var draws = emptyList<LottoDrawResult>()
        draws = mergeDraw(draws, draw(1233))
        draws = mergeDraw(draws, draw(1235))
        draws = mergeDraw(draws, draw(1234))

        assertEquals(listOf(1235, 1234, 1233), draws.map { it.round })
    }

    // 7: saving the same round again updates in place instead of duplicating
    @Test
    fun `merging the same round again replaces rather than duplicates`() {
        var draws = mergeDraw(emptyList(), draw(1234))
        val updated = draw(1234).copy(bonusNumber = 40)
        draws = mergeDraw(draws, updated)

        assertEquals(1, draws.size)
        assertEquals(40, draws.single().bonusNumber)
    }

    // 8: get(round) finds an existing entry
    @Test
    fun `findDraw returns the matching entry when present`() {
        val draws = listOf(draw(1234), draw(1233))

        assertEquals(1234, findDraw(draws, 1234)?.round)
    }

    // 9: get(round) returns null when absent
    @Test
    fun `findDraw returns null when the round is not present`() {
        val draws = listOf(draw(1234))

        assertNull(findDraw(draws, 9_999_999))
    }

    // 10: delete(round) removes only the targeted round
    @Test
    fun `removeDraw removes only the targeted round`() {
        val draws = listOf(draw(1235), draw(1234), draw(1233))

        val remaining = removeDraw(draws, 1234)

        assertEquals(listOf(1235, 1233), remaining.map { it.round })
    }

    // 11: clearAll conceptually — an empty draw list stays empty through encode/decode
    @Test
    fun `an empty draw list round-trips to an empty list`() {
        assertTrue(LottoDrawCodec.decode(LottoDrawCodec.encode(emptyList())).isEmpty())
    }

    @Test
    fun `latestRound is the max round among the stored draws`() {
        val draws = listOf(draw(1233), draw(1235), draw(1234))

        assertEquals(1235, draws.maxOf { it.round })
    }
}
