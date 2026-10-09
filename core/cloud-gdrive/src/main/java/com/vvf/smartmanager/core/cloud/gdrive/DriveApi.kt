package com.vvf.smartmanager.core.cloud.gdrive

import com.squareup.moshi.Json
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.PATCH
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Streaming

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
        @Query("fields") fields: String = "nextPageToken,files(id,name,mimeType,size,modifiedTime,parents,md5Checksum,starred,webViewLink)",
        @Query("pageSize") pageSize: Int = 100,
        @Query("pageToken") pageToken: String? = null
    ): DriveFileListResponse

    @GET("files/{fileId}")
    suspend fun getFile(
        @Header("Authorization") bearer: String,
        @Path("fileId") fileId: String,
        @Query("fields") fields: String = "id,name,mimeType,size,modifiedTime,parents,md5Checksum,starred,webViewLink"
    ): DriveFileDto

    @PATCH("files/{fileId}")
    suspend fun updateFile(
        @Header("Authorization") bearer: String,
        @Path("fileId") fileId: String,
        @Body metadata: RequestBody,
        @Query("addParents") addParents: String? = null,
        @Query("removeParents") removeParents: String? = null,
        @Query("fields") fields: String = "id,name,mimeType,size,modifiedTime,parents,starred,webViewLink"
    ): DriveFileDto

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

    @POST("files")
    suspend fun createFolder(
        @Header("Authorization") bearer: String,
        @Body metadata: RequestBody,
        @Query("fields") fields: String = "id,name,mimeType,parents,modifiedTime"
    ): DriveFileDto

    @GET("about")
    suspend fun about(
        @Header("Authorization") bearer: String,
        @Query("fields") fields: String = "user(emailAddress,displayName),storageQuota"
    ): DriveAboutResponse
}

data class DriveFileListResponse(
    @Json(name = "files") val files: List<DriveFileDto> = emptyList(),
    @Json(name = "nextPageToken") val nextPageToken: String? = null
)

data class DriveFileDto(
    @Json(name = "id") val id: String? = null,
    @Json(name = "name") val name: String? = null,
    @Json(name = "mimeType") val mimeType: String? = null,
    @Json(name = "size") val size: String? = null,
    @Json(name = "modifiedTime") val modifiedTime: String? = null,
    @Json(name = "parents") val parents: List<String> = emptyList(),
    @Json(name = "md5Checksum") val md5Checksum: String? = null,
    @Json(name = "starred") val starred: Boolean? = null,
    @Json(name = "webViewLink") val webViewLink: String? = null
)

data class DriveAboutResponse(
    @Json(name = "storageQuota") val storageQuota: DriveStorageQuota? = null,
    @Json(name = "user") val user: DriveUser? = null
)

data class DriveUser(
    @Json(name = "emailAddress") val emailAddress: String? = null,
    @Json(name = "displayName") val displayName: String? = null
)

data class DriveStorageQuota(
    @Json(name = "limit") val limit: String? = null,
    @Json(name = "usage") val usage: String? = null
)
