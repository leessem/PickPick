package com.leessem.pickpick

import android.content.Context

// Pure list operations behind save/get/delete, kept separate from SharedPreferences I/O so the
// dedup/sort/removal semantics are unit-testable without a real Context.
internal fun mergeDraw(existing: List<LottoDrawResult>, new: LottoDrawResult): List<LottoDrawResult> =
    (existing.filterNot { it.round == new.round } + new).sortedByDescending { it.round }

internal fun findDraw(draws: List<LottoDrawResult>, round: Int): LottoDrawResult? =
    draws.firstOrNull { it.round == round }

internal fun removeDraw(draws: List<LottoDrawResult>, round: Int): List<LottoDrawResult> =
    draws.filterNot { it.round == round }

/**
 * Local cache of previously-fetched LottoDrawResults, keyed by round. Shared across every
 * GenerationSet that references the same round — a draw result is never embedded inside a
 * GenerationSet itself. SharedPreferences-backed, same lightweight shape as GenerationSetStore.
 */
class LottoDrawStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getAll(): List<LottoDrawResult> =
        LottoDrawCodec.decode(prefs.getString(KEY_LOTTO_DRAWS, null)).sortedByDescending { it.round }

    fun get(round: Int): LottoDrawResult? = findDraw(getAll(), round)

    fun latestRound(): Int? = getAll().maxOfOrNull { it.round }

    fun save(draw: LottoDrawResult) {
        val updated = mergeDraw(getAll(), draw)
        prefs.edit().putString(KEY_LOTTO_DRAWS, LottoDrawCodec.encode(updated)).apply()
    }

    fun delete(round: Int) {
        val updated = removeDraw(getAll(), round)
        prefs.edit().putString(KEY_LOTTO_DRAWS, LottoDrawCodec.encode(updated)).apply()
    }

    fun clearAll() {
        prefs.edit().remove(KEY_LOTTO_DRAWS).apply()
    }

    companion object {
        // Same preferences file GenerationSetStore/LottoHistoryStore already use; "lotto_draws"
        // is a separate key so this feature never reads or overwrites their data.
        private const val PREFS_NAME = "pickpick_prefs"
        private const val KEY_LOTTO_DRAWS = "lotto_draws"
    }
}
