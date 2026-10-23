package com.vvf.smartmanager.core.cloud.gdrive

import org.junit.Assert.assertEquals
import org.junit.Test

class DriveFileMapperTest {
    @Test fun rfc3339OffsetsAndFractionalSecondsMapToTheSameInstant() {
        val expected = 1704164645123L // 2024-01-02T03:04:05.123Z
        for (stamp in listOf("2024-01-02T03:04:05.123Z", "2024-01-02T08:34:05.123+05:30", "2024-01-01T22:04:05.123-05:00")) {
            assertEquals(expected, DriveFileDto(id="file", modifiedTime=stamp).toFileItem().lastModified)
        }
        assertEquals(1704164645000L, parseDriveTime("2024-01-02T03:04:05Z"))
        assertEquals(expected, parseDriveTime("2024-01-02T03:04:05.123456Z"))
    }
    @Test fun missingAndInvalidDatesHaveTheSameStableFallback() {
        for (stamp in listOf(null, "", "invalid", "2024-01-02T03:04:05Ztrailing", "2024-13-02T03:04:05Z")) {
            assertEquals(0L, DriveFileDto(modifiedTime=stamp).toFileItem().lastModified)
        }
    }
}
