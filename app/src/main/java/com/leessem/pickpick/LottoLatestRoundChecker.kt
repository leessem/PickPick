package com.leessem.pickpick

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * Finds the newest lotto round the server knows about, without ever guessing round numbers one
 * by one. LottoDrawApi.fetchRawJson(round) is verified (2026-08-17, live) to return a window of
 * up to 10 rounds covering [round-5, round+4] — but clamped to whatever actually exists once
 * round+4 runs past the true latest round, backfilling older rounds to keep the window at 10.
 * That means requesting a round already at or near the latest round returns the true latest as
 * the max round in the window "for free" — no repeated single-round polling required.
 *
 * If the requested round is far enough behind the true latest that the window doesn't reach it
 * (max returned == round + WINDOW_FORWARD_SPAN, i.e. the window was capped by its own size, not
 * by data availability), one more hop is needed. Hops are capped at MAX_HOPS so a call to
 * findLatestRound can never make an unbounded number of network requests.
 */
object LottoLatestRoundChecker {
    internal const val MAX_HOPS = 3
    internal const val WINDOW_FORWARD_SPAN = 4

    private val FIRST_DRAW_DATE: LocalDate = LocalDate.of(2002, 12, 7)

    sealed class Result {
        data class Found(val round: Int) : Result()
        data class NetworkError(val message: String) : Result()
    }

    suspend fun findLatestRound(knownRound: Int): Result = withContext(Dispatchers.IO) {
        try {
            Result.Found(
                resolveLatestRound(knownRound) { round -> extractRoundNumbers(LottoDrawApi.fetchRawJson(round)) }
            )
        } catch (e: IOException) {
            Result.NetworkError(e.message ?: "Network request failed")
        }
    }

    /**
     * Pure hopping decision, network access injected via [fetchWindowRounds] so this is
     * unit-testable with fixed window fixtures instead of a real network call.
     */
    internal fun resolveLatestRound(knownRound: Int, fetchWindowRounds: (Int) -> List<Int>): Int {
        var probe = knownRound
        var bestKnown = knownRound
        repeat(MAX_HOPS) {
            val roundsInWindow = fetchWindowRounds(probe)
            if (roundsInWindow.isEmpty()) return bestKnown

            val maxInWindow = roundsInWindow.max()
            if (maxInWindow > bestKnown) bestKnown = maxInWindow

            // maxInWindow < probe + span means the window was cut short by hitting the true
            // latest round (not by running out of window slots) — that IS the answer.
            if (maxInWindow < probe + WINDOW_FORWARD_SPAN) return bestKnown

            probe = maxInWindow + 1
        }
        return bestKnown
    }

    internal fun extractRoundNumbers(rawJson: String): List<Int> =
        Regex("\"ltEpsd\"\\s*:\\s*(\\d+)").findAll(rawJson).map { it.groupValues[1].toInt() }.toList()

    /**
     * Cold-start seed when nothing is cached locally yet: Korean lotto 6/45 draw #1 was on
     * 2002-12-07 and it has run weekly, without exception, ever since — so "weeks since then"
     * is a good starting guess, refined by the bounded hops in [resolveLatestRound]/
     * [findLatestRound] above.
     */
    internal fun estimateCurrentRound(today: LocalDate): Int {
        val weeksSince = ChronoUnit.WEEKS.between(FIRST_DRAW_DATE, today)
        return (weeksSince + 1).toInt().coerceAtLeast(1)
    }

    fun estimateCurrentRound(nowMillis: Long = System.currentTimeMillis()): Int =
        estimateCurrentRound(Instant.ofEpochMilli(nowMillis).atZone(ZoneId.of("Asia/Seoul")).toLocalDate())
}
