package com.leessem.pickpick

/**
 * Pure, UI-framework-free formatting/aggregation helpers for showing a GenerationCheckResult
 * and a LottoDrawFetchResult in the detail screen. Kept out of MainActivity.kt so this logic
 * stays unit-testable without Compose.
 */

data class LottoResultSummary(
    val totalGames: Int,
    val firstCount: Int,
    val secondCount: Int,
    val thirdCount: Int,
    val fourthCount: Int,
    val fifthCount: Int,
    val noneCount: Int
)

object LottoResultSummarizer {
    fun summarize(result: GenerationCheckResult): LottoResultSummary {
        val all = result.stage1Results + result.stage2Results + listOfNotNull(result.finalResult)
        return LottoResultSummary(
            totalGames = all.size,
            firstCount = all.count { it.rank == LottoRank.FIRST },
            secondCount = all.count { it.rank == LottoRank.SECOND },
            thirdCount = all.count { it.rank == LottoRank.THIRD },
            fourthCount = all.count { it.rank == LottoRank.FOURTH },
            fifthCount = all.count { it.rank == LottoRank.FIFTH },
            noneCount = all.count { it.rank == LottoRank.NONE }
        )
    }
}

fun lottoRankLabel(rank: LottoRank): String = when (rank) {
    LottoRank.FIRST -> "1등"
    LottoRank.SECOND -> "2등"
    LottoRank.THIRD -> "3등"
    LottoRank.FOURTH -> "4등"
    LottoRank.FIFTH -> "5등"
    LottoRank.NONE -> "미당첨"
}

fun lottoRankDisplayText(result: LottoCheckResult): String =
    if (result.rank == LottoRank.FIRST) "🎉 ${lottoRankLabel(result.rank)}" else lottoRankLabel(result.rank)

fun lottoMatchCountText(result: LottoCheckResult): String {
    val bonusSuffix = if (result.rank == LottoRank.SECOND) " + 보너스" else ""
    return "${result.matchedCount}개 일치$bonusSuffix"
}

fun lottoDrawFetchErrorMessage(result: LottoDrawFetchResult): String = when (result) {
    is LottoDrawFetchResult.NotFound -> "해당 회차의 당첨 결과를 찾을 수 없습니다."
    is LottoDrawFetchResult.NetworkError -> "당첨번호를 불러오지 못했습니다.\n인터넷 연결을 확인해주세요."
    is LottoDrawFetchResult.InvalidData -> "당첨번호 데이터를 확인할 수 없습니다."
    is LottoDrawFetchResult.Success -> ""
}
