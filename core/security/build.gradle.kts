plugins {
  alias(libs.plugins.android.library)
  alias(libs.plugins.google.devtools.ksp)
}

android {
  namespace = "com.vvf.smartmanager.core.security"
  compileSdk = 36

  defaultConfig {
    minSdk = 24
    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }
}

dependencies {
  api(project(":core:common"))
  api(project(":core:model"))
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.security.crypto)
  implementation(libs.kotlinx.coroutines.core)
  implementation(libs.hilt.android)

  testImplementation(libs.junit)
  testImplementation(libs.robolectric)
  testImplementation(libs.androidx.core)
}
