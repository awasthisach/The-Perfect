package com.vvf.smartmanager.di

import javax.inject.Qualifier

/** The live encrypted-vault path shared by repositories, backup, and restore. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
@Target(
    AnnotationTarget.FIELD,
    AnnotationTarget.VALUE_PARAMETER,
    AnnotationTarget.FUNCTION,
    AnnotationTarget.PROPERTY_GETTER
)
annotation class VaultDirectory
