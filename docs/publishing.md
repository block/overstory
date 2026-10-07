# Artifact staging and releases

The Maven coordinates are `xyz.block.overstory:core` and `xyz.block.overstory:view-compat`.
Before creating release tags, verify that publishing credentials have access to `xyz.block`
and that the release environment protections are configured.
No public release is available yet.
Kotlin package names remain `com.squareup.ui.compose.overlays` and its subpackages.

## Local validation

Use Java 21 and Android SDK 35 as described in the README. No publication credentials are needed.

```sh
./gradlew -Pversion=0.1.0-SNAPSHOT testDebugUnitTest checkKotlinAbi ktfmtCheck lintRelease assembleDebugAndroidTest publishAllPublicationsToStagingRepository
python3 scripts/verify-artifacts.py 0.1.0-SNAPSHOT
./gradlew -p consumer-test -PoverstoryVersion=0.1.0-SNAPSHOT ktfmtCheck assembleDebug assembleDebugAndroidTest
./gradlew :core:connectedDebugAndroidTest :view-compat:connectedDebugAndroidTest
./gradlew -p consumer-test -PoverstoryVersion=0.1.0-SNAPSHOT connectedDebugAndroidTest
```

Staging writes AARs, source JARs, Dokka documentation JARs, POMs, module metadata, and checksums
to `build/repository`. All three archive types contain the full root license at `META-INF/LICENSE`.
The independent consumer resolves those Maven artifacts rather than project dependencies.
Keep device animation scales enabled; the sheet tests exercise animations.

For Maven Local, use `./gradlew -Pversion=0.1.0-SNAPSHOT publishToMavenLocal` and configure the
application to resolve `xyz.block.overstory` from Maven Local. Remove the override after testing.

## Maven Central releases

See [RELEASING.md](../RELEASING.md) for credentials and environment setup, the tag command that
triggers publication, approval, remote artifact validation, and recovery after a failed run.
