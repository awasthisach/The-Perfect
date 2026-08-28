plugins {
  alias(libs.plugins.android.library)
  alias(libs.plugins.google.devtools.ksp)
}

android {
  namespace = "com.vvf.smartmanager.core.database"
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

ksp {
  arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
  api(project(":core:model"))
  api(project(":core:common"))
  api(project(":core:security"))
  implementation(libs.androidx.room.runtime)
  implementation(libs.androidx.room.ktx)
  implementation(libs.sqlcipher.android)
  "ksp"(libs.androidx.room.compiler)

  testImplementation(libs.junit)
  testImplementation(libs.robolectric)
  testImplementation(libs.androidx.core)
  testImplementation(libs.kotlinx.coroutines.test)
}
