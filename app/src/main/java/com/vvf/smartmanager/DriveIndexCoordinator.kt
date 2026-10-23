package com.vvf.smartmanager
import kotlinx.coroutines.sync.Mutex
/** Serializes full-list checkpoints with index replacement in this single-process application. */
internal object DriveIndexCoordinator { val mutex = Mutex() }
