package com.leessem.pickpick

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LottoLatestRoundCheckerTest {

    // extractRoundNumbers: pure regex parse of the raw window JSON, no network involved.
    @Test
    fun `extractRoundNumbers reads every ltEpsd value out of a raw window response`() {
        val rawJson = """{"data":{"list":[{"ltEpsd":1237,"x":1},{"ltEpsd":1236,"x":2},{"ltEpsd":1235,"x":3}]}}"""

        assertEquals(listOf(1237, 1236, 1235), LottoLatestRoundChecker.extractRoundNumbers(rawJson))
    }

    @Test
    fun `extractRoundNumbers on an empty list returns an empty list`() {
        assertTrue(LottoLatestRoundChecker.extractRoundNumbers("""{"data":{"list":[]}}""").isEmpty())
    }

    // resolveLatestRound: pure hopping logic, network access injected as a fixture lambda.

    @Test
    fun `a single probe that reveals the true latest resolves in one call`() {
        var calls = 0
        val latest = LottoLatestRoundChecker.resolveLatestRound(1234) { round ->
            calls++
            assertEquals(1234, round)
            (1228..1237).toList() // max 1237 < 1234+4=1238 -> clamped by true latest, done
        }

        assertEquals(1237, latest)
        assertEquals(1, calls)
    }

    @Test
    fun `a window that exactly fills round+span hops once more to confirm no further data`() {
        val responses = mutableListOf<Int>()
        val latest = LottoLatestRoundChecker.resolveLatestRound(1233) { round ->
            responses += round
            if (round == 1233) (1228..1237).toList() // max == round+4 exactly: ambiguous, must hop
            else emptyList() // probe at 1238 finds nothing beyond -> confirms 1237 is the true latest
        }

        assertEquals(1237, latest)
        assertEquals(listOf(1233, 1238), responses)
    }

    @Test
    fun `a forward-full window hops again and finds more on the second hop`() {
        val responses = mutableListOf<Int>()
        val latest = LottoLatestRoundChecker.resolveLatestRound(1229) { round ->
            responses += round
            when (round) {
                1229 -> (1229..1233).toList() // forward-full: max == round+4, no clamp signal yet
                1234 -> (1228..1237).toList() // next hop reveals the true latest, 1237
                else -> emptyList()
            }
        }

        assertEquals(1237, latest)
        assertEquals(listOf(1229, 1234), responses)
    }

    @Test
    fun `hops are capped at MAX_HOPS even if the window never stops advancing`() {
        var calls = 0
        val latest = LottoLatestRoundChecker.resolveLatestRound(1000) { round ->
            calls++
            (round..(round + LottoLatestRoundChecker.WINDOW_FORWARD_SPAN)).toList() // always "more"
        }

        assertEquals(LottoLatestRoundChecker.MAX_HOPS, calls)
        // best known after MAX_HOPS hops, never an unbounded search
        assertTrue(latest >= 1000)
    }

    @Test
    fun `an empty window on the very first probe leaves the known round unchanged`() {
        var calls = 0
        val latest = LottoLatestRoundChecker.resolveLatestRound(1234) {
            calls++
            emptyList()
        }

        assertEquals(1234, latest)
        assertEquals(1, calls)
    }

    // estimateCurrentRound: pure date arithmetic, no network — verified against the real
    // 2026-08-17 API investigation where round 1234 was drawn on 2026-07-25.
    @Test
    fun `estimateCurrentRound matches the real round drawn on that date`() {
        assertEquals(1234, LottoLatestRoundChecker.estimateCurrentRound(LocalDate.of(2026, 7, 25)))
    }

    @Test
    fun `estimateCurrentRound does not advance until the next weekly draw`() {
        assertEquals(1234, LottoLatestRoundChecker.estimateCurrentRound(LocalDate.of(2026, 7, 28)))
    }

    @Test
    fun `estimateCurrentRound never returns less than 1`() {
        assertEquals(1, LottoLatestRoundChecker.estimateCurrentRound(LocalDate.of(1990, 1, 1)))
    }
}
