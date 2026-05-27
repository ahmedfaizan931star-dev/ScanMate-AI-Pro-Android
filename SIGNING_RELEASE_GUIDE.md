# Release Signing Guide

This project uses environment-based signing.

## Generate an upload key locally or in a secure Colab session

```bash
keytool -genkeypair \
  -v \
  -keystore scanmate-upload-key.jks \
  -keyalg RSA \
  -keysize 2048 \
  -validity 10000 \
  -alias upload
```

Store this file securely. Do not commit it to GitHub and do not include it in public ZIP files.

## Sign release builds

```bash
export KEYSTORE_PATH="/content/drive/MyDrive/scanmate-upload-key.jks"
export STORE_PASSWORD="your_store_password"
export KEY_ALIAS="upload"
export KEY_PASSWORD="your_key_password"

./gradlew assembleRelease --no-daemon --stacktrace
./gradlew bundleRelease --no-daemon --stacktrace
```

## Output paths

```text
app/build/outputs/apk/release/app-release.apk
app/build/outputs/bundle/release/app-release.aab
```

If signing variables are missing, the APK output may be unsigned:

```text
app/build/outputs/apk/release/app-release-unsigned.apk
```
