package com.vvf.smartmanager.feature.plugins

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.vvf.smartmanager.core.domain.ExtractTextUseCase
import com.vvf.smartmanager.core.domain.IndexOcrTextUseCase
import com.vvf.smartmanager.core.domain.SaveOcrTextUseCase
import com.vvf.smartmanager.core.model.FileCategory
import com.vvf.smartmanager.core.model.FileItem
import com.vvf.smartmanager.core.model.OcrOptions
import com.vvf.smartmanager.core.model.OcrProgress
import com.vvf.smartmanager.core.model.OcrResult
import com.vvf.smartmanager.core.plugin.spi.OcrPluginSPI
import com.vvf.smartmanager.plugin.ocr.OcrPluginImpl
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PluginInfo(
    val id: String,
    val name: String,
    val description: String,
    val version: String,
    val isEnabled: Boolean,
    val isModelDownloaded: Boolean,
    val sizeOnDisk: String,
    val isCore: Boolean = false,
    val downloadProgress: Float? = null
)

data class PluginsUiState(
    val ocrPluginEnabled: Boolean = true,
    val ocrModelDownloaded: Boolean = true,
    val isDownloadingOcrModel: Boolean = false,
    val ocrDownloadProgress: Float = 0f,
    val activeScanningFile: FileItem? = null,
    val isScanning: Boolean = false,
    val scanProgress: OcrProgress? = null,
    val ocrResult: OcrResult? = null,
    val snackbarMessage: String? = null,
    val isTxtSaved: Boolean = false,
    val isIndexed: Boolean = false
)

class PluginsViewModel(
    private val ocrPlugin: OcrPluginSPI,
    private val extractTextUseCase: ExtractTextUseCase,
    private val indexOcrTextUseCase: IndexOcrTextUseCase,
    private val saveOcrTextUseCase: SaveOcrTextUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(PluginsUiState())
    val uiState: StateFlow<PluginsUiState> = _uiState.asStateFlow()

    private var scanningJob: Job? = null

    init {
        viewModelScope.launch {
            val isDownloaded = ocrPlugin.isModelDownloaded()
            _uiState.update {
                it.copy(
                    ocrPluginEnabled = ocrPlugin.isEnabled,
                    ocrModelDownloaded = isDownloaded
                )
            }
        }
    }

    fun toggleOcrPlugin(enabled: Boolean) {
        if (ocrPlugin is OcrPluginImpl) {
            ocrPlugin.setEnabled(enabled)
        }
        _uiState.update {
            it.copy(
                ocrPluginEnabled = enabled,
                snackbarMessage = if (enabled) "OCR Engine Plugin Enabled" else "OCR Engine Plugin Disabled"
            )
        }
    }

    fun downloadOcrModel() {
        if (_uiState.value.isDownloadingOcrModel) return
        viewModelScope.launch {
            _uiState.update { it.copy(isDownloadingOcrModel = true, ocrDownloadProgress = 0f) }
            val success = ocrPlugin.downloadModel { progress ->
                _uiState.update { it.copy(ocrDownloadProgress = progress) }
            }
            _uiState.update {
                it.copy(
                    isDownloadingOcrModel = false,
                    ocrModelDownloaded = success,
                    snackbarMessage = if (success) "ML Kit OCR Model downloaded successfully" else "Failed to download OCR model"
                )
            }
        }
    }

    fun startScan(fileItem: FileItem, options: OcrOptions = OcrOptions()) {
        if (!_uiState.value.ocrPluginEnabled) {
            _uiState.update { it.copy(snackbarMessage = "OCR Plugin is disabled. Enable it to scan.") }
            return
        }

        scanningJob?.cancel()
        scanningJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    activeScanningFile = fileItem,
                    isScanning = true,
                    scanProgress = OcrProgress(currentStep = "Starting OCR Engine..."),
                    ocrResult = null,
                    isTxtSaved = false,
                    isIndexed = false
                )
            }

            val result = extractTextUseCase(
                fileItem = fileItem,
                options = options,
                onProgress = { progress ->
                    _uiState.update { it.copy(scanProgress = progress) }
                }
            )

            result.onSuccess { ocrRes ->
                _uiState.update {
                    it.copy(
                        isScanning = false,
                        ocrResult = ocrRes,
                        snackbarMessage = "Text extracted: ${ocrRes.totalWords} words found in ${ocrRes.processingDurationMs}ms"
                    )
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isScanning = false,
                        snackbarMessage = "OCR scan failed: ${error.message ?: "Unknown error"}"
                    )
                }
            }
        }
    }

    fun cancelScan() {
        extractTextUseCase.cancel()
        scanningJob?.cancel()
        _uiState.update {
            it.copy(
                isScanning = false,
                scanProgress = null,
                snackbarMessage = "OCR Scan cancelled by user"
            )
        }
    }

    fun saveAsTxt() {
        val activeFile = _uiState.value.activeScanningFile ?: return
        val result = _uiState.value.ocrResult ?: return

        viewModelScope.launch {
            val saveResult = saveOcrTextUseCase(activeFile, result)
            saveResult.onSuccess { savedFile ->
                _uiState.update {
                    it.copy(
                        isTxtSaved = true,
                        snackbarMessage = "Saved OCR text to: ${savedFile.name}"
                    )
                }
            }.onFailure { e ->
                _uiState.update {
                    it.copy(snackbarMessage = "Failed to save file: ${e.message}")
                }
            }
        }
    }

    fun indexExtractedText() {
        val activeFile = _uiState.value.activeScanningFile ?: return
        val result = _uiState.value.ocrResult ?: return

        viewModelScope.launch {
            val indexResult = indexOcrTextUseCase(activeFile, result)
            indexResult.onSuccess {
                _uiState.update {
                    it.copy(
                        isIndexed = true,
                        snackbarMessage = "Extracted text indexed with #ocr tags for Core Search"
                    )
                }
            }.onFailure { e ->
                _uiState.update {
                    it.copy(snackbarMessage = "Failed to index OCR text: ${e.message}")
                }
            }
        }
    }

    fun dismissResultDialog() {
        _uiState.update {
            it.copy(
                activeScanningFile = null,
                isScanning = false,
                scanProgress = null,
                ocrResult = null
            )
        }
    }

    fun dismissSnackbar() {
        _uiState.update { it.copy(snackbarMessage = null) }
    }

    companion object {
        fun provideFactory(
            ocrPlugin: OcrPluginSPI,
            extractTextUseCase: ExtractTextUseCase,
            indexOcrTextUseCase: IndexOcrTextUseCase,
            saveOcrTextUseCase: SaveOcrTextUseCase
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return PluginsViewModel(
                    ocrPlugin = ocrPlugin,
                    extractTextUseCase = extractTextUseCase,
                    indexOcrTextUseCase = indexOcrTextUseCase,
                    saveOcrTextUseCase = saveOcrTextUseCase
                ) as T
            }
        }
    }
}
