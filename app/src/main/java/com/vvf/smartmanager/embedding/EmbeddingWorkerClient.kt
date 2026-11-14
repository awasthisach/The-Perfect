package com.vvf.smartmanager.embedding

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.appcheck.FirebaseAppCheck
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

/**
 * Fail-closed client for the production embedding Worker (contract v4).
 *
 * Contract source of truth: cloudflare/drive-semantic-embed/src/index.ts
 *
 * Required request shape:
 * - POST {baseUrl}/embed
 * - Header Authorization: Bearer <Firebase Auth ID token>
 * - Header X-Firebase-AppCheck: <App Check token>
 * - Body: { "texts": string[], "mode": "query"|"document", "version"?: "2"|"3"|"4" }
 *
 * This client never sends a request when:
 * - offline-only mode is enabled
 * - remote AI consent is not granted
 * - Firebase Auth ID token cannot be obtained
 * - App Check token cannot be obtained
 * - baseUrl is blank
 *
 * It does not treat a Google Drive / Credential Manager ID token as a Firebase Auth ID token.
 */
class EmbeddingWorkerClient(
    private val baseUrl: String,
    private val isOfflineOnly: () -> Boolean,
    private val isRemoteAiConsentGranted: () -> Boolean,
    private val firebaseAuth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val firebaseAppCheck: FirebaseAppCheck = FirebaseAppCheck.getInstance(),
    private val httpClient: OkHttpClient = defaultClient(),
) {

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
        MISSING_FIREBASE_AUTH,
        MISSING_APP_CHECK,
    }

    enum class Mode(val wireValue: String) {
        QUERY("query"),
        DOCUMENT("document"),
    }

    /**
     * Request embeddings. Returns [Result.Blocked] without performing any network I/O
     * when preconditions fail.
     */
    suspend fun embed(
        texts: List<String>,
        mode: Mode,
        version: String = "4",
    ): Result = withContext(Dispatchers.IO) {
        if (isOfflineOnly()) {
            return@withContext Result.Blocked(BlockReason.OFFLINE_ONLY)
        }
        if (!isRemoteAiConsentGranted()) {
            return@withContext Result.Blocked(BlockReason.CONSENT_REQUIRED)
        }
        val trimmedBase = baseUrl.trim().trimEnd('/')
        if (trimmedBase.isEmpty()) {
            return@withContext Result.Blocked(BlockReason.MISSING_BASE_URL)
        }
        if (texts.isEmpty() || texts.size > MAX_TEXTS) {
            return@withContext Result.InvalidResponse("texts must contain 1..$MAX_TEXTS items")
        }
        if (texts.any { it.isBlank() || it.length > MAX_TEXT_CHARS }) {
            return@withContext Result.InvalidResponse("each text must be non-blank and <= $MAX_TEXT_CHARS chars")
        }
        if (version !in ALLOWED_VERSIONS) {
            return@withContext Result.InvalidResponse("version must be one of $ALLOWED_VERSIONS")
        }

        val idToken = obtainFirebaseIdToken()
            ?: return@withContext Result.Blocked(BlockReason.MISSING_FIREBASE_AUTH)

        val appCheckToken = obtainAppCheckToken()
            ?: return@withContext Result.Blocked(BlockReason.MISSING_APP_CHECK)

        val bodyJson = JSONObject().apply {
            put("texts", JSONArray(texts))
            put("mode", mode.wireValue)
            put("version", version)
        }

        val request = Request.Builder()
            .url("$trimmedBase/embed")
            .post(bodyJson.toString().toRequestBody(JSON_MEDIA_TYPE))
            .header("Authorization", "Bearer $idToken")
            .header("X-Firebase-AppCheck", appCheckToken)
            .header("Content-Type", "application/json; charset=utf-8")
            .header("Accept", "application/json")
            .build()

        try {
            httpClient.newCall(request).execute().use { response ->
                val responseBody = response.body?.string()
                if (!response.isSuccessful) {
                    return@withContext Result.HttpError(response.code, responseBody)
                }
                parseSuccess(responseBody)
            }
        } catch (e: IOException) {
            Log.w(TAG, "Embedding request failed", e)
            Result.NetworkError(e.message ?: "network_error")
        }
    }

    private suspend fun obtainFirebaseIdToken(): String? {
        val user = firebaseAuth.currentUser ?: return null
        return suspendCancellableCoroutine { cont ->
            user.getIdToken(false)
                .addOnSuccessListener { result ->
                    val token = result.token
                    if (token.isNullOrBlank()) {
                        cont.resume(null)
                    } else {
                        cont.resume(token)
                    }
                }
                .addOnFailureListener {
                    cont.resume(null)
                }
        }
    }

    private suspend fun obtainAppCheckToken(): String? {
        return suspendCancellableCoroutine { cont ->
            firebaseAppCheck.getAppCheckToken(false)
                .addOnSuccessListener { result ->
                    val token = result.token
                    if (token.isNullOrBlank()) {
                        cont.resume(null)
                    } else {
                        cont.resume(token)
                    }
                }
                .addOnFailureListener {
                    cont.resume(null)
                }
        }
    }

    private fun parseSuccess(body: String?): Result {
        if (body.isNullOrBlank()) {
            return Result.InvalidResponse("empty_body")
        }
        return try {
            val json = JSONObject(body)
            val embeddingsJson = json.optJSONArray("embeddings")
                ?: return Result.InvalidResponse("missing_embeddings")
            val model = json.optString("model", "")
            val version = json.optString("version", "")
            val dimension = json.optInt("dimension", -1)
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
                val vector = FloatArray(EXPECTED_DIMENSION) { idx ->
                    vectorJson.getDouble(idx).toFloat()
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

    companion object {
        private const val TAG = "EmbeddingWorkerClient"
        private const val MAX_TEXTS = 32
        private const val MAX_TEXT_CHARS = 8_000
        private const val EXPECTED_DIMENSION = 768
        private val ALLOWED_VERSIONS = setOf("2", "3", "4")
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
