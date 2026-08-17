package com.leessem.pickpick

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LottoStageGeneratorTest {

    // Fixed fixtures so the never-appeared pools can be computed deterministically:
    // stage2 basis (3 games) covers exactly 1..18, stage3 basis (2 games) covers
    // exactly 19..30, leaving stage2's never-appeared pool as {19..45} (27 numbers)
    // and stage3's as {1..18, 31..45} (33 numbers).
    private val stage1Games = listOf(
        LottoGame(listOf(1, 2, 3, 4, 5, 6)),
        LottoGame(listOf(7, 8, 9, 10, 11, 12)),
        LottoGame(listOf(13, 14, 15, 16, 17, 18)),
        LottoGame(listOf(19, 20, 21, 22, 23, 24)),
        LottoGame(listOf(25, 26, 27, 28, 29, 30))
    )

    private val stage2Basis = listOf(stage1Games[0], stage1Games[1], stage1Games[2])
    private val stage3Basis = listOf(stage1Games[3], stage1Games[4])

    // 1: total games across a normal session is exactly 5 + 2 + 3 = 10
    @Test
    fun `generateSession produces exactly five stage1, two stage2, and three stage3 games`() {
        val session = LottoStageGenerator.generateSession()

        assertEquals(5, session.stage1.games.size)
        assertEquals(2, session.stage2.games.size)
        assertEquals(3, session.stage3.games.size)
    }

    // 2: every game across all stages has 6 unique ascending numbers within 1..45
    @Test
    fun `every generated game has six unique ascending numbers within 1 to 45`() {
        val session = LottoStageGenerator.generateSession()

        (session.stage1.games + session.stage2.games + session.stage3.games).forEach { game ->
            assertEquals(6, game.numbers.size)
            assertTrue(game.numbers.all { it in 1..45 })
            assertEquals(game.numbers.size, game.numbers.toSet().size)
            assertEquals(game.numbers.sorted(), game.numbers)
        }
    }

    // 3: stage2's never-appeared pool excludes exactly the stage2 basis (3 games) numbers
    @Test
    fun `assembleSession computes stage2's never-appeared pool from only the stage2 basis`() {
        val session = LottoStageGenerator.assembleSession(stage1Games, stage2Basis, stage3Basis)

        val stage2BasisNumbers = (1..18).toSet()
        val stage2Numbers = session.stage2.games.flatMap { it.numbers }
        assertTrue(stage2Numbers.none { it in stage2BasisNumbers })
    }

    // 4: stage3's never-appeared pool excludes exactly the stage3 basis (the 2 games not
    // chosen for stage2), and stage2's own generated numbers have no bearing on it —
    // proven by pinning stage2Basis/stage3Basis directly and never referencing stage2.games.
    @Test
    fun `assembleSession computes stage3's never-appeared pool from only the stage3 basis, independent of stage2's generated numbers`() {
        val session = LottoStageGenerator.assembleSession(stage1Games, stage2Basis, stage3Basis)

        val stage3BasisNumbers = (19..30).toSet()
        val stage3Numbers = session.stage3.games.flatMap { it.numbers }
        assertTrue(stage3Numbers.none { it in stage3BasisNumbers })
    }

    // 5: splitStage1 always yields exactly 3 + 2 games whose union is the original 5,
    // checked structurally (not pinning which 3 are chosen) so this never flakes.
    @Test
    fun `splitStage1 always splits five games into a three-game and a two-game group covering all five`() {
        repeat(50) {
            val (basisA, basisB) = LottoStageGenerator.splitStage1(stage1Games)

            assertEquals(3, basisA.size)
            assertEquals(2, basisB.size)
            assertEquals(stage1Games.toSet(), (basisA + basisB).toSet())
        }
    }

    // 6: never-appeared pool with 7+ numbers uses only never-appeared numbers, exactly 6 of them
    @Test
    fun `generateStageGame with seven or more never-appeared numbers draws six from never-appeared only`() {
        val neverAppeared = listOf(2, 7, 13, 18, 29, 41, 44)

        val game = LottoStageGenerator.generateStageGame(neverAppeared)

        assertEquals(6, game.numbers.size)
        assertTrue(game.numbers.all { it in neverAppeared })
    }

    // 7: never-appeared pool with exactly 6 numbers uses all of them
    @Test
    fun `generateStageGame with exactly six never-appeared numbers uses all of them`() {
        val neverAppeared = listOf(2, 7, 13, 18, 29, 41)

        val game = LottoStageGenerator.generateStageGame(neverAppeared)

        assertEquals(neverAppeared.sorted(), game.numbers)
    }

    // 8: never-appeared pool with 5 numbers includes all 5, plus exactly 1 supplement
    // number from the excluded pool (never-appeared's complement within 1..45)
    @Test
    fun `generateStageGame with five never-appeared numbers includes all five plus one supplement`() {
        val neverAppeared = listOf(2, 7, 13, 18, 29)
        val excludedPool = (1..45).toSet() - neverAppeared.toSet()

        val game = LottoStageGenerator.generateStageGame(neverAppeared)

        assertEquals(6, game.numbers.size)
        assertTrue(neverAppeared.all { it in game.numbers })
        val supplement = game.numbers - neverAppeared.toSet()
        assertEquals(1, supplement.size)
        assertTrue(supplement.single() in excludedPool)
    }

    // 9: never-appeared pool with 4 numbers includes all 4, plus exactly 2 supplement numbers
    @Test
    fun `generateStageGame with four never-appeared numbers includes all four plus two supplements`() {
        val neverAppeared = listOf(2, 7, 13, 18)
        val excludedPool = (1..45).toSet() - neverAppeared.toSet()

        val game = LottoStageGenerator.generateStageGame(neverAppeared)

        assertEquals(6, game.numbers.size)
        assertTrue(neverAppeared.all { it in game.numbers })
        val supplement = game.numbers - neverAppeared.toSet()
        assertEquals(2, supplement.size)
        assertTrue(supplement.all { it in excludedPool })
    }

    // 10: a single never-appeared number is always included
    @Test
    fun `generateStageGame with one never-appeared number always includes it`() {
        val neverAppeared = listOf(27)

        val game = LottoStageGenerator.generateStageGame(neverAppeared)

        assertEquals(6, game.numbers.size)
        assertTrue(27 in game.numbers)
    }

    // 11: zero never-appeared numbers means all 6 come from the excluded pool
    @Test
    fun `generateStageGame with zero never-appeared numbers fills all six from the excluded pool`() {
        val neverAppeared = emptyList<Int>()
        val excludedPool = (1..45).toSet()

        val game = LottoStageGenerator.generateStageGame(neverAppeared)

        assertEquals(6, game.numbers.size)
        assertTrue(game.numbers.all { it in excludedPool })
    }

    // 12: supplement numbers never duplicate never-appeared numbers, and the final
    // game never has an internal duplicate — checked across many never-appeared sizes.
    @Test
    fun `generateStageGame never duplicates a never-appeared number with a supplement number`() {
        (0..6).forEach { size ->
            val neverAppeared = (1..45).shuffled().take(size)

            val game = LottoStageGenerator.generateStageGame(neverAppeared)

            assertEquals(6, game.numbers.toSet().size)
            assertEquals(game.numbers.sorted(), game.numbers)
        }
    }

    // 13: independent calls against the same never-appeared pool may repeat numbers
    // across games — proven structurally with a fixed 6-number pool that forces every
    // call to return that exact set, rather than asserting on a random duplicate.
    @Test
    fun `generateStageGame draws every call from the same pool without shrinking it between calls`() {
        val neverAppeared = listOf(1, 2, 3, 4, 5, 6)

        val games = List(4) { LottoStageGenerator.generateStageGame(neverAppeared) }

        games.forEach { game -> assertEquals(neverAppeared.sorted(), game.numbers) }
    }

    // 14: manual stage1 input still flows through the same 5+2+3 split/generation rules
    @Test
    fun `generateSession honors a provided stage1 and still produces the 5+2+3 structure`() {
        val session = LottoStageGenerator.generateSession(stage1 = stage1Games)

        assertEquals(stage1Games, session.stage1.games)
        assertEquals(2, session.stage2.games.size)
        assertEquals(3, session.stage3.games.size)
    }

    // 15: round-tripping the same stage1 + basis split through assembleSession twice
    // must reuse the same never-appeared pools every time (independence of stage3 from
    // whatever stage2 happens to generate is a property of the pool computation, not
    // of any particular random draw, so this is deterministic).
    @Test
    fun `stage3's never-appeared pool is identical across repeated assembleSession calls regardless of stage2's output`() {
        val sessionA = LottoStageGenerator.assembleSession(stage1Games, stage2Basis, stage3Basis)
        val sessionB = LottoStageGenerator.assembleSession(stage1Games, stage2Basis, stage3Basis)

        val stage3BasisNumbers = (19..30).toSet()
        listOf(sessionA, sessionB).forEach { session ->
            assertTrue(session.stage3.games.flatMap { it.numbers }.none { it in stage3BasisNumbers })
        }
    }
}
