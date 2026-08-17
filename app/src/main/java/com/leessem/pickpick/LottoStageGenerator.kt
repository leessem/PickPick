package com.leessem.pickpick

data class LottoGame(val numbers: List<Int>)

data class Stage1Result(val games: List<LottoGame>)
data class Stage2Result(val games: List<LottoGame>)
data class Stage3Result(val games: List<LottoGame>)

data class GenerationSession(
    val stage1: Stage1Result,
    val stage2: Stage2Result,
    val stage3: Stage3Result
)

object LottoStageGenerator {
    private val FULL_RANGE = (1..45).toList()
    private const val STAGE1_GAME_COUNT = 5
    private const val STAGE2_GAME_COUNT = 2
    private const val STAGE3_GAME_COUNT = 3

    /**
     * Generates a full session: Stage 1 (5 games, or the given manual games), then
     * randomly splits those 5 games into a 3-game basis for Stage 2 and the
     * remaining 2-game basis for Stage 3. Each stage's games are drawn from the
     * numbers that never appeared in its own basis only — Stage 3 never looks at
     * Stage 2's generated numbers, only at the 2 Stage 1 games not chosen for Stage 2.
     *
     * [stage1] lets a future manual-input flow supply its own 5 games instead of
     * generating them automatically; Stage 2 and Stage 3 always generate automatically.
     */
    fun generateSession(stage1: List<LottoGame>? = null): GenerationSession {
        val stage1Games = stage1 ?: generateGames(STAGE1_GAME_COUNT, FULL_RANGE)
        val (stage2Basis, stage3Basis) = splitStage1(stage1Games)
        return assembleSession(stage1Games, stage2Basis, stage3Basis)
    }

    /**
     * Assembles the full session from an already-resolved Stage 1 and its 3/2 basis
     * split. Exposed internally (rather than private) so tests can pin the basis
     * split directly and deterministically exercise the never-appeared pool
     * computation without depending on [splitStage1]'s randomness.
     */
    internal fun assembleSession(
        stage1Games: List<LottoGame>,
        stage2Basis: List<LottoGame>,
        stage3Basis: List<LottoGame>
    ): GenerationSession {
        val stage2Pool = neverAppearedPool(stage2Basis)
        val stage3Pool = neverAppearedPool(stage3Basis)
        return GenerationSession(
            stage1 = Stage1Result(stage1Games),
            stage2 = Stage2Result(List(STAGE2_GAME_COUNT) { generateStageGame(stage2Pool) }),
            stage3 = Stage3Result(List(STAGE3_GAME_COUNT) { generateStageGame(stage3Pool) })
        )
    }

    /**
     * Randomly splits Stage 1's 5 games (by index, so two games sharing the same
     * numbers are still distinguished) into a 3-game basis for Stage 2 and the
     * remaining 2-game basis for Stage 3.
     */
    internal fun splitStage1(stage1Games: List<LottoGame>): Pair<List<LottoGame>, List<LottoGame>> {
        val basisIndices = stage1Games.indices.shuffled().take(3).toSet()
        val stage2Basis = stage1Games.filterIndexed { index, _ -> index in basisIndices }
        val stage3Basis = stage1Games.filterIndexed { index, _ -> index !in basisIndices }
        return stage2Basis to stage3Basis
    }

    /** Numbers within 1..45 that never appear in [basisGames]. */
    internal fun neverAppearedPool(basisGames: List<LottoGame>): List<Int> =
        FULL_RANGE - usedNumbers(basisGames)

    /**
     * Generates one 6-number game preferring [neverAppeared] numbers: every
     * never-appeared number is included first, and only the numbers still missing
     * (if [neverAppeared] has fewer than 6) are randomly filled in from the
     * complement of [neverAppeared] within 1..45. Because [neverAppeared] and its
     * complement partition the full 1..45 range, the combined candidate pool always
     * has at least 6 distinct numbers, so this can never fail.
     */
    internal fun generateStageGame(neverAppeared: List<Int>): LottoGame {
        val excludedPool = FULL_RANGE - neverAppeared.toSet()
        val supplementCount = (LottoNumberGenerator.PICK_COUNT - neverAppeared.size).coerceAtLeast(0)
        val candidatePool = neverAppeared + excludedPool.shuffled().take(supplementCount)
        val result = LottoNumberGenerator.generateGame(candidatePool)
        check(result is GenerationResult.Success) {
            "candidatePool with ${candidatePool.distinct().size} distinct numbers was insufficient, " +
                "but it is always padded to at least 6"
        }
        return LottoGame(result.numbers)
    }

    /**
     * Generates [count] games from the same [pool], which never shrinks between
     * games — numbers may repeat across games, only never within one game.
     * Stage 1's pool (45) is always large enough for this to succeed; a failure
     * here would mean that invariant broke, so it is treated as a programmer
     * error rather than a recoverable result.
     */
    internal fun generateGames(count: Int, pool: List<Int>): List<LottoGame> =
        (1..count).map { index ->
            val result = LottoNumberGenerator.generateGame(pool)
            check(result is GenerationResult.Success) {
                "Pool with ${pool.distinct().size} distinct numbers was insufficient for game $index/$count, " +
                    "but this pool is expected to always have enough numbers"
            }
            LottoGame(result.numbers)
        }

    private fun usedNumbers(games: List<LottoGame>): Set<Int> =
        games.flatMap { it.numbers }.toSet()
}
