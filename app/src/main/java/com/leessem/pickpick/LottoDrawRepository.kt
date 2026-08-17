package com.leessem.pickpick

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Locale

sealed class LottoDrawFetchResult {
    data class Success(val draw: LottoDrawResult) : LottoDrawFetchResult()
    data class NotFound(val round: Int) : LottoDrawFetchResult()
    data class NetworkError(val message: String) : LottoDrawFetchResult()
    data class InvalidData(val message: String) : LottoDrawFetchResult()
}

object LottoDrawRepository {
    suspend fun getDraw(round: Int): LottoDrawFetchResult = withContext(Dispatchers.IO) {
        val rawJson = try {
            LottoDrawApi.fetchRawJson(round)
        } catch (e: IOException) {
            return@withContext LottoDrawFetchResult.NetworkError(e.message ?: "Network request failed")
        }

        val dto = try {
            LottoDrawApi.parseDrawDto(rawJson, round) ?: return@withContext LottoDrawFetchResult.NotFound(round)
        } catch (e: LottoDrawParseException) {
            return@withContext LottoDrawFetchResult.InvalidData(e.message ?: "Failed to parse response")
        }

        mapToDrawResult(dto, round)
    }

    /**
     * Pure DTO -> LottoDrawResult mapping/validation, kept separate from network/parsing so it
     * can be unit tested with fixed DTO fixtures (no network, no org.json).
     */
    internal fun mapToDrawResult(dto: LottoDrawDto, requestedRound: Int): LottoDrawFetchResult {
        if (dto.round != requestedRound) {
            return LottoDrawFetchResult.InvalidData(
                "Requested round $requestedRound but server returned round ${dto.round}"
            )
        }

        val drawDateMillis = parseDrawDate(dto.drawDateRaw)
            ?: return LottoDrawFetchResult.InvalidData("Invalid draw date: ${dto.drawDateRaw}")

        return try {
            LottoDrawFetchResult.Success(
                LottoDrawResult(
                    round = dto.round,
                    drawDate = drawDateMillis,
                    winningNumbers = dto.winningNumbers.sorted(),
                    bonusNumber = dto.bonusNumber
                )
            )
        } catch (e: IllegalArgumentException) {
            LottoDrawFetchResult.InvalidData(e.message ?: "Invalid draw data")
        }
    }

    private fun parseDrawDate(raw: String): Long? =
        try {
            SimpleDateFormat("yyyyMMdd", Locale.KOREA).apply { isLenient = false }.parse(raw)?.time
        } catch (e: Exception) {
            null
        }
}
