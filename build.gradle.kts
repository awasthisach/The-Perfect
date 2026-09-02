// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
  alias(libs.plugins.android.application) apply false
  alias(libs.plugins.android.library) apply false
  alias(libs.plugins.android.dynamic.feature) apply false
  alias(libs.plugins.kotlin.compose) apply false
  alias(libs.plugins.kotlin.serialization) apply false
  alias(libs.plugins.google.devtools.ksp) apply false
  alias(libs.plugins.hilt) apply false
  alias(libs.plugins.roborazzi) apply false
  alias(libs.plugins.secrets) apply false
  alias(libs.plugins.google.services) apply false
  jacoco
}

subprojects {
  apply(plugin = "jacoco")

  jacoco {
    toolVersion = "0.8.11"
  }
}

tasks.register<Exec>("cpasVerify") {
  group = "cpas"
  description = "Validate CPAS assurance contracts and emit cpas-status.json"
  commandLine("python3", file("tools/audit/cpas_verify.py").absolutePath, "--root", rootDir.absolutePath)
}

