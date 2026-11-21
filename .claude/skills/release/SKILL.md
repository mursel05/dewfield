---
name: release
description: Cut a Dewfield release, from version bump to tag to the GitHub release with the APK. Use when asked to release, ship or publish a new APK version.
---

# Release

1. Make sure `main` is green in CI: tests, APK build and emulator smoke test.
2. Pick the next version (`MAJOR.MINOR.PATCH`). Then:
   - In `android/build.gradle.kts`, increment `versionCode` by 1 and set `versionName`.
   - In `core/.../screen/TitleScreen.kt`, update the version label.
3. Run `./gradlew :core:test`, then commit with `Release vX.Y.Z`.
4. Tag and push:
   ```bash
   git tag vX.Y.Z && git push origin main vX.Y.Z
   ```
5. `.github/workflows/release.yml` builds the APK and creates the GitHub release with generated notes. Check that the release page has `dewfield-X.Y.Z.apk` attached.
6. If the `DEWFIELD_KEYSTORE_*` secrets aren't set, the APK is signed with the debug key. Say so in the release notes, and remind the owner that the first store release needs a real key (`docs/deployment.md`).

Never commit a keystore or passwords.
