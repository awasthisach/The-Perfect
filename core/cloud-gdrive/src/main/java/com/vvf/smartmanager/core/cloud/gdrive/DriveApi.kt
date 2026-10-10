package com.vvf.smartmanager.core.cloud.gdrive

import com.squareup.moshi.Json
import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.PATCH
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Streaming

/** Google Drive REST API v3. Tokens are passed explicitly and must never be logged. */
interface DriveApi {
    @GET("files")
    suspend fun listFiles(
        @Header("Authorization") bearer: String,
        @Query("q") query: String? = null,
        @Query("spaces") spaces: String = "drive",
        @Query("fields") fields: String = "nextPageToken,files(id,name,mimeType,size,modifiedTime,parents,md5Checksum,starred,webViewLink)",
        @Query("pageSize") pageSize: Int = 100,
        @Query("pageToken") pageToken: String? = null,
        @Query("orderBy") orderBy: String? = "name"
    ): DriveFileListResponse

    @GET("changes/startPageToken")
    suspend fun getStartPageToken(@Header("Authorization") bearer: String): DriveStartPageToken

    @GET("changes")
    suspend fun listChanges(
        @Header("Authorization") bearer: String,
        @Query("pageToken") pageToken: String,
        @Query("pageSize") pageSize: Int = 100,
        @Query("includeRemoved") includeRemoved: Boolean = true,
        @Query("fields") fields: String = "nextPageToken,newStartPageToken,changes(fileId,removed,file(id,name,mimeType,size,modifiedTime,parents,md5Checksum,starred,webViewLink))"
    ): DriveChangesResponse

    @PATCH("files/{fileId}")
    suspend fun updateFile(
        @Header("Authorization") bearer: String,
        @Path("fileId") fileId: String,
        @Body metadata: RequestBody,
        @Query("addParents") addParents: String? = null,
        @Query("removeParents") removeParents: String? = null,
        @Query("fields") fields: String = "id,name,mimeType,parents,modifiedTime,starred"
    ): DriveFileDto

    @GET("files/{fileId}")
    suspend fun getFile(
        @Header("Authorization") bearer: String,
        @Path("fileId") fileId: String,
        @Query("fields") fields: String = "id,name,mimeType,parents,modifiedTime,starred"
    ): DriveFileDto

    @Streaming
    @GET("files/{fileId}")
    suspend fun downloadFile(
        @Header("Authorization") bearer: String,
        @Path("fileId") fileId: String,
        @Query("alt") alt: String = "media"
    ): ResponseBody

    @GET("about")
    suspend fun about(@Header("Authorization") bearer: String, @Query("fields") fields: String = "storageQuota"): DriveAboutResponse
}
data class DriveFileListResponse(@Json(name = "files") val files: List<DriveFileDto> = emptyList(), @Json(name = "nextPageToken") val nextPageToken: String? = null)
data class DriveStartPageToken(@Json(name = "startPageToken") val startPageToken: String)
data class DriveChangesResponse(@Json(name = "changes") val changes: List<DriveChangeDto> = emptyList(), @Json(name = "nextPageToken") val nextPageToken: String? = null, @Json(name = "newStartPageToken") val newStartPageToken: String? = null)
data class DriveChangeDto(@Json(name = "fileId") val fileId: String, @Json(name = "removed") val removed: Boolean = false, @Json(name = "file") val file: DriveFileDto? = null)
data class DriveFileDto(
    @Json(name = "id") val id: String? = null,
    @Json(name = "name") val name: String? = null,
    @Json(name = "mimeType") val mimeType: String? = null,
    @Json(name = "size") val size: String? = null,
    @Json(name = "modifiedTime") val modifiedTime: String? = null,
    @Json(name = "parents") val parents: List<String> = emptyList(),
    @Json(name = "md5Checksum") val md5Checksum: String? = null,
    @Json(name = "starred") val starred: Boolean = false,
    @Json(name = "webViewLink") val webViewLink: String? = null
)
data class DriveAboutResponse(@Json(name = "storageQuota") val storageQuota: DriveStorageQuota? = null)
data class DriveStorageQuota(@Json(name = "limit") val limit: String? = null, @Json(name = "usage") val usage: String? = null)
