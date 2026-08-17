package com.leessem.pickpick

sealed class GenerationResult {
    data class Success(val numbers: List<Int>) : GenerationResult()
    data class InsufficientNumbers(val poolSize: Int, val required: Int) : GenerationResult()
}

object LottoNumberGenerator {
    private val FULL_RANGE = (1..45).toList()
    const val PICK_COUNT = 6

    fun generate(): List<Int> {
        val result = generateGame(FULL_RANGE)
        check(result is GenerationResult.Success) { "Full 1..45 range must always be sufficient" }
        return result.numbers
    }

    fun generateGame(pool: List<Int>): GenerationResult {
        val distinctPool = pool.distinct()
        if (distinctPool.size < PICK_COUNT) {
            return GenerationResult.InsufficientNumbers(poolSize = distinctPool.size, required = PICK_COUNT)
        }
        return GenerationResult.Success(distinctPool.shuffled().take(PICK_COUNT).sorted())
    }
}
