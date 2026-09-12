package com.vvf.smartmanager.core.cloud.gdrive

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory

/** Parses the JSON returned by Drive's multipart upload endpoint. */
internal object DriveUploadResponseParser {
    private val adapter by lazy {
        Moshi.Builder()
            .add(KotlinJsonAdapterFactory())
            .build()
            .adapter(DriveFileDto::class.java)
    }

    fun parse(json: String): DriveFileDto? = adapter.fromJson(json)
}
