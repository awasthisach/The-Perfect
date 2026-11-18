package com.vvf.smartmanager.embedding

import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okhttp3.Protocol
import okio.Timeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class EmbeddingWorkerClientTest {

    @Test
    fun embed_blockedWhenOfflineOnly() = runBlocking {
        val factory = FakeCallFactory()
        val result = client(factory, offline = true).embed(listOf("hello"), EmbeddingWorkerClient.Mode.QUERY)
        assertEquals(EmbeddingWorkerClient.BlockReason.OFFLINE_ONLY, (result as EmbeddingWorkerClient.Result.Blocked).reason)
        assertFalse(factory.wasCalled)
    }

    @Test
    fun embed_blockedWhenConsentMissing() = runBlocking {
        val factory = FakeCallFactory()
        val result = client(factory, consent = false).embed(listOf("hello"), EmbeddingWorkerClient.Mode.DOCUMENT)
        assertEquals(EmbeddingWorkerClient.BlockReason.CONSENT_REQUIRED, (result as EmbeddingWorkerClient.Result.Blocked).reason)
        assertFalse(factory.wasCalled)
    }

    @Test
    fun embed_blockedWhenBaseUrlBlank() = runBlocking {
        val factory = FakeCallFactory()
        val result = client(factory, baseUrl = "   ").embed(listOf("hello"), EmbeddingWorkerClient.Mode.QUERY)
        assertEquals(EmbeddingWorkerClient.BlockReason.MISSING_BASE_URL, (result as EmbeddingWorkerClient.Result.Blocked).reason)
        assertFalse(factory.wasCalled)
    }

    @Test
    fun embed_blocksNonHttpsAndUntrustedHostsBeforeSendingTokens() = runBlocking {
        val httpResult = client(FakeCallFactory(), baseUrl = "http://drive-semantic-embed.awasthi-sach.workers.dev")
            .embed(listOf("hello"), EmbeddingWorkerClient.Mode.QUERY)
        val otherHostResult = client(FakeCallFactory(), baseUrl = "https://attacker.example")
            .embed(listOf("hello"), EmbeddingWorkerClient.Mode.QUERY)
        assertEquals(EmbeddingWorkerClient.BlockReason.INVALID_BASE_URL, (httpResult as EmbeddingWorkerClient.Result.Blocked).reason)
        assertEquals(EmbeddingWorkerClient.BlockReason.INVALID_BASE_URL, (otherHostResult as EmbeddingWorkerClient.Result.Blocked).reason)
    }

    @Test
    fun embed_invalidWhenTextsEmptyOrTooMany() = runBlocking {
        val factory = FakeCallFactory()
        val empty = client(factory).embed(emptyList(), EmbeddingWorkerClient.Mode.QUERY)
        val tooMany = client(factory).embed(List(33) { "item-$it" }, EmbeddingWorkerClient.Mode.DOCUMENT)
        assertTrue(empty is EmbeddingWorkerClient.Result.InvalidResponse)
        assertTrue(tooMany is EmbeddingWorkerClient.Result.InvalidResponse)
        assertFalse(factory.wasCalled)
    }

    @Test
    fun embed_acceptsOnlyExactModelVersionCountDimensionAndFiniteValues() = runBlocking {
        val good = client(FakeCallFactory { successPayload() })
            .embed(listOf("one"), EmbeddingWorkerClient.Mode.QUERY)
        assertTrue(good is EmbeddingWorkerClient.Result.Success)
        val success = good as EmbeddingWorkerClient.Result.Success
        assertEquals(1, success.embeddings.size)
        assertEquals(768, success.embeddings.single().size)
        assertEquals("gemini-embedding-2", success.model)
        assertEquals("4", success.version)

        val wrongCount = client(FakeCallFactory { successPayload(count = 0) })
            .embed(listOf("one"), EmbeddingWorkerClient.Mode.QUERY)
        val wrongModel = client(FakeCallFactory { successPayload(model = "other-model") })
            .embed(listOf("one"), EmbeddingWorkerClient.Mode.QUERY)
        val wrongVersion = client(FakeCallFactory { successPayload(version = "3") })
            .embed(listOf("one"), EmbeddingWorkerClient.Mode.QUERY)
        val wrongDimension = client(FakeCallFactory { successPayload(dimension = 384) })
            .embed(listOf("one"), EmbeddingWorkerClient.Mode.QUERY)
        val nonFinite = client(FakeCallFactory { successPayload(firstValue = 1e100) })
            .embed(listOf("one"), EmbeddingWorkerClient.Mode.QUERY)

        assertEquals("embedding_count_mismatch", (wrongCount as EmbeddingWorkerClient.Result.InvalidResponse).message)
        assertEquals("unexpected_model", (wrongModel as EmbeddingWorkerClient.Result.InvalidResponse).message)
        assertEquals("unexpected_version", (wrongVersion as EmbeddingWorkerClient.Result.InvalidResponse).message)
        assertTrue(wrongDimension is EmbeddingWorkerClient.Result.InvalidResponse)
        assertTrue(nonFinite is EmbeddingWorkerClient.Result.InvalidResponse)
    }

    @Test
    fun coroutineCancellationCancelsOkHttpCall() = runBlocking {
        val factory = FakeCallFactory(responseBuilder = null)
        val client = client(factory)
        val job = launch { client.embed(listOf("hello"), EmbeddingWorkerClient.Mode.QUERY) }
        while (!factory.wasCalled) yield()
        job.cancelAndJoin()
        assertTrue(factory.call?.isCanceled == true)
    }

    @Test
    fun consentRevocationCancelsActiveCallAndInvalidatesItsResult() = runBlocking {
        var consent = true
        val factory = FakeCallFactory(responseBuilder = null)
        val client = client(factory, consentProvider = { consent })
        val request = async { client.embed(listOf("hello"), EmbeddingWorkerClient.Mode.QUERY) }
        while (!factory.wasCalled) yield()
        consent = false
        client.cancelOngoingRequests()
        val result = request.await()
        assertEquals(EmbeddingWorkerClient.BlockReason.CONSENT_REQUIRED, (result as EmbeddingWorkerClient.Result.Blocked).reason)
        assertTrue(factory.call?.isCanceled == true)
    }

    private fun client(
        factory: FakeCallFactory,
        baseUrl: String = WORKER_BASE,
        offline: Boolean = false,
        consent: Boolean = true,
        consentProvider: (() -> Boolean)? = null,
    ) = EmbeddingWorkerClient(
        baseUrl = baseUrl,
        isOfflineOnly = { offline },
        isRemoteAiConsentGranted = consentProvider ?: { consent },
        callFactory = factory,
        authTokenProvider = { "firebase-auth-test-token" },
        appCheckTokenProvider = { "app-check-test-token" },
    )

    private class FakeCallFactory(
        private val responseBuilder: ((Request) -> String)? = { successPayload() },
    ) : Call.Factory {
        @Volatile
        var wasCalled = false
            private set

        @Volatile
        var call: FakeCall? = null
            private set

        override fun newCall(request: Request): Call {
            wasCalled = true
            return FakeCall(request, responseBuilder).also { call = it }
        }
    }

    private class FakeCall(
        private val request: Request,
        private val responseBuilder: ((Request) -> String)?,
    ) : Call {
        @Volatile private var canceled = false
        @Volatile private var executed = false
        @Volatile private var callback: Callback? = null

        override fun request(): Request = request
        override fun execute(): Response = error("Synchronous execute must not be used")

        override fun enqueue(responseCallback: Callback) {
            executed = true
            callback = responseCallback
            val body = responseBuilder?.invoke(request) ?: return
            responseCallback.onResponse(
                this,
                Response.Builder()
                    .request(request)
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(body.toResponseBody("application/json; charset=utf-8".toMediaType()))
                    .build(),
            )
        }

        override fun cancel() {
            canceled = true
            callback?.onFailure(this, IOException("Canceled"))
        }

        override fun isExecuted(): Boolean = executed
        override fun isCanceled(): Boolean = canceled
        override fun timeout(): Timeout = Timeout.NONE
        override fun clone(): Call = FakeCall(request, responseBuilder)
    }

    companion object {
        private const val WORKER_BASE = "https://drive-semantic-embed.awasthi-sach.workers.dev"

        private fun successPayload(
            count: Int = 1,
            model: String = "gemini-embedding-2",
            version: String = "4",
            dimension: Int = 768,
            firstValue: Double = 0.25,
        ): String {
            val vector = (0 until dimension).joinToString(",") { index ->
                if (index == 0) firstValue.toString() else "0.0"
            }
            val embeddings = (0 until count).joinToString(",") { "[$vector]" }
            return "{\"embeddings\":[$embeddings],\"model\":\"$model\",\"version\":\"$version\",\"dimension\":$dimension}"
        }
    }
}
