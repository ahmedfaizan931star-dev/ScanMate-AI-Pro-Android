# ScanMate AI Pro — Principal Audit + Targeted Stability Fix Report

## Scope
This pass focused on safe production hardening of the existing app without rebuilding, removing features, renaming package/app branding, or changing the offline-first architecture.

## What was broken / risky
1. **Onboarding persistence race**: `MainActivity` initialized onboarding as `false`, so a returning user could briefly or permanently land on onboarding before DataStore emitted the real value.
2. **CameraX lifecycle cleanup gap**: `CameraScreen` bound the camera provider but did not explicitly unbind when leaving the composable.
3. **Detached coroutine jobs**: `DocumentDetailScreen` used `kotlinx.coroutines.MainScope()` in document OCR/export helpers. These jobs could continue after screen disposal.
4. **AI model default stale**: Gemini default model still pointed to the older 2.5 Flash while the app already had 3-series options.
5. **Network readiness was optimistic**: `NetworkUtils` treated `NET_CAPABILITY_INTERNET` as enough even when Android had not validated real internet access.
6. **UX wording issue**: QR center badge copy used the word “placeholder,” which weakens the product feel and can imply fake UI.

## What was fixed
- Added a real onboarding loading gate before creating the navigation graph.
- Prevented incorrect onboarding relaunch by waiting for DataStore before selecting the start destination.
- Added explicit CameraX provider unbind cleanup when the camera screen leaves composition.
- Removed duplicate camera switch branch expression.
- Replaced detached `MainScope()` calls with the active screen/composable `CoroutineScope`.
- Updated the default Gemini model to `gemini-3.5-flash` and reordered models toward current production-first options while preserving 2.5 fallbacks.
- Updated Settings copy to match the new default model.
- Tightened online detection by requiring Android validated internet.
- Reworded QR badge UX copy to avoid placeholder/prototype language.

## Files modified
- `app/src/main/java/com/synthbyte/scanmate/MainActivity.kt`
- `app/src/main/java/com/synthbyte/scanmate/ui/screens/CameraScreen.kt`
- `app/src/main/java/com/synthbyte/scanmate/ui/screens/DocumentDetailScreen.kt`
- `app/src/main/java/com/synthbyte/scanmate/domain/GeminiModels.kt`
- `app/src/main/java/com/synthbyte/scanmate/ui/screens/SettingsScreen.kt`
- `app/src/main/java/com/synthbyte/scanmate/ui/screens/QrScreen.kt`
- `app/src/main/java/com/synthbyte/scanmate/utils/NetworkUtils.kt`

## Validation performed in this sandbox
- Confirmed project structure exists with Compose screens, Room/DataStore, CameraX, ML Kit OCR/barcode, PDF/ZIP utilities, widgets, and GitHub Actions.
- Verified Gradle wrapper JAR is structurally readable.
- Verified GitHub Actions YAML parses successfully.
- Ran static source scans for `MainScope`, placeholder/fake/mock markers, and major TODO/FIXME markers.
- Ran naive brace-balance static checks across Kotlin source files.

## Build limitation
`./gradlew --version` could not complete in this sandbox because the wrapper attempted to download Gradle 8.9 from `services.gradle.org`, and outbound network resolution is unavailable here. On GitHub Actions this should work because the workflow runs on Ubuntu with internet and already includes `chmod +x ./gradlew` before build commands.

## Commands to run on GitHub Actions / laptop
```bash
chmod +x ./gradlew
./gradlew clean --no-daemon --stacktrace
./gradlew assembleDebug --no-daemon --stacktrace
./gradlew assembleRelease --no-daemon --stacktrace
./gradlew bundleRelease --no-daemon --stacktrace
```

## Remaining limitations
- Full device verification still requires a real Android device/emulator for CameraX preview, capture, QR scanning, OCR latency, PDF opening, sharing intents, and dark/light UI inspection.
- Gradle compile was not completed in this sandbox due blocked Gradle distribution download.
- Larger UI transformation can continue safely after the current stability fixes compile in CI.
