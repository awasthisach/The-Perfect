// Top-level build file where you can add configuration options common to all sub-projects.
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

  // Keep known vulnerable transitive libraries on security-fixed versions.
  // These constraints apply consistently to production and test configurations.
  configurations.configureEach {
    resolutionStrategy.eachDependency {
      when {
        requested.group == "io.netty" && requested.name.startsWith("netty-") ->
          useVersion("4.1.139.Final")
        requested.group == "org.bouncycastle" && requested.name == "bcprov-jdk18on" ->
          useVersion("1.85")
        requested.group == "org.apache.commons" && requested.name == "commons-lang3" ->
          useVersion("3.18.0")
        requested.group == "org.apache.httpcomponents" && requested.name == "httpclient" ->
          useVersion("4.5.14")
      }
    }
  }
}

tasks.register<Exec>("cpasVerify") {
  group = "cpas"
  description = "Validate CPAS assurance contracts and emit cpas-status.json"
  commandLine("python3", file("tools/audit/cpas_verify.py").absolutePath, "--root", rootDir.absolutePath)
}
