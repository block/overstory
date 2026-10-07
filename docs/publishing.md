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

The project uses the [Gradle Maven Publish plugin](https://vanniktech.github.io/gradle-maven-publish-plugin/central/).
Before the first release, a maintainer must confirm namespace access, configure the `maven-central`
GitHub environment with required reviewers and allowed release tags, and arrange these secrets:

- `SONATYPE_CENTRAL_USERNAME`
- `SONATYPE_CENTRAL_PASSWORD`
- `GPG_SECRET_KEY` (ASCII-armored signing key; public key available for verification)
- `GPG_SECRET_PASSPHRASE`

Create a tag such as `v0.1.0` or `v0.1.0-alpha.1` on the reviewed commit when ready to release.
The Release workflow derives the artifact version from that tag, reruns the complete Checks
workflow for that revision, and then waits for environment approval before signing and publishing.
Ordinary PR and main builds only stage locally; they cannot publish to Central.

A successful upload may take time to become available in Maven Central. Validate the published
version with the independent consumer before announcing it:

```sh
./gradlew -p consumer-test -PoverstoryVersion=0.1.0 \
  -PoverstoryRepository=https://repo.maven.apache.org/maven2 --refresh-dependencies \
  assembleDebug assembleDebugAndroidTest
# With a connected device:
./gradlew -p consumer-test -PoverstoryVersion=0.1.0 \
  -PoverstoryRepository=https://repo.maven.apache.org/maven2 connectedDebugAndroidTest
```

The repository override is exclusive to the Overstory group, so a remote validation cannot fall
back to staged or Maven Local copies. Check the Central Portal deployment before retrying a failed
release: published release versions are immutable.
