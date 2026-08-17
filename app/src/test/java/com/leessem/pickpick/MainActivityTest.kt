package com.leessem.pickpick

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * MainActivity.kt's Composables aren't unit-testable (no Compose UI test infra in this project —
 * see app/src/androidTest, which doesn't exist). isRetryableFailure is the one piece of the
 * "다시 시도" button's logic that's plain, framework-free, and worth pinning directly: which
 * LottoDrawFetchResult variants should ever show the button.
 */
class MainActivityTest {

    // 1: NetworkError is retryable — the detail screen shows "다시 시도" for it
    @Test
    fun `NetworkError is retryable`() {
        assertTrue(isRetryableFailure(LottoDrawFetchResult.NetworkError("boom")))
    }

    // 2: NotFound is not retryable — retrying would fail the same way again
    @Test
    fun `NotFound is not retryable`() {
        assertFalse(isRetryableFailure(LottoDrawFetchResult.NotFound(1234)))
    }

    // 3: InvalidData is not retryable — retrying would fail the same way again
    @Test
    fun `InvalidData is not retryable`() {
        assertFalse(isRetryableFailure(LottoDrawFetchResult.InvalidData("bad data")))
    }

    // 4 (회차 미지정): not modeled as a LottoDrawFetchResult at all — DrawCheckState.NoRound never
    // reaches isRetryableFailure, so there is no fetch failure to retry in the first place.
    // Covered structurally: NoRound is a separate DrawCheckState branch that renders no button.
}
