package com.vvf.smartmanager.core.background.drive

object DriveEmbeddingRuntime {
    @Volatile
    private var coordinator: DriveEmbeddingIndexCoordinator? = null

    fun configure(value: DriveEmbeddingIndexCoordinator) {
        coordinator = value
    }

    fun coordinatorOrNull(): DriveEmbeddingIndexCoordinator? = coordinator
}
