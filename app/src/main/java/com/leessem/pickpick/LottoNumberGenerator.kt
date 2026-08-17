package com.leessem.pickpick

object LottoNumberGenerator {
    private val numberRange = 1..45
    private const val PICK_COUNT = 6

    fun generate(): List<Int> = numberRange.shuffled().take(PICK_COUNT).sorted()
}
