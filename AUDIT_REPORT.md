# ScanMate AI Pro Build/UI Audit

## Build blockers found

1. `gradlew` was not executable in Linux/Colab.
2. `gradle/wrapper/gradle-wrapper.jar` is corrupted, causing `Invalid or corrupt jarfile` / `zip END header not found`.
3. Gradle stack used risky future versions: AGP `9.1.1`, Kotlin `2.2.10`, KSP `2.3.5`, Gradle `9.3.1`.
4. Release signing fell back to a local `my-upload-key.jks` path, which can break release builds in Colab.
5. Debug signing referenced a root `debug.keystore`, which is not needed and can fail when missing.
6. Root project name was generic: `My Application`.
7. Tests referenced old/generated sample names and screenshot tooling that were not aligned with the app.

## Colab blockers found

1. Missing automatic SDK/JDK setup.
2. Missing output path documentation.
3. Missing troubleshooting for Gradle wrapper, `JAVA_HOME`, Android SDK, signing and memory errors.
4. No ready-to-run notebook.
5. Corrupt wrapper JAR made direct `./gradlew` impossible without repair/fallback.

## UI/functionality gaps found

1. Home screen had no search/favorites filter.
2. Document detail did not confirm delete.
3. Rename document was missing.
4. QR/barcode history was missing.
5. File manager route/screen was missing.
6. Settings needed clearer API key, online/offline and privacy/help/about content.
7. PDF export quality options were missing.
8. OCR save/share/copy existed but needed better flow and safer image decoding.
9. Online AI behavior needed clearer offline-safe messaging.
10. Low-memory image handling needed sampled bitmap decoding.
