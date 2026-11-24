# NEXORA Android 0.5.1

## Changes in this test build

- Integrated all 15 original user-provided PNG sprites as Android drawable resources.
- Replaced the old atlas artwork in gameplay and the touch-layout editor with the original D-pad, A/B, L/R and neutral system-button artwork, including pressed states.
- Mapped the five D-pad visual states to neutral, up, down, left and right; directional visuals update while the existing touch input is held.
- Kept existing hit targets, native key masks, continuous D-pad direction changes, diagonal input, drag/pinch layout editing and independent opacity settings.
- Retained persistent default emulation speed options (1.0x, 1.5x, 2.0x, 3.0x) and audio volume control (0–100%).
- Bumped Android version to 0.5.1 (versionCode 15).

## Validation still required

- GitHub Actions must complete the Android tests and APK packaging successfully before distributing this build.
- Physical-device validation on the Motorola G54 is still required, especially checking sprite proportions, pressed visuals, button hit areas and editor gestures.
- CI success does not replace gameplay validation on a physical device.

## Data safety

The changes are limited to touch-control artwork and app settings/version metadata. ROM files and existing game save data are not intentionally modified.
