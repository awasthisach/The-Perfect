package com.vvf.smartmanager.core.cloud.gdrive

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DriveIdValidatorTest {
    @Test
    fun acceptsWellFormedDriveResourceIds() {
        assertTrue(DriveIdValidator.isValidFileId("1AbCdEfGhIjKlMnOpQrStUvWxYz_12345"))
        assertTrue(DriveIdValidator.isValidParentId("root"))
    }

    @Test
    fun rejectsFolderNamesPathsAndMalformedIds() {
        assertFalse(DriveIdValidator.isValidFileId("root"))
        assertFalse(DriveIdValidator.isValidParentId("VVF_Backups"))
        assertFalse(DriveIdValidator.isValidFileId("../files/secret"))
        assertFalse(DriveIdValidator.isValidFileId("short"))
        assertFalse(DriveIdValidator.isValidFileId(""))
        assertFalse(DriveIdValidator.isValidFileId(null))
    }
}
