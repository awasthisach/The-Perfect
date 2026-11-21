pluginManagement {
  repositories {
    google {
      content {
        includeGroupByRegex("com\\.android.*")
        includeGroupByRegex("com\\.google.*")
        includeGroupByRegex("androidx.*")
      }
    }
    mavenCentral()
    gradlePluginPortal()
  }
}

// This project sets Java source/target compatibility to 17 and does not declare Gradle Java toolchains.
// CI and Android Studio provide the JDK; no resolver plugin is needed during dependency analysis.

dependencyResolutionManagement {
  repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
  repositories {
    google()
    mavenCentral()
  }
}

rootProject.name = "VVF Smart Manager"

include(":app")

// Core modules
include(":core:common")
include(":core:model")
include(":core:security")
include(":core:database")
include(":core:database-compat")
include(":core:data")
include(":core:domain")
include(":core:background")
include(":core:cloud-gdrive")
include(":core:plugin-spi")

// Feature modules
include(":feature:explorer")
include(":feature:vault")
include(":feature:cleaner")
include(":feature:search")
include(":feature:cloud")
include(":feature:settings")
include(":feature:plugins")

// Dynamic Feature Plugin placeholders
include(":plugins:plugin-ocr")
include(":plugins:plugin-semantic-search")
include(":plugins:plugin-cloud-drivers")
