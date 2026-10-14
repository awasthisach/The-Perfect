package com.vvf.smartmanager.plugin.semanticsearch

import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.tasks.await
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Response
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Authenticated client for the existing Cloudflare embedding worker.
 *
 * The client deliberately sends no browser Origin header. The worker must be deployed with
 * its mobile App Check verification path before this client can successfully call /embed.
 */
class EmbeddingServiceException(val statusCode: Int) : IOException("Embedding service rejected request (HTTP $statusCode)")

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

    private val activeCalls = ConcurrentHashMap.newKeySet<Call>()

    /** Cancels active HTTP requests immediately when neural-search consent is revoked. */
    fun cancelPendingRequests() {
        activeCalls.forEach { it.cancel() }
    }

    fun hasSignedInUser(): Boolean = runCatching { firebaseAuthProvider().currentUser != null }.getOrDefault(false)

    suspend fun healthCheck(): Boolean = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(baseUrl.trimEnd('/') + "/").get().build()
        try {
            execute(request).use { response ->
                if (!response.isSuccessful) return@withContext false
                val payload = response.body?.string()?.let(::JSONObject) ?: return@withContext false
                payload.optBoolean("ok") &&
                    payload.optString("model") == EXPECTED_MODEL &&
                    payload.optInt("dimension") == EMBEDDING_DIMENSION &&
                    payload.optString("version") == EXPECTED_VERSION
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            false
        }
    }

    suspend fun embed(texts: List<String>, mode: String): List<FloatArray> = withContext(Dispatchers.IO) {
        require(texts.isNotEmpty() && texts.size <= MAX_TEXTS) { "Embedding batch must contain 1–$MAX_TEXTS texts" }
        require(mode == "query" || mode == "document") { "Unsupported embedding mode" }
        require(texts.all { it.isNotBlank() && it.length <= MAX_TEXT_CHARS }) { "Embedding text is empty or too long" }
        val estimatedBodyBytes = texts.sumOf { it.toByteArray(Charsets.UTF_8).size + JSON_TEXT_OVERHEAD_BYTES } + JSON_BODY_OVERHEAD_BYTES
        require(estimatedBodyBytes <= MAX_REQUEST_BYTES) { "Embedding batch payload is too large" }

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

        execute(request).use { response ->
            val responseText = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                // Never include tokens or document text in exceptions/logs.
                throw EmbeddingServiceException(response.code)
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


    private suspend fun execute(request: Request): Response = suspendCancellableCoroutine { continuation ->
        val call = http.newCall(request)
        activeCalls.add(call)
        continuation.invokeOnCancellation {
            call.cancel()
            activeCalls.remove(call)
        }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, error: IOException) {
                activeCalls.remove(call)
                if (continuation.isActive) continuation.resumeWithException(error)
            }

            override fun onResponse(call: Call, response: Response) {
                activeCalls.remove(call)
                continuation.resume(response) { _, value, _ -> value.close() }
            }
        })
    }

    companion object {
        const val WORKER_BASE_URL = "https://drive-semantic-embed.awasthi-sach.workers.dev"
        const val EXPECTED_MODEL = "gemini-embedding-2"
        const val EXPECTED_VERSION = "4"
        const val EMBEDDING_DIMENSION = 768
        const val MAX_TEXTS = 32
        const val MAX_TEXT_CHARS = 8_000
        const val MAX_REQUEST_BYTES = 220_000
        private const val JSON_TEXT_OVERHEAD_BYTES = 64
        private const val JSON_BODY_OVERHEAD_BYTES = 128
    }
}
