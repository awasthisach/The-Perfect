plugins {
  alias(libs.plugins.android.library)
  alias(libs.plugins.google.devtools.ksp)
}

android {
  namespace = "com.vvf.smartmanager.core.data"
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
  api(project(":core:model"))
  api(project(":core:common"))
  api(project(":core:database"))
  api(project(":core:security"))
  api(project(":core:plugin-spi"))
  implementation(libs.androidx.core.ktx)
  implementation(libs.kotlinx.coroutines.core)
}
