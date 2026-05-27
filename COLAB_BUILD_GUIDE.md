# ScanMate AI Pro — Google Colab Build Guide

This project is prepared to build Android APK/AAB files from Google Colab without deleting existing app features.

## What the Colab build supports

- Debug APK without a private keystore.
- Release APK/AAB with environment-based signing when you provide keystore variables.
- Unsigned release APK/AAB when signing variables are not provided.
- Linux-compatible Gradle commands.
- Automatic Gradle fallback when the uploaded ZIP contains a corrupted `gradle-wrapper.jar`.
- Android SDK/JDK setup notes for Colab.

## Recommended Colab flow

1. Upload this project ZIP to Colab.
2. Extract it.
3. Open or run `colab-build.ipynb`.
4. Run all cells from top to bottom.

## One-cell command flow after SDK setup

From the project root:

```bash
chmod +x ./gradlew
./gradlew clean --no-daemon
./gradlew assembleDebug --no-daemon --stacktrace
./gradlew assembleRelease --no-daemon --stacktrace
./gradlew bundleRelease --no-daemon --stacktrace
```

## Output paths

Debug APK:

```text
app/build/outputs/apk/debug/app-debug.apk
```

Release APK without signing variables:

```text
app/build/outputs/apk/release/app-release-unsigned.apk
```

Release APK with signing variables:

```text
app/build/outputs/apk/release/app-release.apk
```

Release AAB:

```text
app/build/outputs/bundle/release/app-release.aab
```

The notebook also copies found APK/AAB files to:

```text
/content/scanmate-outputs/
```

## Debug APK build

Debug builds do not require your private keystore:

```bash
./gradlew assembleDebug --no-daemon --stacktrace
```

## Release signing from environment variables

The project no longer depends on a hardcoded local keystore path. To create a signed release, set these environment variables before running `assembleRelease` or `bundleRelease`:

```bash
export KEYSTORE_PATH="/content/drive/MyDrive/scanmate-upload-key.jks"
export STORE_PASSWORD="your_store_password"
export KEY_ALIAS="upload"
export KEY_PASSWORD="your_key_password"
```

Then run:

```bash
./gradlew assembleRelease --no-daemon --stacktrace
./gradlew bundleRelease --no-daemon --stacktrace
```

If those variables are missing, release builds remain unsigned instead of failing because of a fake local keystore path.

## Version management

Defaults are in `gradle.properties`:

```properties
VERSION_CODE=2
VERSION_NAME=1.1.0-colab-ready
SCANMATE_COMPILE_SDK=35
SCANMATE_TARGET_SDK=35
```

Override in Colab:

```bash
./gradlew assembleDebug -PVERSION_CODE=3 -PVERSION_NAME=1.2.0
```

For a future Android 16/17 target SDK upgrade after the matching SDK/platform and AGP support are installed:

```bash
./gradlew assembleDebug -PSCANMATE_COMPILE_SDK=36 -PSCANMATE_TARGET_SDK=36
```

## Troubleshooting

### `JAVA_HOME` error

Install JDK 17 and export the path:

```bash
apt-get update -y
apt-get install -y openjdk-17-jdk
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
export PATH=$JAVA_HOME/bin:$PATH
java -version
```

### `Permission denied: ./gradlew`

Run:

```bash
chmod +x ./gradlew
```

### Corrupted Gradle wrapper JAR

This ZIP originally had a broken wrapper JAR. The `gradlew` launcher now detects that and downloads the Gradle distribution from `gradle-wrapper.properties` when running in Colab.

You can check it with:

```bash
./scripts/doctor.sh
```

### Android SDK missing

Install command-line tools and SDK packages in Colab. The notebook does this automatically. Required packages:

```text
platform-tools
platforms;android-35
build-tools;35.0.0
```

### Dependency version conflict

Run:

```bash
./gradlew clean --refresh-dependencies --no-daemon
./gradlew assembleDebug --no-daemon --stacktrace
```

The project was moved from risky future versions to a stable AGP/Kotlin/KSP combination:

```text
AGP 8.7.3
Kotlin 2.0.21
KSP 2.0.21-1.0.27
Gradle 8.9
```

### AGP/Kotlin mismatch

Do not upgrade Kotlin without also updating KSP to the matching version. Keep these aligned in `gradle/libs.versions.toml`.

### Signing config error

Use environment variables. Do not hardcode your keystore inside Git or ZIP files.

```bash
export KEYSTORE_PATH="/content/drive/MyDrive/your-key.jks"
export STORE_PASSWORD="..."
export KEY_ALIAS="upload"
export KEY_PASSWORD="..."
```

### Colab memory/RAM issue

This project uses conservative Gradle settings:

```properties
org.gradle.jvmargs=-Xmx3g -XX:MaxMetaspaceSize=768m
org.gradle.workers.max=2
kotlin.compiler.execution.strategy=in-process
```

If Colab still runs out of memory, build one artifact at a time:

```bash
./gradlew clean --no-daemon
./gradlew assembleDebug --no-daemon
```

## Production build caution

Do not claim a build is production-ready until `assembleRelease` and/or `bundleRelease` pass in your Colab runtime. This package is prepared for Colab, but final production status depends on the build result, signing, manual QA, and Play Console checks.
