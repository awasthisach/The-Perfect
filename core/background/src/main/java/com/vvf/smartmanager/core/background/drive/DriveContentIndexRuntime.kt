package com.vvf.smartmanager.core.background.drive

object DriveContentIndexRuntime {
    @Volatile
    private var coordinator: DriveContentIndexCoordinator? = null

    fun configure(value: DriveContentIndexCoordinator) {
        coordinator = value
    }

    fun coordinatorOrNull(): DriveContentIndexCoordinator? = coordinator
}
