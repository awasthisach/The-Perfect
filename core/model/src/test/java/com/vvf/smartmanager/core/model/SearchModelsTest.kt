package com.vvf.smartmanager.core.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchModelsTest {

    @Test
    fun sizeRangesAreInclusiveAndNeverOverlapAtMegabyteAndGigabyteBoundaries() {
        val oneMb = 1024L * 1024L
        val tenMb = 10L * oneMb
        val hundredMb = 100L * oneMb
        val oneGb = 1024L * oneMb

        val boundaries = listOf(
            Triple(oneMb - 1, SizeFilter.TINY, SizeFilter.SMALL),
            Triple(oneMb, SizeFilter.SMALL, SizeFilter.TINY),
            Triple(tenMb - 1, SizeFilter.SMALL, SizeFilter.MEDIUM),
            Triple(tenMb, SizeFilter.MEDIUM, SizeFilter.SMALL),
            Triple(hundredMb - 1, SizeFilter.MEDIUM, SizeFilter.LARGE),
            Triple(hundredMb, SizeFilter.LARGE, SizeFilter.MEDIUM),
            Triple(oneGb - 1, SizeFilter.LARGE, SizeFilter.HUGE),
            Triple(oneGb, SizeFilter.HUGE, SizeFilter.LARGE)
        )

        boundaries.forEach { (size, expected, adjacent) ->
            assertTrue("$size must match $expected", expected.contains(size))
            assertFalse("$size must not also match $adjacent", adjacent.contains(size))
            assertTrue("Any must include $size", SizeFilter.ANY.contains(size))
        }
        assertTrue(SizeFilter.HUGE.contains(Long.MAX_VALUE))
        assertFalse(SizeFilter.TINY.contains(-1L))
    }
}
