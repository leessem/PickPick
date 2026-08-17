package com.leessem.pickpick

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LottoDrawCacheTest {

    private fun draw(round: Int = 1234) = LottoDrawResult(
        round = round,
        drawDate = 1784937600000L,
        winningNumbers = listOf(1, 15, 19, 31, 35, 43),
        bonusNumber = 27
    )

    private fun set(round: Int?) = GenerationSet(
        id = "id-$round",
        lottoRound = round,
        createdAt = 0L,
        stage1Games = listOf(LottoGame(listOf(1, 15, 19, 31, 35, 43))),
        stage2Games = emptyList(),
        stage3Games = emptyList()
    )

    // 16: when the cache already has the round, the fetch function must never be invoked
    @Test
    fun `a cache hit never calls the fetch function`() = runBlocking {
        val cache = mutableMapOf(1234 to draw(1234))
        var fetchCalls = 0

        val result = resolveDraw(
            round = 1234,
            getCached = { cache[it] },
            saveToCache = { cache[it.round] = it },
            fetch = { fetchCalls++; LottoDrawFetchResult.Success(draw(1234)) }
        )

        assertEquals(0, fetchCalls)
        assertTrue(result is DrawLookupResult.FromCache)
        assertEquals(1234, (result as DrawLookupResult.FromCache).draw.round)
    }

    // 17: on a cache miss, fetch is called and a successful result is written back to the cache
    @Test
    fun `a cache miss calls fetch once and stores the successful result`() = runBlocking {
        val cache = mutableMapOf<Int, LottoDrawResult>()
        var fetchCalls = 0

        val result = resolveDraw(
            round = 1234,
            getCached = { cache[it] },
            saveToCache = { cache[it.round] = it },
            fetch = { fetchCalls++; LottoDrawFetchResult.Success(draw(1234)) }
        )

        assertEquals(1, fetchCalls)
        assertTrue(result is DrawLookupResult.Fetched)
        assertEquals(draw(1234), cache[1234])
    }

    // 18: a NetworkError on a cache miss leaves any existing (other-round) local data untouched
    @Test
    fun `a network error does not disturb existing cached data for other rounds`() = runBlocking {
        val cache = mutableMapOf(1233 to draw(1233))

        val result = resolveDraw(
            round = 1234,
            getCached = { cache[it] },
            saveToCache = { cache[it.round] = it },
            fetch = { LottoDrawFetchResult.NetworkError("boom") }
        )

        assertTrue(result is DrawLookupResult.Failed)
        assertEquals(draw(1233), cache[1233])
        assertFalse(cache.containsKey(1234))
    }

    // 19: a NotFound result is never written to the cache
    @Test
    fun `a NotFound result is not saved to the cache`() = runBlocking {
        val cache = mutableMapOf<Int, LottoDrawResult>()

        resolveDraw(
            round = 9_999_999,
            getCached = { cache[it] },
            saveToCache = { cache[it.round] = it },
            fetch = { LottoDrawFetchResult.NotFound(9_999_999) }
        )

        assertTrue(cache.isEmpty())
    }

    // 20: multiple GenerationSets on the same round share one LottoDrawResult
    @Test
    fun `multiple generation sets on the same round share one draw result`() {
        val sharedDraw = draw(1234)
        val setA = set(1234)
        val setB = set(1234)

        val checkA = LottoResultChecker.check(setA, sharedDraw)
        val checkB = LottoResultChecker.check(setB, sharedDraw)

        assertEquals(checkA.stage1Results, checkB.stage1Results)
    }

    // 15: a GenerationSet's round connects to the matching stored draw via findDraw
    @Test
    fun `a GenerationSet round looks up its draw result from the store`() {
        val store = listOf(draw(1233), draw(1234), draw(1235))
        val generationSet = set(1234)

        val matchedDraw = generationSet.lottoRound?.let { findDraw(store, it) }

        assertEquals(1234, matchedDraw?.round)
        val check = LottoResultChecker.check(generationSet, matchedDraw!!)
        assertEquals(LottoRank.FIRST, check.stage1Results.single().rank)
    }

    // The detail screen's "다시 시도" button re-invokes resolveDraw with the exact same
    // arguments as the initial load — these tests simulate that by calling resolveDraw twice
    // in a row, standing in for "first load fails, user taps retry."

    // 21: a NetworkError followed by a retry that succeeds transitions to Fetched
    @Test
    fun `a retry call after a NetworkError succeeds and returns Fetched`() = runBlocking {
        val cache = mutableMapOf<Int, LottoDrawResult>()

        val firstAttempt = resolveDraw(
            round = 1234,
            getCached = { cache[it] },
            saveToCache = { cache[it.round] = it },
            fetch = { LottoDrawFetchResult.NetworkError("boom") }
        )
        assertTrue(firstAttempt is DrawLookupResult.Failed)
        assertTrue((firstAttempt as DrawLookupResult.Failed).fetchResult is LottoDrawFetchResult.NetworkError)

        val retryAttempt = resolveDraw(
            round = 1234,
            getCached = { cache[it] },
            saveToCache = { cache[it.round] = it },
            fetch = { LottoDrawFetchResult.Success(draw(1234)) }
        )

        assertTrue(retryAttempt is DrawLookupResult.Fetched)
        assertEquals(1234, (retryAttempt as DrawLookupResult.Fetched).draw.round)
        assertEquals(draw(1234), cache[1234])
    }

    // 22: a NetworkError followed by a retry that fails again stays Failed with NetworkError
    @Test
    fun `a retry call after a NetworkError fails again and remains Failed`() = runBlocking {
        val cache = mutableMapOf<Int, LottoDrawResult>()

        resolveDraw(
            round = 1234,
            getCached = { cache[it] },
            saveToCache = { cache[it.round] = it },
            fetch = { LottoDrawFetchResult.NetworkError("boom") }
        )

        val retryAttempt = resolveDraw(
            round = 1234,
            getCached = { cache[it] },
            saveToCache = { cache[it.round] = it },
            fetch = { LottoDrawFetchResult.NetworkError("boom again") }
        )

        assertTrue(retryAttempt is DrawLookupResult.Failed)
        assertTrue((retryAttempt as DrawLookupResult.Failed).fetchResult is LottoDrawFetchResult.NetworkError)
        assertTrue(cache.isEmpty())
    }
}
