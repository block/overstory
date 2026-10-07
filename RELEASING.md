# Releasing Overstory

The [Release workflow](.github/workflows/release.yml) publishes signed artifacts to Maven Central
when a version tag is pushed. It publishes `xyz.block.overstory:core` and
`xyz.block.overstory:view-compat` at the version from the tag, without its leading `v`.

## Before the first release

- Verify the publishing credentials can publish under `xyz.block`.
- Configure the `maven-central` GitHub environment with required reviewers and an allowed **tag**
  pattern of `v*`.
- Make these secrets available to the publishing job, either as organization secrets granted to
  this repository, repository secrets, or environment secrets:
  - `SONATYPE_CENTRAL_USERNAME` and `SONATYPE_CENTRAL_PASSWORD`: Central Portal token credentials.
  - `GPG_SECRET_KEY`: ASCII-armored private signing key; its public key must be available for verification.
  - `GPG_SECRET_PASSPHRASE`: signing-key passphrase.

Organization secrets do not need to be copied into the environment. The environment gates the
publishing job; it does not restrict organization secrets to that job.

## Trigger a release

1. Merge the intended changes and check that the selected commit's build and device tests pass.
   Verify the repository is ready for a public artifact release.
2. Choose an unused version; the first planned release is `0.1.0`. Maven Central releases are
   immutable, including prereleases. `-SNAPSHOT` tags are rejected.
3. Fetch and inspect the exact commit, then push only the new tag:

   ```sh
   git fetch origin main --tags
   release_tag=v0.1.0
   release_commit=$(git rev-parse origin/main)
   git show --stat "$release_commit"
   # Confirm this is the reviewed commit you intend to release before continuing.
   git tag -a "$release_tag" "$release_commit" -m "Release $release_tag"
   git push origin "refs/tags/$release_tag"
   ```

The tag push is the trigger; creating a GitHub Release is not required. No version-file edit is
needed: the workflow passes the tag-derived version to Gradle. Do not move or force-push release tags.

## Approve and monitor

Open [Actions → Release](https://github.com/block/overstory/actions/workflows/release.yml) and select
the run for the tag:

1. The workflow validates the version and reruns the complete Checks workflow, including device
   tests and artifact consumer tests, for the tagged revision.
2. Once checks pass, a required reviewer approves the `maven-central` deployment under
   **Review deployments**.
3. The publishing job builds, signs, and uploads using Vanniktech's
   `publishAndReleaseToMavenCentral` task. This requests automatic publication, not an upload-only test.

Publication uses the [Gradle Maven Publish plugin](https://vanniktech.github.io/gradle-maven-publish-plugin/central/).
The workflow does not create a GitHub Release or release notes automatically.

## Verify the published artifacts

Use Java 21 and Android SDK 35 as described in [README.md](README.md). After Central has made the
artifacts available, build the independent consumer against Central, using the version just released:

```sh
release_version=0.1.0
./gradlew -p consumer-test -PoverstoryVersion="$release_version" \
  -PoverstoryRepository=https://repo.maven.apache.org/maven2 --refresh-dependencies \
  assembleDebug assembleDebugAndroidTest
# With a connected device/emulator and animations enabled:
./gradlew -p consumer-test -PoverstoryVersion="$release_version" \
  -PoverstoryRepository=https://repo.maven.apache.org/maven2 connectedDebugAndroidTest
```

The repository override is exclusive to the Overstory group, so these checks cannot fall back to
staged or Maven Local copies. A successful upload or workflow is not by itself proof that Central
has finished distributing the artifacts. Confirm both modules resolve before announcing the release
or updating consumers. A GitHub Release with release notes may then be created against the existing tag.

## If a run fails

- **Before upload:** fix the cause. If source or workflow changes are needed, merge them and use a
  new version/tag. A transient failure can be rerun against the unchanged tag.
- **During or after upload:** inspect the run's deployment ID in the
  [Central Portal](https://central.sonatype.com/publishing/deployments) before retrying. A failed or
  timed-out job can still have uploaded or published artifacts.
- **Already published:** verify that version; never try to overwrite it. Corrections require a new version.
- **Failed/unpublished deployment:** resolve or discard that deployment in the Portal before retrying,
  following its status and errors. Do not blindly rerun an upload with an ambiguous outcome.

For credential-free local staging and artifact inspection, see [docs/publishing.md](docs/publishing.md).
