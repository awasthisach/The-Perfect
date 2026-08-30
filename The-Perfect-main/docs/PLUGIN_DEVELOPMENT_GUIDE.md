# VVF Smart Manager — Plugin Development Guide

## 1. Introduction
VVF Smart Manager uses a strictly isolated **Service Provider Interface (SPI)** architecture located in `:core:plugin-spi`. This ensures that heavy features such as OCR, AI Semantic Search, and secondary Cloud drivers are completely separated from the core application and can be loaded, enabled, disabled, or updated on demand.

---

## 2. Core Plugin Interfaces

### A. OCR Plugin Interface (`IOcrEngine.kt`)
To implement a custom OCR provider (e.g., Tesseract or a cloud-based OCR service):

```kotlin
package com.vvf.smartmanager.core.plugin.spi

import com.vvf.smartmanager.core.model.FileItem
import com.vvf.smartmanager.core.model.OcrOptions
import com.vvf.smartmanager.core.model.OcrProgress
import com.vvf.smartmanager.core.model.OcrResult

interface OcrPluginSPI {
    val isEnabled: Boolean
    val engineName: String
    suspend fun isModelDownloaded(): Boolean
    suspend fun downloadModel(onProgress: (Float) -> Unit): Boolean
    suspend fun extractText(
        fileItem: FileItem,
        options: OcrOptions = OcrOptions(),
        onProgress: (OcrProgress) -> Unit = {}
    ): Result<OcrResult>
    fun cancelOngoing()
}
```

### B. Semantic Search SPI (`ISemanticSearchEngine.kt`)
To implement an on-device embedding or vector similarity engine:

```kotlin
package com.vvf.smartmanager.core.plugin.spi

import com.vvf.smartmanager.core.model.FileItem
import com.vvf.smartmanager.core.model.SimilarityMatch

interface SemanticSearchSPI {
    val isEnabled: Boolean
    val modelName: String
    val embeddingDimension: Int
    suspend fun isModelReady(): Boolean
    suspend fun downloadModel(onProgress: (Float) -> Unit): Boolean
    suspend fun generateEmbedding(text: String): Result<FloatArray>
    suspend fun findSimilarFiles(
        query: String,
        targetFiles: List<FileItem>,
        minSimilarity: Float
    ): Result<List<SimilarityMatch>>
    fun calculateCosineSimilarity(vec1: FloatArray, vec2: FloatArray): Float
    fun release()
}
```

---

## 3. Registering Your Plugin in the Lifecycle Manager

Plugins are registered with the central `PluginManager` during application initialization:

```kotlin
val pluginManager = PluginManager()

// Register your custom OCR implementation
val customOcrPlugin = MyCustomOcrEngine(context)
pluginManager.registerOcrPlugin(customOcrPlugin)

// Register your custom Semantic AI implementation
val customSemanticPlugin = MyCustomTFLiteEngine(context)
pluginManager.registerSemanticPlugin(customSemanticPlugin)
```

---

## 4. Best Practices for Plugin Development
1. **Never block the UI thread**: All heavy processing must execute on `Dispatchers.Default` or `Dispatchers.IO`.
2. **Handle Cancellation gracefully**: Check `coroutineContext.isActive` regularly during long inferences or file scans.
3. **Respect Memory Trimming**: Implement `release()` or cleanup methods to purge model buffers when the operating system signals low memory.
