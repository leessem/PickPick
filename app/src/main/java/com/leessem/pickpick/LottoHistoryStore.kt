package com.leessem.pickpick

import android.content.Context

data class LottoRecord(val numbers: List<Int>, val timestamp: Long)

object LottoHistoryCodec {
    private const val RECORD_DELIMITER = ";"
    private const val FIELD_DELIMITER = ":"
    private const val NUMBER_DELIMITER = ","

    fun encode(history: List<LottoRecord>): String =
        history.joinToString(RECORD_DELIMITER) { record ->
            "${record.timestamp}$FIELD_DELIMITER${record.numbers.joinToString(NUMBER_DELIMITER)}"
        }

    fun decode(raw: String?): List<LottoRecord> {
        if (raw.isNullOrEmpty()) return emptyList()
        return raw.split(RECORD_DELIMITER).map { entry ->
            val (timestampPart, numbersPart) = entry.split(FIELD_DELIMITER, limit = 2)
            LottoRecord(
                numbers = numbersPart.split(NUMBER_DELIMITER).map { it.toInt() },
                timestamp = timestampPart.toLong()
            )
        }
    }
}

class LottoHistoryStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getHistory(): List<LottoRecord> =
        LottoHistoryCodec.decode(prefs.getString(KEY_HISTORY, null)).sortedByDescending { it.timestamp }

    fun addRecord(record: LottoRecord) {
        val history = listOf(record) + getHistory()
        prefs.edit().putString(KEY_HISTORY, LottoHistoryCodec.encode(history)).apply()
    }

    companion object {
        private const val PREFS_NAME = "pickpick_prefs"
        private const val KEY_HISTORY = "lotto_history"
    }
}
