package com.leessem.pickpick

data class LottoDrawResult(
    val round: Int,
    val drawDate: Long,
    val winningNumbers: List<Int>,
    val bonusNumber: Int
) {
    init {
        require(winningNumbers.size == 6) { "winningNumbers must contain exactly 6 numbers" }
        require(winningNumbers.all { it in 1..45 }) { "winningNumbers must be within 1..45" }
        require(winningNumbers.toSet().size == winningNumbers.size) { "winningNumbers must not contain duplicates" }
        require(winningNumbers == winningNumbers.sorted()) { "winningNumbers must be sorted ascending" }
        require(bonusNumber in 1..45) { "bonusNumber must be within 1..45" }
        require(bonusNumber !in winningNumbers) { "bonusNumber must not duplicate a winning number" }
    }
}

enum class LottoRank { FIRST, SECOND, THIRD, FOURTH, FIFTH, NONE }

data class LottoCheckResult(
    val rank: LottoRank,
    val matchedCount: Int,
    val bonusMatched: Boolean
)

data class GenerationCheckResult(
    val stage1Results: List<LottoCheckResult>,
    val stage2Results: List<LottoCheckResult>,
    val stage3Results: List<LottoCheckResult>
)

object LottoResultChecker {
    fun check(game: LottoGame, draw: LottoDrawResult): LottoCheckResult {
        val matchedCount = game.numbers.count { it in draw.winningNumbers }
        val bonusMatched = draw.bonusNumber in game.numbers
        // Order matters: 5 matches + bonus must resolve to SECOND before the plain
        // "5 matches" branch would otherwise claim it as THIRD.
        val rank = when {
            matchedCount == 6 -> LottoRank.FIRST
            matchedCount == 5 && bonusMatched -> LottoRank.SECOND
            matchedCount == 5 -> LottoRank.THIRD
            matchedCount == 4 -> LottoRank.FOURTH
            matchedCount == 3 -> LottoRank.FIFTH
            else -> LottoRank.NONE
        }
        return LottoCheckResult(rank = rank, matchedCount = matchedCount, bonusMatched = bonusMatched)
    }

    fun check(set: GenerationSet, draw: LottoDrawResult): GenerationCheckResult =
        GenerationCheckResult(
            stage1Results = set.stage1Games.map { check(it, draw) },
            stage2Results = set.stage2Games.map { check(it, draw) },
            stage3Results = set.stage3Games.map { check(it, draw) }
        )
}
