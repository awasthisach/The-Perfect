package com.vvf.smartmanager.core.background.drive

import com.vvf.smartmanager.core.cloud.gdrive.GoogleDriveService
import com.vvf.smartmanager.core.database.dao.DriveIndexDao
import com.vvf.smartmanager.core.domain.DriveTextExtractor
import com.vvf.smartmanager.core.plugin.spi.IOcrEngine
import android.content.Context

/** Typed runtime bridge for the Drive text-index worker; credentials stay in the service. */
object DriveContentIndexRuntime {
    @Volatile private var context: Context? = null
    @Volatile private var service: GoogleDriveService? = null
    @Volatile private var dao: DriveIndexDao? = null
    @Volatile private var extractor: DriveTextExtractor? = null
    @Volatile private var ocrEngine: IOcrEngine? = null
    @Volatile private var fullContentConsent: (() -> Boolean)? = null

    fun configure(
        context: Context,
        driveService: GoogleDriveService,
        driveIndexDao: DriveIndexDao,
        textExtractor: DriveTextExtractor,
        ocrEngine: IOcrEngine,
        fullContentConsentGranted: () -> Boolean
    ) {
        this.context = context.applicationContext
        service = driveService
        dao = driveIndexDao
        extractor = textExtractor
        this.ocrEngine = ocrEngine
        fullContentConsent = fullContentConsentGranted
    }

    fun coordinatorOrNull(): DriveContentIndexCoordinator? {
        val appContext = context ?: return null
        val driveService = service ?: return null
        val driveIndexDao = dao ?: return null
        val textExtractor = extractor ?: return null
        val engine = ocrEngine ?: return null
        val consent = fullContentConsent ?: return null
        return DriveContentIndexCoordinator(appContext, driveService, driveIndexDao, textExtractor, engine, consent)
    }
}
