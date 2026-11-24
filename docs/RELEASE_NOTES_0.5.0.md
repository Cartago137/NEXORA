# NEXORA Android 0.5.0

## Changes in this test build

- Added a persistent default emulation speed preference: 1.0x, 1.5x, 2.0x or 3.0x.
- The selected default speed is applied when a game starts; the in-game MENU still allows changing speed during play.
- Added a persistent game-audio volume control (0–100%) in Settings > Audio.
- Replaced placeholder content in Settings > Emulation and Settings > Audio with working controls.
- Bumped Android version to 0.5.0 (versionCode 14).

## Existing behavior retained for testing

- Touch-control editor keeps per-control drag and pinch resizing.
- Touch-control opacity remains independent and is saved automatically.
- The live D-pad retains continuous direction changes and diagonal input.
- Existing native game saves and per-game save-state files are not intentionally modified by these settings.

## Not included in this build

- The 15 user-contributed PNG control sprites are recorded in the repository but are not yet integrated into the in-app atlas.
- Appearance settings remain informational; no theme selector is exposed yet.
- Physical-device validation on the Motorola G54 is still pending.

## Validation

The GitHub Actions Android build for the final source commit must complete successfully before distributing this APK. CI success verifies automated tests and packaging only; it does not replace gameplay validation on a physical device.
