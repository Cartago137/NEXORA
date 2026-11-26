# NEXORA 0.4.3

## Android controls-atlas crash fix

- Replaced the Compose `ImageBitmap.imageResource` atlas loading path with direct Android `BitmapFactory.decodeResource` decoding.
- Added bounds checks for the expected 5-column, 3-row sprite atlas before drawing a cell.
- Added a lightweight vector fallback for touch controls if the optional atlas cannot be decoded, preventing the game screen from crashing during control composition.
- Preserved the existing sprite cell mapping, touch input behavior, and native GBA core flow.

## Validation

- CI build and tests: pending.
- Physical-device validation on the Motorola G54: pending.
- The 0.4.2 crash log showed the mGBA core and per-game battery save loading successfully before a Compose `NullPointerException` in `NexoraAtlasSprite`; this release addresses that UI failure path. It does not claim device-level resolution until retested.

- CI trigger commit: build verification for the 0.4.3 candidate.
