package com.vvf.smartmanager.core.domain.cloud

/**
 * PROD-002: Upload results must be durable provider identifiers, never blank/optimistic placeholders.
 * Fail-closed: empty or whitespace remote IDs are rejected before any SUCCESS state is published.
 */
object DurableUploadContract {
    fun requireDurableRemoteId(remoteId: String?): String {
        val normalized = remoteId?.trim().orEmpty()
        require(normalized.isNotEmpty()) {
            "Cloud upload returned a blank remote identifier; refusing to mark the operation durable"
        }
        return normalized
    }

    fun isDurableRemoteId(remoteId: String?): Boolean =
        !remoteId.isNullOrBlank()
}
