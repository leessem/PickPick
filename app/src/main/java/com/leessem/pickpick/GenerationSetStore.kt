package com.leessem.pickpick

import android.content.Context

data class GenerationSet(
    val id: String,
    val lottoRound: Int?,
    val createdAt: Long,
    val stage1Games: List<LottoGame>,
    val stage2Games: List<LottoGame>,
    val finalGame: LottoGame?
)

object GenerationSetCodec {
    private const val SET_DELIMITER = ";"
    private const val FIELD_DELIMITER = ":"
    private const val GAME_DELIMITER = "|"
    private const val NUMBER_DELIMITER = ","
    private const val FIELD_COUNT = 6

    fun encode(sets: List<GenerationSet>): String =
        sets.joinToString(SET_DELIMITER) { set ->
            listOf(
                set.id,
                set.lottoRound?.toString().orEmpty(),
                set.createdAt.toString(),
                encodeGames(set.stage1Games),
                encodeGames(set.stage2Games),
                set.finalGame?.let { encodeGame(it) }.orEmpty()
            ).joinToString(FIELD_DELIMITER)
        }

    fun decode(raw: String?): List<GenerationSet> {
        if (raw.isNullOrEmpty()) return emptyList()
        return raw.split(SET_DELIMITER).map { entry ->
            // limit = FIELD_COUNT: game/number blocks never contain FIELD_DELIMITER themselves,
            // so this always yields exactly 6 parts for a validly-encoded entry.
            val fields = entry.split(FIELD_DELIMITER, limit = FIELD_COUNT)
            GenerationSet(
                id = fields[0],
                lottoRound = fields[1].takeIf { it.isNotEmpty() }?.toInt(),
                createdAt = fields[2].toLong(),
                stage1Games = decodeGames(fields[3]),
                stage2Games = decodeGames(fields[4]),
                finalGame = fields[5].takeIf { it.isNotEmpty() }?.let { decodeGame(it) }
            )
        }
    }

    private fun encodeGame(game: LottoGame): String = game.numbers.joinToString(NUMBER_DELIMITER)

    private fun encodeGames(games: List<LottoGame>): String = games.joinToString(GAME_DELIMITER) { encodeGame(it) }

    private fun decodeGame(raw: String): LottoGame = LottoGame(raw.split(NUMBER_DELIMITER).map { it.toInt() })

    private fun decodeGames(raw: String): List<LottoGame> =
        if (raw.isEmpty()) emptyList() else raw.split(GAME_DELIMITER).map { decodeGame(it) }
}

class GenerationSetStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getAll(): List<GenerationSet> =
        GenerationSetCodec.decode(prefs.getString(KEY_GENERATION_SETS, null)).sortedByDescending { it.createdAt }

    fun save(set: GenerationSet) {
        val all = listOf(set) + getAll()
        prefs.edit().putString(KEY_GENERATION_SETS, GenerationSetCodec.encode(all)).apply()
    }

    fun delete(id: String) {
        val remaining = getAll().filterNot { it.id == id }
        prefs.edit().putString(KEY_GENERATION_SETS, GenerationSetCodec.encode(remaining)).apply()
    }

    fun updateRound(id: String, round: Int?) {
        val updated = getAll().map { set -> if (set.id == id) set.copy(lottoRound = round) else set }
        prefs.edit().putString(KEY_GENERATION_SETS, GenerationSetCodec.encode(updated)).apply()
    }

    fun clearAll() {
        prefs.edit().remove(KEY_GENERATION_SETS).apply()
    }

    companion object {
        // Same preferences file LottoHistoryStore already uses; "generation_sets" is a
        // separate key so this feature never reads or overwrites the existing "lotto_history" data.
        private const val PREFS_NAME = "pickpick_prefs"
        private const val KEY_GENERATION_SETS = "generation_sets"
    }
}
