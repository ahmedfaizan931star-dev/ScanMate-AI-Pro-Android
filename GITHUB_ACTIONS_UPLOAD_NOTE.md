# GitHub Actions Upload Note

Upload the contents of this folder as your repository root.

The repository root must contain:

- `.github/workflows/android-build.yml`
- `app/`
- `gradle/`
- `build.gradle.kts`
- `settings.gradle.kts`
- `gradlew`

After pushing to `main` or `master`, GitHub Actions will build:

- Debug APK
- Release APK
- Release AAB

The workflow uses a clean Gradle 8.9 download in CI because this project ZIP may contain a damaged `gradle-wrapper.jar` on some uploads.
