# Changelog

All notable changes to ImageSeek are documented here.

The format is based on Keep a Changelog. Version numbers follow the Android app's `versionName`.

## [2.1.2] - 2026-09-23

### Added
- Session upload cache: the same selected image and quality setting reuse a temporary image URL for up to 55 minutes.
- Cache is bounded to 8 entries and expired entries are removed before reuse.
- Main-frame WebView errors are surfaced to the UI with a retry action.
- Android Safe Browsing handling for risky pages.
- External-browser action for the current result page.
- Strict 16 KiB maximum response-body size for temporary-upload services.

### Changed
- Rebuilt the main UI as a normal production interface with image preview, quality selector, seven-engine selector, privacy guidance, and upload progress.
- Result-page system Back always returns to ImageSeek's selection screen instead of getting trapped in WebView history.
- WebView debugging is explicitly disabled.
- Debug builds produced from the repository are marked non-debuggable.
- Temporary upload retries use cancellable coroutine delays.
- Litterbox remains the primary temporary host; Uguu is used as fallback.
- WebView navigation allows HTTPS only and disables file/content access, mixed content, pop-up windows, and third-party cookies.
- Images are decoded to a bounded size and re-encoded as JPEG before upload, so original EXIF/GPS metadata is not uploaded with the original file.

### Removed
- Detailed DebugLog subsystem.
- Public debug log files and captured source/upload images.
- Full HTTP request/response logging.
- DEBUG UI text and diagnostic log-path display.
- Debug-only Compose tooling dependencies.

### Fixed
- Result-page Back behavior on Android 16.
- Duplicate uploads when switching search engines after returning to the selection screen.
- Duplicate WebView loads while switching engines.
- Stale upload results replacing newer searches.

## [2.1.1] - 2026-09-18

### Changed
- Hardened temporary upload retry/fallback handling.
- Improved WebView result navigation and renderer cleanup.
- Added detailed temporary debug tracing for validation during development.

## [2.1.0]

### Added
- Seven reverse-image-search engines.
- Three image preprocessing quality levels.
- Android Photo Picker, file selection, and `ACTION_SEND image/*` support.
