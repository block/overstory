# Contributing to Overstory

Submit changes through pull requests with the owners in [CODEOWNERS](CODEOWNERS).
For bug reports, include a minimal reproduction, expected behavior, and the Android, Kotlin,
and Compose versions you used. Remove sensitive information from logs and screenshots.

Use the build and validation commands in [README](README.md). Keep API dumps under each module's
`api/` directory; run `./gradlew updateKotlinAbi` only for intentional API changes and review the diff.
Use `./gradlew ktfmtFormat` to format Kotlin and
`./gradlew -p consumer-test -PoverstoryVersion=0.1.0-SNAPSHOT ktfmtFormat` for the consumer.
Keep reusable behavior tests with the library and application-specific visual and policy tests
with the application. Use the existing Apache 2.0 copyright header in new Kotlin files.
Preserve existing copyright notices and upstream attribution when importing code.

Changes to Compose or savedstate versions must run the complete view-compat suite, including the
[saveable-registry contract tests](docs/saveable-registry.md). Do not remove its compatibility
workaround without validating state preservation across wrapper changes and Activity recreation.

Follow the [governance](GOVERNANCE.md),
[code of conduct](https://github.com/block/.github/blob/main/CODE_OF_CONDUCT.md), and [license](LICENSE).
