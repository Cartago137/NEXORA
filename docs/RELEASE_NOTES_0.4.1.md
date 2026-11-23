# NEXORA Android 0.4.1 — Gameplay controls visual update

## Included
- Replaces placeholder gameplay controls with the approved purple NEXORA sprite atlas.
- Adds the neutral D-pad and four directional pressed states (up, down, left, right).
- Adds pressed visual states for A, B, L, R, START, SELECT and MENU.
- Uses the approved round blank sprite for MENU, SELECT and START.
- Corrects the shoulder-button artwork placement: L label at the end and R label at the beginning.
- Adds a dark-purple control shell with subtle star sparkles.
- Preserves the existing continuous D-pad drag input and native key masks.
- Keeps the full game frame centered in landscape and above the control deck in portrait.

## Validation
- Automated Android build and JVM unit tests run in GitHub Actions for this commit.
- Physical Motorola G54 validation remains required for touch feel, sizing, orientation, audio and emulation regression.
- No claim of physical-device acceptance is made by this source change.
