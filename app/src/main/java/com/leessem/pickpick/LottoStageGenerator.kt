package com.leessem.pickpick

data class LottoGame(val numbers: List<Int>)

data class Stage1Result(val games: List<LottoGame>)

data class Stage2Result(
    val regularGames: List<LottoGame>,
    val finalGame: GenerationResult
)

data class GenerationSession(
    val stage1: Stage1Result,
    val stage2: Stage2Result
)

object LottoStageGenerator {
    private val FULL_RANGE = (1..45).toList()
    private const val STAGE1_GAME_COUNT = 5
    private const val STAGE2_REGULAR_GAME_COUNT = 4

    /**
     * Generates a full session: Stage 1 (5 games, or the given manual games),
     * Stage 2's 4 regular games (excluding every number Stage 1 used), and the
     * final never-appeared game (excluding every number Stage 1 or Stage 2's
     * regular games used).
     *
     * [stage1] lets a future manual-input flow supply its own 5 games instead of
     * generating them automatically; Stage 2 always generates automatically.
     */
    fun generateSession(stage1: List<LottoGame>? = null): GenerationSession {
        val stage1Games = stage1 ?: generateGames(STAGE1_GAME_COUNT, FULL_RANGE)
        val stage2Pool = FULL_RANGE - usedNumbers(stage1Games)
        val stage2RegularGames = generateGames(STAGE2_REGULAR_GAME_COUNT, stage2Pool)
        return assembleSession(stage1Games, stage2RegularGames)
    }

    /**
     * Assembles the final session from already-resolved Stage 1 and Stage 2 regular
     * games by computing the never-appeared pool and generating the final game from
     * it. Exposed internally (rather than private) so tests can pin both stages and
     * deterministically exercise the never-appeared pool's success/insufficiency,
     * since Stage 1 and Stage 2's own pools are always large enough to succeed and
     * only the never-appeared pool can realistically run short.
     */
    internal fun assembleSession(stage1Games: List<LottoGame>, stage2RegularGames: List<LottoGame>): GenerationSession {
        val neverAppearedPool = FULL_RANGE - usedNumbers(stage1Games) - usedNumbers(stage2RegularGames)
        val finalGame = LottoNumberGenerator.generateGame(neverAppearedPool)
        return GenerationSession(
            stage1 = Stage1Result(stage1Games),
            stage2 = Stage2Result(stage2RegularGames, finalGame)
        )
    }

    /**
     * Generates [count] games from the same [pool], which never shrinks between
     * games — numbers may repeat across games, only never within one game.
     * Stage 1's pool (45) and Stage 2's pool (>= 15, since Stage 1 can use at
     * most 30 of the 45 numbers) are always large enough for this to succeed;
     * a failure here would mean that invariant broke, so it is treated as a
     * programmer error rather than a recoverable result.
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
