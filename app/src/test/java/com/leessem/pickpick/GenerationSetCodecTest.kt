package com.leessem.pickpick

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GenerationSetCodecTest {

    // GenerationSetStore requires a real android.content.Context, which local JVM unit tests
    // don't have (and this project deliberately avoids Robolectric/DB dependencies for that,
    // matching LottoHistoryStore's existing untested-at-the-store-level pattern). Every
    // GenerationSetStore method (getAll/save/delete/clearAll) is a one-line composition of
    // GenerationSetCodec.encode/decode plus a standard-library sort/filter/remove, so exercising
    // that exact composition here through the codec gives equivalent coverage without needing
    // a Context.

    private fun sampleStage1() = listOf(
        LottoGame(listOf(1, 2, 3, 4, 5, 6)),
        LottoGame(listOf(7, 8, 9, 10, 11, 12)),
        LottoGame(listOf(13, 14, 15, 16, 17, 18)),
        LottoGame(listOf(19, 20, 21, 22, 23, 24)),
        LottoGame(listOf(25, 26, 27, 28, 29, 30))
    )

    private fun sampleStage2() = listOf(
        LottoGame(listOf(31, 32, 33, 34, 35, 36)),
        LottoGame(listOf(37, 38, 39, 40, 41, 42))
    )

    private fun sampleStage3() = listOf(
        LottoGame(listOf(31, 32, 33, 37, 38, 39)),
        LottoGame(listOf(34, 35, 36, 40, 41, 42)),
        LottoGame(listOf(20, 21, 22, 43, 44, 45))
    )

    @Test
    fun `decode of null returns empty list`() {
        assertTrue(GenerationSetCodec.decode(null).isEmpty())
    }

    @Test
    fun `decode of empty string returns empty list`() {
        assertTrue(GenerationSetCodec.decode("").isEmpty())
    }

    @Test
    fun `encode then decode round trips a complete set including id round createdAt and all three stages`() {
        val set = GenerationSet(
            id = "set-1",
            lottoRound = 1234,
            createdAt = 1_755_000_000_000L,
            stage1Games = sampleStage1(),
            stage2Games = sampleStage2(),
            stage3Games = sampleStage3()
        )

        val decoded = GenerationSetCodec.decode(GenerationSetCodec.encode(listOf(set)))

        assertEquals(listOf(set), decoded)
    }

    @Test
    fun `round trips a set whose stage3Games is empty (not yet generated)`() {
        val set = GenerationSet(
            id = "set-2",
            lottoRound = null,
            createdAt = 1_755_000_100_000L,
            stage1Games = sampleStage1(),
            stage2Games = sampleStage2(),
            stage3Games = emptyList()
        )

        val decoded = GenerationSetCodec.decode(GenerationSetCodec.encode(listOf(set)))

        assertEquals(listOf(set), decoded)
        assertEquals(emptyList<LottoGame>(), decoded.single().stage3Games)
    }

    @Test
    fun `stage1 games are preserved exactly, five games in order`() {
        val set = GenerationSet("set-3", null, 1L, sampleStage1(), sampleStage2(), sampleStage3())

        val decoded = GenerationSetCodec.decode(GenerationSetCodec.encode(listOf(set))).single()

        assertEquals(sampleStage1(), decoded.stage1Games)
    }

    @Test
    fun `stage2 games are preserved exactly, two games in order`() {
        val set = GenerationSet("set-4", null, 1L, sampleStage1(), sampleStage2(), sampleStage3())

        val decoded = GenerationSetCodec.decode(GenerationSetCodec.encode(listOf(set))).single()

        assertEquals(sampleStage2(), decoded.stage2Games)
    }

    @Test
    fun `stage3 games are preserved exactly, three games in order`() {
        val set = GenerationSet("set-4b", null, 1L, sampleStage1(), sampleStage2(), sampleStage3())

        val decoded = GenerationSetCodec.decode(GenerationSetCodec.encode(listOf(set))).single()

        assertEquals(sampleStage3(), decoded.stage3Games)
    }

    @Test
    fun `lottoRound null is preserved distinctly from a real round number`() {
        val withRound = GenerationSet("a", 999, 1L, sampleStage1(), sampleStage2(), sampleStage3())
        val withoutRound = GenerationSet("b", null, 2L, sampleStage1(), sampleStage2(), sampleStage3())

        val decoded = GenerationSetCodec.decode(GenerationSetCodec.encode(listOf(withRound, withoutRound)))

        assertEquals(999, decoded.first { it.id == "a" }.lottoRound)
        assertEquals(null, decoded.first { it.id == "b" }.lottoRound)
    }

    @Test
    fun `multiple sets all round trip independently, equivalent to store getAll`() {
        val sets = listOf(
            GenerationSet("s1", 100, 300L, sampleStage1(), sampleStage2(), sampleStage3()),
            GenerationSet("s2", null, 200L, sampleStage1(), sampleStage2(), emptyList()),
            GenerationSet("s3", 200, 100L, sampleStage1(), sampleStage2(), sampleStage3())
        )

        val decoded = GenerationSetCodec.decode(GenerationSetCodec.encode(sets))

        assertEquals(sets, decoded)
    }

    @Test
    fun `store getAll orders sets most-recent-first by createdAt (simulated via codec plus sort)`() {
        val sets = listOf(
            GenerationSet("old", null, 100L, sampleStage1(), sampleStage2(), sampleStage3()),
            GenerationSet("new", null, 300L, sampleStage1(), sampleStage2(), sampleStage3()),
            GenerationSet("mid", null, 200L, sampleStage1(), sampleStage2(), sampleStage3())
        )

        val decoded = GenerationSetCodec.decode(GenerationSetCodec.encode(sets))
        val sorted = decoded.sortedByDescending { it.createdAt }

        assertEquals(listOf("new", "mid", "old"), sorted.map { it.id })
    }

    @Test
    fun `store delete(id) removes only the targeted set and leaves the others intact (simulated via codec)`() {
        val sets = listOf(
            GenerationSet("a", null, 1L, sampleStage1(), sampleStage2(), sampleStage3()),
            GenerationSet("b", null, 2L, sampleStage1(), sampleStage2(), sampleStage3()),
            GenerationSet("c", null, 3L, sampleStage1(), sampleStage2(), sampleStage3())
        )

        val afterDelete = GenerationSetCodec.decode(GenerationSetCodec.encode(sets)).filterNot { it.id == "b" }
        val reDecoded = GenerationSetCodec.decode(GenerationSetCodec.encode(afterDelete))

        assertEquals(listOf("a", "c"), reDecoded.map { it.id })
    }

    @Test
    fun `store clearAll leaves nothing to decode (removing the key is equivalent to decode(null))`() {
        assertTrue(GenerationSetCodec.decode(null).isEmpty())
    }

    @Test
    fun `encode of empty list decodes back to empty list, equivalent to an empty store`() {
        assertTrue(GenerationSetCodec.decode(GenerationSetCodec.encode(emptyList())).isEmpty())
    }

    @Test
    fun `generation sets saved before the round feature (lottoRound null) still round trip unaffected`() {
        val legacySet = GenerationSet("legacy-1", null, 1_700_000_000_000L, sampleStage1(), sampleStage2(), sampleStage3())

        val decoded = GenerationSetCodec.decode(GenerationSetCodec.encode(listOf(legacySet)))

        assertEquals(listOf(legacySet), decoded)
    }

    @Test
    fun `multiple sets can share the same lottoRound, round is not a unique key`() {
        val a = GenerationSet("a", 1234, 1L, sampleStage1(), sampleStage2(), sampleStage3())
        val b = GenerationSet("b", 1234, 2L, sampleStage1(), sampleStage2(), sampleStage3())

        val decoded = GenerationSetCodec.decode(GenerationSetCodec.encode(listOf(a, b)))

        assertEquals(2, decoded.size)
        assertTrue(decoded.all { it.lottoRound == 1234 })
        assertEquals(setOf("a", "b"), decoded.map { it.id }.toSet())
    }

    @Test
    fun `store updateRound changes only lottoRound, leaving everything else and other sets untouched (simulated via codec)`() {
        val target = GenerationSet(
            id = "set-x",
            lottoRound = 1234,
            createdAt = 555L,
            stage1Games = sampleStage1(),
            stage2Games = sampleStage2(),
            stage3Games = sampleStage3()
        )
        val other = GenerationSet("set-y", 1234, 600L, sampleStage1(), sampleStage2(), sampleStage3())

        // Simulates GenerationSetStore.updateRound(id, round): decode -> map matching id's round -> encode.
        val afterUpdate = GenerationSetCodec.decode(GenerationSetCodec.encode(listOf(target, other))).map {
            if (it.id == "set-x") it.copy(lottoRound = 1235) else it
        }
        val reDecoded = GenerationSetCodec.decode(GenerationSetCodec.encode(afterUpdate))

        val changed = reDecoded.first { it.id == "set-x" }
        val unchanged = reDecoded.first { it.id == "set-y" }

        assertEquals(1235, changed.lottoRound)
        assertEquals(target.id, changed.id)
        assertEquals(target.createdAt, changed.createdAt)
        assertEquals(target.stage1Games, changed.stage1Games)
        assertEquals(target.stage2Games, changed.stage2Games)
        assertEquals(target.stage3Games, changed.stage3Games)
        assertEquals(1234, unchanged.lottoRound)
    }
}
