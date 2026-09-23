# Contributing

Contributions are welcome. Please keep changes focused, reviewable, and compatible with the project's low-permission design.

## Development requirements

- JDK 17
- Gradle 8.13
- Android SDK 36
- Android Build Tools 35.0.0 or newer compatible tools
- Kotlin 2.3.21 / Android Gradle Plugin 8.13.2

The repository currently does not include a Gradle Wrapper binary, so install Gradle 8.13 locally or use an equivalent trusted CI environment.

## Build and verify

```bash
gradle --no-daemon clean testDebugUnitTest lintDebug assembleDebug
```

The APK is written to:

```text
app/build/outputs/apk/debug/app-debug.apk
```

## Pull requests

Before opening a pull request:

1. Keep `minSdk = 29`, `targetSdk = 36`, and `compileSdk = 36` unless the change specifically requires an SDK migration.
2. Do not add storage, media-library, accessibility, overlay, package-list, or root permissions without a clearly documented requirement.
3. Do not add background analytics, telemetry, or detailed user-content logging.
4. Preserve HTTPS-only result navigation and strict temporary-upload URL validation.
5. Run unit tests, Android Lint, and an APK build.
6. Describe any privacy, networking, WebView, or signing implications in the PR.

## Architecture

The implementation is intentionally compact:

- `MainActivity.kt`: Compose UI, image selection/sharing, bounded image decode, result WebView, engine mapping, upload-cache coordination.
- `TemporaryImageUploader.kt`: JPEG encoding, upload size limits, Litterbox retry logic, Uguu fallback, multipart upload, response and URL validation.

Small, cohesive changes are preferred over introducing layers that do not materially improve maintainability or testability.
