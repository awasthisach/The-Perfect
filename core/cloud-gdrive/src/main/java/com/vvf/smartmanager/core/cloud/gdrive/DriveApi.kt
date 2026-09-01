package com.vvf.smartmanager.core.cloud.gdrive

import com.squareup.moshi.Json
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Streaming

/**
 * Google Drive REST API v3 (https://www.googleapis.com/drive/v3/).
 * Authenticated via Bearer access token from Credential Manager / OAuth.
 *
 * DTOs use Moshi reflection ([com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory])
 * so :core:cloud-gdrive does not require KSP codegen.
 */
interface DriveApi {

    @GET("files")
    suspend fun listFiles(
        @Header("Authorization") bearer: String,
        @Query("q") query: String? = null,
        @Query("spaces") spaces: String = "drive",
        @Query("fields") fields: String = "files(id,name,mimeType,size,modifiedTime,parents,md5Checksum)",
        @Query("pageSize") pageSize: Int = 100
    ): DriveFileListResponse

    @Streaming
    @GET("files/{fileId}")
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
        @Part file: MultipartBody.Part,
        @Query("fields") fields: String = "id,name,mimeType,size,md5Checksum,parents,modifiedTime"
    ): DriveFileDto

    @GET("about")
    suspend fun about(
        @Header("Authorization") bearer: String,
        @Query("fields") fields: String = "storageQuota"
    ): DriveAboutResponse
}

data class DriveFileListResponse(
    @Json(name = "files") val files: List<DriveFileDto> = emptyList()
)

data class DriveFileDto(
    @Json(name = "id") val id: String? = null,
    @Json(name = "name") val name: String? = null,
    @Json(name = "mimeType") val mimeType: String? = null,
    @Json(name = "size") val size: String? = null,
    @Json(name = "modifiedTime") val modifiedTime: String? = null,
    @Json(name = "parents") val parents: List<String> = emptyList(),
    @Json(name = "md5Checksum") val md5Checksum: String? = null
)

data class DriveAboutResponse(
    @Json(name = "storageQuota") val storageQuota: DriveStorageQuota? = null
)

data class DriveStorageQuota(
    @Json(name = "limit") val limit: String? = null,
    @Json(name = "usage") val usage: String? = null
)
