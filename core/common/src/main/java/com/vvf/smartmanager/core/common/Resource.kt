package com.vvf.smartmanager.core.common

/**
 * Generic sealed class for handling domain & UI operation states.
 */
sealed interface Resource<out T> {
    data class Success<out T>(val data: T) : Resource<T>
    data class Error(val message: String, val cause: Throwable? = null) : Resource<Nothing>
    data object Loading : Resource<Nothing>
    data object Idle : Resource<Nothing>
}

inline fun <T> Resource<T>.onSuccess(action: (T) -> Unit): Resource<T> {
    if (this is Resource.Success) action(data)
    return this
}

inline fun <T> Resource<T>.onError(action: (message: String, cause: Throwable?) -> Unit): Resource<T> {
    if (this is Resource.Error) action(message, cause)
    return this
}
