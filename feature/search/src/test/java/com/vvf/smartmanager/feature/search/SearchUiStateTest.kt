package com.vvf.smartmanager.feature.search

import org.junit.Assert.assertFalse
import org.junit.Test

class SearchUiStateTest {
    @Test
    fun neuralSearchIsDisabledByDefaultUntilProviderAndConsentAreReady() {
        assertFalse(SearchUiState().isSemanticEnabled)
    }
}
