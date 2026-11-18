package com.vvf.smartmanager.embedding

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.appcheck.FirebaseAppCheck
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Fail-closed client for the production embedding Worker (contract v4).
 *
 * Requests are restricted to the configured HTTPS Worker host. Firebase Auth and App Check
 * credentials are never sent to arbitrary URLs. Call [cancelOngoingRequests] when consent is
 * revoked; requests created before a revocation are invalidated even if consent is re-enabled.
 */
class EmbeddingWorkerClient(
    private val baseUrl: String,
    private val isOfflineOnly: () -> Boolean,
    private val isRemoteAiConsentGranted: () -> Boolean,
    private val firebaseAuthProvider: () -> FirebaseAuth = { FirebaseAuth.getInstance() },
    private val firebaseAppCheckProvider: () -> FirebaseAppCheck = { FirebaseAppCheck.getInstance() },
    private val callFactory: Call.Factory = defaultClient(),
    private val authTokenProvider: (suspend () -> String?)? = null,
    private val appCheckTokenProvider: (suspend () -> String?)? = null,
    private val expectedModel: String = EXPECTED_MODEL,
    private val allowedWorkerHosts: Set<String> = DEFAULT_ALLOWED_WORKER_HOSTS,
) {

    private val firebaseAuth by lazy { firebaseAuthProvider() }
    private val firebaseAppCheck by lazy { firebaseAppCheckProvider() }
    private val callLock = Any()
    private var requestGeneration = 0L
    private val activeCalls = mutableSetOf<Call>()

    sealed class Result {
        data class Success(
            val embeddings: List<FloatArray>,
            val model: String,
            val version: String,
            val dimension: Int,
        ) : Result()

        data class Blocked(val reason: BlockReason) : Result()
        data class HttpError(val code: Int, val body: String?) : Result()
        data class NetworkError(val message: String) : Result()
        data class InvalidResponse(val message: String) : Result()
    }

    enum class BlockReason {
        OFFLINE_ONLY,
        CONSENT_REQUIRED,
        MISSING_BASE_URL,
        INVALID_BASE_URL,
        MISSING_FIREBASE_AUTH,
        MISSING_APP_CHECK,
    }

    enum class Mode(val wireValue: String) {
        QUERY("query"),
        DOCUMENT("document"),
    }

    /** Cancel every in-flight request and invalidate requests that captured an earlier consent state. */
    fun cancelOngoingRequests() {
        val calls = synchronized(callLock) {
            requestGeneration += 1
            activeCalls.toList().also { activeCalls.clear() }
        }
        calls.forEach(Call::cancel)
    }

    /**
     * Request embeddings. No request is started unless offline mode is off, consent is granted,
     * the URL is allowlisted HTTPS, and both Firebase credentials are available.
     */
    suspend fun embed(
        texts: List<String>,
        mode: Mode,
        version: String = DEFAULT_VERSION,
    ): Result {
        if (isOfflineOnly()) return Result.Blocked(BlockReason.OFFLINE_ONLY)
        if (!isRemoteAiConsentGranted()) return Result.Blocked(BlockReason.CONSENT_REQUIRED)
        val requestGenerationAtStart = synchronized(callLock) { requestGeneration }

        val base = baseUrl.trim()
        if (base.isEmpty()) return Result.Blocked(BlockReason.MISSING_BASE_URL)
        val parsedBase = parseAllowedBaseUrl(base)
            ?: return Result.Blocked(BlockReason.INVALID_BASE_URL)
        if (texts.isEmpty() || texts.size > MAX_TEXTS) {
            return Result.InvalidResponse("texts must contain 1..$MAX_TEXTS items")
        }
        if (texts.any { it.isBlank() || it.length > MAX_TEXT_CHARS }) {
            return Result.InvalidResponse("each text must be non-blank and <= $MAX_TEXT_CHARS chars")
        }
        if (version !in ALLOWED_VERSIONS) {
            return Result.InvalidResponse("version must be one of $ALLOWED_VERSIONS")
        }

        val idToken = (if (authTokenProvider != null) authTokenProvider.invoke() else obtainFirebaseIdToken())
            ?: return Result.Blocked(BlockReason.MISSING_FIREBASE_AUTH)
        val appCheckToken = (if (appCheckTokenProvider != null) appCheckTokenProvider.invoke() else obtainAppCheckToken())
            ?: return Result.Blocked(BlockReason.MISSING_APP_CHECK)

        val bodyJson = JSONObject().apply {
            put("texts", JSONArray(texts))
            put("mode", mode.wireValue)
            put("version", version)
        }
        val request = Request.Builder()
            .url(parsedBase.newBuilder().addPathSegment("embed").build())
            .post(bodyJson.toString().toRequestBody(JSON_MEDIA_TYPE))
            .header("Authorization", "Bearer $idToken")
            .header("X-Firebase-AppCheck", appCheckToken)
            .header("Content-Type", "application/json; charset=utf-8")
            .header("Accept", "application/json")
            .build()

        val call = callFactory.newCall(request)
        val registered = synchronized(callLock) {
            if (requestGeneration != requestGenerationAtStart || isOfflineOnly() || !isRemoteAiConsentGranted()) {
                false
            } else {
                activeCalls.add(call)
                true
            }
        }
        if (!registered) {
            return if (isOfflineOnly()) Result.Blocked(BlockReason.OFFLINE_ONLY)
            else Result.Blocked(BlockReason.CONSENT_REQUIRED)
        }

        return try {
            call.awaitResponse().use { response ->
                val responseBody = response.body?.string()
                if (!response.isSuccessful) {
                    Result.HttpError(response.code, responseBody?.take(MAX_ERROR_BODY_CHARS))
                } else {
                    parseSuccess(
                        body = responseBody,
                        expectedCount = texts.size,
                        expectedVersion = version,
                        expectedModel = expectedModel,
                    )
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (e: IOException) {
            currentCoroutineContext().ensureActive()
            if (isOfflineOnly()) return Result.Blocked(BlockReason.OFFLINE_ONLY)
            if (!isRemoteAiConsentGranted()) return Result.Blocked(BlockReason.CONSENT_REQUIRED)
            Log.w(TAG, "Embedding request failed", e)
            Result.NetworkError(e.message ?: "network_error")
        } finally {
            synchronized(callLock) { activeCalls.remove(call) }
        }
    }

    private fun parseAllowedBaseUrl(raw: String): HttpUrl? {
        val url = raw.toHttpUrlOrNull() ?: return null
        if (!url.isHttps) return null
        if (url.host !in allowedWorkerHosts) return null
        if (url.port != 443 || url.encodedPath != "/") return null
        if (url.username.isNotEmpty() || url.password.isNotEmpty()) return null
        if (url.encodedQuery != null || url.encodedFragment != null) return null
        return url
    }

    private suspend fun obtainFirebaseIdToken(): String? {
        val user = firebaseAuth.currentUser ?: return null
        return suspendCancellableCoroutine { cont ->
            user.getIdToken(false)
                .addOnSuccessListener { result ->
                    val token = result.token?.takeIf { it.isNotBlank() }
                    cont.resumeIfActive(token)
                }
                .addOnFailureListener {
                    cont.resumeIfActive(null)
                }
        }
    }

    private suspend fun obtainAppCheckToken(): String? = suspendCancellableCoroutine { cont ->
        firebaseAppCheck.getAppCheckToken(false)
            .addOnSuccessListener { result ->
                val token = result.token?.takeIf { it.isNotBlank() }
                cont.resumeIfActive(token)
            }
            .addOnFailureListener {
                cont.resumeIfActive(null)
            }
    }

    private fun <T> CancellableContinuation<T>.resumeIfActive(value: T) {
        if (!isActive) return
        try {
            resume(value)
        } catch (_: IllegalStateException) {
            // Cancellation or a terminal Task callback won the race to resume.
        }
    }

    private fun parseSuccess(
        body: String?,
        expectedCount: Int,
        expectedVersion: String,
        expectedModel: String,
    ): Result {
        if (body.isNullOrBlank()) return Result.InvalidResponse("empty_body")
        return try {
            val json = JSONObject(body)
            val embeddingsJson = json.optJSONArray("embeddings")
                ?: return Result.InvalidResponse("missing_embeddings")
            val model = json.optString("model", "")
            val version = json.optString("version", "")
            val dimension = json.optInt("dimension", -1)
            if (embeddingsJson.length() != expectedCount) {
                return Result.InvalidResponse("embedding_count_mismatch")
            }
            if (model != expectedModel) return Result.InvalidResponse("unexpected_model")
            if (version != expectedVersion) return Result.InvalidResponse("unexpected_version")
            if (dimension != EXPECTED_DIMENSION) {
                return Result.InvalidResponse("unexpected_dimension:$dimension")
            }

            val embeddings = mutableListOf<FloatArray>()
            for (i in 0 until embeddingsJson.length()) {
                val vectorJson = embeddingsJson.optJSONArray(i)
                    ?: return Result.InvalidResponse("invalid_vector_at_$i")
                if (vectorJson.length() != EXPECTED_DIMENSION) {
                    return Result.InvalidResponse("vector_length_mismatch_at_$i")
                }
                val vector = FloatArray(EXPECTED_DIMENSION)
                for (idx in 0 until EXPECTED_DIMENSION) {
                    val value = vectorJson.getDouble(idx)
                    val floatValue = value.toFloat()
                    if (!value.isFinite() || !floatValue.isFinite()) {
                        return Result.InvalidResponse("non_finite_vector_value_at_${i}_$idx")
                    }
                    vector[idx] = floatValue
                }
                embeddings.add(vector)
            }
            Result.Success(
                embeddings = embeddings,
                model = model,
                version = version,
                dimension = dimension,
            )
        } catch (e: Exception) {
            Result.InvalidResponse(e.message ?: "parse_error")
        }
    }

    private suspend fun Call.awaitResponse(): Response = suspendCancellableCoroutine { continuation ->
        continuation.invokeOnCancellation { cancel() }
        enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (!continuation.isActive) return
                try {
                    continuation.resumeWithException(e)
                } catch (_: IllegalStateException) {
                    // Cancellation won the race; there is no response resource to close.
                }
            }

            override fun onResponse(call: Call, response: Response) {
                if (!continuation.isActive) {
                    response.close()
                    return
                }
                try {
                    continuation.resume(response)
                } catch (_: IllegalStateException) {
                    // Cancellation won the race; ensure the response body is not leaked.
                    response.close()
                }
            }
        })
    }

    companion object {
        private const val TAG = "EmbeddingWorkerClient"
        private const val MAX_TEXTS = 32
        private const val MAX_TEXT_CHARS = 8_000
        private const val MAX_ERROR_BODY_CHARS = 1_024
        private const val EXPECTED_DIMENSION = 768
        private const val EXPECTED_MODEL = "gemini-embedding-2"
        private const val DEFAULT_VERSION = "4"
        private val ALLOWED_VERSIONS = setOf("2", "3", "4")
        private val DEFAULT_ALLOWED_WORKER_HOSTS = setOf("drive-semantic-embed.awasthi-sach.workers.dev")
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

        private fun defaultClient(): OkHttpClient =
            OkHttpClient.Builder()
                .connectTimeout(20, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .callTimeout(90, TimeUnit.SECONDS)
                .build()
    }
}
