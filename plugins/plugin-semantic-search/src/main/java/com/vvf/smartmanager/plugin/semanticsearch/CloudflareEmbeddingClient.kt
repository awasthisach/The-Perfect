package com.vvf.smartmanager.plugin.semanticsearch

import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.tasks.await
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Authenticated client for the existing Cloudflare embedding worker.
 *
 * The client deliberately sends no browser Origin header. The worker must be deployed with
 * its mobile App Check verification path before this client can successfully call /embed.
 */
class CloudflareEmbeddingClient(
    private val baseUrl: String = WORKER_BASE_URL,
    private val firebaseAuthProvider: () -> FirebaseAuth = { FirebaseAuth.getInstance() },
    private val appCheckProvider: () -> FirebaseAppCheck = { FirebaseAppCheck.getInstance() },
    private val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(35, TimeUnit.SECONDS)
        .callTimeout(45, TimeUnit.SECONDS)
        .build()
) {
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    fun hasSignedInUser(): Boolean = runCatching { firebaseAuthProvider().currentUser != null }.getOrDefault(false)

    suspend fun healthCheck(): Boolean = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(baseUrl.trimEnd('/') + "/").get().build()
        try {
            http.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext false
                val payload = response.body?.string()?.let(::JSONObject) ?: return@withContext false
                payload.optBoolean("ok") &&
                    payload.optString("model") == EXPECTED_MODEL &&
                    payload.optInt("dimension") == EMBEDDING_DIMENSION &&
                    payload.optString("version") == EXPECTED_VERSION
            }
        } catch (_: Exception) {
            false
        }
    }

    suspend fun embed(texts: List<String>, mode: String): List<FloatArray> = withContext(Dispatchers.IO) {
        require(texts.isNotEmpty() && texts.size <= MAX_TEXTS) { "Embedding batch must contain 1–$MAX_TEXTS texts" }
        require(mode == "query" || mode == "document") { "Unsupported embedding mode" }
        require(texts.all { it.isNotBlank() && it.length <= MAX_TEXT_CHARS }) { "Embedding text is empty or too long" }

        val user = runCatching { firebaseAuthProvider().currentUser }.getOrNull()
            ?: throw IOException("Sign in to Google Drive before using neural search")
        val idToken = user.getIdToken(false).await().token
            ?.takeIf { it.isNotBlank() } ?: throw IOException("Firebase authentication token is unavailable")
        val appCheckToken = appCheckProvider().getAppCheckToken(false).await().token
            .takeIf { it.isNotBlank() } ?: throw IOException("App authenticity verification is unavailable")

        val body = JSONObject()
            .put("texts", JSONArray(texts))
            .put("mode", mode)
            .put("version", EXPECTED_VERSION)
            .toString()
            .toRequestBody(jsonMediaType)
        val request = Request.Builder()
            .url(baseUrl.trimEnd('/') + "/embed")
            .header("Authorization", "Bearer $idToken")
            .header("X-Firebase-AppCheck", appCheckToken)
            .header("Accept", "application/json")
            .post(body)
            .build()

        http.newCall(request).execute().use { response ->
            val responseText = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                // Never include tokens or document text in exceptions/logs.
                throw IOException("Embedding service rejected request (HTTP ${response.code})")
            }
            val payload = JSONObject(responseText)
            if (payload.optString("model") != EXPECTED_MODEL ||
                payload.optString("version") != EXPECTED_VERSION ||
                payload.optInt("dimension") != EMBEDDING_DIMENSION
            ) throw IOException("Embedding service model contract mismatch")

            val vectors = payload.optJSONArray("embeddings")
                ?: throw IOException("Embedding service returned no vectors")
            if (vectors.length() != texts.size) throw IOException("Embedding response count mismatch")
            (0 until vectors.length()).map { index ->
                val vector = vectors.optJSONArray(index)
                    ?: throw IOException("Embedding response contains an invalid vector")
                if (vector.length() != EMBEDDING_DIMENSION) throw IOException("Embedding vector dimension mismatch")
                FloatArray(EMBEDDING_DIMENSION) { component ->
                    val value = vector.optDouble(component, Double.NaN)
                    if (!value.isFinite()) throw IOException("Embedding vector contains an invalid value")
                    value.toFloat()
                }
            }
        }
    }

    companion object {
        const val WORKER_BASE_URL = "https://drive-semantic-embed.awasthi-sach.workers.dev"
        const val EXPECTED_MODEL = "gemini-embedding-2"
        const val EXPECTED_VERSION = "3"
        const val EMBEDDING_DIMENSION = 768
        const val MAX_TEXTS = 32
        const val MAX_TEXT_CHARS = 8_000
    }
}
