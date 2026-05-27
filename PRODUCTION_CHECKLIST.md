# ScanMate AI Pro Production Checklist

## Build

- [ ] `./gradlew clean` passes.
- [ ] `./gradlew assembleDebug` passes.
- [ ] `./gradlew assembleRelease` passes.
- [ ] `./gradlew bundleRelease` passes.
- [ ] Release signing variables are set in CI/Colab, not hardcoded.
- [ ] APK/AAB output files are downloaded and tested.

## App quality

- [ ] Camera permission flow works.
- [ ] Scan capture works on a real Android phone.
- [ ] Image import works.
- [ ] OCR extraction works.
- [ ] PDF export works at Small, Balanced and High quality.
- [ ] QR generation, saving and sharing work.
- [ ] QR/barcode scan-from-gallery works.
- [ ] ZIP backup works.
- [ ] File Manager lists and shares app-managed files.
- [ ] AI assistant shows a clear missing-key state.
- [ ] Gemini API request works after adding a user key.
- [ ] Offline mode still supports core scanner/files/export features.

## Android compatibility

- [ ] Test Android 9 physical or emulator device.
- [ ] Test Android 10 scoped storage behavior.
- [ ] Test Android 11+ file sharing.
- [ ] Test Android 12+ permission behavior.
- [ ] Test Android 13 without notification permission.
- [ ] Test Android 14/15/16 edge-to-edge and navigation behavior.

## Store readiness

- [ ] Replace `PRIVACY_NOTICE.md` placeholder with final privacy policy.
- [ ] Confirm app icon/adaptive icon quality.
- [ ] Confirm app name and package/applicationId.
- [ ] Create signed release AAB.
- [ ] Run Play Console pre-launch report.
- [ ] Do manual QA on low-end devices.
