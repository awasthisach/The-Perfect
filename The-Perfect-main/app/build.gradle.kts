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
    versionCode = 1
    versionName = "1.0"
    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  val releaseKeystorePath = System.getenv("KEYSTORE_PATH")
  val releaseStorePassword = System.getenv("STORE_PASSWORD")
  val releaseKeyPassword = System.getenv("KEY_PASSWORD")
  val releaseKeyAlias = System.getenv("KEY_ALIAS") ?: "upload"
  val releaseKeystore = releaseKeystorePath?.let(::file)
  val hasReleaseSigning = releaseKeystore?.isFile == true && !releaseStorePassword.isNullOrBlank() && !releaseKeyPassword.isNullOrBlank()
  val releaseTaskRequested = gradle.startParameter.taskNames.any { it.contains("release", ignoreCase = true) }
  val allowUnsignedRelease = System.getenv("ALLOW_UNSIGNED_RELEASE")?.equals("true", ignoreCase = true) == true

  if (releaseTaskRequested && !hasReleaseSigning && !allowUnsignedRelease) {
    throw GradleException("Release signing material is missing. Set KEYSTORE_PATH, STORE_PASSWORD, KEY_PASSWORD and optionally KEY_ALIAS, or explicitly set ALLOW_UNSIGNED_RELEASE=true for non-production CI verification.")
  }

  signingConfigs {
    create("release") {
      if (hasReleaseSigning) {
        storeFile = releaseKeystore
        storePassword = releaseStorePassword
        keyAlias = releaseKeyAlias
        keyPassword = releaseKeyPassword
      }
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
      if (hasReleaseSigning) signingConfig = signingConfigs.getByName("release")
    }
    debug { signingConfig = signingConfigs.getByName("debugConfig") }
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }
  buildFeatures { compose = true; buildConfig = true }
  testOptions { unitTests { isIncludeAndroidResources = true } }
  dependenciesInfo { includeInApk = false; includeInBundle = true }
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
