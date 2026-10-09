package com.vvf.smartmanager

import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.auth.FirebaseAuth
import com.vvf.smartmanager.core.cloud.gdrive.DriveSessionPolicy
import com.vvf.smartmanager.core.domain.EmbeddingBackendInfo
import com.vvf.smartmanager.core.domain.EmbeddingBatch
import com.vvf.smartmanager.core.domain.EmbeddingMode
import com.vvf.smartmanager.core.domain.EmbeddingProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Android client for the existing embed worker. It sends a Firebase ID token plus Firebase App Check;
 * it never sends the Drive access token, sets no Origin header, and fails closed until the worker
 * explicitly advertises Android App Check support.
 */
class DriveEmbeddingClient(
    private val firebaseAuth: FirebaseAuth,
    private val appCheck: FirebaseAppCheck,
    private val googleAccountEmail: () -> String?,
    private val consentGranted: () -> Boolean,
    private val baseUrl: String = DEFAULT_BASE_URL
) : EmbeddingProvider {
    init { require(baseUrl.startsWith("https://")) { "Embedding endpoint must use HTTPS." } }
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .callTimeout(45, TimeUnit.SECONDS)
        .build()

    override suspend fun health(): Result<EmbeddingBackendInfo> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder().url(baseUrl.trimEnd('/') + "/").get().build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw IllegalStateException("Embedding health check failed.")
                val payload = JSONObject(response.body?.string().orEmpty())
                val info = EmbeddingBackendInfo(
                    model = payload.optString("model"),
                    version = payload.optString("version"),
                    dimension = payload.optInt("dimension", 0),
                    mobileAuthEnabled = payload.optBoolean("mobileAuthEnabled", false)
                )
                val expected = info.model == EXPECTED_MODEL &&
                    info.version == EXPECTED_VERSION &&
                    info.dimension == EXPECTED_DIMENSION
                Result.success(info.copy(mobileAuthEnabled = info.mobileAuthEnabled && expected))
            }
        } catch (_: Exception) {
            Result.failure(IllegalStateException("Embedding service is not reachable or is not configured for Android App Check."))
        }
    }

    override suspend fun embed(texts: List<String>, mode: EmbeddingMode): Result<EmbeddingBatch> =
        withContext(Dispatchers.IO) {
            try {
                check(consentGranted()) { "Embedding consent is required." }
                require(texts.isNotEmpty() && texts.size <= MAX_TEXTS) { "Embedding batch size is invalid." }
                require(texts.all { it.isNotBlank() && it.length <= MAX_CHARS }) {
                    "Embedding text is empty or exceeds the configured limit."
                }
                val account = firebaseAuth.currentUser
                    ?: throw IllegalStateException("Firebase sign-in is required.")
                require(DriveSessionPolicy.accountsMatch(account.email, googleAccountEmail())) {
                    "Google Drive and Firebase accounts do not match."
                }
                val backend = health().getOrElse { throw it }
                require(backend.mobileAuthEnabled) {
                    "Embedding service has not enabled Android App Check. No embedding request was sent."
                }

                // Firebase ID token is the worker's Authorization credential. Never substitute Drive OAuth.
                val idToken = account.getIdToken(false).await().token
                    ?: throw IllegalStateException("Could not refresh Firebase ID token.")
                val appCheckToken = appCheck.getAppCheckToken(false).await().token
                    ?: throw IllegalStateException("Could not obtain Firebase App Check token.")
                val body = JSONObject()
                    .put("texts", JSONArray(texts))
                    .put("mode", mode.wireValue)
                    .put("version", EXPECTED_VERSION)
                    .toString()
                require(body.toByteArray(Charsets.UTF_8).size <= MAX_BODY_BYTES) {
                    "Embedding request exceeds the configured size limit."
                }

                val request = Request.Builder()
                    .url(baseUrl.trimEnd('/') + "/embed")
                    .post(body.toRequestBody(JSON_MEDIA_TYPE))
                    .header("Authorization", "Bearer $idToken")
                    .header("X-Firebase-AppCheck", appCheckToken)
                    // Native Android deliberately sends no Origin header. The worker must verify App Check.
                    .build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        throw IllegalStateException(
                            if (response.code == 401 || response.code == 403)
                                "Embedding authentication was rejected. Verify Firebase App Check worker configuration."
                            else "Embedding request failed with HTTP ${response.code}."
                        )
                    }
                    val payload = JSONObject(response.body?.string().orEmpty())
                    require(payload.optString("model") == EXPECTED_MODEL) { "Embedding model mismatch." }
                    require(payload.optString("version") == EXPECTED_VERSION) { "Embedding contract version mismatch." }
                    require(payload.optInt("dimension", 0) == EXPECTED_DIMENSION) { "Embedding dimension mismatch." }
                    val rows = payload.optJSONArray("embeddings") ?: throw IllegalStateException("Embedding response is invalid.")
                    require(rows.length() == texts.size) { "Embedding response count mismatch." }
                    val vectors = (0 until rows.length()).map { rowIndex ->
                        val row = rows.optJSONArray(rowIndex) ?: throw IllegalStateException("Embedding vector is invalid.")
                        require(row.length() == EXPECTED_DIMENSION) { "Embedding vector dimension mismatch." }
                        FloatArray(row.length()) { column ->
                            val value = row.optDouble(column, Double.NaN)
                            require(value.isFinite()) { "Embedding vector contains a non-finite value." }
                            value.toFloat()
                        }
                    }
                    Result.success(EmbeddingBatch(vectors))
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                Result.failure(IllegalStateException(e.message ?: "Embedding request failed."))
            }
        }

    companion object {
        const val DEFAULT_BASE_URL = "https://drive-semantic-embed.awasthi-sach.workers.dev"
        const val EXPECTED_MODEL = "gemini-embedding-2"
        const val EXPECTED_VERSION = "3"
        const val EXPECTED_DIMENSION = 768
        private const val MAX_TEXTS = 32
        private const val MAX_CHARS = 8000
        private const val MAX_BODY_BYTES = 300_000
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}
