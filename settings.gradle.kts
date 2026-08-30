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

plugins { id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0" }

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
