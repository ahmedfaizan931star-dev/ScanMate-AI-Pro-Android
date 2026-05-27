# Android 9–16 Compatibility Notes and Android 17 Readiness

## Current SDK strategy

- `minSdk = 24`, so Android 7+ is technically supported.
- Android 9 support is included because Android 9 is API 28 and is above the min SDK.
- Default `compileSdk` / `targetSdk` are set through `gradle.properties` for easy upgrades:
  - `SCANMATE_COMPILE_SDK=35`
  - `SCANMATE_TARGET_SDK=35`
- Android 16 devices can run apps targeting older SDKs, but future Play Store requirements may require increasing target SDK.

## Android 9

- CameraX and ML Kit are kept within modern AndroidX-supported ranges.
- App files are stored in app-managed external files directories, avoiding broad shared-storage assumptions.
- Debug/release builds do not require new Android-only APIs at runtime.

## Android 10 scoped storage

- Scans, PDFs, QR images, OCR text and backups use `context.getExternalFilesDir(...)`.
- Sharing uses `FileProvider`, not raw `file://` URIs.
- This avoids legacy broad storage permissions.

## Android 11+ storage behavior

- No `MANAGE_EXTERNAL_STORAGE` permission is used.
- File Manager screen lists app-managed files only.
- Import uses Android content pickers instead of direct shared-storage scraping.

## Android 12+

- Manifest uses explicit `android:exported="true"` for the launcher activity.
- Backup/data extraction XML files are present.
- Camera permission is requested at runtime.

## Android 13

- No notification feature is currently used, so `POST_NOTIFICATIONS` is not requested.
- Media access is handled through content pickers, so broad media permissions are avoided.

## Android 14

- Runtime camera permission flow remains explicit.
- File sharing remains `FileProvider` based.
- No background service or alarm behavior requiring Android 14-specific declarations is introduced.

## Android 15/16

- `enableEdgeToEdge()` is enabled in `MainActivity`.
- Theme code updates status/navigation bar icon appearance for light/dark mode.
- UI uses touch-friendly Material 3 components, rounded cards, and app-safe storage.
- Default target SDK is stable for Colab builds. For full Android 16 targeting, install SDK platform 36 and build with:

```bash
./gradlew assembleDebug -PSCANMATE_COMPILE_SDK=36 -PSCANMATE_TARGET_SDK=36
```

## Android 17 readiness

The build is now easier to upgrade because:

- SDK values are Gradle properties, not hardcoded in multiple files.
- Signing is environment-based.
- Package structure is still simple and beginner-editable.
- Core features are offline-safe and do not depend on hidden backend infrastructure.
- Version catalog centralizes AGP/Kotlin/KSP/AndroidX versions.

When Android 17 SDK/platform is available, upgrade in this order:

1. Install the Android 17 SDK platform and build tools.
2. Update AGP/Gradle only if required.
3. Update `SCANMATE_COMPILE_SDK` and `SCANMATE_TARGET_SDK`.
4. Run `assembleDebug`, then `assembleRelease`, then `bundleRelease`.
5. Test camera, OCR, QR scanning, file sharing, PDF export, ZIP backup and AI key flow on a real device.
