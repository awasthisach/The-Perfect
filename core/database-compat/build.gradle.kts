plugins {
  alias(libs.plugins.android.library)
}

android {
  namespace = "com.vvf.smartmanager.core.database.compat"
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
  // sqlcipher-android SQLiteDatabase extends SupportSQLiteDatabase (androidx.sqlite).
  androidTestImplementation("androidx.sqlite:sqlite:2.7.0")
  androidTestImplementation(libs.androidx.junit)
  androidTestImplementation(libs.androidx.runner)
  androidTestImplementation(libs.androidx.core)
  androidTestImplementation(libs.sqlcipher.android.compat)
}
