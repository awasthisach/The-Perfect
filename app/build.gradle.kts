import com.google.gms.googleservices.GoogleServicesPlugin.MissingGoogleServicesStrategy

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.google.devtools.ksp)
  alias(libs.plugins.roborazzi)
  alias(libs.plugins.secrets)
  alias(libs.plugins.google.services)
}

android {
  namespace = "com.vvf.smartmanager"
  compileSdk = 36

  defaultConfig {
    applicationId = "com.vvf.smartmanager"
    minSdk = 24
    targetSdk = 36

    val configuredVersionCode = providers.environmentVariable("VERSION_CODE").orNull?.toIntOrNull() ?: 1
    require(configuredVersionCode in 1..2100000000) { "VERSION_CODE must be between 1 and 2100000000" }
    versionCode = configuredVersionCode

    val configuredVersionName = providers.environmentVariable("VERSION_NAME").orNull ?: "1.0.0"
    require(configuredVersionName.matches(Regex("\\d+\\.\\d+\\.\\d+(?:[-+][0-9A-Za-z.-]+)?"))) {
      "VERSION_NAME must use semantic-version form such as 1.2.3 or 1.2.3-beta.1"
    }
    versionName = configuredVersionName

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  signingConfigs {
    create("release") {
      val keystorePath = providers.environmentVariable("KEYSTORE_PATH").orNull
      if (keystorePath.isNullOrBlank()) {
        throw GradleException("KEYSTORE_PATH is required for release signing")
      }
      val keystoreFile = file(keystorePath)
      require(keystoreFile.isFile) { "KEYSTORE_PATH does not point to a readable keystore: $keystorePath" }
      storeFile = keystoreFile
      storePassword = providers.environmentVariable("STORE_PASSWORD").orNull
      keyAlias = providers.environmentVariable("KEY_ALIAS").orNull
      keyPassword = providers.environmentVariable("KEY_PASSWORD").orNull
      require(!storePassword.isNullOrBlank()) { "STORE_PASSWORD is required for release signing" }
      require(!keyAlias.isNullOrBlank()) { "KEY_ALIAS is required for release signing" }
      require(!keyPassword.isNullOrBlank()) { "KEY_PASSWORD is required for release signing" }
    }
    create("debugConfig") {
      storeFile = file("${rootDir}/debug.keystore")
      storePassword = "android"
      keyAlias = "androiddebugkey"
      keyPassword = "android"
    }
  }

  buildTypes {
    release {
      isCrunchPngs = false
      isMinifyEnabled = true
      isShrinkResources = true
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")

      val requiredSigningVars = listOf("KEYSTORE_PATH", "STORE_PASSWORD", "KEY_ALIAS", "KEY_PASSWORD")
      val missingSigningVars = requiredSigningVars.filter { providers.environmentVariable(it).orNull.isNullOrBlank() }
      if (missingSigningVars.isNotEmpty()) {
        throw GradleException(
          "Release signing is not configured. Set ${missingSigningVars.joinToString()} before running assembleRelease. " +
            "Release builds must never fall back to the Android debug keystore."
        )
      }
      signingConfig = signingConfigs.getByName("release")
    }
    debug { signingConfig = signingConfigs.getByName("debugConfig") }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }
  buildFeatures {
    compose = true
    buildConfig = true
  }
  testOptions { unitTests { isIncludeAndroidResources = true } }
  dependenciesInfo {
    includeInApk = false
    includeInBundle = true
  }
}

secrets {
  propertiesFileName = ".env"
  defaultPropertiesFileName = ".env.example"
  ignoreList.add("FIREBASE_APPCHECK_DEBUG_TOKEN")
}

googleServices { missingGoogleServicesStrategy = MissingGoogleServicesStrategy.WARN }

dependencies {
  implementation(project(":core:common"))
  implementation(project(":core:model"))
  implementation(project(":core:security"))
  implementation(project(":core:database"))
  implementation(project(":core:data"))
  implementation(project(":core:domain"))
  implementation(project(":core:background"))
  implementation(project(":core:cloud-gdrive"))
  implementation(project(":core:plugin-spi"))

  implementation(project(":feature:explorer"))
  implementation(project(":feature:vault"))
  implementation(project(":feature:cleaner"))
  implementation(project(":feature:search"))
  implementation(project(":feature:cloud"))
  implementation(project(":feature:settings"))
  implementation(project(":feature:plugins"))

  implementation(project(":plugins:plugin-ocr"))
  implementation(project(":plugins:plugin-semantic-search"))
  implementation(project(":plugins:plugin-cloud-drivers"))

  implementation(platform(libs.androidx.compose.bom))
  implementation(platform(libs.firebase.bom))
  implementation(libs.androidx.activity.compose)
  implementation(libs.androidx.compose.material.icons.core)
  implementation(libs.androidx.compose.material.icons.extended)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.graphics)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  implementation(libs.androidx.navigation.compose)
  implementation(libs.androidx.room.ktx)
  implementation(libs.androidx.room.runtime)
  implementation(libs.coil.compose)
  implementation(libs.converter.moshi)
  implementation(libs.firebase.ai)
  implementation(libs.firebase.appcheck.recaptcha)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.coroutines.core)
  implementation(libs.logging.interceptor)
  implementation(libs.moshi.kotlin)
  implementation(libs.okhttp)
  implementation(libs.retrofit)
  testImplementation(libs.androidx.compose.ui.test.junit4)
  testImplementation(libs.androidx.core)
  testImplementation(libs.androidx.junit)
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.robolectric)
  testImplementation(libs.roborazzi)
  testImplementation(libs.roborazzi.compose)
  testImplementation(libs.roborazzi.junit.rule)
  androidTestImplementation(platform(libs.androidx.compose.bom))
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  androidTestImplementation(libs.androidx.espresso.core)
  androidTestImplementation(libs.androidx.junit)
  androidTestImplementation(libs.androidx.runner)
  debugImplementation(libs.androidx.compose.ui.test.manifest)
  debugImplementation(libs.androidx.compose.ui.tooling)
  "ksp"(libs.androidx.room.compiler)
  "ksp"(libs.moshi.kotlin.codegen)
}
