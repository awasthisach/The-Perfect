package com.vvf.smartmanager.feature.cleaner

import com.vvf.smartmanager.core.model.DuplicateAutoSelectStrategy
import com.vvf.smartmanager.core.model.DuplicateLevel
import org.junit.Assert.assertEquals
import org.junit.Test

class CleanerSelectionPolicyTest {

    @Test
    fun levelThreeSimilarityNeverPreselectsFilesForDeletion() {
        assertEquals(
            DuplicateAutoSelectStrategy.DESELECT_ALL,
            defaultDuplicateAutoSelectStrategy(DuplicateLevel.LEVEL_3_SIMILARITY)
        )
    }

    @Test
    fun exactDuplicateLevelsKeepExistingSelectionBehavior() {
        assertEquals(
            DuplicateAutoSelectStrategy.KEEP_OLDEST,
            defaultDuplicateAutoSelectStrategy(DuplicateLevel.LEVEL_1_SIZE)
        )
        assertEquals(
            DuplicateAutoSelectStrategy.KEEP_OLDEST,
            defaultDuplicateAutoSelectStrategy(DuplicateLevel.LEVEL_2_HASH)
        )
    }
}
