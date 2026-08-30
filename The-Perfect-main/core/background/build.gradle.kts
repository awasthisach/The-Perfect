plugins {
  alias(libs.plugins.android.library)
  alias(libs.plugins.google.devtools.ksp)
}

android {
  namespace = "com.vvf.smartmanager.core.background"
  compileSdk = 36

  defaultConfig {
    minSdk = 24
    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }
}

dependencies {
  api(project(":core:domain"))
  api(project(":core:common"))
  implementation(libs.androidx.work.runtime.ktx)
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.androidx.work.testing)
  testImplementation(libs.robolectric)
  testImplementation(libs.androidx.core)
}
