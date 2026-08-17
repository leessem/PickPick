package com.leessem.pickpick

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class LottoResultDisplayTest {

    private fun result(rank: LottoRank, matchedCount: Int, bonusMatched: Boolean = false) =
        LottoCheckResult(rank = rank, matchedCount = matchedCount, bonusMatched = bonusMatched)

    // stage1: one of each non-NONE rank (5); stage2: all NONE (2); stage3: all NONE (3) -> 10 games total.
    private fun allRanksCheckResult() = GenerationCheckResult(
        stage1Results = listOf(
            result(LottoRank.FIRST, 6),
            result(LottoRank.SECOND, 5, bonusMatched = true),
            result(LottoRank.THIRD, 5),
            result(LottoRank.FOURTH, 4),
            result(LottoRank.FIFTH, 3)
        ),
        stage2Results = listOf(
            result(LottoRank.NONE, 2),
            result(LottoRank.NONE, 1)
        ),
        stage3Results = listOf(
            result(LottoRank.NONE, 0),
            result(LottoRank.NONE, 2),
            result(LottoRank.NONE, 1)
        )
    )

    private fun allNoneCheckResult() = GenerationCheckResult(
        stage1Results = List(5) { result(LottoRank.NONE, 1) },
        stage2Results = List(2) { result(LottoRank.NONE, 0) },
        stage3Results = List(3) { result(LottoRank.NONE, 2) }
    )

    // 1: full 10-game (5 stage1 + 2 stage2 + 3 stage3) summary computes correctly
    @Test
    fun `summarizes ten games (5 stage1 + 2 stage2 + 3 stage3) correctly`() {
        val summary = LottoResultSummarizer.summarize(allRanksCheckResult())

        assertEquals(10, summary.totalGames)
    }

    // 2-4: stage1/stage2/stage3 counts all feed into the total
    @Test
    fun `stage1 stage2 and stage3 results are all included in the total`() {
        val summary = LottoResultSummarizer.summarize(allRanksCheckResult())

        assertEquals(5 + 2 + 3, summary.totalGames)
    }

    // 5: an empty stage3Results (not yet generated) does not crash and yields five games
    @Test
    fun `an empty stage3 result list is handled without error and yields five games`() {
        val summary = LottoResultSummarizer.summarize(
            GenerationCheckResult(
                stage1Results = List(5) { result(LottoRank.NONE, 1) },
                stage2Results = emptyList(),
                stage3Results = emptyList()
            )
        )

        assertEquals(5, summary.totalGames)
    }

    // 6-10: each rank is represented in the counts
    @Test
    fun `first through fifth place counts are each included`() {
        val summary = LottoResultSummarizer.summarize(allRanksCheckResult())

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
