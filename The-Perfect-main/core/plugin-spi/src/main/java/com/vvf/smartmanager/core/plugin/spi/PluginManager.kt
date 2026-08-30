package com.vvf.smartmanager.core.plugin.spi

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class PluginCategory {
    OCR,
    SEMANTIC_AI,
    CLOUD_DRIVER,
    UTILITY
}

data class PluginDescriptor(
    val id: String,
    val name: String,
    val description: String,
    val version: String,
    val category: PluginCategory,
    val isInstalled: Boolean,
    val isEnabled: Boolean,
    val isModelDownloaded: Boolean,
    val downloadProgress: Float? = null,
    val isCore: Boolean = false,
    val sizeMb: Int = 0
)

/**
 * Central Dynamic Plugin Registry & Lifecycle Manager for VVF Smart Manager.
 * Ensures strict separation between Core APK and downloadable On-Demand plugins.
 */
class PluginManager(
    private var ocrPlugin: OcrPluginSPI? = null,
    private var semanticSearchPlugin: SemanticSearchSPI? = null,
    private val cloudDrivers: MutableList<CloudDriverSPI> = mutableListOf()
) {

    private val _pluginsState = MutableStateFlow<List<PluginDescriptor>>(emptyList())
    val pluginsState: StateFlow<List<PluginDescriptor>> = _pluginsState.asStateFlow()

    fun registerOcrPlugin(plugin: OcrPluginSPI) {
        this.ocrPlugin = plugin
    }

    fun registerSemanticPlugin(plugin: SemanticSearchSPI) {
        this.semanticSearchPlugin = plugin
    }

    fun registerCloudDriver(driver: CloudDriverSPI) {
        if (cloudDrivers.none { it.driverId == driver.driverId }) {
            cloudDrivers.add(driver)
        }
    }

    fun getOcrPlugin(): OcrPluginSPI? = ocrPlugin
    fun getSemanticPlugin(): SemanticSearchSPI? = semanticSearchPlugin
    fun getCloudDrivers(): List<CloudDriverSPI> = cloudDrivers.toList()

    fun setPluginEnabled(pluginId: String, enabled: Boolean) {
        _pluginsState.update { list ->
            list.map { if (it.id == pluginId) it.copy(isEnabled = enabled) else it }
        }
    }

    fun updateDownloadProgress(pluginId: String, progress: Float?, isDownloaded: Boolean) {
        _pluginsState.update { list ->
            list.map {
                if (it.id == pluginId) {
                    it.copy(
                        downloadProgress = progress,
                        isModelDownloaded = isDownloaded,
                        isInstalled = isDownloaded
                    )
                } else it
            }
        }
    }

    suspend fun refreshPluginDescriptors() {
        val list = mutableListOf<PluginDescriptor>()

        val isOcrDownloaded = ocrPlugin?.isModelDownloaded() ?: true

        // 1. OCR Plugin
        list.add(
            PluginDescriptor(
                id = "plugin.ocr.mlkit",
                name = "ML Kit OCR Text Scanner",
                description = "On-demand downloadable ML Kit OCR plugin for PDF & image text extraction.",
                version = ocrPlugin?.version ?: "1.0.0",
                category = PluginCategory.OCR,
                isInstalled = isOcrDownloaded,
                isEnabled = ocrPlugin?.isEnabled ?: true,
                isModelDownloaded = isOcrDownloaded,
                sizeMb = 12
            )
        )

        val isSemanticReady = semanticSearchPlugin?.isModelReady() ?: false

        // 2. Semantic Search AI Plugin
        list.add(
            PluginDescriptor(
                id = "plugin.semantic.tflite",
                name = "AI Semantic Search Engine",
                description = "Lightweight local TFLite vector embedding model for concept-based document search.",
                version = semanticSearchPlugin?.version ?: "1.0.0",
                category = PluginCategory.SEMANTIC_AI,
                isInstalled = isSemanticReady,
                isEnabled = semanticSearchPlugin?.isEnabled ?: true,
                isModelDownloaded = isSemanticReady,
                sizeMb = 18
            )
        )

        // 3. Multi-Cloud Drivers Plugin
        list.add(
            PluginDescriptor(
                id = "plugin.cloud.drivers",
                name = "Multi-Cloud & Network Drivers",
                description = "Plugins for OneDrive, Dropbox, NextCloud, S3, and local NAS network storage.",
                version = "1.0.0",
                category = PluginCategory.CLOUD_DRIVER,
                isInstalled = cloudDrivers.isNotEmpty(),
                isEnabled = true,
                isModelDownloaded = true,
                sizeMb = 5
            )
        )

        _pluginsState.value = list
    }
}
