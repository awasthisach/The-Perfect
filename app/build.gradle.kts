import com.google.gms.googleservices.GoogleServicesPlugin.MissingGoogleServicesStrategy

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.google.devtools.ksp)
  alias(libs.plugins.hilt)
  alias(libs.plugins.roborazzi)
  alias(libs.plugins.secrets)
  alias(libs.plugins.google.services)
}


// Fixed debug keystore — stable SHA-1 for Google Sign-In (code 10 DEVELOPER_ERROR).
// SHA-1: CA:7D:39:65:CA:FE:58:93:29:C3:9D:B4:C7:EA:AB:B9:C3:B9:7E:16
// Google Cloud Console → Credentials → Android OAuth client:
//   package: com.vvf.smartmanager
//   SHA-1:   CA:7D:39:65:CA:FE:58:93:29:C3:9D:B4:C7:EA:AB:B9:C3:B9:7E:16
val vvfDebugKeystoreFile = rootProject.file("app/vvf-debug.keystore")
if (!vvfDebugKeystoreFile.exists()) {
  val b64 = "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"
  vvfDebugKeystoreFile.parentFile?.mkdirs()
  vvfDebugKeystoreFile.writeBytes(java.util.Base64.getDecoder().decode(b64))
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
    versionName = configuredVersionName
    require(configuredVersionName.matches(Regex("\\d+\\.\\d+\\.\\d+(?:[-+][0-9A-Za-z.-]+)?"))) {
      "VERSION_NAME must use semantic-version form such as 1.2.3 or 1.2.3-beta.1"
    }

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  signingConfigs {
    create("release") {
      val keystorePath = providers.environmentVariable("KEYSTORE_PATH").orNull
      if (!keystorePath.isNullOrBlank()) storeFile = file(keystorePath)
      storePassword = providers.environmentVariable("STORE_PASSWORD").orNull
      keyAlias = providers.environmentVariable("KEY_ALIAS").orNull
      keyPassword = providers.environmentVariable("KEY_PASSWORD").orNull
    }
    getByName("debug") {
      storeFile = vvfDebugKeystoreFile
      storePassword = "vvfdebug123"
      keyAlias = "vvfdebug"
      keyPassword = "vvfdebug123"
    }
  }

  buildTypes {
    release {
      isCrunchPngs = false
      isMinifyEnabled = true
      isShrinkResources = true
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      signingConfig = signingConfigs.getByName("release")
    }
    debug {
      signingConfig = signingConfigs.getByName("debug")
    }
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
    includeInApk = true
    includeInBundle = true
  }
}

secrets {
  propertiesFileName = ".env"
  defaultPropertiesFileName = ".env.example"
  ignoreList.add("FIREBASE_APPCHECK_DEBUG_TOKEN")
}

val releaseTaskRequested = gradle.startParameter.taskNames.any {
  it.contains("release", ignoreCase = true) || it.contains("bundle", ignoreCase = true)
}

if (releaseTaskRequested) {
  val requiredSigningVars = listOf("KEYSTORE_PATH", "STORE_PASSWORD", "KEY_ALIAS", "KEY_PASSWORD")
  val missingSigningVars = requiredSigningVars.filter { providers.environmentVariable(it).orNull.isNullOrBlank() }
  if (missingSigningVars.isNotEmpty()) {
    throw GradleException(
      "Release signing is not configured. Set ${missingSigningVars.joinToString()} before running a release task. " +
        "Release builds must never fall back to the Android debug keystore."
    )
  }
  val releaseKeystore = providers.environmentVariable("KEYSTORE_PATH").orNull!!
  require(file(releaseKeystore).isFile) { "KEYSTORE_PATH does not point to a readable keystore: $releaseKeystore" }
  if (!file("google-services.json").isFile) {
    throw GradleException(
      "google-services.json is required for production release builds. " +
        "Provide the production Firebase configuration through the release secret injection step."
    )
  }
}

googleServices { missingGoogleServicesStrategy = MissingGoogleServicesStrategy.IGNORE }

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
  implementation(libs.androidx.work.runtime.ktx)
  implementation(libs.coil.compose)
  implementation(libs.converter.moshi)
  implementation(libs.firebase.ai)
  implementation(libs.firebase.appcheck.recaptcha)
  implementation(libs.hiltAndroidRuntime)
  implementation("com.google.dagger:dagger:2.60.1")
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
  "ksp"(libs.hiltCompiler)
  "ksp"(libs.androidx.room.compiler)
  "ksp"(libs.moshi.kotlin.codegen)
}
