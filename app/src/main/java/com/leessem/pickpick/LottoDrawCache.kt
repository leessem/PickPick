package com.leessem.pickpick

/**
 * Cache-first draw lookup: check the local cache before ever calling LottoDrawRepository, and
 * persist a newly-fetched draw so the next lookup for the same round skips the network entirely.
 * A failed fetch (NotFound/InvalidData/NetworkError) is never written to the cache.
 *
 * getCached/saveToCache/fetch are injected (rather than depending on LottoDrawStore/
 * LottoDrawRepository directly) so this can be unit tested with in-memory fakes — this project's
 * local JVM tests can't exercise a real Context-backed SharedPreferences or real network call.
 */
sealed class DrawLookupResult {
    data class FromCache(val draw: LottoDrawResult) : DrawLookupResult()
    data class Fetched(val draw: LottoDrawResult) : DrawLookupResult()
    data class Failed(val fetchResult: LottoDrawFetchResult) : DrawLookupResult()
}

suspend fun resolveDraw(
    round: Int,
    getCached: (Int) -> LottoDrawResult?,
    saveToCache: (LottoDrawResult) -> Unit,
    fetch: suspend (Int) -> LottoDrawFetchResult = LottoDrawRepository::getDraw
): DrawLookupResult {
    getCached(round)?.let { return DrawLookupResult.FromCache(it) }
    return when (val result = fetch(round)) {
        is LottoDrawFetchResult.Success -> {
            saveToCache(result.draw)
            DrawLookupResult.Fetched(result.draw)
        }
        else -> DrawLookupResult.Failed(result)
    }
}
