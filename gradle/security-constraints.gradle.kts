// Security floors participate in normal conflict resolution: newer requests win.
// These exact Netty modules share the 4.1 version line. tcnative and incubator
// artifacts have independent version schemes and must never receive this floor.
val nettyModules = listOf(
  "netty-buffer", "netty-codec-http2", "netty-codec-http", "netty-codec-socks",
  "netty-codec", "netty-common", "netty-handler-proxy", "netty-handler",
  "netty-resolver", "netty-transport-native-unix-common", "netty-transport"
)
val securityFloors = nettyModules.map { "io.netty:$it:4.1.139.Final" } + listOf(
  "org.bouncycastle:bcprov-jdk18on:1.85",
  "org.bouncycastle:bcpkix-jdk18on:1.85",
  "org.bouncycastle:bcutil-jdk18on:1.85",
  "org.apache.commons:commons-lang3:3.18.0",
  "org.apache.httpcomponents:httpclient:4.5.14",
  "com.google.firebase:firebase-components:19.0.1",
  "org.jetbrains:annotations:26.1.0"
)
configurations.configureEach {
  if (isCanBeDeclared) {
    val configurationName = name
    securityFloors.forEach { coordinate ->
      project.dependencies.constraints.add(configurationName, coordinate) {
        because("Minimum reviewed security version; do not downgrade newer dependencies")
      }
    }
  }
  resolutionStrategy.eachDependency {
    // Repair only the known vulnerable Android request, preserving variant and
    // newer requests. All other dependency floors use constraints above.
    if (requested.group == "com.google.guava" && requested.name == "guava" && requested.version == "28.1-android") {
      useVersion("33.4.8-android")
    }
  }
}
