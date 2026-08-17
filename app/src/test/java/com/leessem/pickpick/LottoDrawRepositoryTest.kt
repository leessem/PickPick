package com.leessem.pickpick

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LottoDrawRepositoryTest {

    // Network-error paths (dropped connection, timeout, non-2xx status) are deliberately not
    // exercised here: this project avoids JVM unit tests that depend on real network, and
    // LottoDrawApi.fetchRawJson isn't mocked out. Those paths are verified on-device instead
    // (see the device verification report), where the failure can be triggered for real.

    private fun validDto(round: Int = 1234) = LottoDrawDto(
        round = round,
        winningNumbers = listOf(1, 15, 19, 31, 35, 43),
        bonusNumber = 27,
        drawDateRaw = "20260725"
    )

    @Test
    fun `a well-formed dto maps to a Success wrapping a matching LottoDrawResult`() {
        val result = LottoDrawRepository.mapToDrawResult(validDto(), requestedRound = 1234)

        assertTrue(result is LottoDrawFetchResult.Success)
        val draw = (result as LottoDrawFetchResult.Success).draw
        assertEquals(1234, draw.round)
        assertEquals(listOf(1, 15, 19, 31, 35, 43), draw.winningNumbers)
        assertEquals(27, draw.bonusNumber)
    }

    @Test
    fun `winningNumbers has exactly six numbers on success`() {
        val result = LottoDrawRepository.mapToDrawResult(validDto(), requestedRound = 1234)

        val draw = (result as LottoDrawFetchResult.Success).draw
        assertEquals(6, draw.winningNumbers.size)
    }

    @Test
    fun `bonusNumber is carried through correctly on success`() {
        val result = LottoDrawRepository.mapToDrawResult(validDto(), requestedRound = 1234)

        assertEquals(27, (result as LottoDrawFetchResult.Success).draw.bonusNumber)
    }

    @Test
    fun `server round matching the requested round succeeds`() {
        val result = LottoDrawRepository.mapToDrawResult(validDto(round = 1234), requestedRound = 1234)

        assertTrue(result is LottoDrawFetchResult.Success)
    }

    @Test
    fun `server round not matching the requested round is InvalidData`() {
        val result = LottoDrawRepository.mapToDrawResult(validDto(round = 1233), requestedRound = 1234)

        assertTrue(result is LottoDrawFetchResult.InvalidData)
    }

    @Test
    fun `wrong winningNumbers count is InvalidData`() {
        val dto = validDto().copy(winningNumbers = listOf(1, 2, 3, 4, 5))

        val result = LottoDrawRepository.mapToDrawResult(dto, requestedRound = 1234)

        assertTrue(result is LottoDrawFetchResult.InvalidData)
    }

    @Test
    fun `duplicate winningNumbers is InvalidData`() {
        val dto = validDto().copy(winningNumbers = listOf(1, 1, 3, 4, 5, 6))

        val result = LottoDrawRepository.mapToDrawResult(dto, requestedRound = 1234)

        assertTrue(result is LottoDrawFetchResult.InvalidData)
    }

    @Test
    fun `out-of-range winningNumbers is InvalidData`() {
        val dto = validDto().copy(winningNumbers = listOf(1, 2, 3, 4, 5, 46))

        val result = LottoDrawRepository.mapToDrawResult(dto, requestedRound = 1234)

        assertTrue(result is LottoDrawFetchResult.InvalidData)
    }

    @Test
    fun `out-of-range bonusNumber is InvalidData`() {
        val dto = validDto().copy(bonusNumber = 0)

        val result = LottoDrawRepository.mapToDrawResult(dto, requestedRound = 1234)

        assertTrue(result is LottoDrawFetchResult.InvalidData)
    }

    @Test
    fun `bonusNumber duplicating a winning number is InvalidData`() {
        val dto = validDto().copy(bonusNumber = 1) // 1 is already in winningNumbers

        val result = LottoDrawRepository.mapToDrawResult(dto, requestedRound = 1234)

        assertTrue(result is LottoDrawFetchResult.InvalidData)
    }

    @Test
    fun `winningNumbers out of server order is normalized to ascending rather than rejected`() {
        val dto = validDto().copy(winningNumbers = listOf(43, 1, 35, 19, 31, 15))

        val result = LottoDrawRepository.mapToDrawResult(dto, requestedRound = 1234)

        assertTrue(result is LottoDrawFetchResult.Success)
        assertEquals(listOf(1, 15, 19, 31, 35, 43), (result as LottoDrawFetchResult.Success).draw.winningNumbers)
    }

    @Test
    fun `an unparseable draw date is InvalidData`() {
        val dto = validDto().copy(drawDateRaw = "not-a-date")

        val result = LottoDrawRepository.mapToDrawResult(dto, requestedRound = 1234)

        assertTrue(result is LottoDrawFetchResult.InvalidData)
    }

    @Test
    fun `a nonexistent round produces NotFound via the api parser (no dto reaches the mapper)`() {
        val emptyListJson = """{"data":{"list":[]}}"""

        val dto = LottoDrawApi.parseDrawDto(emptyListJson, 9_999_999)

        assertEquals(null, dto)
    }

    @Test
    fun `a LottoDrawResult produced by the repository connects correctly to LottoResultChecker`() {
        val result = LottoDrawRepository.mapToDrawResult(validDto(), requestedRound = 1234)
        val draw = (result as LottoDrawFetchResult.Success).draw

        val firstPlaceGame = LottoGame(listOf(1, 15, 19, 31, 35, 43))
        val secondPlaceGame = LottoGame(listOf(1, 15, 19, 31, 35, 27)) // 5 matched + bonus 27
        val noWinGame = LottoGame(listOf(2, 3, 4, 5, 6, 7))

        assertEquals(LottoRank.FIRST, LottoResultChecker.check(firstPlaceGame, draw).rank)
        assertEquals(LottoRank.SECOND, LottoResultChecker.check(secondPlaceGame, draw).rank)
        assertEquals(LottoRank.NONE, LottoResultChecker.check(noWinGame, draw).rank)
    }
}
