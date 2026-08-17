package com.leessem.pickpick

/**
 * Pure encode/decode for a list of LottoDrawResult, in the same delimiter-based style as
 * GenerationSetCodec (no org.json — its write path throws "not mocked" in this project's local
 * JVM unit tests).
 */
object LottoDrawCodec {
    private const val DRAW_DELIMITER = ";"
    private const val FIELD_DELIMITER = ":"
    private const val NUMBER_DELIMITER = ","
    private const val FIELD_COUNT = 4

    fun encode(draws: List<LottoDrawResult>): String =
        draws.joinToString(DRAW_DELIMITER) { draw ->
            listOf(
                draw.round.toString(),
                draw.drawDate.toString(),
                draw.winningNumbers.joinToString(NUMBER_DELIMITER),
                draw.bonusNumber.toString()
            ).joinToString(FIELD_DELIMITER)
        }

    fun decode(raw: String?): List<LottoDrawResult> {
        if (raw.isNullOrEmpty()) return emptyList()
        return raw.split(DRAW_DELIMITER).mapNotNull { entry -> decodeOne(entry) }
    }

    // A malformed entry (corrupt prefs, partial write) is dropped rather than letting one bad
    // record take down the whole store — LottoDrawResult's own init{} validation is reused as
    // the integrity check, so decode can never hand back an out-of-range or duplicate-number draw.
    private fun decodeOne(entry: String): LottoDrawResult? = try {
        val fields = entry.split(FIELD_DELIMITER, limit = FIELD_COUNT)
        LottoDrawResult(
            round = fields[0].toInt(),
            drawDate = fields[1].toLong(),
            winningNumbers = fields[2].split(NUMBER_DELIMITER).map { it.toInt() },
            bonusNumber = fields[3].toInt()
        )
    } catch (e: Exception) {
        null
    }
}
