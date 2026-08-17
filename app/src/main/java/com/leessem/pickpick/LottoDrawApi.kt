package com.leessem.pickpick

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

class LottoDrawParseException(message: String) : Exception(message)

/**
 * Raw fields lifted from the 동행복권(dhlottery) response for one draw round, before any
 * semantic validation (date parsing, round matching) — that happens in LottoDrawRepository.
 */
data class LottoDrawDto(
    val round: Int,
    val winningNumbers: List<Int>,
    val bonusNumber: Int,
    val drawDateRaw: String
)

/**
 * Talks to dhlottery.co.kr's internal (undocumented but publicly used by the site itself)
 * lotto 6/45 history endpoint. Verified by hand against the live site on 2026-08-17:
 * GET https://www.dhlottery.co.kr/lt645/selectPstLt645InfoNew.do?srchDir=center&srchLtEpsd={round}
 * returns { "data": { "list": [ { "ltEpsd", "tm1WnNo".."tm6WnNo", "bnsWnNo", "ltRflYmd", ... }, ... ] } }
 * — a window of ~10 rounds, not necessarily just the requested one, with no auth/session
 * required (only the Referer/X-Requested-With headers below).
 */
object LottoDrawApi {
    private const val ENDPOINT_URL = "https://www.dhlottery.co.kr/lt645/selectPstLt645InfoNew.do"
    private const val REFERER_URL = "https://www.dhlottery.co.kr/lt645/result"
    private const val CONNECT_TIMEOUT_MS = 10_000
    private const val READ_TIMEOUT_MS = 10_000
    private const val MAX_RESPONSE_BYTES = 2_000_000

    fun fetchRawJson(round: Int): String {
        val url = URL("$ENDPOINT_URL?srchDir=center&srchLtEpsd=$round")
        val connection = url.openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "GET"
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = READ_TIMEOUT_MS
            connection.setRequestProperty("X-Requested-With", "XMLHttpRequest")
            connection.setRequestProperty("Accept", "application/json, text/javascript, */*; q=0.01")
            connection.setRequestProperty("Referer", REFERER_URL)

            val status = connection.responseCode
            if (status !in 200..299) {
                throw IOException("Unexpected HTTP status $status")
            }

            val bytes = connection.inputStream.use { readBounded(it, MAX_RESPONSE_BYTES) }
            return String(bytes, Charsets.UTF_8)
        } finally {
            connection.disconnect()
        }
    }

    /**
     * Extracts the list entry whose "ltEpsd" equals [round] and lifts its fields into a DTO.
     * Returns null when the payload is well-formed but simply doesn't contain that round
     * (nonexistent round, or a round not yet drawn). Throws [LottoDrawParseException] when the
     * payload doesn't even look like the expected envelope, or the matched entry is missing
     * fields we need — both signal a real parsing problem rather than "not found".
     *
     * Deliberately not a general JSON parser: each list entry is a known-flat object (no nested
     * braces), so isolating one entry by regex and reading its "key":value pairs is enough, and
     * avoids org.json (whose write path throws "not mocked" in this project's local JVM tests).
     */
    internal fun parseDrawDto(rawJson: String, round: Int): LottoDrawDto? {
        if (!rawJson.contains("\"data\"") || !rawJson.contains("\"list\"")) {
            throw LottoDrawParseException("Response does not look like a lotto draw list payload")
        }

        val itemRegex = Regex("\\{[^{}]*\"ltEpsd\"\\s*:\\s*$round\\s*[,}][^{}]*\\}")
        val itemJson = itemRegex.find(rawJson)?.value ?: return null
        val fields = extractFlatFields(itemJson)

        val epsd = fields["ltEpsd"]?.toIntOrNull()
        val tm1 = fields["tm1WnNo"]?.toIntOrNull()
        val tm2 = fields["tm2WnNo"]?.toIntOrNull()
        val tm3 = fields["tm3WnNo"]?.toIntOrNull()
        val tm4 = fields["tm4WnNo"]?.toIntOrNull()
        val tm5 = fields["tm5WnNo"]?.toIntOrNull()
        val tm6 = fields["tm6WnNo"]?.toIntOrNull()
        val bonus = fields["bnsWnNo"]?.toIntOrNull()
        val dateRaw = fields["ltRflYmd"]

        if (epsd == null || tm1 == null || tm2 == null || tm3 == null || tm4 == null ||
            tm5 == null || tm6 == null || bonus == null || dateRaw == null
        ) {
            throw LottoDrawParseException("Matched draw entry is missing expected fields")
        }

        return LottoDrawDto(
            round = epsd,
            winningNumbers = listOf(tm1, tm2, tm3, tm4, tm5, tm6),
            bonusNumber = bonus,
            drawDateRaw = dateRaw
        )
    }

    private fun extractFlatFields(flatObjectJson: String): Map<String, String> {
        val fieldRegex = Regex("\"(\\w+)\"\\s*:\\s*(\"[^\"]*\"|-?\\d+(?:\\.\\d+)?)")
        return fieldRegex.findAll(flatObjectJson).associate { match ->
            val key = match.groupValues[1]
            val rawValue = match.groupValues[2]
            key to if (rawValue.startsWith("\"")) rawValue.substring(1, rawValue.length - 1) else rawValue
        }
    }

    internal fun readBounded(stream: InputStream, maxBytes: Int): ByteArray {
        val buffer = ByteArrayOutputStream()
        val chunk = ByteArray(8192)
        var total = 0
        while (true) {
            val read = stream.read(chunk)
            if (read == -1) break
            total += read
            if (total > maxBytes) throw IOException("Response exceeded $maxBytes bytes")
            buffer.write(chunk, 0, read)
        }
        return buffer.toByteArray()
    }
}
