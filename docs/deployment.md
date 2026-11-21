# Building and releasing

## CI (`.github/workflows/ci.yml`)

It runs on every push to `main`, on every pull request, and by hand.

1. **test**
   - Runs `./gradlew :core:test` without the Android module.
   - Test reports are uploaded when it fails.
2. **android**
   - Runs `./gradlew :android:assembleRelease` on a runner with the Android SDK.
   - Uploads `dewfield-<version>.apk` as the `dewfield-apk` artifact.
3. **smoke**
   - Boots an API 34 emulator, installs that APK and checks it starts and enters the game.
   - Uploads screenshots.

## Releases (`.github/workflows/release.yml`)

1. Bump `versionCode` and `versionName` in `android/build.gradle.kts`, and the version label in `TitleScreen`.
2. Commit, then tag and push:
   ```bash
   git tag v0.3.0 && git push origin v0.3.0
   ```
3. The workflow builds the release APK and publishes a GitHub release with the APK attached and generated notes.

## Signing

The release build reads its keystore from environment variables, so no secret lives in the repo:

| Variable | CI secret |
| --- | --- |
| `DEWFIELD_KEYSTORE` (path) | `DEWFIELD_KEYSTORE_BASE64` (the .jks, base64-encoded; CI decodes it to a file) |
| `DEWFIELD_KEYSTORE_PASSWORD` | `DEWFIELD_KEYSTORE_PASSWORD` |
| `DEWFIELD_KEY_ALIAS` | `DEWFIELD_KEY_ALIAS` |
| `DEWFIELD_KEY_PASSWORD` | `DEWFIELD_KEY_PASSWORD` |

**Without the secrets**, the release APK is signed with the debug key:

- It is fine for sideloading and testing.
- Android won't install an update signed with a different key over it.
- Before publishing anywhere, create a real upload key and add the secrets:
  ```bash
  keytool -genkeypair -v -keystore dewfield-release.jks -alias dewfield -keyalg RSA -keysize 4096 -validity 10000
  ```
  Keep the keystore and passwords outside the repo, in a password manager.

## Building locally

```bash
# Requires the Android SDK (platform 36) and ANDROID_HOME or local.properties
./gradlew :android:assembleRelease
adb install -r android/build/outputs/apk/release/*.apk
```

## Google Play (later)

- Use `./gradlew :android:bundleRelease` to get an AAB. Enrol in Play App Signing, upload with the upload key, and fill in the content rating (no ads, no data collection).
- The app requests no permissions.
