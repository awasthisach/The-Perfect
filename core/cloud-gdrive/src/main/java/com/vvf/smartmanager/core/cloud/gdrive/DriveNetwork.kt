package com.vvf.smartmanager.core.cloud.gdrive

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * Builds a Drive API client. Body logging is never enabled to avoid token leakage.
 * Authorization can be supplied per-call via [DriveApi] headers, or via [setDefaultAccessToken].
 */
object DriveNetwork {

    private const val BASE_URL = "https://www.googleapis.com/drive/v3/"

    private val defaultToken = AtomicReference<String?>(null)

    fun setDefaultAccessToken(token: String?) {
        defaultToken.set(token)
    }

    fun createApi(debugLogging: Boolean = false): DriveApi {
        val authInterceptor = Interceptor { chain ->
            val original = chain.request()
            val token = defaultToken.get()
            val request = if (token != null && original.header("Authorization") == null) {
                original.newBuilder()
                    .header("Authorization", "Bearer $token")
                    .build()
            } else {
                original
            }
            chain.proceed(request)
        }

        val logging = HttpLoggingInterceptor().apply {
            // BASIC only in debug builds when explicitly requested; never BODY (tokens).
            level = if (debugLogging) {
                HttpLoggingInterceptor.Level.BASIC
            } else {
                HttpLoggingInterceptor.Level.NONE
            }
        }

        val client = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .addInterceptor(authInterceptor)
            .addInterceptor(logging)
            .build()

        val moshi = Moshi.Builder()
            .add(KotlinJsonAdapterFactory())
            .build()

        return Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(client)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(DriveApi::class.java)
    }
}
