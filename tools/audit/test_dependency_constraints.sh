#!/usr/bin/env bash
# Resolve synthetic local Maven fixtures with the actual production constraint script.
# No network or fabricated compliance report is involved.
set -euo pipefail
repo_root="$(cd "$(dirname "$0")/../.." && pwd)"
fixture_root="$(mktemp -d)"
trap 'rm -rf "$fixture_root"' EXIT
python3 - "$repo_root" "$fixture_root" <<'PY'
from pathlib import Path
import json,sys,zipfile
repo,root=map(Path,sys.argv[1:])
cases=[
 ('commonsOld','org.apache.commons','commons-lang3','3.17.0','3.18.0'),
 ('commonsNew','org.apache.commons','commons-lang3','3.19.0','3.19.0'),
 ('firebaseNew','com.google.firebase','firebase-components','20.0.0','20.0.0'),
 ('nettyOld','io.netty','netty-common','4.1.120.Final','4.1.139.Final'),
 ('nettyNew','io.netty','netty-common','4.2.0.Final','4.2.0.Final'),
 ('nativeIndependent','io.netty','netty-tcnative-boringssl-static','2.0.70.Final','2.0.70.Final'),
 ('incubatorIndependent','io.netty','netty-incubator-codec-http3','0.0.29.Final','0.0.29.Final'),
 ('pkixOld','org.bouncycastle','bcpkix-jdk18on','1.79','1.85'),
 ('pkixNew','org.bouncycastle','bcpkix-jdk18on','1.86','1.86'),
 ('guavaOld','com.google.guava','guava','28.1-android','33.4.8-android'),
 ('guavaNew','com.google.guava','guava','99.0-android','99.0-android'),
]
for _,group,name,version,expected in cases:
 for value in {version,expected}:
  path=root/'maven'/group.replace('.','/')/name/value;path.mkdir(parents=True,exist_ok=True)
  (path/f'{name}-{value}.pom').write_text(f'<project><modelVersion>4.0.0</modelVersion><groupId>{group}</groupId><artifactId>{name}</artifactId><version>{value}</version></project>')
  with zipfile.ZipFile(path/f'{name}-{value}.jar','w'):pass
(root/'settings.gradle.kts').write_text('rootProject.name = "security-floor-regression"\n')
lines=['repositories { maven { url = uri("maven") } }',f'apply(from = {json.dumps(str(repo/"gradle/security-constraints.gradle.kts"))})']
for config,group,name,version,expected in cases:
 lines += [f'val {config} = configurations.create("{config}")',f'dependencies.add("{config}", "{group}:{name}:{version}")']
lines += ['tasks.register("verifySecurityFloors") { doLast {']
for config,group,name,version,expected in cases:
 lines += [f'check({config}.resolvedConfiguration.resolvedArtifacts.single().moduleVersion.id.version == "{expected}") {{ "{config} resolved incorrectly" }}']
lines += ['println("Verified 11 security-floor resolution cases")','} }']
(root/'build.gradle.kts').write_text('\n'.join(lines)+'\n')
PY
"$repo_root/gradlew" --no-daemon --offline '-Dorg.gradle.jvmargs=-Xmx1024m -Dfile.encoding=UTF-8' --max-workers=2 -p "$fixture_root" verifySecurityFloors --stacktrace
