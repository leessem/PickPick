package com.leessem.pickpick

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.IOException

class LottoDrawApiTest {

    // A trimmed but real shape of the dhlottery response (verified against the live endpoint
    // on 2026-08-17), with only the fields the parser reads kept, plus one unrelated numeric
    // field per entry to prove the regex-based extraction ignores fields it doesn't need.
    private fun listPayload(vararg entries: String): String =
        """{"resultCode":null,"resultMessage":null,"data":{"list":[${entries.joinToString(",")}]}}"""

    private fun entry(epsd: Int, nums: List<Int>, bonus: Int, date: String, extra: String = "\"gmSqNo\":5133") =
        """{$extra,"ltEpsd":$epsd,"tm1WnNo":${nums[0]},"tm2WnNo":${nums[1]},"tm3WnNo":${nums[2]},""" +
            """"tm4WnNo":${nums[3]},"tm5WnNo":${nums[4]},"tm6WnNo":${nums[5]},"bnsWnNo":$bonus,"ltRflYmd":"$date"}"""

    @Test
    fun `parses the matching entry out of a multi-round list response`() {
        val json = listPayload(
            entry(1235, listOf(6, 7, 11, 15, 39, 43), 20, "20260801"),
            entry(1234, listOf(1, 15, 19, 31, 35, 43), 27, "20260725"),
            entry(1233, listOf(2, 7, 20, 25, 37, 40), 29, "20260718")
        )

        val dto = LottoDrawApi.parseDrawDto(json, 1234)

        assertEquals(1234, dto?.round)
        assertEquals(listOf(1, 15, 19, 31, 35, 43), dto?.winningNumbers)
        assertEquals(27, dto?.bonusNumber)
        assertEquals("20260725", dto?.drawDateRaw)
    }

    @Test
    fun `does not confuse a round number that is a prefix of another (34 vs 341)`() {
        val json = listPayload(
            entry(341, listOf(1, 2, 3, 4, 5, 6), 7, "20260101"),
            entry(34, listOf(10, 11, 12, 13, 14, 15), 16, "20030101")
        )

        val dto = LottoDrawApi.parseDrawDto(json, 34)

        assertEquals(34, dto?.round)
        assertEquals(listOf(10, 11, 12, 13, 14, 15), dto?.winningNumbers)
    }

    @Test
    fun `returns null when the payload is well-formed but the round is absent (empty list)`() {
        val json = listPayload()

        val dto = LottoDrawApi.parseDrawDto(json, 9_999_999)

        assertEquals(null, dto)
    }

    @Test
    fun `returns null when the round simply is not in the returned window`() {
        val json = listPayload(entry(1234, listOf(1, 2, 3, 4, 5, 6), 7, "20260101"))

        val dto = LottoDrawApi.parseDrawDto(json, 5555)

        assertEquals(null, dto)
    }

    @Test
    fun `throws a parse exception for a payload that is not the expected envelope at all`() {
        assertThrows(LottoDrawParseException::class.java) {
            LottoDrawApi.parseDrawDto("not even json {{{", 1234)
        }
    }

    @Test
    fun `throws a parse exception when the matched entry is missing an expected field`() {
        val json = """{"data":{"list":[{"ltEpsd":1234,"tm1WnNo":1}]}}"""

        assertThrows(LottoDrawParseException::class.java) {
            LottoDrawApi.parseDrawDto(json, 1234)
        }
    }

    @Test
    fun `readBounded returns all bytes when within the limit`() {
        val data = "hello".toByteArray(Charsets.UTF_8)

        val result = LottoDrawApi.readBounded(ByteArrayInputStream(data), maxBytes = 100)

        assertEquals("hello", String(result, Charsets.UTF_8))
    }

    @Test
    fun `readBounded throws instead of buffering an unbounded oversized response`() {
        val data = ByteArray(1000)

        assertThrows(IOException::class.java) {
            LottoDrawApi.readBounded(ByteArrayInputStream(data), maxBytes = 10)
        }
    }
}
