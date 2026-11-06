package com.vvf.smartmanager.core.cloud.gdrive

/**
 * Signals that Drive authorization needs an interactive user-consent resolution.
 *
 * Callers must route the user through AuthorizationClient.authorize() and launch its
 * PendingIntent; silently retrying the protected Drive request cannot grant consent.
 */
class DriveAuthorizationRequiredException(message: String) : Exception(message)
