package com.leessem.pickpick

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LottoStageGeneratorTest {

    // Fixed fixtures so the never-appeared pool can be computed deterministically:
    // stage1 covers exactly 1..30, stage2 regular covers exactly 31..39, leaving
    // exactly {40..45} (6 numbers) never appeared.
    private val stage1CoveringOneToThirty = listOf(
        LottoGame(listOf(1, 2, 3, 4, 5, 6)),
        LottoGame(listOf(7, 8, 9, 10, 11, 12)),
        LottoGame(listOf(13, 14, 15, 16, 17, 18)),
        LottoGame(listOf(19, 20, 21, 22, 23, 24)),
        LottoGame(listOf(25, 26, 27, 28, 29, 30))
    )

    private val stage2RegularCoveringThirtyOneToThirtyNine = listOf(
        LottoGame(listOf(31, 32, 33, 34, 35, 36)),
        LottoGame(listOf(37, 38, 39, 31, 32, 33)),
        LottoGame(listOf(34, 35, 36, 37, 38, 39)),
        LottoGame(listOf(31, 32, 33, 34, 35, 36))
    )

    // 1: Stage 1 produces 5 games
    // 2/3: each game has exactly 6 numbers, no duplicates within a game
    // 5: Stage 2 regular produces exactly 4 games
    @Test
    fun `generateSession produces stage1 of five games and stage2 of four regular games`() {
        val session = LottoStageGenerator.generateSession()

        assertEquals(5, session.stage1.games.size)
        assertEquals(4, session.stage2.regularGames.size)
    }

    @Test
    fun `every generated game has six unique ascending numbers within 1 to 45`() {
        val session = LottoStageGenerator.generateSession()

        (session.stage1.games + session.stage2.regularGames).forEach { game ->
            assertEquals(6, game.numbers.size)
            assertTrue(game.numbers.all { it in 1..45 })
            assertEquals(game.numbers.size, game.numbers.toSet().size)
            assertEquals(game.numbers.sorted(), game.numbers)
        }
    }

    // 6: stage2 regular games never contain a stage1 number (structural, deterministic
    // because stage2's pool is computed as 1..45 minus stage1's numbers)
    @Test
    fun `generateSession excludes every stage1 number from stage2 regular games`() {
        val session = LottoStageGenerator.generateSession()

        val stage1Numbers = session.stage1.games.flatMap { it.numbers }.toSet()
        val stage2RegularNumbers = session.stage2.regularGames.flatMap { it.numbers }

        assertTrue(stage2RegularNumbers.none { it in stage1Numbers })
    }

    // 4 & 7: numbers may repeat across games within the same stage, because the pool
    // does not shrink between games. Proven structurally (fixed 6-number pool forces
    // every game to equal that exact set) instead of asserting a random duplicate.
    @Test
    fun `generateGames draws every game from the same pool without shrinking it between games`() {
        val pool = listOf(1, 2, 3, 4, 5, 6)

        val games = LottoStageGenerator.generateGames(4, pool)

        games.forEach { game -> assertEquals(pool.sorted(), game.numbers) }
    }

    // 9, 10, 11, 12, 8: never-appeared pool computed correctly, final game has 6 numbers
    // drawn only from it, and succeeds when the pool has exactly 6 numbers.
    @Test
    fun `assembleSession generates the final game from exactly the numbers that never appeared`() {
        val session = LottoStageGenerator.assembleSession(
            stage1CoveringOneToThirty,
            stage2RegularCoveringThirtyOneToThirtyNine
        )

        val finalResult = session.stage2.finalGame
        assertTrue(finalResult is GenerationResult.Success)
        assertEquals(listOf(40, 41, 42, 43, 44, 45), (finalResult as GenerationResult.Success).numbers)
    }

    // 13: never-appeared pool with 5 or fewer numbers returns an explicit InsufficientNumbers,
    // never a silently-succeeding or reused-number game.
    @Test
    fun `assembleSession reports insufficient numbers for the final game when fewer than six never appeared`() {
        val stage2RegularCoveringThirtyOneToForty = listOf(
            LottoGame(listOf(31, 32, 33, 34, 35, 36)),
            LottoGame(listOf(37, 38, 39, 40, 31, 32)),
            LottoGame(listOf(33, 34, 35, 36, 37, 38)),
            LottoGame(listOf(39, 40, 31, 32, 33, 34))
        )

        val session = LottoStageGenerator.assembleSession(
            stage1CoveringOneToThirty,
            stage2RegularCoveringThirtyOneToForty
        )

        val finalResult = session.stage2.finalGame
        assertTrue(finalResult is GenerationResult.InsufficientNumbers)
        val insufficient = finalResult as GenerationResult.InsufficientNumbers
        assertEquals(5, insufficient.poolSize)
        assertEquals(6, insufficient.required)
    }

    // Manual Stage 1 input hook: stage2 regular games must still be generated
    // automatically and still exclude every manually-provided stage1 number.
    @Test
    fun `generateSession honors a provided stage1 and still excludes its numbers from stage2 regular games`() {
        val session = LottoStageGenerator.generateSession(stage1 = stage1CoveringOneToThirty)

        assertEquals(stage1CoveringOneToThirty, session.stage1.games)
        val stage1Numbers = (1..30).toSet()
        val stage2RegularNumbers = session.stage2.regularGames.flatMap { it.numbers }
        assertTrue(stage2RegularNumbers.none { it in stage1Numbers })
    }
}
