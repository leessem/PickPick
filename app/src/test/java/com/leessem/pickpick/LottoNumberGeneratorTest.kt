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

    @Test
    fun `generateGame with sufficient pool returns success with six unique sorted numbers from pool`() {
        val pool = (1..10).toList()

        val result = LottoNumberGenerator.generateGame(pool)

        assertTrue(result is GenerationResult.Success)
        val numbers = (result as GenerationResult.Success).numbers
        assertEquals(6, numbers.size)
        assertEquals(numbers.size, numbers.toSet().size)
        assertEquals(numbers.sorted(), numbers)
        assertTrue(numbers.all { it in pool })
    }

    @Test
    fun `generateGame with pool exactly six returns all pool numbers`() {
        val pool = listOf(2, 9, 15, 23, 31, 44)

        val result = LottoNumberGenerator.generateGame(pool)

        assertTrue(result is GenerationResult.Success)
        assertEquals(pool.sorted(), (result as GenerationResult.Success).numbers)
    }

    @Test
    fun `generateGame with pool smaller than six returns insufficient numbers`() {
        val pool = listOf(1, 2, 3, 4, 5)

        val result = LottoNumberGenerator.generateGame(pool)

        assertTrue(result is GenerationResult.InsufficientNumbers)
        val insufficient = result as GenerationResult.InsufficientNumbers
        assertEquals(5, insufficient.poolSize)
        assertEquals(6, insufficient.required)
    }

    @Test
    fun `generateGame with empty pool returns insufficient numbers`() {
        val result = LottoNumberGenerator.generateGame(emptyList())

        assertTrue(result is GenerationResult.InsufficientNumbers)
        assertEquals(0, (result as GenerationResult.InsufficientNumbers).poolSize)
    }

    @Test
    fun `generateGame normalizes duplicate pool entries before checking sufficiency`() {
        val poolWithDuplicates = listOf(1, 1, 1, 2, 2, 3)

        val result = LottoNumberGenerator.generateGame(poolWithDuplicates)

        assertTrue(result is GenerationResult.InsufficientNumbers)
        assertEquals(3, (result as GenerationResult.InsufficientNumbers).poolSize)
    }

    @Test
    fun `generateGame with duplicate entries but enough distinct numbers succeeds without duplicates`() {
        val poolWithDuplicates = listOf(1, 1, 2, 3, 4, 5, 6)

        val result = LottoNumberGenerator.generateGame(poolWithDuplicates)

        assertTrue(result is GenerationResult.Success)
        val numbers = (result as GenerationResult.Success).numbers
        assertEquals(listOf(1, 2, 3, 4, 5, 6), numbers)
    }
}
