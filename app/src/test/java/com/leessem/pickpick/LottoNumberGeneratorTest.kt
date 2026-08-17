package com.leessem.pickpick

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LottoNumberGeneratorTest {

    @Test
    fun `generate returns six numbers`() {
        val numbers = LottoNumberGenerator.generate()

        assertEquals(6, numbers.size)
    }

    @Test
    fun `generate returns numbers within 1 to 45`() {
        val numbers = LottoNumberGenerator.generate()

        assertTrue(numbers.all { it in 1..45 })
    }

    @Test
    fun `generate returns unique numbers`() {
        val numbers = LottoNumberGenerator.generate()

        assertEquals(numbers.size, numbers.toSet().size)
    }

    @Test
    fun `generate returns numbers sorted ascending`() {
        val numbers = LottoNumberGenerator.generate()

        assertEquals(numbers.sorted(), numbers)
    }

    @Test
    fun `generate produces varying results across calls`() {
        val results = (1..20).map { LottoNumberGenerator.generate() }

        assertTrue(results.toSet().size > 1)
    }
}
