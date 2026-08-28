plugins {
  alias(libs.plugins.android.library)
  alias(libs.plugins.google.devtools.ksp)
}

android {
  namespace = "com.vvf.smartmanager.plugin.semanticsearch"
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
  api(project(":core:plugin-spi"))
  api(project(":core:model"))
  api(project(":core:common"))
  implementation(libs.androidx.core.ktx)
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
}
