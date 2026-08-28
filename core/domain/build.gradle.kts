plugins {
  alias(libs.plugins.android.library)
  alias(libs.plugins.google.devtools.ksp)
}

android {
  namespace = "com.vvf.smartmanager.core.domain"
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
  api(project(":core:data"))
  api(project(":core:model"))
  api(project(":core:plugin-spi"))
  api(project(":core:cloud-gdrive"))
  api(project(":core:common"))
  implementation(libs.kotlinx.coroutines.core)
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
}
