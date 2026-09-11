plugins {
  alias(libs.plugins.android.library)
}

android {
  namespace = "com.vvf.smartmanager.core.database.compat"
  compileSdk = 37

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
  // Validate the current SQLCipher Android Room/SQLite integration against the legacy encrypted fixture.
  androidTestImplementation("androidx.sqlite:sqlite:2.7.0")
  androidTestImplementation(libs.androidx.junit)
  androidTestImplementation(libs.androidx.runner)
  androidTestImplementation(libs.androidx.core)
  androidTestImplementation(libs.sqlcipher.android)
}
