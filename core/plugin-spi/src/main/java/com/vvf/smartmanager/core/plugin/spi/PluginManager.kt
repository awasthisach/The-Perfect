package com.vvf.smartmanager.core.plugin.spi

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

private const val OCR_PLUGIN_ID = "plugin.ocr.mlkit"
private const val SEMANTIC_PLUGIN_ID = "plugin.semantic.tflite"
private const val CLOUD_PLUGIN_ID = "plugin.cloud.drivers"

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

/** Central dynamic plugin registry and lifecycle manager. */
class PluginManager(
    private var ocrPlugin: OcrPluginSPI? = null,
    private var semanticSearchPlugin: SemanticSearchSPI? = null,
    cloudDrivers: List<CloudDriverSPI> = emptyList()
) {
    private val cloudDrivers = cloudDrivers
        .distinctBy { it.driverId }
        .toMutableList()

    private val _pluginsState = MutableStateFlow<List<PluginDescriptor>>(emptyList())
    val pluginsState: StateFlow<List<PluginDescriptor>> = _pluginsState.asStateFlow()

    fun registerOcrPlugin(plugin: OcrPluginSPI) {
        require(plugin.pluginId == OCR_PLUGIN_ID) { "Unexpected OCR plugin id: ${plugin.pluginId}" }
        ocrPlugin = plugin
    }

    fun registerSemanticPlugin(plugin: SemanticSearchSPI) {
        require(plugin.pluginId == SEMANTIC_PLUGIN_ID) { "Unexpected semantic plugin id: ${plugin.pluginId}" }
        semanticSearchPlugin = plugin
    }

    fun registerCloudDriver(driver: CloudDriverSPI) {
        require(driver.driverId.isNotBlank()) { "Cloud driver id must not be blank" }
        if (cloudDrivers.none { it.driverId == driver.driverId }) cloudDrivers.add(driver)
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
        require(progress == null || progress in 0f..1f) { "progress must be between 0 and 1" }
        _pluginsState.update { list ->
            list.map { descriptor ->
                if (descriptor.id == pluginId) descriptor.copy(
                    downloadProgress = progress,
                    isModelDownloaded = isDownloaded,
                    isInstalled = isDownloaded
                ) else descriptor
            }
        }
    }

    suspend fun refreshPluginDescriptors() {
        val ocrDownloaded = ocrPlugin?.isModelDownloaded() ?: false
        val semanticReady = semanticSearchPlugin?.isModelReady() ?: false

        _pluginsState.value = listOf(
            PluginDescriptor(
                id = OCR_PLUGIN_ID,
                name = "ML Kit OCR Text Scanner",
                description = "On-demand OCR for PDF and image text extraction.",
                version = ocrPlugin?.version ?: "1.0.0",
                category = PluginCategory.OCR,
                isInstalled = ocrPlugin != null && ocrDownloaded,
                isEnabled = ocrPlugin?.isEnabled ?: false,
                isModelDownloaded = ocrDownloaded,
                sizeMb = 12
            ),
            PluginDescriptor(
                id = SEMANTIC_PLUGIN_ID,
                name = "AI Semantic Search Engine",
                description = "Local TFLite vector embedding model for concept-based search.",
                version = semanticSearchPlugin?.version ?: "1.0.0",
                category = PluginCategory.SEMANTIC_AI,
                isInstalled = semanticSearchPlugin != null && semanticReady,
                isEnabled = semanticSearchPlugin?.isEnabled ?: false,
                isModelDownloaded = semanticReady,
                sizeMb = 18
            ),
            PluginDescriptor(
                id = CLOUD_PLUGIN_ID,
                name = "Multi-Cloud & Network Drivers",
                description = "Drivers for supported cloud and network storage providers.",
                version = "1.0.0",
                category = PluginCategory.CLOUD_DRIVER,
                isInstalled = cloudDrivers.isNotEmpty(),
                isEnabled = cloudDrivers.isNotEmpty(),
                isModelDownloaded = true,
                sizeMb = 5
            )
        )
    }
}
