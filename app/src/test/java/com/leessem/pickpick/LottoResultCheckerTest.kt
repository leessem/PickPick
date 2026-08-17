package com.leessem.pickpick

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class LottoResultCheckerTest {

    private val draw = LottoDrawResult(
        round = 1234,
        drawDate = 1_755_000_000_000L,
        winningNumbers = listOf(1, 2, 3, 4, 5, 6),
        bonusNumber = 7
    )

    @Test
    fun `all six numbers matched is first place`() {
        val result = LottoResultChecker.check(LottoGame(listOf(1, 2, 3, 4, 5, 6)), draw)

        assertEquals(LottoRank.FIRST, result.rank)
        assertEquals(6, result.matchedCount)
    }

    @Test
    fun `five matched plus bonus is second place`() {
        val result = LottoResultChecker.check(LottoGame(listOf(1, 2, 3, 4, 5, 7)), draw)

        assertEquals(LottoRank.SECOND, result.rank)
        assertEquals(5, result.matchedCount)
        assertTrue(result.bonusMatched)
    }

    @Test
    fun `five matched without bonus is third place`() {
        val result = LottoResultChecker.check(LottoGame(listOf(1, 2, 3, 4, 5, 8)), draw)

        assertEquals(LottoRank.THIRD, result.rank)
        assertEquals(5, result.matchedCount)
        assertFalse(result.bonusMatched)
    }

    @Test
    fun `four matched is fourth place`() {
        val result = LottoResultChecker.check(LottoGame(listOf(1, 2, 3, 4, 8, 9)), draw)

        assertEquals(LottoRank.FOURTH, result.rank)
        assertEquals(4, result.matchedCount)
    }

    @Test
    fun `three matched is fifth place`() {
        val result = LottoResultChecker.check(LottoGame(listOf(1, 2, 3, 8, 9, 10)), draw)

        assertEquals(LottoRank.FIFTH, result.rank)
        assertEquals(3, result.matchedCount)
    }

    @Test
    fun `two matched is not a win`() {
        val result = LottoResultChecker.check(LottoGame(listOf(1, 2, 8, 9, 10, 11)), draw)

        assertEquals(LottoRank.NONE, result.rank)
        assertEquals(2, result.matchedCount)
    }

    @Test
    fun `one matched is not a win`() {
        val result = LottoResultChecker.check(LottoGame(listOf(1, 8, 9, 10, 11, 12)), draw)

        assertEquals(LottoRank.NONE, result.rank)
        assertEquals(1, result.matchedCount)
    }

    @Test
    fun `zero matched is not a win`() {
        val result = LottoResultChecker.check(LottoGame(listOf(8, 9, 10, 11, 12, 13)), draw)

        assertEquals(LottoRank.NONE, result.rank)
        assertEquals(0, result.matchedCount)
    }

    @Test
    fun `bonus matched with only four winning numbers is not second place`() {
        val result = LottoResultChecker.check(LottoGame(listOf(1, 2, 3, 4, 7, 9)), draw)

        assertEquals(LottoRank.FOURTH, result.rank)
        assertEquals(4, result.matchedCount)
        assertTrue(result.bonusMatched)
    }

    @Test
    fun `LottoDrawResult rejects a winning list that is not exactly six numbers`() {
        assertThrows(IllegalArgumentException::class.java) {
            LottoDrawResult(1, 0L, listOf(1, 2, 3, 4, 5), 6)
        }
    }

    @Test
    fun `LottoDrawResult rejects winning numbers outside 1 to 45`() {
        assertThrows(IllegalArgumentException::class.java) {
            LottoDrawResult(1, 0L, listOf(1, 2, 3, 4, 5, 46), 6)
        }
    }

    @Test
    fun `LottoDrawResult rejects duplicate winning numbers`() {
        assertThrows(IllegalArgumentException::class.java) {
            LottoDrawResult(1, 0L, listOf(1, 1, 3, 4, 5, 6), 7)
        }
    }

    @Test
    fun `LottoDrawResult rejects unsorted winning numbers`() {
        assertThrows(IllegalArgumentException::class.java) {
            LottoDrawResult(1, 0L, listOf(6, 5, 4, 3, 2, 1), 7)
        }
    }

    @Test
    fun `LottoDrawResult rejects a bonus number that duplicates a winning number`() {
        assertThrows(IllegalArgumentException::class.java) {
            LottoDrawResult(1, 0L, listOf(1, 2, 3, 4, 5, 6), 6)
        }
    }

    @Test
    fun `LottoDrawResult rejects a bonus number outside 1 to 45`() {
        assertThrows(IllegalArgumentException::class.java) {
            LottoDrawResult(1, 0L, listOf(1, 2, 3, 4, 5, 6), 0)
        }
    }

    private fun sampleSet(stage3Games: List<LottoGame>) = GenerationSet(
        id = "set-1",
        lottoRound = 1234,
        createdAt = 1L,
        stage1Games = listOf(
            LottoGame(listOf(1, 2, 3, 4, 5, 6)),       // FIRST
            LottoGame(listOf(1, 2, 3, 4, 5, 7)),       // SECOND
            LottoGame(listOf(1, 2, 3, 4, 5, 8)),       // THIRD
            LottoGame(listOf(1, 2, 3, 4, 8, 9)),       // FOURTH
            LottoGame(listOf(1, 2, 3, 8, 9, 10))       // FIFTH
        ),
        stage2Games = listOf(
            LottoGame(listOf(8, 9, 10, 11, 12, 13)),   // NONE
            LottoGame(listOf(1, 2, 3, 4, 5, 6))        // FIRST
        ),
        stage3Games = stage3Games
    )

    private val sampleStage3 = listOf(
        LottoGame(listOf(1, 8, 9, 10, 11, 12)),    // NONE
        LottoGame(listOf(1, 2, 8, 9, 10, 11)),     // NONE
        LottoGame(listOf(1, 2, 3, 4, 5, 7))        // SECOND
    )

    @Test
    fun `checking a generation set evaluates all five stage1 games independently`() {
        val result = LottoResultChecker.check(sampleSet(stage3Games = emptyList()), draw)

        assertEquals(5, result.stage1Results.size)
        assertEquals(
            listOf(LottoRank.FIRST, LottoRank.SECOND, LottoRank.THIRD, LottoRank.FOURTH, LottoRank.FIFTH),
            result.stage1Results.map { it.rank }
        )
    }

    @Test
    fun `checking a generation set evaluates both stage2 games independently`() {
        val result = LottoResultChecker.check(sampleSet(stage3Games = emptyList()), draw)

        assertEquals(2, result.stage2Results.size)
        assertEquals(
            listOf(LottoRank.NONE, LottoRank.FIRST),
            result.stage2Results.map { it.rank }
        )
    }

    @Test
    fun `checking a generation set evaluates all three stage3 games independently`() {
        val result = LottoResultChecker.check(sampleSet(stage3Games = sampleStage3), draw)

        assertEquals(3, result.stage3Results.size)
        assertEquals(
            listOf(LottoRank.NONE, LottoRank.NONE, LottoRank.SECOND),
            result.stage3Results.map { it.rank }
        )
    }

    @Test
    fun `checking a generation set with no stage3 games yet leaves stage3Results empty`() {
        val result = LottoResultChecker.check(sampleSet(stage3Games = emptyList()), draw)

        assertNotNull(result.stage3Results)
        assertEquals(0, result.stage3Results.size)
        // Stage1/stage2 results must still be computed normally.
        assertEquals(5, result.stage1Results.size)
        assertEquals(2, result.stage2Results.size)
    }
}
