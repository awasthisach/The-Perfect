package com.vvf.smartmanager.core.cloud.gdrive

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Streaming
import okhttp3.ResponseBody

/**
 * Google Drive REST API v3 (https://www.googleapis.com/drive/v3/).
 * Authenticated via Bearer access token from Credential Manager / OAuth.
 */
interface DriveApi {

    @GET("files")
    suspend fun listFiles(
        @Header("Authorization") bearer: String,
        @Query("q") query: String? = null,
        @Query("spaces") spaces: String = "drive",
        @Query("fields") fields: String = "files(id,name,mimeType,size,modifiedTime,parents)",
        @Query("pageSize") pageSize: Int = 100
    ): DriveFileListResponse

    @GET("files/{fileId}")
    @Streaming
    suspend fun downloadFile(
        @Header("Authorization") bearer: String,
        @Path("fileId") fileId: String,
        @Query("alt") alt: String = "media"
    ): ResponseBody

    @Multipart
    @POST("files?uploadType=multipart")
    suspend fun uploadFile(
        @Header("Authorization") bearer: String,
        @Part("metadata") metadata: RequestBody,
        @Part file: MultipartBody.Part
    ): DriveFileDto

    @GET("about")
    suspend fun about(
        @Header("Authorization") bearer: String,
        @Query("fields") fields: String = "storageQuota"
    ): DriveAboutResponse
}

@JsonClass(generateAdapter = true)
data class DriveFileListResponse(
    @Json(name = "files") val files: List<DriveFileDto> = emptyList()
)

@JsonClass(generateAdapter = true)
data class DriveFileDto(
    @Json(name = "id") val id: String? = null,
    @Json(name = "name") val name: String? = null,
    @Json(name = "mimeType") val mimeType: String? = null,
    @Json(name = "size") val size: String? = null,
    @Json(name = "modifiedTime") val modifiedTime: String? = null
)

@JsonClass(generateAdapter = true)
data class DriveAboutResponse(
    @Json(name = "storageQuota") val storageQuota: DriveStorageQuota? = null
)

@JsonClass(generateAdapter = true)
data class DriveStorageQuota(
    @Json(name = "limit") val limit: String? = null,
    @Json(name = "usage") val usage: String? = null
)
