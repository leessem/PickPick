package com.leessem.pickpick

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class LottoResultDisplayTest {

    private fun result(rank: LottoRank, matchedCount: Int, bonusMatched: Boolean = false) =
        LottoCheckResult(rank = rank, matchedCount = matchedCount, bonusMatched = bonusMatched)

    // stage1: one of each non-NONE rank; stage2: all NONE; final: NONE -> 10 games total.
    private fun allRanksCheckResult(finalResult: LottoCheckResult?) = GenerationCheckResult(
        stage1Results = listOf(
            result(LottoRank.FIRST, 6),
            result(LottoRank.SECOND, 5, bonusMatched = true),
            result(LottoRank.THIRD, 5),
            result(LottoRank.FOURTH, 4),
            result(LottoRank.FIFTH, 3)
        ),
        stage2Results = listOf(
            result(LottoRank.NONE, 2),
            result(LottoRank.NONE, 1),
            result(LottoRank.NONE, 0),
            result(LottoRank.NONE, 2)
        ),
        finalResult = finalResult
    )

    private fun allNoneCheckResult() = GenerationCheckResult(
        stage1Results = List(5) { result(LottoRank.NONE, 1) },
        stage2Results = List(4) { result(LottoRank.NONE, 0) },
        finalResult = result(LottoRank.NONE, 2)
    )

    // 1: full 10-game summary computes correctly
    @Test
    fun `summarizes ten games (5 stage1 + 4 stage2 + 1 final) correctly`() {
        val summary = LottoResultSummarizer.summarize(allRanksCheckResult(result(LottoRank.NONE, 1)))

        assertEquals(10, summary.totalGames)
    }

    // 2 & 3: stage1/stage2 counts feed into the total (implicitly proven by the 9 vs 10 split below)
    @Test
    fun `stage1 and stage2 results are both included in the total`() {
        val summary = LottoResultSummarizer.summarize(allRanksCheckResult(finalResult = null))

        // 5 stage1 + 4 stage2, no final -> 9
        assertEquals(9, summary.totalGames)
    }

    // 4: final game present is counted
    @Test
    fun `a present final game is counted in the total`() {
        val summary = LottoResultSummarizer.summarize(allRanksCheckResult(result(LottoRank.NONE, 0)))

        assertEquals(10, summary.totalGames)
    }

    // 5: final game null does not crash and yields 9 total
    @Test
    fun `a null final game is handled without error and yields nine games`() {
        val summary = LottoResultSummarizer.summarize(allRanksCheckResult(finalResult = null))

        assertEquals(9, summary.totalGames)
    }

    // 6-10: each rank is represented in the counts
    @Test
    fun `first through fifth place counts are each included`() {
        val summary = LottoResultSummarizer.summarize(allRanksCheckResult(result(LottoRank.NONE, 1)))

        assertEquals(1, summary.firstCount)
        assertEquals(1, summary.secondCount)
        assertEquals(1, summary.thirdCount)
        assertEquals(1, summary.fourthCount)
        assertEquals(1, summary.fifthCount)
    }

    // 11: all-NONE set summarizes to zero wins
    @Test
    fun `a set with no wins summarizes to all-none`() {
        val summary = LottoResultSummarizer.summarize(allNoneCheckResult())

        assertEquals(10, summary.totalGames)
        assertEquals(10, summary.noneCount)
        assertEquals(0, summary.firstCount)
        assertEquals(0, summary.secondCount)
        assertEquals(0, summary.thirdCount)
        assertEquals(0, summary.fourthCount)
        assertEquals(0, summary.fifthCount)
    }

    // 13-15: each fetch failure type maps to its own distinct, non-empty message
    @Test
    fun `NetworkError NotFound and InvalidData each produce a distinct non-empty message`() {
        val networkMessage = lottoDrawFetchErrorMessage(LottoDrawFetchResult.NetworkError("boom"))
        val notFoundMessage = lottoDrawFetchErrorMessage(LottoDrawFetchResult.NotFound(1234))
        val invalidMessage = lottoDrawFetchErrorMessage(LottoDrawFetchResult.InvalidData("bad data"))

        assertEquals(true, networkMessage.isNotBlank())
        assertEquals(true, notFoundMessage.isNotBlank())
        assertEquals(true, invalidMessage.isNotBlank())
        assertNotEquals(networkMessage, notFoundMessage)
        assertNotEquals(notFoundMessage, invalidMessage)
        assertNotEquals(networkMessage, invalidMessage)
    }

    @Test
    fun `rank label and match count text render as expected for a second place result`() {
        val secondPlace = result(LottoRank.SECOND, 5, bonusMatched = true)

        assertEquals("2등", lottoRankLabel(LottoRank.SECOND))
        assertEquals("2등", lottoRankDisplayText(secondPlace))
        assertEquals("5개 일치 + 보너스", lottoMatchCountText(secondPlace))
    }

    @Test
    fun `first place gets a celebratory display text`() {
        val firstPlace = result(LottoRank.FIRST, 6)

        assertEquals("🎉 1등", lottoRankDisplayText(firstPlace))
        assertEquals("6개 일치", lottoMatchCountText(firstPlace))
    }

    @Test
    fun `a non-win result labels as not-won without a match-count bonus suffix`() {
        val noWin = result(LottoRank.NONE, 2)

        assertEquals("미당첨", lottoRankDisplayText(noWin))
        assertEquals("2개 일치", lottoMatchCountText(noWin))
    }
}
